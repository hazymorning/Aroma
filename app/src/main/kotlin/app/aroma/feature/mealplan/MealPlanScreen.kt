package app.aroma.feature.mealplan

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.MealType
import app.aroma.data.AromaError
import app.aroma.feature.recipe.PlanSheet
import app.aroma.ui.components.AromaAlertDialog
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.AromaPullToRefreshBox
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.IconButton
import app.aroma.ui.components.IconButtonReach
import app.aroma.ui.components.LargeHeader
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PressTint
import app.aroma.ui.components.RecipeRow
import app.aroma.ui.components.SEPARATOR
import app.aroma.ui.components.ScrollToTopOnReselect
import app.aroma.ui.components.SecondaryButton
import app.aroma.ui.components.SheetAction
import app.aroma.ui.components.Skeleton
import app.aroma.ui.components.SkeletonLine
import app.aroma.ui.components.StatusBarEdge
import app.aroma.ui.components.Subtitle
import app.aroma.ui.components.Text
import app.aroma.ui.components.TextAction
import app.aroma.ui.components.TonalBox
import app.aroma.ui.components.animateRow
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.hideThen
import app.aroma.ui.components.mealTypeLabel
import app.aroma.ui.components.recipeTime
import app.aroma.ui.components.reducedMotion
import app.aroma.ui.components.rememberFullSheetState
import app.aroma.ui.components.scrolledBeneath
import app.aroma.ui.components.skeletonRow
import app.aroma.ui.components.weekday
import app.aroma.ui.components.weekdayShort
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.ContentMaxWidth
import app.aroma.ui.theme.EndSpace
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.HeaderGap
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.SectionGap
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.abs

data class MealPlanDay(val date: LocalDate, val entries: List<MealPlanEntry>)

/**
 * The plan of one calendar week, Monday to Sunday, from [weekStart]. [weekItems] counts the
 * ingredients of the week's recipes once they are known.
 */
data class MealPlanUiState(
    val today: LocalDate = LocalDate.now(),
    val weekStart: LocalDate = today,
    val days: List<MealPlanDay> = emptyList(),
    val weekItems: Int? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val error: AromaError? = null,
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)

    val showsToday: Boolean get() = today in weekStart..weekEnd

    /** The recipes planned in the week, each once. */
    val weekRecipes: Int get() = days.flatMap { day -> day.entries.mapNotNull { it.recipe?.id } }.distinct().size
}

data class MealPlanActions(
    val onPreviousWeek: () -> Unit = {},
    val onNextWeek: () -> Unit = {},
    val onToday: () -> Unit = {},
    val onAdd: (LocalDate) -> Unit = {},
    val onOpenEntry: (MealPlanEntry) -> Unit = {},
    val onDeleteEntry: (MealPlanEntry) -> Unit = {},
    val onShopEntry: (MealPlanEntry) -> Unit = {},
    val onShopWeek: () -> Unit = {},
    val onMoveEntry: (MealPlanEntry, LocalDate, MealType) -> Unit = { _, _, _ -> },
    val onRefresh: () -> Unit = {},
)

/**
 * The plan as an agenda of the calendar week: a day per row, its short name and figure on the left,
 * its meals as recipe rows on the right, and today on a soft surface that reaches past the column.
 * A swipe or the switcher in the head turns the week. Days past fold into one line. [focus] is a
 * day the page should bring into view, for which [onFocusConsume] is told once it has.
 */
@Composable
fun MealPlanScreen(
    state: MealPlanUiState,
    actions: MealPlanActions,
    modifier: Modifier = Modifier,
    focus: LocalDate? = null,
    onFocusConsume: () -> Unit = {},
) {
    // By id, so the sheets survive rotation and close by themselves once their entry is gone.
    var sheetEntryId by rememberSaveable { mutableStateOf<Int?>(null) }
    var movingEntryId by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmShopping by rememberSaveable { mutableStateOf(false) }
    // A swipe across the page turns the week: to the left the next, to the right the one before.
    val swipeDistance = with(LocalDensity.current) { SwipeDistance.toPx() }
    val previousWeek by rememberUpdatedState(actions.onPreviousWeek)
    val nextWeek by rememberUpdatedState(actions.onNextWeek)
    AromaPullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = modifier.fillMaxSize()) {
        // Another week drifts in a little from the side it comes from.
        // With the system's animations off, the weeks only cross-fade.
        val quiet = reducedMotion()
        AnimatedContent(
            targetState = state,
            contentKey = { it.weekStart },
            transitionSpec = {
                val forward = targetState.weekStart > initialState.weekStart
                if (quiet) {
                    fadeIn(Motion.edge()) togetherWith fadeOut(Motion.edge())
                } else {
                    val enter =
                        slideInHorizontally(Motion.page()) { if (forward) it / WEEK_SLIDE else -it / WEEK_SLIDE } + fadeIn(Motion.page())
                    val exit =
                        slideOutHorizontally(Motion.page()) { if (forward) -it / WEEK_SLIDE else it / WEEK_SLIDE } + fadeOut(Motion.edge())
                    enter togetherWith exit
                }
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(swipeDistance) {
                        var dragged = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragEnd = {
                                if (abs(dragged) > swipeDistance) {
                                    if (dragged < 0) {
                                        nextWeek()
                                    } else {
                                        previousWeek()
                                    }
                                }
                            },
                        ) { _, amount -> dragged += amount }
                    },
            label = "week",
        ) { week ->
            WeekPage(week, actions, focus, onFocusConsume, onMore = { sheetEntryId = it.id }, onShopWeek = { confirmShopping = true })
        }
    }
    val sheetEntry = state.entry(sheetEntryId)
    if (sheetEntry != null) {
        EntrySheet(
            entry = sheetEntry,
            onShop = { actions.onShopEntry(sheetEntry) },
            onMove = { movingEntryId = sheetEntry.id },
            onRemove = { actions.onDeleteEntry(sheetEntry) },
            onDismiss = { sheetEntryId = null },
        )
    }
    val movingEntry = state.entry(movingEntryId)
    if (movingEntry != null) {
        PlanSheet(
            today = state.today,
            title = stringResource(R.string.plan_move),
            actionLabel = stringResource(R.string.plan_move),
            day = movingEntry.date,
            type = movingEntry.type,
            onPlan = { date, type ->
                movingEntryId = null
                actions.onMoveEntry(movingEntry, date, type)
            },
            onDismiss = { movingEntryId = null },
        )
    }
    if (confirmShopping) {
        val recipes = state.weekRecipes
        AromaAlertDialog(
            onDismissRequest = { confirmShopping = false },
            title = stringResource(R.string.plan_week_ingredients),
            text = pluralStringResource(R.plurals.plan_shop_week_message, recipes, recipes),
            confirmLabel = stringResource(R.string.plan_shop_week_confirm),
            onConfirm = {
                confirmShopping = false
                actions.onShopWeek()
            },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

/** How far another week drifts in: a twelfth of the page, enough to show which way the week turned. */
private const val WEEK_SLIDE = 12

/** How far a finger goes across the page before the week turns. */
private val SwipeDistance = 64.dp

@Composable
private fun WeekPage(
    state: MealPlanUiState,
    actions: MealPlanActions,
    focus: LocalDate?,
    onFocusConsume: () -> Unit,
    onMore: (MealPlanEntry) -> Unit,
    onShopWeek: () -> Unit,
) {
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(listState)
    var pastOpen by rememberSaveable(state.weekStart) { mutableStateOf(false) }
    // Past days fold into one line from two on, in the week that is under way.
    val past = if (state.showsToday) state.days.filter { it.date < state.today } else emptyList()
    val folded = past.size >= FOLD_FROM && !pastOpen
    val shown = if (folded) state.days - past.toSet() else state.days
    val focused by rememberUpdatedState(onFocusConsume)
    LaunchedEffect(focus, state.isLoading, state.weekStart) {
        val day = focus ?: return@LaunchedEffect
        if (state.isLoading || day !in state.weekStart..state.weekEnd) return@LaunchedEffect
        if (day < state.today) pastOpen = true
        shownIndex(state, day, folded = day >= state.today && folded)?.let { listState.animateScrollToItem(it) }
        focused()
    }
    // The status bar's edge belongs to the week, so it moves with it as the week turns.
    Box(Modifier.fillMaxSize()) {
        WeekList(state, listState, actions, past, folded, shown, onOpenPast = { pastOpen = true }, onMore, onShopWeek)
        StatusBarEdge(scrolled = listState::scrolledBeneath)
    }
}

@Composable
private fun WeekList(
    state: MealPlanUiState,
    listState: LazyListState,
    actions: MealPlanActions,
    past: List<MealPlanDay>,
    folded: Boolean,
    shown: List<MealPlanDay>,
    onOpenPast: () -> Unit,
    onMore: (MealPlanEntry) -> Unit,
    onShopWeek: () -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + EndSpace),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") { Header(state, actions, Modifier.measure()) }
        when {
            state.isLoading -> {
                item(key = "loading") { Loading(Modifier.skeletonRow(this).measure()) }
            }

            state.error != null && state.days.all { it.entries.isEmpty() } -> {
                item(key = "error") {
                    ErrorState(state.error, onRetry = actions.onRefresh, modifier = Modifier.animateRow(this).measure())
                }
            }

            else -> {
                if (folded) {
                    item(key = "past") {
                        PastDays(
                            past,
                            onOpen = onOpenPast,
                            modifier =
                                Modifier
                                    .animateRow(this)
                                    .measure(),
                        )
                    }
                }
                agenda(shown, state.today, actions, onMore)
                if (state.weekRecipes > 0) {
                    item(key = "shop") {
                        WeekIngredients(
                            recipes = state.weekRecipes,
                            items = state.weekItems,
                            onShop = onShopWeek,
                            modifier =
                                Modifier
                                    .animateRow(this)
                                    .measure()
                                    .padding(start = PageMargin, end = PageMargin, top = SectionGap),
                        )
                    }
                }
            }
        }
    }
}

/** From how many past days on they fold into one line. */
private const val FOLD_FROM = 2

/** Where [day] stands in the list: after the head and, where the past is folded, its line. */
private fun shownIndex(state: MealPlanUiState, day: LocalDate, folded: Boolean): Int? {
    val past = state.days.count { it.date < state.today }
    val position = state.days.indexOfFirst { it.date == day }.takeIf { it >= 0 } ?: return null
    return if (folded) 1 + 1 + (position - past) else 1 + position
}

/** The page keeps a reading measure on wide screens. */
private fun Modifier.measure(): Modifier = widthIn(max = ContentMaxWidth).fillMaxWidth()

/**
 * "Wochenplan" with the week it shows under it, "23.–29. November · KW 48", and a link back to the
 * week under way where another is shown. The switcher turns the week.
 */
@Composable
private fun Header(state: MealPlanUiState, actions: MealPlanActions, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val range = remember(state.weekStart, locale) { formatRange(state.weekStart, state.weekEnd, locale) }
    val week = weekNumbers(state.weekStart, state.weekEnd)
    Column(modifier.padding(bottom = HeaderGap)) {
        LargeHeader(
            title = stringResource(R.string.nav_meal_plan),
            subtitle = {
                Column {
                    Subtitle(range + SEPARATOR + week)
                    // Away from the week under way, the way back stands on a line of its own.
                    if (!state.showsToday) TextAction(stringResource(R.string.plan_this_week), actions.onToday)
                }
            },
        ) { WeekSwitcher(actions.onPreviousWeek, actions.onNextWeek) }
        if (state.isOffline) OfflineNotice(Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s12))
    }
}

/** ‹ and › on one 40 dp pill of the page's tonal fill, each with a 48 dp target. */
@Composable
private fun WeekSwitcher(onPrevious: () -> Unit, onNext: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .padding(horizontal = SwitcherInset)
                .size(SwitcherWidth, SwitcherHeight)
                .background(AromaTheme.tonalFill, CircleShape),
        )
        Row {
            SwitcherTarget(R.drawable.ic_chevron_left, stringResource(R.string.plan_previous_week), onPrevious)
            SwitcherTarget(R.drawable.ic_chevron_right, stringResource(R.string.plan_next_week), onNext)
        }
    }
}

@Composable
private fun SwitcherTarget(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(TouchTarget)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = PressTint, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = AromaTheme.colors.ink, modifier = Modifier.size(Glyph.control))
    }
}

/** The pill behind the two targets: as tall as a stepper, reaching 4 dp past each glyph's target on the inner side only. */
private val SwitcherHeight = 40.dp
private val SwitcherWidth = 88.dp
private val SwitcherInset = 4.dp

/**
 * The calendar weeks the shown days fall into, "KW 48" or "KW 48–49". ISO weeks, as German
 * calendars print them, and because a bare language setting like "de" has no region to count by.
 */
@Composable
private fun weekNumbers(start: LocalDate, end: LocalDate): String {
    val first = start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val last = end.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    return if (first == last) {
        stringResource(R.string.plan_week_number, first)
    } else {
        stringResource(R.string.plan_week_numbers, first, last)
    }
}

/** "23.–29. November" in German, "November 23–29" in English; across months "30. Nov.–6. Dez.". */
internal fun formatRange(start: LocalDate, end: LocalDate, locale: Locale): String {
    fun format(date: LocalDate, pattern: String) = date.format(DateTimeFormatter.ofPattern(pattern, locale))
    val german = locale.language == "de"
    return when {
        start.month == end.month && german -> "${format(start, "d.")}–${format(end, "d. MMMM")}"
        start.month == end.month -> "${format(start, "MMMM d")}–${format(end, "d")}"
        german -> "${format(start, "d. MMM")}–${format(end, "d. MMM")}"
        else -> "${format(start, "MMM d")}–${format(end, "MMM d")}"
    }
}

/** The days, 24 dp apart: a day's meals stand closer together than the days do. */
private fun LazyListScope.agenda(days: List<MealPlanDay>, today: LocalDate, actions: MealPlanActions, onMore: (MealPlanEntry) -> Unit) {
    days.forEachIndexed { index, day ->
        item(key = "day-${day.date}") {
            DayRow(
                day = day,
                today = today,
                actions = actions,
                onMore = onMore,
                modifier =
                    Modifier
                        .animateRow(this)
                        .measure()
                        .padding(top = if (index == 0) 0.dp else DayGap),
            )
        }
    }
}

/**
 * A day: its short name over its date on the page margin, and its meals beside it, each row as tall
 * as a meal's picture, so every day keeps the same rhythm and its date stands level with the middle
 * of its first row. An empty day shows a slot to plan a dish in, where a meal's picture would be; a
 * planned one has a plus for one more, its circle ending on the margin like the week switcher's. A
 * day gone by fades and offers nothing more to plan.
 */
@Composable
private fun DayRow(
    day: MealPlanDay,
    today: LocalDate,
    actions: MealPlanActions,
    onMore: (MealPlanEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val isToday = day.date == today
    val past = day.date < today
    val markWidth = DayColumn * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val addLabel = stringResource(R.string.plan_add_for, remember(day.date, locale) { weekday(day.date, locale) })
    val adds = !past && day.entries.isNotEmpty()
    Column(
        modifier.padding(start = PageMargin, end = if (adds) PageMargin - IconButtonReach else PageMargin),
        verticalArrangement = Arrangement.spacedBy(MealGap),
    ) {
        if (day.entries.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DayMark(day.date, isToday, past, locale, Modifier.width(markWidth))
                Spacer(Modifier.width(MarkGap))
                if (past) {
                    NothingPlanned(Modifier.weight(1f))
                } else {
                    PlanSlot(addLabel, onClick = { actions.onAdd(day.date) }, modifier = Modifier.weight(1f))
                }
            }
        }
        day.entries.forEachIndexed { index, entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index == 0) DayMark(day.date, isToday, past, locale, Modifier.width(markWidth)) else Spacer(Modifier.width(markWidth))
                Spacer(Modifier.width(MarkGap))
                Box(Modifier.weight(1f)) { EntryRow(entry, past, actions, onMore) }
                if (adds) {
                    Spacer(Modifier.width(Space.s8))
                    if (index ==
                        0
                    ) {
                        IconButton(R.drawable.ic_add, addLabel, { actions.onAdd(day.date) })
                    } else {
                        Spacer(Modifier.width(TouchTarget))
                    }
                }
            }
        }
    }
}

/**
 * A day's short name over its date, both on the page margin. Today says "Heute" in sage over its
 * date in full ink; the days to come stand in second ink, the days gone by in tertiary. Every day
 * stands alike in size and place, so the column keeps its line.
 */
@Composable
private fun DayMark(
    date: LocalDate,
    isToday: Boolean,
    past: Boolean,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val colors = AromaTheme.colors
    val name = if (isToday) stringResource(R.string.home_today) else remember(date, locale) { weekdayShort(date, locale) }
    val full = remember(date, locale) { weekday(date, locale) }
    val quiet =
        when {
            past -> colors.inkTertiary
            else -> colors.inkSecondary
        }
    Column(modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(
            name,
            style = if (isToday) AromaTheme.type.meta.copy(fontWeight = FontWeight.Medium) else AromaTheme.type.meta,
            color = if (isToday) colors.accent else quiet,
            maxLines = 1,
        )
        Text(
            date.dayOfMonth.toString(),
            style = AromaTheme.type.numeral,
            color = if (isToday) colors.ink else quiet,
            maxLines = 1,
            modifier = Modifier.semantics { this.contentDescription = "$full ${date.dayOfMonth}." },
        )
    }
}

/** Where a day still has room: a slot the size of a meal's picture with a plus, and "Gericht planen" where a name would stand. */
@Composable
private fun PlanSlot(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AromaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .heightIn(min = SlotSize)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { this.contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(SlotSize)
                .clip(AromaTheme.shapes.thumbnail)
                .background(AromaTheme.tonalFill)
                .indication(interaction, PressTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_add),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(Glyph.control),
            )
        }
        Spacer(Modifier.width(SlotTextGap))
        Text(stringResource(R.string.plan_dish), style = AromaTheme.type.label, color = colors.accent, maxLines = 2)
    }
}

/** A day gone by with nothing planned: a quiet note where a name would stand, and no slot. */
@Composable
private fun NothingPlanned(modifier: Modifier = Modifier) {
    Row(modifier.heightIn(min = SlotSize), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(SlotSize + SlotTextGap))
        Text(stringResource(R.string.plan_nothing_planned), style = AromaTheme.type.bodySmall, color = AromaTheme.colors.inkTertiary)
    }
}

/** The column a day's name and date stand in, and the room between it and the meals. */
private val DayColumn = 44.dp
private val MarkGap = Space.s12

/** A meal's picture and an empty day's slot: one size, so every day's first row is as tall as every other. */
private val SlotSize = Space.s64

/** Between a picture or slot and the words beside it, as in every recipe row. */
private val SlotTextGap = Space.s16

/** Between two days, and between the meals of one day. */
private val DayGap = Space.s24
private val MealGap = Space.s12

/**
 * A planned meal as a recipe row, "Abendessen · 45 Min." under its name. A tap opens the recipe, a
 * long press the meal's sheet; screen readers get the sheet's actions on the row itself.
 */
@Composable
private fun EntryRow(entry: MealPlanEntry, past: Boolean, actions: MealPlanActions, onMore: (MealPlanEntry) -> Unit) {
    val recipe = entry.recipe
    val shopLabel = stringResource(R.string.recipe_add_to_list)
    val removeLabel = stringResource(R.string.plan_remove)
    val time = recipe?.let { recipeTime(it) }
    RecipeRow(
        title = recipe?.name ?: entry.title,
        image = recipe?.image,
        onClick = { if (recipe != null) actions.onOpenEntry(entry) else onMore(entry) },
        onLongClick = { onMore(entry) },
        meta = listOfNotNull(mealTypeLabel(entry.type), time ?: entry.note.takeIf { it.isNotBlank() }),
        dimmed = past,
        thumbnail = SlotSize,
        fallbackIcon = if (recipe == null) R.drawable.ic_notes else R.drawable.ic_plate,
        modifier =
            Modifier.semantics {
                customActions =
                    listOfNotNull(
                        recipe?.let {
                            CustomAccessibilityAction(shopLabel) {
                                actions.onShopEntry(entry)
                                true
                            }
                        },
                        CustomAccessibilityAction(removeLabel) {
                            actions.onDeleteEntry(entry)
                            true
                        },
                    )
            },
    )
}

/** Days gone by in the week under way, folded into one line, "Mo–Mi · 2 Gerichte"; a tap opens them. */
@Composable
private fun PastDays(days: List<MealPlanDay>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val dishes = days.sumOf { it.entries.size }
    val span = remember(days, locale) { "${weekdayShort(days.first().date, locale)}–${weekdayShort(days.last().date, locale)}" }
    val label = span + SEPARATOR + pluralStringResource(R.plurals.plan_past_dishes, dishes, dishes)
    Row(
        modifier
            .padding(horizontal = PageMargin)
            .padding(bottom = Space.s8)
            .clip(AromaTheme.shapes.round)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.plan_show_past), onClick = onOpen)
            .heightIn(min = TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AromaTheme.type.label, color = AromaTheme.colors.inkTertiary, modifier = Modifier.weight(1f))
        Icon(
            painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            tint = AromaTheme.colors.inkTertiary,
            modifier = Modifier.size(Glyph.control),
        )
    }
}

/**
 * What the week's recipes need, to put on the shopping list in one go: "Zutaten der Woche" over
 * "3 Gerichte · 14 Artikel", with the button beside them where both fit on their lines, else under them.
 */
@Composable
private fun WeekIngredients(recipes: Int, items: Int?, onShop: () -> Unit, modifier: Modifier = Modifier) {
    val meta =
        listOfNotNull(
            pluralStringResource(R.plurals.plan_week_dishes, recipes, recipes),
            items?.let { pluralStringResource(R.plurals.plan_week_items, it, it) },
        ).joinToString(SEPARATOR)
    TonalBox(modifier) {
        Layout(
            contents =
                listOf(
                    {
                        Column {
                            Text(stringResource(R.string.plan_week_ingredients), style = AromaTheme.type.label)
                            Text(meta, style = AromaTheme.type.meta)
                        }
                    },
                    { SecondaryButton(stringResource(R.string.plan_to_list), onShop, compact = true) },
                ),
        ) { (words, action), constraints ->
            val width = constraints.maxWidth
            val gap = Space.s16.roundToPx()
            val button = action.first().measure(Constraints(maxWidth = width))
            val text = words.first()
            if (text.maxIntrinsicWidth(Constraints.Infinity) + gap + button.width <= width) {
                val placed = text.measure(Constraints(maxWidth = width - gap - button.width))
                val height = maxOf(placed.height, button.height)
                layout(width, height) {
                    placed.place(0, (height - placed.height) / 2)
                    button.place(width - button.width, (height - button.height) / 2)
                }
            } else {
                val placed = text.measure(Constraints(maxWidth = width))
                val top = Space.s12.roundToPx()
                layout(width, placed.height + top + button.height) {
                    placed.place(0, 0)
                    button.place(0, placed.height + top)
                }
            }
        }
    }
}

/** What a long press offers: the shopping list for recipes, moving the meal and removing it from the plan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheet(
    entry: MealPlanEntry,
    onShop: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    val choose = { action: () -> Unit ->
        scope.hideThen(sheetState) {
            onDismiss()
            action()
        }
    }
    AromaBottomSheet(
        onDismissRequest = onDismiss,
        title = entry.recipe?.name ?: entry.title,
        titleStyle = AromaTheme.type.cardTitle,
        sheetState = sheetState,
        meta = {
            Text(
                text = "${weekday(entry.date, locale)}$SEPARATOR${mealTypeLabel(entry.type)}",
                style = AromaTheme.type.bodySmall,
            )
        },
    ) {
        Column(Modifier.padding(bottom = Space.s16)) {
            if (entry.recipe != null) {
                SheetAction(R.drawable.ic_basket_add, stringResource(R.string.recipe_add_to_list), onClick = { choose(onShop) })
            }
            SheetAction(R.drawable.ic_plan, stringResource(R.string.plan_move), onClick = { choose(onMove) })
            SheetAction(R.drawable.ic_delete, stringResource(R.string.plan_remove), onClick = { choose(onRemove) }, destructive = true)
        }
    }
}

/** The week's outline while it loads: days with a meal each, in the grid the days take. */
@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = PageMargin), verticalArrangement = Arrangement.spacedBy(DayGap)) {
        repeat(LOADING_DAYS) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(DayColumn), verticalArrangement = Arrangement.spacedBy(Space.s8)) {
                    SkeletonLine(Modifier.width(Space.s20))
                    SkeletonLine(Modifier.width(Space.s24))
                }
                Spacer(Modifier.width(MarkGap))
                Skeleton(Modifier.size(SlotSize), shape = AromaTheme.shapes.thumbnail)
                Spacer(Modifier.width(SlotTextGap))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s8)) {
                    SkeletonLine(Modifier.fillMaxWidth(NAME_SHARE))
                    SkeletonLine(Modifier.fillMaxWidth(META_SHARE))
                }
            }
        }
    }
}

private const val LOADING_DAYS = 4
private const val NAME_SHARE = 0.7f
private const val META_SHARE = 0.4f

private fun MealPlanUiState.entry(id: Int?): MealPlanEntry? =
    id?.let {
        days.firstNotNullOfOrNull { day ->
            day.entries.firstOrNull {
                it.id ==
                    id
            }
        }
    }
