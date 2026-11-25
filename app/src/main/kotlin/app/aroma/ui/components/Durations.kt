package app.aroma.ui.components

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import app.aroma.R
import app.aroma.core.format.ReadTime
import app.aroma.core.format.TimeText
import app.aroma.core.model.RecipeTime

/**
 * A duration as Aroma writes times: "45 Min.", "1 Std. 20 Min.", "2 Std.", with a narrow
 * no-break space between number and unit, so the two never part at a line's end.
 */
fun Resources.formatDuration(seconds: Int): String {
    if (seconds < 60) return getString(R.string.duration_seconds, seconds)
    return formatMinutes((seconds + 30) / 60)
}

/** [formatDuration] for text that goes to Mealie, which keeps plain spaces. */
fun Resources.formatDurationPlain(seconds: Int): String = formatDuration(seconds).replace(NARROW_NO_BREAK_SPACE, " ")

private fun Resources.formatMinutes(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> getString(R.string.duration_minutes, minutes)
        minutes == 0 -> getString(R.string.duration_hours, hours)
        else -> getString(R.string.duration_hours_minutes, hours, minutes)
    }
}

/** A time read from a recipe, written the same way: "ca. 2 Std.", "10–15 Min.", "1 Std. 20 Min.". */
fun Resources.formatReadTime(time: ReadTime): String {
    val upTo = time.upTo
    val written =
        when {
            upTo == null -> formatMinutes(time.minutes)
            upTo < 60 -> getString(R.string.duration_minutes_span, time.minutes, upTo)
            time.minutes % 60 == 0 && upTo % 60 == 0 -> getString(R.string.duration_hours_span, time.minutes / 60, upTo / 60)
            else -> formatMinutes(time.minutes) + "–" + formatMinutes(upTo)
        }
    return if (time.prefix.isEmpty()) written else "${time.prefix} $written"
}

/**
 * A recipe's time as every page writes it, or null where it has none: seconds from Mealie, or its
 * text read into minutes. Text that says something else stays as written; nothing at all, or no
 * time at all ("0 min"), is null, so the time is left out rather than shown as nothing.
 */
@Composable
@ReadOnlyComposable
fun recipeTimeText(time: RecipeTime?): String? = LocalResources.current.recipeTimeText(time)

fun Resources.recipeTimeText(time: RecipeTime?): String? {
    if (time == null || time.isEmpty) return null
    val seconds = time.seconds
    if (seconds != null && seconds > 0) return formatDuration(seconds)
    val text = time.text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val read = TimeText.read(text) ?: return text
    return if (read.minutes == 0 && read.upTo == null) null else formatReadTime(read)
}

/** The minutes of [time] where it is one plain time, read from its seconds or its text; null for a span or text. */
fun RecipeTime.minutes(): Int? {
    seconds?.takeIf { it > 0 }?.let { return (it + 30) / 60 }
    return text?.let(TimeText::read)?.takeIf { it.upTo == null && it.prefix.isEmpty() && it.minutes > 0 }?.minutes
}

/** The total time, or prep and cook added up where the total is missing and both are known. */
fun totalTime(total: RecipeTime?, prep: RecipeTime?, cook: RecipeTime?): RecipeTime? {
    if (total != null && !total.isEmpty) return total
    val prepMinutes = prep?.minutes() ?: return null
    val cookMinutes = cook?.minutes() ?: return null
    return RecipeTime((prepMinutes + cookMinutes) * 60, null)
}

/** Short countdown text such as "4:05" or "1:02:30". */
fun formatClock(seconds: Long): String {
    val clamped = seconds.coerceAtLeast(0)
    val h = clamped / 3600
    val m = (clamped % 3600) / 60
    val s = clamped % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
