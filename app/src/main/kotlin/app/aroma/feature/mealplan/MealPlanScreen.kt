package app.aroma.feature.mealplan

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.ImageSize
import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.MealType
import app.aroma.data.AromaError
import app.aroma.feature.recipe.PlanSheet
import app.aroma.ui.components.AromaAlertDialog
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.AromaPullToRefreshBox
import app.aroma.ui.components.BoxPart
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.IconButton
import app.aroma.ui.components.IconButtonReach
import app.aroma.ui.components.LargeHeader
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.SEPARATOR
import app.aroma.ui.components.ScrollToTopOnReselect
import app.aroma.ui.components.Separator
import app.aroma.ui.components.SheetAction
import app.aroma.ui.components.Skeleton
import app.aroma.ui.components.SkeletonLine
import app.aroma.ui.components.StatusBarCover
import app.aroma.ui.components.SwipeToRemove
import app.aroma.ui.components.Text
import app.aroma.ui.components.TextAction
import app.aroma.ui.components.TonalBox
import app.aroma.ui.components.TonalBoxPart
import app.aroma.ui.components.animateRow
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.hideThen
import app.aroma.ui.components.mealTypeLabel
import app.aroma.ui.components.rememberFullSheetState
import app.aroma.ui.components.weekday
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.BoxPadding
import app.aroma.ui.theme.EndSpace
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Motion.Decelerate
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.RowHeight
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.ThumbnailSize
import app.aroma.ui.theme.tabular
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    val onShopWeek: () -> Unit = {},
    val onMoveEntry: (MealPlanEntry, LocalDate, MealType) -> Unit = { _, _, _ -> },
    val onRefresh: () -> Unit = {},
)

@Composable
fun MealPlanScreen(state: MealPlanUiState, actions: MealPlanActions, modifier: Modifier = Modifier) {
    // Another week is another page, not an edit of this one: it shows at once, from its top.
    val listState = key(state.weekStart) { rememberLazyListState() }
    ScrollToTopOnReselect(listState)
    // By id, so the sheets survive rotation and close by themselves once their entry is gone.
    var sheetEntryId by rememberSaveable { mutableStateOf<Int?>(null) }
    var movingEntryId by rememberSaveable { mutableStateOf<Int?>(null) }
    AromaPullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + EndSpace),
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
                        daySection(day, today = state.today, actions = actions, onMore = { sheetEntryId = it.id })
                    }
                }
            }
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
}

@Composable
private fun Header(state: MealPlanUiState, actions: MealPlanActions) {
    val locale = currentLocale()
    val weekEnd = state.weekStart.plusDays(6)
    val range = remember(state.weekStart, locale) { formatRange(state.weekStart, weekEnd, locale) }
    val showsToday = state.today in state.weekStart..weekEnd
    Column(Modifier.padding(bottom = Space.s8)) {
        val recipes = state.days.sumOf { day -> day.entries.count { it.recipe != null } }
        var confirmShopping by rememberSaveable { mutableStateOf(false) }
        LargeHeader(title = stringResource(R.string.nav_meal_plan)) {
            if (recipes > 0) IconButton(R.drawable.ic_basket_add, stringResource(R.string.plan_shop_week), { confirmShopping = true })
        }
        if (confirmShopping) {
            AromaAlertDialog(
                onDismissRequest = { confirmShopping = false },
                title = stringResource(R.string.plan_shop_week),
                text = pluralStringResource(R.plurals.plan_shop_week_message, recipes, recipes),
                confirmLabel = stringResource(R.string.plan_shop_week_confirm),
                onConfirm = {
                    confirmShopping = false
                    actions.onShopWeek()
                },
                dismissLabel = stringResource(R.string.action_cancel),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = PageMargin, end = PageMargin - IconButtonReach, top = Space.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = range, style = AromaTheme.type.label.tabular(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = weekNumbers(state.weekStart, weekEnd),
                    style = AromaTheme.type.bodySmall.tabular(),
                    color = AromaTheme.colors.inkSecondary,
                    maxLines = 1,
                )
            }
            if (!showsToday) {
                TextAction(
                    text = stringResource(R.string.plan_this_week),
                    onClick = actions.onToday,
                    modifier = Modifier.padding(start = Space.s16, end = Space.s8),
                )
            }
            IconButton(R.drawable.ic_chevron_left, stringResource(R.string.plan_previous_week), actions.onPreviousWeek)
            IconButton(R.drawable.ic_chevron_right, stringResource(R.string.plan_next_week), actions.onNextWeek)
        }
        if (state.isOffline) OfflineNotice(Modifier.padding(horizontal = PageMargin))
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

/** A day is one card: its header, then one item per meal, with hairlines between the meals. */
private fun LazyListScope.daySection(day: MealPlanDay, today: LocalDate, actions: MealPlanActions, onMore: (MealPlanEntry) -> Unit) {
    val parts = day.entries.size + 1
    item(key = "day-${day.date}") {
        TonalBoxPart(
            part = BoxPart.of(0, parts),
            modifier =
                Modifier
                    .animateRow(this)
                    .padding(start = PageMargin, end = PageMargin, top = Space.s12),
        ) {
            PlanDayHeader(day.date, today, isEmpty = day.entries.isEmpty(), onAdd = { actions.onAdd(day.date) })
        }
    }
    itemsIndexed(day.entries, key = { _, entry -> "entry-${entry.id}" }) { index, entry ->
        TonalBoxPart(part = BoxPart.of(index + 1, parts), modifier = Modifier.animateRow(this).padding(horizontal = PageMargin)) {
            if (index > 0) Separator()
            SwipeToRemove(onRemove = { actions.onDeleteEntry(entry) }) {
                EntryRow(
                    entry = entry,
                    paper = AromaTheme.tonalFill,
                    onClick = { if (entry.recipe != null) actions.onOpenEntry(entry) else onMore(entry) },
                    onMore = { onMore(entry) },
                    onShop = entry.recipe?.let { { actions.onShopEntry(entry) } },
                    onRemove = { actions.onDeleteEntry(entry) },
                )
            }
        }
    }
}

/** A day's name and date, today's in the accent, with an [action] at the end. */
@Composable
private fun DayHead(date: LocalDate, today: LocalDate, action: @Composable () -> Unit) {
    val locale = currentLocale()
    val name = remember(date, locale) { weekday(date, locale) }
    val shortDate =
        remember(date, today.year, locale) {
            val skeleton = if (date.year == today.year) "MMMd" else "MMMdy"
            date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
        }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight.oneLine)
            .padding(start = BoxPadding, end = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(name, style = AromaTheme.type.label, color = if (date == today) AromaTheme.colors.accent else AromaTheme.colors.ink)
            Spacer(Modifier.width(Space.s8))
            Text(shortDate, style = AromaTheme.type.meta)
        }
        action()
    }
}

/**
 * The day's head with a quiet add at its end. An empty day says so underneath, and the whole day
 * becomes one target that plans a meal.
 */
@Composable
private fun PlanDayHeader(date: LocalDate, today: LocalDate, isEmpty: Boolean, onAdd: () -> Unit) {
    val locale = currentLocale()
    val addLabel = stringResource(R.string.plan_add_for, remember(date, locale) { weekday(date, locale) })
    // The note takes its room at once, so the list glides around it instead of following it in
    // steps; it fades in when the day's last meal goes, and is simply there on a day that opens empty.
    val note = remember { Animatable(if (isEmpty) 1f else 0f) }
    LaunchedEffect(isEmpty) { if (isEmpty) note.animateTo(1f, tween(Motion.STATE, easing = Motion.Decelerate)) else note.snapTo(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (isEmpty) Modifier.clickable(onClickLabel = addLabel, role = Role.Button, onClick = onAdd) else Modifier),
    ) {
        DayHead(date, today) { IconButton(R.drawable.ic_add, addLabel, onAdd) }
        if (isEmpty) {
            Text(
                text = stringResource(R.string.plan_day_empty),
                style = AromaTheme.type.bodySmall,
                color = AromaTheme.colors.inkSecondary,
                modifier =
                    Modifier
                        .padding(start = BoxPadding, end = BoxPadding, bottom = Space.s12)
                        .graphicsLayer { alpha = note.value },
            )
        }
    }
}

/**
 * A planned meal: thumbnail, meal type and title. A tap opens the recipe, a long press the entry's
 * sheet; screen readers get both actions of the sheet directly.
 */
@Composable
private fun EntryRow(
    entry: MealPlanEntry,
    paper: Color,
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
                .background(paper)
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
                }.padding(horizontal = BoxPadding, vertical = Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryThumbnail(entry)
        Spacer(Modifier.width(Space.s16))
        Column(Modifier.weight(1f)) {
            Text(mealTypeLabel(entry.type), style = AromaTheme.type.bodySmall, color = AromaTheme.colors.inkSecondary)
            Text(
                text = entry.recipe?.name ?: entry.title,
                style = AromaTheme.type.cardTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.note.isNotBlank()) {
                Text(
                    text = entry.note,
                    style = AromaTheme.type.bodySmall,
                    color = AromaTheme.colors.inkSecondary,
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
            shape = AromaTheme.shapes.thumbnail,
            fallbackIconSize = Glyph.control,
            modifier = Modifier.size(ThumbnailSize),
        )
    } else {
        Box(
            Modifier
                .size(ThumbnailSize)
                .background(AromaTheme.colors.surfacePressed, AromaTheme.shapes.thumbnail),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_notes),
                contentDescription = null,
                tint = AromaTheme.colors.inkTertiary,
                modifier = Modifier.size(Glyph.control),
            )
        }
    }
}

/** What a long press offers: the shopping list for recipes, and removal from the plan. */
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
                color = AromaTheme.colors.inkSecondary,
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

/** How long the outline's lines are: a day's name, then a meal's type and title. */
private val DayWidth = 120.dp
private val EntryLineWidths = listOf(64.dp, 180.dp)

/** The week's outline while it loads: day cards with one meal each. */
@Composable
private fun Loading() {
    Column {
        repeat(3) {
            TonalBox(Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s12), contentPadding = PaddingValues()) {
                Box(
                    Modifier
                        .padding(start = BoxPadding)
                        .height(RowHeight.oneLine),
                    contentAlignment = Alignment.CenterStart,
                ) { SkeletonLine(Modifier.width(DayWidth)) }
                Row(Modifier.padding(horizontal = BoxPadding, vertical = Space.s12), verticalAlignment = Alignment.CenterVertically) {
                    Skeleton(Modifier.size(ThumbnailSize), shape = AromaTheme.shapes.thumbnail)
                    Spacer(Modifier.width(Space.s16))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
                        EntryLineWidths.forEach { SkeletonLine(Modifier.width(it)) }
                    }
                }
            }
        }
    }
}

private fun MealPlanUiState.entry(id: Int?): MealPlanEntry? =
    id?.let {
        days.firstNotNullOfOrNull { day ->
            day.entries.firstOrNull {
                it.id ==
                    id
            }
        }
    }
