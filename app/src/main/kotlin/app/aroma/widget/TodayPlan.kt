package app.aroma.widget

import androidx.compose.ui.unit.DpSize
import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.MealType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A day of the plan, its meals in the order of the day. */
internal data class PlanDay(val date: LocalDate, val meals: List<MealPlanEntry>)

/** What the Today widget shows: [days] from the one it looks at on, and the meal it leads with, if that day has one. */
internal data class TodayPlan(val today: LocalDate, val days: List<PlanDay>, val next: MealPlanEntry?) {
    val day: PlanDay get() = days.first()
}

/**
 * The plan as it stands at [now]: today until dinner is over, then tomorrow, led by the first meal
 * of that day that is not over yet, or by its last once all are.
 */
internal fun todayPlan(entries: List<MealPlanEntry>, now: LocalDateTime, dayCount: Int = SHOWN_DAYS): TodayPlan {
    val time = now.toLocalTime()
    val today = now.toLocalDate()
    val first = if (time < DINNER_OVER) today else today.plusDays(1)
    val byDay = entries.groupBy { it.date }
    val days = (0 until dayCount).map { first.plusDays(it.toLong()) }.map { PlanDay(it, byDay[it].orEmpty().sortedWith(ByTimeOfDay)) }
    val meals = days.first().meals
    val next = if (first == today) meals.firstOrNull { time < it.type.over } ?: meals.lastOrNull() else meals.firstOrNull()
    return TodayPlan(today, days, next)
}

/** When the plan seen at [now] next looks different: as a meal is over, and at midnight. */
internal fun nextChange(now: LocalDateTime): LocalDateTime {
    val later =
        MealType.entries
            .map { it.over }
            .filter { it > now.toLocalTime() }
            .minOrNull()
    return later?.let { now.toLocalDate().atTime(it) } ?: now.toLocalDate().plusDays(1).atStartOfDay()
}

/** By when a meal of this type is over, so the widget moves on to the next. */
internal val MealType.over: LocalTime
    get() =
        when (this) {
            MealType.Breakfast -> LocalTime.of(11, 0)
            MealType.Lunch -> LocalTime.of(14, 0)
            MealType.Snack -> LocalTime.of(17, 0)
            MealType.Dinner, MealType.Side, MealType.Drink, MealType.Dessert -> DINNER_OVER
        }

private val DINNER_OVER: LocalTime = LocalTime.of(21, 0)

/** Breakfast, lunch, the afternoon snack, then dinner with what goes with it, in Mealie's order. */
private val ByTimeOfDay = compareBy<MealPlanEntry>({ it.type.over }, { it.type.ordinal })

/** The day the widget looks at and the six after it: a week, which the cached plan always covers. */
private const val SHOWN_DAYS = 7

/** How the Today widget arranges itself at a size. */
internal enum class TodayLayout {
    /** One row: the next meal's name. */
    Line,

    /** About two by two: the next meal's photo, its name on it. */
    Photo,

    /** Wide: the next meal's photo beside the day's meals. */
    PhotoAndDay,

    /** Tall: a header over the day and the days after it. */
    Week,
    ;

    companion object {
        /** The sizes laid out for; the launcher shows the closest that fits. */
        val sizes: Set<DpSize> =
            setOf(
                DpSize(WidgetSizes.Narrow, WidgetSizes.Smallest),
                DpSize(WidgetSizes.Wide, WidgetSizes.Smallest),
                DpSize(WidgetSizes.Narrow, WidgetSizes.Low),
                DpSize(WidgetSizes.Wide, WidgetSizes.Low),
                DpSize(WidgetSizes.Narrow, WidgetSizes.Tall),
                DpSize(WidgetSizes.Wide, WidgetSizes.Tall),
            )

        fun at(size: DpSize): TodayLayout =
            when {
                size.height >= WidgetSizes.Tall -> Week
                size.height < WidgetSizes.Low -> Line
                size.width >= WidgetSizes.Wide -> PhotoAndDay
                else -> Photo
            }
    }
}
