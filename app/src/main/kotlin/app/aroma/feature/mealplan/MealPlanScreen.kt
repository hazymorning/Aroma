package app.aroma.feature.mealplan

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.ImageSize
import app.aroma.core.model.MealPlanEntry
import app.aroma.data.AromaError
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.AromaPullToRefreshBox
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.Kicker
import app.aroma.ui.components.LinkTone
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PageEndSpace
import app.aroma.ui.components.PageHeader
import app.aroma.ui.components.Placeholder
import app.aroma.ui.components.QuietLink
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.ScrollToTopOnReselect
import app.aroma.ui.components.StatusBarScrim
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.mealTypeLabel
import app.aroma.ui.components.rememberRowSwipeState
import app.aroma.ui.navigation.Transitions.Settle
import app.aroma.ui.theme.CardTitle
import app.aroma.ui.theme.IconKeyline
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.tabular
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale

data class MealPlanDay(val date: LocalDate, val entries: List<MealPlanEntry>)

data class MealPlanUiState(
    val today: LocalDate = LocalDate.now(),
    val weekStart: LocalDate = today,
    val days: List<MealPlanDay> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val error: AromaError? = null,
)

data class MealPlanActions(
    val onPreviousWeek: () -> Unit = {},
    val onNextWeek: () -> Unit = {},
    val onToday: () -> Unit = {},
    val onAdd: (LocalDate) -> Unit = {},
    val onOpenEntry: (MealPlanEntry) -> Unit = {},
    val onDeleteEntry: (MealPlanEntry) -> Unit = {},
    val onShopEntry: (MealPlanEntry) -> Unit = {},
    val onRefresh: () -> Unit = {},
)

private val Thumbnail = 56.dp
private val RowGlyph = 20.dp

/**
 * Shorter than the add button's 48 dp target, which Material lets reach past the row for touch,
 * so a weekday sits as close to its meals as to its hairline.
 */
private val DayRowHeight = 40.dp

@Composable
fun MealPlanScreen(state: MealPlanUiState, actions: MealPlanActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(listState)
    // By id, so the sheet survives rotation and closes by itself once its entry is gone.
    var sheetEntryId by rememberSaveable { mutableStateOf<Int?>(null) }
    AromaPullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + PageEndSpace),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { Header(state, actions) }
            when {
                state.isLoading -> {
                    item(key = "loading") { Loading() }
                }

                state.error != null && state.days.all { it.entries.isEmpty() } -> {
                    item(key = "error") {
                        ErrorState(state.error, onRetry = actions.onRefresh, modifier = Modifier.fillMaxWidth())
                    }
                }

                else -> {
                    state.days.forEach { day ->
                        daySection(day, isToday = day.date == state.today, actions = actions, onMore = { sheetEntryId = it.id })
                    }
                }
            }
        }
        StatusBarScrim()
    }
    val sheetEntry = sheetEntryId?.let { id -> state.days.firstNotNullOfOrNull { day -> day.entries.firstOrNull { it.id == id } } }
    if (sheetEntry != null) {
        EntrySheet(
            entry = sheetEntry,
            onShop = { actions.onShopEntry(sheetEntry) },
            onRemove = { actions.onDeleteEntry(sheetEntry) },
            onDismiss = { sheetEntryId = null },
        )
    }
}

@Composable
private fun Header(state: MealPlanUiState, actions: MealPlanActions) {
    val locale = currentLocale()
    val weekEnd = state.weekStart.plusDays(6)
    val range = remember(state.weekStart, locale) { formatRange(state.weekStart, weekEnd, locale) }
    val showsToday = state.today in state.weekStart..weekEnd
    // The week row's 48 dp targets already hold the range off the title; the space below it is
    // larger, so the range reads as the title's second line rather than as the first day's.
    Column(Modifier.padding(bottom = Space.xs)) {
        PageHeader(title = stringResource(R.string.nav_meal_plan), kicker = weekNumbers(state.weekStart, weekEnd))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = ScreenMargin, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = range,
                style = MaterialTheme.typography.titleMedium.tabular(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!showsToday) {
                QuietLink(
                    text = stringResource(R.string.plan_this_week),
                    onClick = actions.onToday,
                    tone = LinkTone.Ink,
                    modifier = Modifier.padding(start = Space.m, end = Space.xs),
                )
            }
            RowAction(R.drawable.ic_chevron_left, stringResource(R.string.plan_previous_week), actions.onPreviousWeek)
            RowAction(R.drawable.ic_chevron_right, stringResource(R.string.plan_next_week), actions.onNextWeek)
        }
        if (state.isOffline) OfflineNotice(Modifier.padding(horizontal = ScreenMargin))
    }
}

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

/** "23.–29. November" in German, "November 23–29" in English; across months "30. Nov. – 6. Dez.". */
private fun formatRange(start: LocalDate, end: LocalDate, locale: Locale): String {
    fun format(date: LocalDate, pattern: String) = date.format(DateTimeFormatter.ofPattern(pattern, locale))
    val german = locale.language == "de"
    return when {
        start.month == end.month && german -> "${format(start, "d.")}–${format(end, "d. MMMM")}"
        start.month == end.month -> "${format(start, "MMMM d")}–${format(end, "d")}"
        german -> "${format(start, "d. MMM")} – ${format(end, "d. MMM")}"
        else -> "${format(start, "MMM d")} – ${format(end, "MMM d")}"
    }
}

private fun LazyListScope.daySection(day: MealPlanDay, isToday: Boolean, actions: MealPlanActions, onMore: (MealPlanEntry) -> Unit) {
    item(key = "day-${day.date}") {
        DayHeader(
            date = day.date,
            isToday = isToday,
            isEmpty = day.entries.isEmpty(),
            onAdd = { actions.onAdd(day.date) },
            modifier = Modifier.settling(this),
        )
    }
    itemsIndexed(day.entries, key = { _, entry -> "entry-${entry.id}" }) { index, entry ->
        SwipeToRemove(
            onRemove = { actions.onDeleteEntry(entry) },
            modifier =
                Modifier
                    .settling(this)
                    .padding(top = if (index > 0) Space.xxs else 0.dp),
        ) {
            EntryRow(
                entry = entry,
                onClick = { if (entry.recipe != null) actions.onOpenEntry(entry) else onMore(entry) },
                onMore = { onMore(entry) },
                onShop = entry.recipe?.let { { actions.onShopEntry(entry) } },
                onRemove = { actions.onDeleteEntry(entry) },
            )
        }
    }
}

/** Rows fade in and out and the ones below glide into place, so a removal never jumps. */
private fun Modifier.settling(scope: LazyItemScope): Modifier =
    with(scope) {
        animateItem(
            fadeInSpec = tween(Motion.STATE),
            placementSpec = tween(Motion.EXPAND, easing = Settle),
            fadeOutSpec = tween(Motion.STATE),
        )
    }

private fun weekday(date: LocalDate, locale: Locale): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

/**
 * A hairline, then the weekday and its date on one baseline with a quiet add at the end. An empty
 * day says so underneath, and the whole day becomes one target that plans a meal.
 */
@Composable
private fun DayHeader(
    date: LocalDate,
    isToday: Boolean,
    isEmpty: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val weekday = remember(date, locale) { weekday(date, locale) }
    val addLabel = stringResource(R.string.plan_add_for, weekday)
    Column(modifier.padding(top = Space.s)) {
        Hairline(Modifier.padding(horizontal = ScreenMargin))
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (isEmpty) Modifier.clickable(onClickLabel = addLabel, role = Role.Button, onClick = onAdd) else Modifier)
                .padding(top = Space.xs),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(DayRowHeight)
                        .padding(start = ScreenMargin, end = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DayTitle(date, weekday, isToday, Modifier.weight(1f))
                RowAction(R.drawable.ic_add, addLabel, onAdd)
            }
            AnimatedVisibility(
                visible = isEmpty,
                enter = fadeIn(tween(Motion.STATE)) + expandVertically(tween(Motion.EXPAND, easing = Settle)),
                exit = fadeOut(tween(Motion.STATE)) + shrinkVertically(tween(Motion.EXPAND, easing = Settle)),
            ) {
                Text(
                    text = stringResource(R.string.plan_day_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, bottom = Space.s),
                )
            }
        }
    }
}

/** "Montag 23. Nov.", with a sage "Heute" after today's date. */
@Composable
private fun DayTitle(date: LocalDate, weekday: String, isToday: Boolean, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val shortDate =
        remember(date, locale) { date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)) }
    Row(modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(weekday, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.alignByBaseline())
        Spacer(Modifier.width(Space.xs))
        Text(
            text = shortDate,
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
        if (isToday) {
            Spacer(Modifier.width(Space.s))
            Kicker(stringResource(R.string.home_today), Modifier.alignByBaseline(), color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A plain 48 dp action with a 20 dp glyph in the soft ink, for the week and day rows. */
@Composable
private fun RowAction(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(RowGlyph),
        )
    }
}

/** Swiping a row towards the start removes it; the snackbar offers the way back. */
@Composable
private fun SwipeToRemove(onRemove: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberRowSwipeState()
    val haptics = LocalHapticFeedback.current
    val armed = state.targetValue == SwipeToDismissBoxValue.EndToStart
    LaunchedEffect(armed) {
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { onRemove() },
        modifier = modifier,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                    .padding(end = ScreenMargin),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(RowGlyph),
                )
            }
        },
    ) { content() }
}

/**
 * A planned meal: thumbnail, meal type and title. A tap opens the recipe, a long press the entry's
 * sheet; screen readers get both actions of the sheet directly.
 */
@Composable
private fun EntryRow(
    entry: MealPlanEntry,
    onClick: () -> Unit,
    onMore: () -> Unit,
    onShop: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val shopLabel = stringResource(R.string.recipe_add_to_list)
    val removeLabel = stringResource(R.string.plan_remove)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // Opaque, so the swipe background only shows where the row has moved away.
                .background(MaterialTheme.colorScheme.background)
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = stringResource(R.string.action_more),
                    onLongClick = onMore,
                    onClick = onClick,
                ).semantics {
                    customActions =
                        listOfNotNull(
                            onShop?.let { shop ->
                                CustomAccessibilityAction(shopLabel) {
                                    shop()
                                    true
                                }
                            },
                            CustomAccessibilityAction(removeLabel) {
                                onRemove()
                                true
                            },
                        )
                }.padding(horizontal = ScreenMargin, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryThumbnail(entry)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Kicker(mealTypeLabel(entry.type))
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = entry.recipe?.name ?: entry.title,
                style = CardTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.note.isNotBlank()) {
                Text(
                    text = entry.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EntryThumbnail(entry: MealPlanEntry) {
    if (entry.recipe != null) {
        RecipeImage(
            image = entry.recipe.image,
            size = ImageSize.Thumbnail,
            shape = MaterialTheme.shapes.small,
            fallbackIconSize = RowGlyph,
            modifier = Modifier.size(Thumbnail),
        )
    } else {
        Box(
            Modifier
                .size(Thumbnail)
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_notes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(RowGlyph),
            )
        }
    }
}

/** What a long press offers: the shopping list for recipes, and removal from the plan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheet(entry: MealPlanEntry, onShop: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    val choose = { action: () -> Unit ->
        scope.hideThen(sheetState) {
            onDismiss()
            action()
        }
    }
    AromaBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = Space.m),
        ) {
            Column(Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xs, bottom = Space.m)) {
                Kicker("${weekday(entry.date, locale)} · ${mealTypeLabel(entry.type)}")
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = entry.recipe?.name ?: entry.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (entry.recipe != null) {
                SheetAction(R.drawable.ic_basket_add, stringResource(R.string.recipe_add_to_list), onClick = { choose(onShop) })
                Hairline(Modifier.padding(start = IconKeyline, end = ScreenMargin))
            }
            SheetAction(R.drawable.ic_delete, stringResource(R.string.plan_remove), onClick = { choose(onRemove) }, destructive = true)
        }
    }
}

/** A 56 dp sheet row; the label starts on the 64 dp keyline after its glyph. */
@Composable
private fun SheetAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = ScreenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(IconKeyline - ScreenMargin - Space.m), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(RowGlyph),
            )
        }
        Spacer(Modifier.width(Space.m))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The week's outline while it loads: day lines with one meal each. */
@Composable
private fun Loading() {
    Column(Modifier.padding(top = Space.s)) {
        repeat(3) {
            Hairline(Modifier.padding(horizontal = ScreenMargin))
            Box(
                Modifier
                    .padding(start = ScreenMargin, top = Space.xs)
                    .height(DayRowHeight),
                contentAlignment = Alignment.CenterStart,
            ) { TextLinePlaceholder(120.dp) }
            Row(Modifier.padding(horizontal = ScreenMargin, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                Placeholder(Modifier.size(Thumbnail), shape = MaterialTheme.shapes.small)
                Spacer(Modifier.width(Space.m))
                Column {
                    TextLinePlaceholder(64.dp)
                    Spacer(Modifier.height(Space.xs))
                    TextLinePlaceholder(180.dp)
                }
            }
            Spacer(Modifier.height(Space.s))
        }
    }
}

@Composable
private fun TextLinePlaceholder(width: Dp) {
    Placeholder(
        Modifier
            .width(width)
            .height(12.dp),
        shape = MaterialTheme.shapes.extraSmall,
    )
}
