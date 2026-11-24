package app.aroma.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * A widget of [columns] by [rows] cells on a phone, as Android's guide to widget sizes gives it:
 * (73n − 16) × (118m − 16) dp in portrait and (142n − 15) × (66m − 15) dp in landscape.
 */
internal fun cells(columns: Int, rows: Int, landscape: Boolean = false): DpSize =
    if (landscape) DpSize((142 * columns - 15).dp, (66 * rows - 15).dp) else DpSize((73 * columns - 16).dp, (118 * rows - 16).dp)

/**
 * Which of a widget's declared sizes Android shows at the widget's [size] from Android 12: the
 * largest that fits, with a dp to spare for rounding, else the smallest. The widget is laid out
 * for that size and then stretched to its own.
 */
internal fun Set<DpSize>.shownAt(size: DpSize): DpSize = filter { it fitsIn size }.maxByOrNull { it.area } ?: minBy { it.area }

/** Which one Glance shows before Android 12, for each orientation's size: the closest that fits, else the smallest. */
internal fun Set<DpSize>.shownBefore12At(size: DpSize): DpSize =
    filter { it fitsIn size }.minByOrNull {
        val width = size.width.value - it.width.value
        val height = size.height.value - it.height.value
        width * width + height * height
    } ?: minBy { it.area }

private infix fun DpSize.fitsIn(size: DpSize) = ceil(size.width.value) + 1 > width.value && ceil(size.height.value) + 1 > height.value

private val DpSize.area: Float get() = width.value * height.value
