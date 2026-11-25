package app.aroma.data

import app.aroma.data.session.Session
import app.aroma.testing.address
import app.aroma.testing.json
import app.aroma.testing.mealieServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class MealPlanCacheTest {
    private lateinit var env: TestEnvironment

    /** The days with something planned on the server. */
    private val planned = listOf(DAY, DAY.plusDays(1), DAY.plusDays(6), DAY.plusDays(8))

    private val server =
        mealieServer { request ->
            val start = LocalDate.parse(request.url.queryParameter("start_date"))
            val end = LocalDate.parse(request.url.queryParameter("end_date"))
            val entries = planned.filter { it in start..end }.joinToString(",") { """{"id":${it.dayOfMonth},"date":"$it"}""" }
            json("""{"items":[$entries]}""")
        }

    @Before
    fun setUp() {
        env = TestEnvironment()
        runBlocking {
            env.sessions.start(Session(serverUrl = server.address, token = "t", userId = "u", username = "mara"))
        }
    }

    @After
    fun tearDown() {
        env.close()
        server.close()
    }

    @Test
    fun `offline on the next day, the week cached the day before still shows the days it covers`() =
        runBlocking {
            env.mealPlans.range(DAY, 7).first { !it.isRefreshing }
            server.close()

            val nextDay = env.mealPlans.range(DAY.plusDays(1), 7).first { !it.isRefreshing }

            assertTrue(nextDay.isOffline)
            assertEquals(listOf(DAY.plusDays(1), DAY.plusDays(6)), nextDay.data.map { it.date })
        }

    @Test
    fun `another week waits for the server instead of showing the cached week's empty days`() =
        runBlocking {
            env.mealPlans.range(DAY, 7).first { !it.isRefreshing }

            val nextWeek = env.mealPlans.range(DAY.plusDays(7), 7).first()

            assertEquals(listOf(DAY.plusDays(8)), nextWeek.data.map { it.date })
        }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 11, 23)
    }
}
