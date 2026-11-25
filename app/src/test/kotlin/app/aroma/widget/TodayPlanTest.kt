package app.aroma.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TodayPlanTest {
    private val today: LocalDate = LocalDate.of(2026, 11, 23)
    private val tomorrow: LocalDate = today.plusDays(1)

    private fun meal(type: MealType, date: LocalDate = today) =
        MealPlanEntry(type.ordinal + 10 * date.dayOfMonth, date, type, type.name, "", null)

    private fun planAt(hour: Int, minute: Int, vararg entries: MealPlanEntry) = todayPlan(entries.toList(), today.atTime(hour, minute))

    private val breakfast = meal(MealType.Breakfast)
    private val lunch = meal(MealType.Lunch)
    private val snack = meal(MealType.Snack)
    private val dinner = meal(MealType.Dinner)
    private val dessert = meal(MealType.Dessert)
    private val tomorrowsBreakfast = meal(MealType.Breakfast, tomorrow)

    @Test
    fun `the meal not over yet leads, lunch until two and a snack before dinner`() {
        assertEquals(lunch, planAt(13, 59, breakfast, lunch, dinner).next)
        assertEquals(dinner, planAt(14, 0, breakfast, lunch, dinner).next)
        assertEquals(snack, planAt(15, 0, dinner, snack).next)
    }

    @Test
    fun `after dinner the widget looks at tomorrow and the week after it`() {
        val plan = planAt(21, 30, dinner, tomorrowsBreakfast)

        assertEquals(tomorrow, plan.day.date)
        assertEquals(tomorrowsBreakfast, plan.next)
        assertEquals((0L..6L).map { tomorrow.plusDays(it) }, plan.days.map { it.date })
    }

    @Test
    fun `once all of today's meals are over the last stays until dinner time, and a day with none has no next meal`() {
        val plan = planAt(15, 0, breakfast, lunch, tomorrowsBreakfast)

        assertEquals(today, plan.day.date)
        assertEquals(lunch, plan.next)
        assertNull(planAt(10, 0, tomorrowsBreakfast).next)
    }

    @Test
    fun `a day's meals run in the order of the day`() {
        val side = meal(MealType.Side)
        val plan = planAt(8, 0, dessert, side, dinner, snack, lunch, breakfast)

        assertEquals(listOf(breakfast, lunch, snack, dinner, side, dessert), plan.day.meals)
    }

    @Test
    fun `the lead changes as a meal is over and at midnight`() {
        assertEquals(today.atTime(11, 0), nextChange(today.atTime(9, 30)))
        assertEquals(today.atTime(21, 0), nextChange(today.atTime(LocalTime.of(17, 0))))
        assertEquals(tomorrow.atStartOfDay(), nextChange(today.atTime(21, 0)))
    }

    @Test
    fun `the launcher shows each widget the layout for its own size, from one cell to a tablet`() {
        for (width in 40..900 step 3) {
            for (height in 40..600 step 3) {
                val size = DpSize(width.dp, height.dp)
                assertShown(size, TodayLayout.sizes) { TodayLayout.at(it) }
                assertShown(size, ShoppingLayout.sizes) { ShoppingLayout.at(it) }
            }
        }
    }

    /** Android lays a widget out for the one of its [sizes] it picks for [size]: that must take the layout of [size] itself. */
    private fun assertShown(size: DpSize, sizes: Set<DpSize>, layoutAt: (DpSize) -> Any) {
        val layout = layoutAt(size)
        val shown = layoutAt(sizes.shownAt(size)) to layoutAt(sizes.shownBefore12At(size))
        // Spelling out the sizes of the hundred thousand checks that pass would take most of the test's time.
        if (shown != layout to layout) assertEquals("$size from Android 12, and before", layout to layout, shown)
    }
}
