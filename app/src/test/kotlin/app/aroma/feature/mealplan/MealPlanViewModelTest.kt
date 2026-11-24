package app.aroma.feature.mealplan

import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.aroma.data.TestEnvironment
import app.aroma.data.session.Session
import app.aroma.data.settings.SettingsRepository
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
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MealPlanViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var env: TestEnvironment
    private val settings by lazy { SettingsRepository(PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb") }) }

    /** What the server has planned, by id. */
    private val planned = ConcurrentHashMap(mapOf(1 to DAY, 2 to DAY.plusDays(1)))
    private val planReads = AtomicInteger()

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    if (request.method == "DELETE") {
                        planned.remove(path.substringAfterLast('/').toInt())
                        return MockResponse.Builder().code(200).build()
                    }
                    if (!path.endsWith("/mealplans")) return MockResponse.Builder().code(404).build()
                    planReads.incrementAndGet()
                    val entries = planned.entries.joinToString(",") { (id, date) -> """{"id":$id,"date":"$date","title":"Meal $id"}""" }
                    // As on a phone, the copy kept on it answers well before the server does.
                    return MockResponse
                        .Builder()
                        .addHeader("Content-Type", "application/json")
                        .body("""{"items":[$entries]}""")
                        .headersDelay(SERVER_MILLIS, TimeUnit.MILLISECONDS)
                        .build()
                }
            }
        server.start()
        env = TestEnvironment()
        runBlocking {
            env.sessions.start(Session(serverUrl = server.url("/").toString().trimEnd('/'), token = "t", userId = "u", username = "mara"))
        }
    }

    @After
    fun tearDown() {
        env.close()
        server.close()
    }

    @Test
    fun `a removed meal stays gone while the plan is read again from the copy on the phone and from the server`() {
        val plan =
            MealPlanViewModel(
                env.mealPlans,
                env.recipes,
                env.shopping,
                settings,
                env.connection,
                Clock.fixed(DAY.atStartOfDay(ZONE).toInstant(), ZONE),
            )
        until { plan.shown() == setOf(1, 2) }
        val removed =
            plan.state.value.days
                .flatMap { it.entries }
                .first { it.id == 1 }

        plan.delete(removed)
        val seen = mutableListOf<Set<Int>>()
        until {
            seen += plan.shown()
            planReads.get() == 2 && seen.size > SETTLED_POLLS && seen.takeLast(SETTLED_POLLS).all { it == setOf(2) }
        }

        assertTrue("The removed meal came back: $seen", seen.none { 1 in it })
        assertEquals(setOf(2), plan.shown())
    }

    private fun MealPlanViewModel.shown(): Set<Int> = state.value.days.flatMapTo(mutableSetOf()) { day -> day.entries.map { it.id } }

    /** Lets the main thread run, where the view model's work goes on, until [done]. */
    private fun until(done: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (done()) return
            Thread.sleep(10)
        }
        fail("Gave up waiting")
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 11, 23)
        val ZONE: ZoneId = ZoneId.systemDefault()
        const val SERVER_MILLIS = 200L

        /** Looks long enough at the plan once the server has answered to see it stay as it is. */
        const val SETTLED_POLLS = 30
    }
}
