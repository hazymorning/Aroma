package app.aroma.data.network

import app.aroma.data.AromaError
import app.aroma.data.isConnectivity
import app.aroma.data.toAromaError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Whether Mealie answers. The [NetworkMonitor] says whether the phone has a network at all, and
 * every request to the server, through [answered] and [unanswered], whether Mealie answered it.
 *
 * While Mealie is out of reach, screens show what the phone keeps at once instead of waiting for
 * requests that go unanswered until they time out; see [whileReachable]. Aroma asks Mealie again
 * when a network joins, when a screen wanted something from it, and, only while the app is in view,
 * after pauses that grow to half a minute. [isReachable] turns true the moment Mealie answers,
 * which has the screens load again.
 */
class ServerConnection(
    networks: NetworkMonitor,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    /** The smallest request there is, to see whether Mealie answers. */
    private val ping: suspend () -> Unit,
) {
    private val reachable = MutableStateFlow(true)

    /** False while Mealie is out of reach: the phone has no network, or the last request found no answer. */
    val isReachable: StateFlow<Boolean> = reachable.asStateFlow()

    @Volatile
    private var hasNetwork = true

    /** When Mealie last answered or failed to. */
    @Volatile
    private var lastHeard: TimeMark? = null

    private var asking: Job? = null

    init {
        scope.launch {
            networks.isOnline.collect { online ->
                hasNetwork = online
                when {
                    !online -> reachable.value = false

                    // Another way to the server: it may answer through it.
                    !reachable.value -> ask(now = true)
                }
            }
        }
    }

    /** A request to Mealie got an answer. */
    fun answered() {
        lastHeard = timeSource.markNow()
        reachable.value = true
    }

    /** A request to Mealie got no answer. */
    fun unanswered() {
        lastHeard = timeSource.markNow()
        reachable.value = false
    }

    /** Starts over for another sign-in, whose server is taken to answer until a request finds otherwise. */
    fun reset() {
        lastHeard = null
        reachable.value = hasNetwork
    }

    /**
     * Runs [block], which asks Mealie for something a screen can do without: at once fails with
     * [AromaError.Offline] while Mealie is out of reach, and gives up the same way as soon as another
     * request finds it so, as [block] would only wait for its own timeout. Mealie is asked again soon.
     */
    suspend fun <T> whileReachable(block: suspend () -> T): T {
        if (!reachable.value) {
            ask()
            throw AromaError.Offline()
        }
        return coroutineScope {
            val lost =
                launch {
                    reachable.first { !it }
                    throw AromaError.Offline()
                }
            try {
                block()
            } finally {
                lost.cancel()
            }
        }
    }

    /**
     * Asks whether Mealie answers, unless that is being asked already, Mealie was heard from moments
     * ago or there is no network to ask through; [now] asks anyway, as a network just joined.
     */
    fun ask(now: Boolean = false) {
        synchronized(this) {
            val heardLately = lastHeard?.let { it.elapsedNow() < ASK_GAP } == true
            if (!now && (!hasNetwork || asking?.isActive == true || heardLately)) return
            asking?.cancel()
            asking = scope.launch { askOnce() }
        }
    }

    private suspend fun askOnce() {
        val answers =
            try {
                withTimeout(ASK_LIMIT) { ping() }
                true
            } catch (_: TimeoutCancellationException) {
                false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Any answer, even an error, is Mealie answering.
                !error.toAromaError().isConnectivity
            }
        if (answers) answered() else unanswered()
    }

    /**
     * For as long as the app is in view: while Mealie is out of reach although the phone has a
     * network, asks again after pauses that grow to half a minute. Without a network there is
     * nobody to ask; the network's return brings a question of its own.
     */
    suspend fun askWhileInView() {
        reachable.collectLatest { answers ->
            if (answers) return@collectLatest
            var pause = FIRST_PAUSE
            while (true) {
                ask()
                delay(pause)
                pause = (pause * 2).coerceAtMost(LONGEST_PAUSE)
            }
        }
    }

    private companion object {
        /** Mealie answers its smallest request well within this, or not at all. */
        val ASK_LIMIT = 10.seconds

        /** Questions a screen raises come no closer together than this. */
        val ASK_GAP = 5.seconds

        val FIRST_PAUSE = 5.seconds
        val LONGEST_PAUSE = 30.seconds
    }
}
