package app.aroma.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import app.aroma.AppContainer
import app.aroma.R
import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.MealType
import app.aroma.data.attempt
import app.aroma.data.session.SessionState
import app.aroma.ui.navigation.LaunchRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** What the Today widget shows. */
internal sealed interface TodayState {
    data object Loading : TodayState

    data object SignedOut : TodayState

    /** Nothing is cached yet and the server could not be reached. */
    data object Unreachable : TodayState

    /** [hero] is the next meal's photo; [thumbnails] are the photos of the listed meals' recipes, by recipe id. */
    data class Ready(val plan: TodayPlan, val hero: Bitmap?, val thumbnails: Map<String, Bitmap>) : TodayState
}

/** The Today widget: the next meal on the plan, the rest of its day and, given the room, the week. */
class TodayWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_error) {
    override val sizeMode = SizeMode.Responsive(TodayLayout.sizes)
    override val previewSizeMode = SizeMode.Responsive(TodayLayout.sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val clock = Clock.systemDefaultZone()
        val firstLook = container.widgetLooks.first()
        val firstState = todayStates(context, container, clock).firstShown(TodayState.Loading)
        TodayRollover.schedule(context, clock)
        provideContent {
            val look by container.widgetLooks.collectAsState(firstLook)
            var retries by remember { mutableIntStateOf(0) }
            val state by remember(retries) { todayStates(context, container, clock) }.collectAsState(firstState)
            WidgetTheme(look) { TodayContent(state, onRetry = { retries++ }) }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val look = context.appContainer.widgetLooks.first()
        provideContent { WidgetTheme(look) { TodayContent(previewToday(LocalContext.current), onRetry = {}) } }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in TodayRollover.actions) {
            val manager = AppWidgetManager.getInstance(context)
            onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, javaClass)))
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Widgets.keepFresh(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Also called without widgets, as the clock or the language changes: then nothing needs refreshing.
        if (appWidgetIds.isNotEmpty()) Widgets.keepFresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        TodayRollover.cancel(context)
        Widgets.removed(context)
    }
}

/**
 * Renders the Today widgets again when what they lead with changes: as a meal is over and at
 * midnight, and when the clock or the time zone is changed. The alarm is inexact and waits for the
 * phone to wake, so it never wakes it.
 */
internal object TodayRollover {
    private const val ROLL_OVER = "app.aroma.action.TODAY_ROLL_OVER"

    val actions = setOf(ROLL_OVER, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)

    fun schedule(context: Context, clock: Clock) {
        val at = clock.instant().plus(untilNextChange(LocalDateTime.now(clock), clock.zone))
        context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, at.toEpochMilli(), intent(context))
    }

    fun cancel(context: Context) = context.getSystemService(AlarmManager::class.java).cancel(intent(context))

    private fun intent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TodayWidgetReceiver::class.java).setAction(ROLL_OVER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** The widget's state as the session, the cached plan and the time of day change. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun todayStates(context: Context, container: AppContainer, clock: Clock): Flow<TodayState> =
    container.sessions.state.flatMapLatest { session ->
        when (session) {
            SessionState.Loading -> flowOf(TodayState.Loading)
            SessionState.SignedOut -> flowOf(TodayState.SignedOut)
            is SessionState.SignedIn -> plannedStates(context, container, clock)
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
private fun plannedStates(context: Context, container: AppContainer, clock: Clock): Flow<TodayState> =
    combine(container.mealPlans.upcoming(), changesOfLead(clock)) { entries, now -> entries?.let { todayPlan(it, now) } }
        .distinctUntilChanged()
        .transformLatest { plan ->
            if (plan != null) {
                emit(context.withPhotos(plan))
            } else {
                // Nothing cached yet: fetch it once; the cached copy then comes through the plan above.
                emit(TodayState.Loading)
                attempt { container.mealPlans.refreshUpcoming(LocalDate.now(clock)) }.onFailure { emit(TodayState.Unreachable) }
            }
        }

/** The time now, and again whenever the meal the widget leads with may change. */
private fun changesOfLead(clock: Clock): Flow<LocalDateTime> =
    flow {
        while (true) {
            val now = LocalDateTime.now(clock)
            emit(now)
            delay(untilNextChange(now, clock.zone).toMillis())
        }
    }

private suspend fun Context.withPhotos(plan: TodayPlan): TodayState.Ready =
    coroutineScope {
        val hero =
            plan.next
                ?.recipe
                ?.image
                ?.let { async { recipePhoto(it, HERO_SIZE) } }
        val thumbnails =
            plan.days
                .flatMap { it.meals }
                .mapNotNull { it.recipe }
                .distinctBy { it.id }
                .mapNotNull { recipe -> recipe.image?.let { recipe.id to async { recipePhoto(it, THUMBNAIL_SIZE) } } }
        TodayState.Ready(plan, hero?.await(), thumbnails.mapNotNull { (id, photo) -> photo.await()?.let { id to it } }.toMap())
    }

@Composable
internal fun TodayContent(state: TodayState, onRetry: () -> Unit) {
    val context = LocalContext.current
    val openPlan = actionStartActivity(LaunchRequest.MealPlan.intent(context))
    when (state) {
        TodayState.Loading -> {
            WidgetSurface { LoadingOutline() }
        }

        TodayState.SignedOut -> {
            WidgetMessage(
                icon = R.drawable.ic_plan,
                title = context.getString(R.string.widget_signed_out),
                onClick = openPlan,
                body = context.getString(R.string.widget_today_signed_out),
                button = context.getString(R.string.widget_sign_in),
            )
        }

        TodayState.Unreachable -> {
            WidgetMessage(
                icon = R.drawable.ic_cloud_off,
                title = context.getString(R.string.widget_unreachable_title),
                onClick = action(block = onRetry),
                body = context.getString(R.string.widget_unreachable_message),
                button = context.getString(R.string.widget_retry),
            )
        }

        is TodayState.Ready -> {
            val layout = TodayLayout.at(LocalSize.current)
            when {
                layout == TodayLayout.Week -> Week(state, openPlan)
                state.plan.next == null -> EmptyDay(state.plan, openPlan)
                layout == TodayLayout.Line -> Line(state, state.plan.next)
                layout == TodayLayout.Photo -> Hero(state, state.plan.next)
                else -> HeroAndDay(state, state.plan.next)
            }
        }
    }
}

/** One row: the next meal's name, and its photo when wide. */
@Composable
private fun Line(state: TodayState.Ready, lead: MealPlanEntry) {
    WidgetSurface(GlanceModifier.clickable(open(lead))) {
        Row(GlanceModifier.fillMaxSize().padding(horizontal = WidgetPadding), verticalAlignment = Alignment.CenterVertically) {
            if (LocalSize.current.width >= WidgetSizes.Wide) {
                Thumbnail(lead, state.thumbnails[lead.recipe?.id])
                Spacer(GlanceModifier.width(16.dp))
            }
            Column(GlanceModifier.defaultWeight()) {
                Text(leadLabel(state.plan, lead), style = WidgetType.small, maxLines = 1)
                Text(lead.name, style = WidgetType.name, maxLines = 1)
            }
        }
    }
}

/** The next meal as the app's cards show a recipe: its photo, given the room, and its name set underneath. */
@Composable
private fun Hero(state: TodayState.Ready, lead: MealPlanEntry) {
    WidgetSurface(GlanceModifier.clickable(open(lead))) {
        Column(GlanceModifier.fillMaxSize().padding(WidgetPadding), verticalAlignment = Alignment.CenterVertically) {
            if (LocalSize.current.height >= PHOTO_OVER_CAPTION) {
                PhotoTile(lead, state.hero, GlanceModifier.fillMaxWidth().defaultWeight())
                Spacer(GlanceModifier.height(8.dp))
            }
            Text(leadLabel(state.plan, lead), style = WidgetType.small, maxLines = 1)
            Text(lead.name, style = WidgetType.name, maxLines = 2)
        }
    }
}

/**
 * The next meal's photo beside the meals of its day from it on, so the next one is never scrolled
 * away; its label says when the day is tomorrow.
 */
@Composable
private fun HeroAndDay(state: TodayState.Ready, lead: MealPlanEntry) {
    val meals =
        state.plan.day.meals
            .dropWhile { it != lead }
    WidgetSurface {
        Row(GlanceModifier.fillMaxSize().padding(WidgetPadding)) {
            // The photo opens the meal as its row does, so a screen reader names the meal here too.
            val tile =
                GlanceModifier
                    .width(HERO_TILE_WIDTH)
                    .fillMaxHeight()
                    .clickable(open(lead))
                    .semantics { contentDescription = lead.name }
            PhotoTile(lead, state.hero, tile)
            Spacer(GlanceModifier.width(16.dp - MEAL_INSET))
            LazyColumn(GlanceModifier.defaultWeight().fillMaxHeight()) {
                items(meals) { meal -> MealRow(meal, label = if (meal == lead) leadLabel(state.plan, lead) else null) }
            }
        }
    }
}

/** The plan's name over the days from the one the widget looks at on, the next meal on the sage wash. */
@Composable
private fun Week(state: TodayState.Ready, openPlan: Action) {
    val context = LocalContext.current
    val photos = LocalSize.current.width >= WidgetSizes.Wide
    WidgetSurface {
        Column(GlanceModifier.fillMaxSize()) {
            WidgetHeader(context.getString(R.string.widget_meal_plan), openPlan, GlanceModifier.padding(top = HeaderTop))
            LazyColumn(GlanceModifier.defaultWeight().padding(horizontal = WidgetPadding - MEAL_INSET)) {
                state.plan.days.forEach { day ->
                    item { DayLabel(dayName(day.date, state.plan.today)) }
                    if (day.meals.isEmpty()) {
                        item { NothingPlanned(lead = day == state.plan.day, openPlan) }
                    } else {
                        items(day.meals) { meal ->
                            MealRow(meal, state.thumbnails[meal.recipe?.id], showPhoto = photos, washed = meal == state.plan.next)
                        }
                    }
                }
                item { Spacer(GlanceModifier.height(8.dp)) }
            }
        }
    }
}

/** A day with nothing planned, which the smaller widgets say in place of the next meal. */
@Composable
private fun EmptyDay(plan: TodayPlan, openPlan: Action) {
    val context = LocalContext.current
    val title = context.getString(if (plan.day.date == plan.today) R.string.widget_today_empty else R.string.widget_tomorrow_empty)
    WidgetMessage(
        icon = R.drawable.ic_calendar_add,
        title = title,
        onClick = openPlan,
        button = context.getString(R.string.widget_plan_meal),
    )
}

/** A day's name in sage, as the app names a group of rows. */
@Composable
private fun DayLabel(name: String) {
    Text(
        text = name,
        style = WidgetType.label.copy(color = WidgetColors.primary),
        maxLines = 1,
        modifier = GlanceModifier.fillMaxWidth().padding(start = MEAL_INSET, end = MEAL_INSET, top = 16.dp, bottom = 4.dp),
    )
}

/**
 * A planned meal: its photo when there is room, its kind, or [label] in its place, and its name;
 * [washed] sets it on the sage wash.
 */
@Composable
private fun MealRow(
    meal: MealPlanEntry,
    photo: Bitmap? = null,
    showPhoto: Boolean = false,
    label: String? = null,
    washed: Boolean = false,
) {
    val context = LocalContext.current
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .then(if (washed) GlanceModifier.fill(WidgetColors.selected) else GlanceModifier)
                .clickable(open(meal))
                .padding(horizontal = MEAL_INSET, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showPhoto) {
            Thumbnail(meal, photo)
            Spacer(GlanceModifier.width(16.dp))
        }
        Column(GlanceModifier.defaultWeight()) {
            val kind = label ?: context.getString(meal.type.label)
            Text(
                kind,
                style = WidgetType.small.copy(color = if (washed) WidgetColors.onSelected else WidgetColors.onSurfaceVariant),
                maxLines = 1,
            )
            Text(
                meal.name,
                style = WidgetType.name.copy(color = if (washed) WidgetColors.onSelected else WidgetColors.onSurface),
                maxLines = 2,
            )
        }
    }
}

/** A day with nothing planned, which opens the plan; the day the widget looks at offers to plan a meal, as a link. */
@Composable
private fun NothingPlanned(lead: Boolean, openPlan: Action) {
    val context = LocalContext.current
    Row(
        GlanceModifier
            .fillMaxWidth()
            .height(TargetSize)
            .clickable(openPlan)
            .padding(horizontal = MEAL_INSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.widget_day_empty),
            style = WidgetType.small,
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (lead) {
            Text(
                context.getString(R.string.widget_plan_meal),
                style = WidgetType.small.copy(color = WidgetColors.primary),
                maxLines = 1,
            )
        }
    }
}

/** A meal's photo with the app's card corners, or the glyph of a recipe without one or of a note on a soft fill. */
@Composable
private fun PhotoTile(meal: MealPlanEntry, photo: Bitmap?, modifier: GlanceModifier = GlanceModifier) {
    if (photo != null) {
        Photo(photo, modifier.cornerRadius(CardCorner))
    } else {
        Box(modifier.fill(WidgetColors.card), contentAlignment = Alignment.Center) { Glyph(meal.glyph, WidgetColors.onSurfaceVariant) }
    }
}

/** A meal's thumbnail beside its words, or the glyph of a recipe without a photo or of a note. */
@Composable
private fun Thumbnail(meal: MealPlanEntry, photo: Bitmap?) {
    if (photo != null) {
        Photo(photo, GlanceModifier.size(THUMBNAIL_SIZE).cornerRadius(ThumbnailCorner))
    } else {
        Box(GlanceModifier.size(THUMBNAIL_SIZE).fill(WidgetColors.card, R.drawable.widget_thumbnail), contentAlignment = Alignment.Center) {
            Glyph(meal.glyph, WidgetColors.onSurfaceVariant, size = 20.dp)
        }
    }
}

/** A meal with a recipe opens the recipe; a note opens the plan. */
@Composable
@ReadOnlyComposable
private fun open(meal: MealPlanEntry): Action {
    val context = LocalContext.current
    val intent = meal.recipe?.let { LaunchRequest.Recipe(it.slug).intent(context) } ?: LaunchRequest.MealPlan.intent(context)
    return actionStartActivity(intent)
}

/** "Dinner", or "Tomorrow · Breakfast" once the widget looks at tomorrow. */
@Composable
@ReadOnlyComposable
private fun leadLabel(plan: TodayPlan, lead: MealPlanEntry): String {
    val context = LocalContext.current
    val kind = context.getString(lead.type.label)
    return if (plan.day.date == plan.today) kind else context.getString(R.string.widget_tomorrow_meal, kind)
}

/** "Today", "Tomorrow", then the day of the week. */
@Composable
@ReadOnlyComposable
private fun dayName(date: LocalDate, today: LocalDate): String {
    val context = LocalContext.current
    return when (date) {
        today -> context.getString(R.string.widget_today)
        today.plusDays(1) -> context.getString(R.string.widget_tomorrow)
        else -> date.format(DateTimeFormatter.ofPattern("EEEE", context.resources.configuration.locales[0]))
    }
}

private val MealPlanEntry.name: String get() = recipe?.name ?: title.ifBlank { note }

private val MealPlanEntry.glyph: Int get() = if (recipe != null) R.drawable.ic_plate else R.drawable.ic_notes

@get:StringRes
private val MealType.label: Int
    get() =
        when (this) {
            MealType.Breakfast -> R.string.meal_breakfast
            MealType.Lunch -> R.string.meal_lunch
            MealType.Dinner -> R.string.meal_dinner
            MealType.Side -> R.string.meal_side
            MealType.Snack -> R.string.meal_snack
            MealType.Drink -> R.string.meal_drink
            MealType.Dessert -> R.string.meal_dessert
        }

/** About as large as the next meal's photo shows; Mealie's small rendition is not much larger. */
private val HERO_SIZE = 160.dp
private val HERO_TILE_WIDTH = 112.dp
private val THUMBNAIL_SIZE = 48.dp

/** From this height a photo fits above the next meal's two lines of words. */
private val PHOTO_OVER_CAPTION = 170.dp

/** How far a meal's words stand inside its row, so the sage wash has room around them. */
private val MEAL_INSET = 8.dp
