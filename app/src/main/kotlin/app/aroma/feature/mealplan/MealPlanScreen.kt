package app.aroma.feature.mealplan

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import app.aroma.ui.components.IconCircleMark
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
import app.aroma.ui.components.StatusBarCover
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
import app.aroma.ui.components.weekday
import app.aroma.ui.components.weekdayShort
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.BoxGap
import app.aroma.ui.theme.ContentMaxWidth
import app.aroma.ui.theme.EndSpace
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.HeaderGap
import app.aroma.ui.theme.HighlightReach
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.SectionGap
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.ThumbnailSize
import app.aroma.ui.theme.TouchTarget
import app.aroma.ui.theme.tabular
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
        // Another week slides in from the side it comes from, as a page of a diary turns.
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
        StatusBarCover()
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

/** How far another week slides: a third of the page, the rest is a fade. */
private const val WEEK_SLIDE = 3

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
    // A recipe planned twice in the week stays put as it opens: its photo could not tell which row it left.
    val repeated =
        remember(state.days) {
            state.days
                .flatMap { day -> day.entries.mapNotNull { it.recipe?.slug } }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        }
    val focused by rememberUpdatedState(onFocusConsume)
    LaunchedEffect(focus, state.isLoading, state.weekStart) {
        val day = focus ?: return@LaunchedEffect
        if (state.isLoading || day !in state.weekStart..state.weekEnd) return@LaunchedEffect
        if (day < state.today) pastOpen = true
        shownIndex(state, day, folded = day >= state.today && folded)?.let { listState.animateScrollToItem(it) }
        focused()
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + EndSpace),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") { Header(state, actions, Modifier.measure()) }
        when {
            state.isLoading -> {
                item(key = "loading") { Loading(Modifier.measure()) }
            }

            state.error != null && state.days.all { it.entries.isEmpty() } -> {
                item(key = "error") { ErrorState(state.error, onRetry = actions.onRefresh, modifier = Modifier.measure()) }
            }

            else -> {
                if (folded) {
                    item(key = "past") {
                        PastDays(
                            past,
                            onOpen = { pastOpen = true },
                            modifier =
                                Modifier
                                    .animateRow(this)
                                    .measure(),
                        )
                    }
                }
                agenda(shown, state.today, actions, onMore, repeated)
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
                FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
                    Subtitle(range + SEPARATOR + week + if (state.showsToday) "" else SEPARATOR)
                    if (!state.showsToday) TextAction(stringResource(R.string.plan_this_week), actions.onToday, inline = true)
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
private fun LazyListScope.agenda(
    days: List<MealPlanDay>,
    today: LocalDate,
    actions: MealPlanActions,
    onMore: (MealPlanEntry) -> Unit,
    repeated: Set<String>,
) {
    days.forEachIndexed { index, day ->
        item(key = "day-${day.date}") {
            DayRow(
                day = day,
                today = today,
                actions = actions,
                onMore = onMore,
                repeated = repeated,
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
 * A day: its short name over its figure in a column of 44 dp, and its meals beside it, with a
 * plus for one more; an empty day offers to plan a dish and is never taller than 56 dp. Today
 * stands on a soft surface that reaches 12 dp past the column; past days fade.
 */
@Composable
private fun DayRow(
    day: MealPlanDay,
    today: LocalDate,
    actions: MealPlanActions,
    onMore: (MealPlanEntry) -> Unit,
    repeated: Set<String>,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val isToday = day.date == today
    val past = day.date < today
    val content =
        @Composable {
            Row(Modifier.fillMaxWidth()) {
                DayMark(day.date, isToday, past, locale)
                Column(Modifier.weight(1f)) {
                    if (day.entries.isEmpty()) {
                        Box(Modifier.heightIn(min = EmptyDayHeight), contentAlignment = Alignment.CenterStart) {
                            TextAction(stringResource(R.string.plan_dish), { actions.onAdd(day.date) }, icon = R.drawable.ic_add)
                        }
                    } else {
                        Row {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MealGap)) {
                                day.entries.forEach { entry ->
                                    EntryRow(
                                        entry,
                                        past,
                                        actions,
                                        onMore,
                                        travels =
                                            entry.recipe?.slug !in repeated,
                                    )
                                }
                            }
                            val addLabel = stringResource(R.string.plan_add_for, remember(day.date, locale) { weekday(day.date, locale) })
                            // Level with the first meal's thumbnail.
                            IconButton(R.drawable.ic_add, addLabel, { actions.onAdd(day.date) }, Modifier.padding(top = Space.s4))
                        }
                    }
                }
            }
        }
    if (isToday) {
        TonalBox(
            modifier.padding(horizontal = PageMargin - HighlightReach),
            contentPadding = PaddingValues(HighlightReach),
        ) { content() }
    } else {
        Box(modifier.padding(horizontal = PageMargin)) { content() }
    }
}

/** A day's short name over its figure: "Heute" in sage on today, past days in tertiary ink. The figure orients, it does not head. */
@Composable
private fun DayMark(date: LocalDate, isToday: Boolean, past: Boolean, locale: Locale) {
    val colors = AromaTheme.colors
    val name = remember(date, locale) { weekdayShort(date, locale) }
    val full = remember(date, locale) { weekday(date, locale) }
    // The column grows with the font size, so "Heute" is never cut.
    val width = DayColumn * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(
        Modifier
            .width(width)
            .semantics(mergeDescendants = true) { heading() },
    ) {
        if (isToday) {
            // "Heute" stands where the short name does, in its size, in sage and medium weight (6.3).
            Text(
                stringResource(R.string.home_today),
                style = AromaTheme.type.meta.copy(fontWeight = FontWeight.Medium),
                color = colors.accent,
                maxLines = 1,
            )
        } else {
            Text(name, style = AromaTheme.type.meta, color = if (past) colors.inkTertiary else colors.inkSecondary, maxLines = 1)
        }
        Text(
            text = date.dayOfMonth.toString(),
            style = AromaTheme.type.numeral,
            color =
                when {
                    isToday -> colors.ink
                    past -> colors.inkTertiary
                    else -> colors.inkSecondary
                },
            modifier = Modifier.semantics { this.contentDescription = "$full ${date.dayOfMonth}." },
        )
    }
}

/** The column a day's name and figure stand in; an empty day's row is no taller than a row of one line. */
private val DayColumn = 44.dp
private val EmptyDayHeight = 56.dp

/** Between two days, and between the meals of one day, as between recipe rows anywhere. */
private val DayGap = Space.s24
private val MealGap = Space.s12

/**
 * A planned meal as a recipe row, "Abendessen · 45 Min." under its name. A tap opens the recipe, a
 * long press the meal's sheet; screen readers get the sheet's actions on the row itself.
 */
@Composable
private fun EntryRow(
    entry: MealPlanEntry,
    past: Boolean,
    actions: MealPlanActions,
    onMore: (MealPlanEntry) -> Unit,
    travels: Boolean,
) {
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
        fallbackIcon = if (recipe == null) R.drawable.ic_notes else R.drawable.ic_plate,
        slug = recipe?.slug?.takeIf { travels },
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

/** What the week's recipes need, to put on the shopping list in one go; it stands in for the basket the head once had. */
@Composable
private fun WeekIngredients(recipes: Int, items: Int?, onShop: () -> Unit, modifier: Modifier = Modifier) {
    TonalBox(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircleMark(R.drawable.ic_basket)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Space.s12),
            ) {
                Text(stringResource(R.string.plan_week_ingredients), style = AromaTheme.type.label)
                val from = pluralStringResource(R.plurals.plan_week_from, recipes, recipes)
                Text(
                    text =
                        listOfNotNull(
                            from,
                            items?.let { pluralStringResource(R.plurals.plan_week_items, it, it) },
                        ).joinToString(SEPARATOR),
                    style = AromaTheme.type.meta,
                )
            }
            SecondaryButton(stringResource(R.string.plan_to_list), onShop, compact = true)
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

/** The week's outline while it loads: days with a meal each. */
@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = PageMargin), verticalArrangement = Arrangement.spacedBy(Space.s16)) {
        repeat(LOADING_DAYS) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(DayColumn), verticalArrangement = Arrangement.spacedBy(Space.s8)) {
                    SkeletonLine(Modifier.width(Space.s20))
                    SkeletonLine(Modifier.width(Space.s24))
                }
                Skeleton(Modifier.size(ThumbnailSize), shape = AromaTheme.shapes.thumbnail)
                Spacer(Modifier.width(Space.s16))
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
