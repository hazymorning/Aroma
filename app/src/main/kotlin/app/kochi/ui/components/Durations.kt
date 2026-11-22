package app.kochi.ui.components

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import app.kochi.R
import app.kochi.core.model.RecipeTime

fun Resources.formatDuration(seconds: Int): String {
    if (seconds < 60) return getString(R.string.duration_seconds, seconds)
    val totalMinutes = (seconds + 30) / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> getString(R.string.duration_minutes, minutes)
        minutes == 0 -> getString(R.string.duration_hours, hours)
        else -> getString(R.string.duration_hours_minutes, hours, minutes)
    }
}

/** Short countdown text such as "4:05" or "1:02:30". */
fun formatClock(seconds: Long): String {
    val clamped = seconds.coerceAtLeast(0)
    val h = clamped / 3600
    val m = (clamped % 3600) / 60
    val s = clamped % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
@ReadOnlyComposable
fun recipeTimeText(time: RecipeTime?): String? {
    if (time == null || time.isEmpty) return null
    val seconds = time.seconds
    return if (seconds != null && seconds > 0) LocalResources.current.formatDuration(seconds) else time.text?.trim()
}
