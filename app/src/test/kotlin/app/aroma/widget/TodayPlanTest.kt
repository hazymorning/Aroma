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
    fun `the meal not over yet leads, lunch until two`() {
        assertEquals(lunch, planAt(13, 59, breakfast, lunch, dinner).next)
        assertEquals(dinner, planAt(14, 0, breakfast, lunch, dinner).next)
    }

    @Test
    fun `an afternoon snack leads before dinner`() {
        assertEquals(snack, planAt(15, 0, dinner, snack).next)
    }

    @Test
    fun `after dinner the widget looks at tomorrow`() {
        val plan = planAt(21, 30, dinner, tomorrowsBreakfast)

        assertEquals(tomorrow, plan.day.date)
        assertEquals(tomorrowsBreakfast, plan.next)
    }

    @Test
    fun `once all of today's meals are over the last stays until dinner time`() {
        val plan = planAt(15, 0, breakfast, lunch, tomorrowsBreakfast)

        assertEquals(today, plan.day.date)
        assertEquals(lunch, plan.next)
    }

    @Test
    fun `a day with nothing planned has no next meal`() {
        val plan = planAt(10, 0, tomorrowsBreakfast)

        assertEquals(today, plan.day.date)
        assertNull(plan.next)
    }

    @Test
    fun `a day's meals run in the order of the day`() {
        val side = meal(MealType.Side)
        val plan = planAt(8, 0, dessert, side, dinner, snack, lunch, breakfast)

        assertEquals(listOf(breakfast, lunch, snack, dinner, side, dessert), plan.day.meals)
    }

    @Test
    fun `the days run a week from the day looked at`() {
        val plan = planAt(22, 0)

        assertEquals((1L..7L).map { today.plusDays(it) }, plan.days.map { it.date })
    }

    @Test
    fun `the lead changes as a meal is over and at midnight`() {
        assertEquals(today.atTime(11, 0), nextChange(today.atTime(9, 30)))
        assertEquals(today.atTime(21, 0), nextChange(today.atTime(LocalTime.of(17, 0))))
        assertEquals(tomorrow.atStartOfDay(), nextChange(today.atTime(21, 0)))
    }

    @Test
    fun `each number of cells has its layout, in portrait and in landscape`() {
        val expected =
            mapOf(
                // One cell, and one cell wide however high, is the photo alone.
                cells(1, 1) to TodayLayout.Tile,
                cells(1, 1, landscape = true) to TodayLayout.Tile,
                cells(1, 2) to TodayLayout.Tile,
                cells(1, 3) to TodayLayout.Tile,
                // Two by one is the name on the darkened photo.
                cells(2, 1) to TodayLayout.Banner,
                cells(2, 1, landscape = true) to TodayLayout.Banner,
                cells(3, 1) to TodayLayout.Banner,
                // Four by one: the photo beside the day in portrait, the thumbnail beside the name in landscape.
                cells(4, 1) to TodayLayout.PhotoAndDay,
                cells(4, 1, landscape = true) to TodayLayout.Line,
                cells(5, 1, landscape = true) to TodayLayout.Line,
                // Two by two is the photo with the name on it; in landscape it is wide and low.
                cells(2, 2) to TodayLayout.Cover,
                cells(3, 2) to TodayLayout.Cover,
                cells(2, 2, landscape = true) to TodayLayout.PhotoAndDay,
                cells(4, 2) to TodayLayout.PhotoAndDay,
                cells(4, 2, landscape = true) to TodayLayout.PhotoAndDay,
                cells(4, 4, landscape = true) to TodayLayout.PhotoAndDay,
                cells(2, 3) to TodayLayout.Week,
                cells(4, 3) to TodayLayout.Week,
                cells(4, 4) to TodayLayout.Week,
            )
        expected.forEach { (size, layout) ->
            assertEquals("$size", layout, TodayLayout.at(size))
            assertEquals("$size from Android 12", layout, TodayLayout.at(TodayLayout.sizes.shownAt(size)))
            assertEquals("$size before Android 12", layout, TodayLayout.at(TodayLayout.sizes.shownBefore12At(size)))
        }
    }

    @Test
    fun `the launcher shows the layout for the widget's own size, from one cell to a tablet`() {
        for (width in 40..900 step 3) {
            for (height in 40..600 step 3) {
                val size = DpSize(width.dp, height.dp)
                val layout = TodayLayout.at(size)
                assertEquals("$size from Android 12", layout, TodayLayout.at(TodayLayout.sizes.shownAt(size)))
                assertEquals("$size before Android 12", layout, TodayLayout.at(TodayLayout.sizes.shownBefore12At(size)))
            }
        }
    }
}
