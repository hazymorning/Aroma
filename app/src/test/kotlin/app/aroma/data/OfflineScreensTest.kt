package app.aroma.data

import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.core.model.Meal
import app.aroma.data.reminder.RatingReminder
import app.aroma.data.session.Session
import app.aroma.feature.home.HomeViewModel
import app.aroma.feature.recipes.RecipesViewModel
import app.aroma.feature.shopping.ShoppingViewModel
import app.aroma.testing.address
import app.aroma.testing.json
import app.aroma.testing.mealieServer
import app.aroma.testing.until
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * The screens against a server that stops answering: each shows what the phone keeps at once, says
 * it is offline, and loads again by itself as soon as Mealie answers, sending what waited meanwhile.
 */
@RunWith(RobolectricTestRunner::class)
class OfflineScreensTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val env = TestEnvironment()
    private val opened = mutableListOf<ViewModel>()

    /** A server that takes the requests but never answers them, as one out of reach of the phone's network. */
    @Volatile
    private var silent = false

    private val server =
        mealieServer { request ->
            requests += request
            if (silent) {
                return@mealieServer MockResponse
                    .Builder()
                    .headersDelay(1, TimeUnit.HOURS)
                    .body("{}")
                    .build()
            }
            val path = request.url.encodedPath
            when {
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

    @Before
    fun setUp() {
        runBlocking {
            env.sessions.start(Session(serverUrl = server.address, token = "t", userId = "u", username = "mara"))
            env.recipes.downloadAll()
            env.recipes.recent().last()
            env.shopping.lists().last()
            env.shopping.refresh(LIST)
        }
    }

    @After
    fun tearDown() {
        opened.forEach { it.viewModelScope.cancel() }
        env.close()
        server.close()
    }

    @Test
    fun `without a network, the downloaded recipes show, and are searched and picked from, without asking Mealie`() {
        goOffline()
        val library = open(RecipesViewModel(env.recipes, env.connection))
        until { library.names().isNotEmpty() }
        assertEquals(NAMES.sorted(), library.names().sorted())
        assertTrue(library.state.value.isOffline)

        library.toggleQuick()
        until { library.names() == listOf("Gnocchi") }

        // Nothing is filed under dinner on this server, so nothing can match and the page says how to file it.
        library.toggleQuick()
        library.toggleMeal(Meal.Dinner)
        until { library.names().isEmpty() && library.state.value.mealNames() == emptyList<String>() }

        library.toggleMeal(Meal.Dinner)
        library.onQueryChange("linsen")
        // The library searches once the typing stops.
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
        until { library.names() == listOf("Linsensuppe") }
        assertEquals("Mealie was not asked", 0, requests.size)
    }

    @Test
    fun `without a network, home shows its stored sections at once and says it is offline`() {
        goOffline()
        val reminderFile = folder.newFile("reminder.preferences_pb")
        val reminder = RatingReminder(PreferenceDataStoreFactory.create { reminderFile })
        val home =
            open(HomeViewModel(env.recipes, env.mealPlans, env.shopping, env.settings, env.sessions, env.auth, reminder, env.connection))
        until { !home.state.value.isLoading && home.state.value.isOffline }
        assertEquals(
            NAMES.sorted(),
            home.state.value.recent
                .map { it.name }
                .sorted(),
        )
        // The silent server never answers, so the page did not wait for what it asked.
        val asked = requests.map { it.url.encodedPath }.filterNot { it == "/api/users/self" }
        assertEquals("Mealie was asked for nothing but the user's name", emptyList<String>(), asked)
    }

    @Test
    fun `once Mealie answers again, the screens load by themselves and what waited is sent`() {
        goOffline()
        runBlocking { env.shopping.add(LIST, "Milch") }
        val library = open(RecipesViewModel(env.recipes, env.connection))
        val shopping = open(ShoppingViewModel(env.shopping, env.settings, env.connection))
        until { library.state.value.isOffline && shopping.state.value.isOffline && shopping.state.value.pendingChanges == 1 }

        silent = false
        env.network.isOnline.value = true
        until { !library.state.value.isOffline && !shopping.state.value.isOffline && shopping.state.value.pendingChanges == 0 }
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

    private fun <T : ViewModel> open(viewModel: T): T = viewModel.also(opened::add)

    private fun RecipesViewModel.names() = state.value.recipes.map { it.name }

    private fun listing() =
        NAMES.joinToString(prefix = """{"page":1,"per_page":-1,"total":${NAMES.size},"total_pages":1,"items":[""", postfix = "]}") {
            """{"id":"id-$it","slug":"${it.lowercase()}","name":"$it","totalTime":"${TIMES[it]}","updatedAt":"2026-11-01T10:00:00"}"""
        }

    private fun recipe(slug: String): MockResponse {
        val name = NAMES.firstOrNull { it.lowercase() == slug } ?: return MockResponse.Builder().code(404).build()
        return json("""{"id":"id-$name","slug":"$slug","name":"$name","updatedAt":"2026-11-01T10:00:00"}""")
    }

    private companion object {
        const val LIST = "list-1"
        val NAMES = listOf("Gnocchi", "Linsensuppe", "Ofenkuerbis")

        /** As Mealie keeps them, as text; only the gnocchi are quick. */
        val TIMES = mapOf("Gnocchi" to "20 Minuten", "Linsensuppe" to "1 Stunde", "Ofenkuerbis" to "")
    }
}
