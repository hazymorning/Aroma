package app.kochi.feature.recipe

import kotlin.math.roundToInt

/**
 * Scaling in steps a cook expects: whole servings when the recipe states them,
 * otherwise a multiplier of ½, 1, 2, 3 and so on.
 */
object Servings {
    private val multipliers = listOf(0.5, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)

    fun step(baseServings: Double, scale: Double, direction: Int): Double {
        if (baseServings > 0) {
            val target = (baseServings * scale).roundToInt() + direction
            return target.coerceIn(1, 99) / baseServings
        }
        val index = multipliers.indexOfFirst { it >= scale - 1e-6 }.takeIf { it >= 0 } ?: multipliers.lastIndex
        return multipliers[(index + direction).coerceIn(0, multipliers.lastIndex)]
    }

    fun canDecrease(baseServings: Double, scale: Double): Boolean =
        if (baseServings > 0) baseServings * scale > 1.0 + 1e-6 else scale > multipliers.first() + 1e-6
}
