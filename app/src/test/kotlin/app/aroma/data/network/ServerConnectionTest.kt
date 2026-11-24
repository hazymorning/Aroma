package app.aroma.data.network

import app.aroma.data.AromaError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConnectionTest {
    private val network = MutableStateFlow(true)
    private var answers = false
    private var asked = 0

    private fun TestScope.connection() =
        ServerConnection(
            object : NetworkMonitor {
                override val isOnline = network
            },
            backgroundScope,
            testScheduler.timeSource,
        ) {
            asked++
            if (!answers) throw ConnectException("no route")
        }

    @Test
    fun `out of reach, Mealie is asked again only while the app is in view, until it answers`() =
        runTest {
            val connection = connection()
            runCurrent()
            connection.unanswered()
            advanceTimeBy(10.minutes)
            runCurrent()
            assertEquals("nothing is asked while the app is out of view", 0, asked)

            val inView = backgroundScope.launch { connection.askWhileInView() }
            runCurrent()
            assertEquals("coming into view asks at once", 1, asked)
            advanceTimeBy(5.seconds + 1.seconds)
            assertEquals(2, asked)

            answers = true
            advanceTimeBy(30.seconds)
            assertTrue(connection.isReachable.value)
            val settled = asked
            advanceTimeBy(10.minutes)
            assertEquals("once Mealie answers, it is not asked again", settled, asked)
            inView.cancel()
        }

    @Test
    fun `without a network Mealie is out of reach at once, and asked as soon as a network joins`() =
        runTest {
            val connection = connection()
            runCurrent()
            network.value = false
            runCurrent()
            assertFalse(connection.isReachable.value)
            assertEquals(0, asked)

            answers = true
            network.value = true
            runCurrent()
            assertEquals(1, asked)
            assertTrue(connection.isReachable.value)
        }

    @Test
    fun `a screen gives up waiting as soon as another request finds Mealie out of reach, and does not wait at all after`() =
        runTest {
            val connection = connection()
            val never = CompletableDeferred<Unit>()
            val waiting = async { runCatching { connection.whileReachable { never.await() } } }
            runCurrent()
            connection.unanswered()
            runCurrent()
            assertTrue(waiting.await().exceptionOrNull() is AromaError.Offline)

            val atOnce = runCatching { connection.whileReachable { never.await() } }
            assertTrue(atOnce.exceptionOrNull() is AromaError.Offline)
        }
}
