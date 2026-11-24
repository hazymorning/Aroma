package app.aroma.data

import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.aroma.data.reminder.RatingReminder
import app.aroma.data.session.Session
import app.aroma.data.settings.SettingsRepository
import app.aroma.feature.home.HomeViewModel
import app.aroma.feature.recipes.RecipesViewModel
import app.aroma.feature.shopping.ShoppingViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * The screens against a server that stops answering: each shows what the phone keeps at once, says
 * it is offline, and loads again by itself as soon as Mealie answers, sending what waited meanwhile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineScreensTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private lateinit var env: TestEnvironment
    private val settings by lazy { SettingsRepository(PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb") }) }

    /** A server that takes the requests but never answers them, as one out of reach of the phone's network. */
    @Volatile
    private var silent = false

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    if (silent) {
                        return MockResponse
                            .Builder()
                            .headersDelay(1, TimeUnit.HOURS)
                            .body("{}")
                            .build()
                    }
                    val path = request.url.encodedPath
                    return when {
                        path == "/api/app/about" -> json("""{"version":"v2.8.0"}""")
                        path == "/api/recipes" -> json(listing())
                        path.startsWith("/api/recipes/") -> recipe(path.removePrefix("/api/recipes/"))
                        path == "/api/users/self/ratings" -> json("""{"ratings":[]}""")
                        path.startsWith("/api/organizers/") || path == "/api/households/cookbooks" -> json("""{"items":[]}""")
                        path == "/api/households/mealplans" -> json("""{"items":[]}""")
                        path == "/api/households/shopping/lists" -> json("""{"items":[{"id":"$LIST","name":"Markt"}]}""")
                        path == "/api/households/shopping/lists/$LIST" -> json("""{"id":"$LIST","name":"Markt","listItems":[]}""")
                        path == "/api/households/shopping/items" -> json("""{"createdItems":[]}""")
                        else -> MockResponse.Builder().code(404).build()
                    }
                }
            }
        server.start()
        env = TestEnvironment()
        runBlocking {
            env.sessions.start(Session(serverUrl = server.url("/").toString().trimEnd('/'), token = "t", userId = "u", username = "mara"))
            env.recipes.downloadAll()
            env.recipes.recent().last()
            env.shopping.lists().last()
            env.shopping.refresh(LIST)
        }
    }

    @After
    fun tearDown() {
        env.close()
        server.close()
    }

    @Test
    fun `without a network, the downloaded recipes show at once, search at once and nothing waits for Mealie`() {
        goOffline()
        val library = RecipesViewModel(env.recipes, env.connection)
        val shown =
            until {
                library.state.value.recipes
                    .isNotEmpty()
            }
        assertEquals(
            NAMES.sorted(),
            library.state.value.recipes
                .map { it.name }
                .sorted(),
        )
        assertTrue(library.state.value.isOffline)

        library.onQueryChange("linsen")
        val searched =
            until {
                library.state.value.recipes
                    .map { it.name } == listOf("Linsensuppe")
            }
        assertTrue("shown after $shown ms, searched after $searched ms", shown < AT_ONCE && searched < AT_ONCE + DEBOUNCE)
        assertEquals("Mealie was not asked", 0, requests.size)
    }

    @Test
    fun `without a network, home shows its stored sections at once and says it is offline`() {
        goOffline()
        val home = HomeViewModel(env.recipes, env.mealPlans, env.sessions, env.auth, reminder(), env.connection)
        val shown = until { !home.state.value.isLoading && home.state.value.isOffline }
        assertTrue("shown after $shown ms", shown < AT_ONCE)
        assertEquals(
            NAMES.sorted(),
            home.state.value.recent
                .map { it.name }
                .sorted(),
        )
    }

    @Test
    fun `once Mealie answers again, the screens load by themselves and what waited is sent`() {
        goOffline()
        runBlocking { env.shopping.add(LIST, "Milch") }
        val library = RecipesViewModel(env.recipes, env.connection)
        val shopping = ShoppingViewModel(env.shopping, settings, env.connection)
        until { library.state.value.isOffline && shopping.state.value.isOffline && shopping.state.value.pendingChanges == 1 }

        silent = false
        env.network.isOnline.value = true
        val back = until { !library.state.value.isOffline && !shopping.state.value.isOffline && shopping.state.value.pendingChanges == 0 }
        assertTrue("back after $back ms", back < AT_ONCE)
        assertTrue(requests.any { it.method == "POST" && it.url.encodedPath == "/api/households/shopping/items" })
        assertEquals(0, runBlocking { env.shopping.pendingCount(LIST).first() })
    }

    /** The phone loses its network; Mealie would not answer anyway. */
    private fun goOffline() {
        silent = true
        env.network.isOnline.value = false
        until { !env.connection.isReachable.value }
        requests.clear()
    }

    private fun reminder() = RatingReminder(PreferenceDataStoreFactory.create { folder.newFile("reminder.preferences_pb") })

    /** Lets the main thread run, where the view models work, until [done]; returns how long that took. */
    private fun until(done: () -> Boolean): Long {
        val start = System.nanoTime()
        repeat(1000) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            if (done()) return (System.nanoTime() - start) / 1_000_000
            Thread.sleep(10)
        }
        fail("Gave up waiting")
        return -1
    }

    private fun listing() =
        NAMES.joinToString(prefix = """{"page":1,"per_page":-1,"total":${NAMES.size},"total_pages":1,"items":[""", postfix = "]}") {
            """{"id":"id-$it","slug":"${it.lowercase()}","name":"$it","updatedAt":"2026-11-01T10:00:00"}"""
        }

    private fun recipe(slug: String): MockResponse {
        val name = NAMES.firstOrNull { it.lowercase() == slug } ?: return MockResponse.Builder().code(404).build()
        return json("""{"id":"id-$name","slug":"$slug","name":"$name","updatedAt":"2026-11-01T10:00:00"}""")
    }

    private fun json(body: String) =
        MockResponse
            .Builder()
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    private companion object {
        const val LIST = "list-1"
        val NAMES = listOf("Gnocchi", "Linsensuppe", "Ofenkuerbis")

        /** Well under a second: no request is waited for. */
        const val AT_ONCE = 1_500L

        /** The library waits this long after the last key before it searches. */
        const val DEBOUNCE = 300L
    }
}
