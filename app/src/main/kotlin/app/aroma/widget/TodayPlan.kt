package app.aroma.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.MealType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

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

/** How long from [now] in [zone] until [nextChange], so a night that changes the clocks still ends at midnight. */
internal fun untilNextChange(now: LocalDateTime, zone: ZoneId): Duration = Duration.between(now.atZone(zone), nextChange(now).atZone(zone))

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

/**
 * How the Today widget arranges itself at a size. A cell of a phone is 57 to about 100 dp wide and
 * about 100 to 120 dp high in portrait, and 127 to about 200 dp wide and 51 to about 70 dp high in
 * landscape (the least are those of Android's guide to widget sizes), so the layouts change where
 * one number of cells gives way to the next, in both.
 */
internal enum class TodayLayout {
    /** One cell: the next meal's photo and nothing else. */
    Tile,

    /** About two by one: the next meal's name on its photo, darkened. */
    Banner,

    /** Four cells in a row in landscape: the next meal's name beside its thumbnail. */
    Line,

    /** About two by two: the next meal's photo filling the widget, its name set at its foot. */
    Cover,

    /** Wide: the next meal's photo beside the day's meals. */
    PhotoAndDay,

    /** Tall: a header over the day and the days after it. */
    Week,
    ;

    companion object {
        /**
         * From here a widget is a row in portrait, about 100 dp high or more, with room for a meal's
         * kind over three lines of its name; lower, it is a row in landscape.
         */
        val PortraitRow = 90.dp

        /** From here a row in landscape is four cells long, too long and thin for a photo behind its words. */
        private val LongRow = 440.dp

        /** From here a widget about two cells wide has room for the photo above the name set on it. */
        private val CoverHeight = 170.dp

        /**
         * The sizes laid out for, each at the corner of the sizes its layout takes. From Android 12
         * the launcher shows the largest that fits and Glance before it the closest that fits, so
         * the corners form a grid wherever one layout meets another: then both pick the layout [at]
         * gives for the widget's own size. A widget smaller than all of them shows the first.
         */
        val sizes: Set<DpSize> =
            setOf(
                DpSize(WidgetSizes.Smallest, WidgetSizes.Smallest),
                DpSize(WidgetSizes.Wide, WidgetSizes.Smallest),
                DpSize(LongRow, WidgetSizes.Smallest),
                DpSize(WidgetSizes.Narrow, PortraitRow),
                DpSize(WidgetSizes.Wide, PortraitRow),
                DpSize(LongRow, PortraitRow),
                DpSize(WidgetSizes.Narrow, CoverHeight),
                DpSize(WidgetSizes.Wide, CoverHeight),
                DpSize(WidgetSizes.Narrow, WidgetSizes.Tall),
                DpSize(WidgetSizes.Wide, WidgetSizes.Tall),
                DpSize(LongRow, WidgetSizes.Tall),
            )

        fun at(size: DpSize): TodayLayout =
            when {
                size.width < WidgetSizes.Narrow -> Tile
                size.height < PortraitRow && size.width < WidgetSizes.Wide -> Tile
                size.height < PortraitRow && size.width < LongRow -> Banner
                size.height < PortraitRow -> Line
                size.height >= WidgetSizes.Tall -> Week
                size.width >= WidgetSizes.Wide -> PhotoAndDay
                size.height >= CoverHeight -> Cover
                else -> Banner
            }
    }
}
