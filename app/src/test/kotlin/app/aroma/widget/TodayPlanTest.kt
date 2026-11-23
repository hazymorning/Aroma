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
    fun `a layout for every size`() {
        assertEquals(TodayLayout.Line, TodayLayout.at(DpSize(150.dp, 60.dp)))
        assertEquals(TodayLayout.Photo, TodayLayout.at(DpSize(130.dp, 102.dp)))
        assertEquals(TodayLayout.Photo, TodayLayout.at(DpSize(150.dp, 220.dp)))
        assertEquals(TodayLayout.PhotoAndDay, TodayLayout.at(DpSize(276.dp, 220.dp)))
        assertEquals(TodayLayout.PhotoAndDay, TodayLayout.at(DpSize(554.dp, 117.dp)))
        assertEquals(TodayLayout.Week, TodayLayout.at(DpSize(130.dp, 337.dp)))
        assertEquals(TodayLayout.Week, TodayLayout.at(DpSize(276.dp, 455.dp)))
    }
}
