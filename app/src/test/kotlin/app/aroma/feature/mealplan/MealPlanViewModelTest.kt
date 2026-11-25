package app.aroma.feature.mealplan

import androidx.lifecycle.viewModelScope
import app.aroma.data.TestEnvironment
import app.aroma.data.session.Session
import app.aroma.testing.address
import app.aroma.testing.mealieServer
import app.aroma.testing.until
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class MealPlanViewModelTest {
    private val env = TestEnvironment()
    private val opened = mutableListOf<MealPlanViewModel>()

    /** What the server has planned, by id. */
    private val planned = ConcurrentHashMap(mapOf(1 to DAY, 2 to DAY.plusDays(1)))
    private val planReads = AtomicInteger()

    private val server =
        mealieServer { request ->
            val path = request.url.encodedPath
            when {
                request.method == "DELETE" -> {
                    planned.remove(path.substringAfterLast('/').toInt())
                    MockResponse.Builder().code(200).build()
                }

                path.endsWith("/mealplans") -> {
                    planReads.incrementAndGet()
                    val entries = planned.entries.joinToString(",") { (id, date) -> """{"id":$id,"date":"$date","title":"Meal $id"}""" }
                    // As on a phone, the copy kept on it answers well before the server does.
                    MockResponse
                        .Builder()
                        .addHeader("Content-Type", "application/json")
                        .body("""{"items":[$entries]}""")
                        .headersDelay(SERVER_MILLIS, TimeUnit.MILLISECONDS)
                        .build()
                }

                else -> {
                    MockResponse.Builder().code(404).build()
                }
            }
        }

    @Before
    fun setUp() {
        runBlocking { env.sessions.start(Session(serverUrl = server.address, token = "t", userId = "u", username = "mara")) }
    }

    @After
    fun tearDown() {
        // The server's answer to the last read would otherwise reach the plan in the next test.
        opened.forEach { it.viewModelScope.cancel() }
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
                env.settings,
                env.connection,
                Clock.fixed(DAY.atStartOfDay(ZONE).toInstant(), ZONE),
            ).also(opened::add)
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

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 11, 23)
        val ZONE: ZoneId = ZoneId.systemDefault()
        const val SERVER_MILLIS = 50L

        /** Looks a few times at the plan once it is read again, by when the phone's copy has shown. */
        const val SETTLED_POLLS = 5
    }
}
