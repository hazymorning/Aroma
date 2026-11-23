package app.aroma.timer

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CookingTimersTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        TimerNotifications.createChannel(context)
    }

    @Test
    fun `a timer started after another has rung counts down and rings as well`() =
        runTest {
            val timers = CookingTimers(context, backgroundScope)
            timers.start(1, "Teig", "Brot")
            runCurrent()
            pass(1_000)

            timers.start(2, "Ofen", "Brot")
            runCurrent()
            pass(1_000)
            val oven = timers.timers.value.last()
            assertEquals(1_000L, oven.remaining(timers.now.value))
            pass(1_000)

            assertTrue(timers.timers.value.none { it.isRunning })
            assertTrue(alarms.scheduledAlarms.isEmpty())
            val rung = notifications.allNotifications
            assertEquals(2, rung.size)
            assertTrue("the alarm may post it too", rung.all { it.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 })
        }

    @Test
    fun `pausing takes back the alarm and going on sets it again`() =
        runTest {
            val timers = CookingTimers(context, backgroundScope)
            timers.start(60, "Teig", "Brot")
            val dough = timers.timers.value.single()

            timers.toggle(dough.id)
            assertTrue(alarms.scheduledAlarms.isEmpty())
            timers.toggle(dough.id)
            assertEquals(1, alarms.scheduledAlarms.size)
        }

    /** Moves on both the clock the timers read and the one their loop waits on. */
    private fun TestScope.pass(millis: Long) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(millis))
        advanceTimeBy(millis)
        runCurrent()
    }
}
