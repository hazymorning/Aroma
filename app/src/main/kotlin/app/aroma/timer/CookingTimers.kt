package app.aroma.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * A running or paused countdown. [endsAt] is on the elapsed-realtime clock and null while paused;
 * [durationMillis] is how long it was set for, to show how much of it is left.
 */
data class CookingTimer(
    val id: Long,
    val label: String,
    val recipeName: String,
    val endsAt: Long?,
    val remainingMillis: Long,
    val durationMillis: Long = remainingMillis,
) {
    val isRunning: Boolean get() = endsAt != null

    fun remaining(now: Long): Long = endsAt?.let { (it - now).coerceAtLeast(0) } ?: remainingMillis
}

/**
 * Timers survive leaving the cooking screen. Each running timer also has an alarm,
 * so the notification arrives even when Aroma is in the background.
 */
class CookingTimers(private val context: Context, scope: CoroutineScope) {
    private val mutableTimers = MutableStateFlow<List<CookingTimer>>(emptyList())
    val timers: StateFlow<List<CookingTimer>> = mutableTimers.asStateFlow()

    private val mutableNow = MutableStateFlow(SystemClock.elapsedRealtime())

    /** Ticks once per second while any timer runs. */
    val now: StateFlow<Long> = mutableNow.asStateFlow()

    private val alarms = context.getSystemService(AlarmManager::class.java)

    val canUseExactAlarms: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    init {
        // One loop ticks on whole seconds while any timer runs; collectLatest ends it once none does.
        scope.launch {
            mutableTimers
                .map { list -> list.any { it.isRunning } }
                .distinctUntilChanged()
                .collectLatest { running ->
                    while (running) {
                        tick()
                        delay(1000 - SystemClock.elapsedRealtime() % 1000)
                    }
                }
        }
    }

    fun start(seconds: Int, label: String, recipeName: String) {
        val now = SystemClock.elapsedRealtime()
        val timer = CookingTimer(System.nanoTime(), label, recipeName, now + seconds * 1000L, seconds * 1000L)
        mutableTimers.update { it + timer }
        schedule(timer)
    }

    fun toggle(id: Long) {
        val now = SystemClock.elapsedRealtime()
        val toggled =
            mutableTimers
                .updateAndGet { list ->
                    list.map { timer ->
                        when {
                            timer.id != id -> timer
                            timer.isRunning -> timer.copy(endsAt = null, remainingMillis = timer.remaining(now))
                            else -> timer.copy(endsAt = now + timer.remainingMillis)
                        }
                    }
                }.firstOrNull { it.id == id } ?: return
        if (toggled.isRunning) schedule(toggled) else cancelAlarm(toggled)
    }

    fun dismiss(id: Long) {
        mutableTimers.value.firstOrNull { it.id == id }?.let { cancelAlarm(it) }
        mutableTimers.update { list -> list.filterNot { it.id == id } }
    }

    fun dismissAll() {
        mutableTimers.value.forEach(::cancelAlarm)
        mutableTimers.value = emptyList()
    }

    /**
     * Sets the running timers' alarms again once Aroma may set exact ones. From Android 14 on it may not
     * at first, and allowing it in the system's settings leaves the inexact alarms already set as they are.
     */
    fun useExactAlarms() {
        if (canUseExactAlarms) mutableTimers.value.filter { it.isRunning }.forEach(::schedule)
    }

    /** Moves [now] on and rings the timers that have ended. */
    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        mutableNow.value = now
        val finished = mutableTimers.value.filter { it.isRunning && it.remaining(now) == 0L }
        if (finished.isEmpty()) return
        mutableTimers.update { list ->
            list.map { timer -> if (timer in finished) timer.copy(endsAt = null, remainingMillis = 0) else timer }
        }
        finished.forEach { timer ->
            cancelAlarm(timer)
            TimerNotifications.showFinished(context, timer.id, timer.recipeName, timer.label)
        }
    }

    private fun schedule(timer: CookingTimer) {
        val endsAt = timer.endsAt ?: return
        val pending = alarmIntent(timer)
        if (canUseExactAlarms) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endsAt, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endsAt, pending)
        }
    }

    private fun cancelAlarm(timer: CookingTimer) = alarms.cancel(alarmIntent(timer))

    private fun alarmIntent(timer: CookingTimer): PendingIntent {
        val intent =
            Intent(context, TimerAlarmReceiver::class.java)
                .putExtra(TimerAlarmReceiver.EXTRA_ID, timer.id)
                .putExtra(TimerAlarmReceiver.EXTRA_RECIPE, timer.recipeName)
                .putExtra(TimerAlarmReceiver.EXTRA_LABEL, timer.label)
        return PendingIntent.getBroadcast(
            context,
            timer.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
