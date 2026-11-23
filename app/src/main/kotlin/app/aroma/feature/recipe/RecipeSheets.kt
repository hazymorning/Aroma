package app.aroma.feature.recipe

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.format.IngredientFormatter
import app.aroma.core.format.IngredientLine
import app.aroma.core.model.MealType
import app.aroma.core.model.Recipe
import app.aroma.core.model.ShoppingListInfo
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.AromaFilterChip
import app.aroma.ui.components.CheckCircle
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.Kicker
import app.aroma.ui.components.LinkTone
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.QuietLink
import app.aroma.ui.components.RatingStars
import app.aroma.ui.components.SegmentedControl
import app.aroma.ui.components.Stepper
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.mealTypeLabel
import app.aroma.ui.components.ratingTouchInset
import app.aroma.ui.theme.IconKeyline
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Up to this many lists are offered as segments; more scroll by as chips. */
private const val MAX_SEGMENTS = 3

/** The rating sheet's stars, large enough to hit with a thumb. */
private const val STAR_SIZE = 28

/** How long the chosen stars stay in view before the rating sheet closes. */
private const val RATED_PAUSE_MILLIS = 600L

/** Lets the cook untick what is already at home before adding a recipe to a list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(
    recipe: Recipe,
    scale: Double,
    lists: List<ShoppingListInfo>?,
    onAdd: (ShoppingListInfo, Set<Int>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    preferredListId: String? = null,
    onScale: (Double) -> Unit = {},
) {
    val locale = currentLocale()
    var selected by rememberSaveable { mutableStateOf(recipe.ingredients.indices.toSet()) }
    var listId by rememberSaveable { mutableStateOf<String?>(null) }
    val list = lists?.firstOrNull { it.id == (listId ?: preferredListId) } ?: lists?.firstOrNull()
    val shown =
        remember(recipe.ingredients) {
            recipe.ingredients.withIndex().filter { (_, it) -> it.note.isNotBlank() || it.isParsed || it.display.isNotBlank() }
        }
    val allSelected = selected.size == recipe.ingredients.size
    AromaBottomSheet(onDismissRequest = onDismiss, modifier = modifier, sheetState = rememberFullSheetState()) {
        SheetTitle(stringResource(R.string.recipe_add_to_list))
        if (lists != null && lists.size > 1) {
            ListChoice(lists, selected = list, onSelect = { listId = it.id }, modifier = Modifier.padding(bottom = Space.m))
        }
        // The row on the recipe sits above the servings, so they can be chosen here as well.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The minus circle, not its touch area, starts on the margin.
            Stepper(
                label = servingsLabel(recipe.servings, scale, locale),
                onDecrease = { onScale(Servings.step(recipe.servings, scale, -1)) },
                onIncrease = { onScale(Servings.step(recipe.servings, scale, +1)) },
                canDecrease = Servings.canDecrease(recipe.servings, scale),
                modifier = Modifier.offset(x = -Space.xxs),
            )
            Spacer(Modifier.weight(1f))
            QuietLink(
                text = stringResource(if (allSelected) R.string.select_none else R.string.select_all),
                onClick = { selected = if (allSelected) emptySet() else recipe.ingredients.indices.toSet() },
                tone = LinkTone.Ink,
            )
        }
        LazyColumn(
            Modifier
                .weight(1f, fill = false)
                .padding(top = Space.xs),
        ) {
            itemsIndexed(shown, key = { _, (index, _) -> index }) { position, (index, ingredient) ->
                if (position > 0) Hairline(Modifier.padding(start = IconKeyline, end = ScreenMargin))
                IngredientChoice(
                    line = IngredientFormatter.format(ingredient, scale, locale),
                    checked = index in selected,
                    onToggle = { selected = if (index in selected) selected - index else selected + index },
                )
            }
        }
        if (lists != null && lists.isEmpty()) {
            Text(
                text = stringResource(R.string.shopping_no_lists_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m),
            )
        }
        SheetFooter {
            PrimaryButton(
                text = pluralStringResource(R.plurals.add_ingredients, selected.size, selected.size),
                onClick = { list?.let { onAdd(it, selected) } },
                enabled = list != null && selected.isNotEmpty(),
                loading = lists == null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A few lists as segments, more as a row of chips. */
@Composable
private fun ListChoice(
    lists: List<ShoppingListInfo>,
    selected: ShoppingListInfo?,
    onSelect: (ShoppingListInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lists.size <= MAX_SEGMENTS) {
        SegmentedControl(
            options = lists.map { it.name },
            selectedIndex = lists.indexOf(selected),
            onSelect = { onSelect(lists[it]) },
            modifier = modifier.padding(horizontal = ScreenMargin),
        )
    } else {
        LazyRow(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = ScreenMargin),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            items(lists, key = { it.id }) { option ->
                AromaFilterChip(selected = option == selected, label = option.name, onClick = { onSelect(option) })
            }
        }
    }
}

/** An ingredient to take along or leave out; what stays at home reads a shade softer. */
@Composable
private fun IngredientChoice(line: IngredientLine, checked: Boolean, onToggle: () -> Unit) {
    val color by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(Motion.STATE),
        label = "ingredientChoice",
    )
    val noteColor = MaterialTheme.colorScheme.onSurfaceVariant
    val text =
        remember(line, noteColor) {
            buildAnnotatedString {
                append(listOf(line.measureText(), line.text).filter { it.isNotBlank() }.joinToString(" "))
                line.note?.takeIf { it.isNotBlank() }?.let { note ->
                    if (length > 0) append(", ")
                    withStyle(SpanStyle(color = noteColor)) { append(note) }
                }
            }
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(checked, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = ScreenMargin, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        CheckCircle(checked)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

/** Day and meal for planning a recipe; days are the coming week. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSheet(today: LocalDate, onPlan: (LocalDate, MealType) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    var day by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var type by rememberSaveable { mutableStateOf(MealType.Dinner) }
    val days = remember(today) { (0L until 7L).map { today.plusDays(it) } }
    val todayLabel = stringResource(R.string.home_today)
    val tomorrowLabel = stringResource(R.string.plan_tomorrow)
    AromaBottomSheet(onDismissRequest = onDismiss, modifier = modifier, sheetState = rememberFullSheetState()) {
        SheetTitle(stringResource(R.string.recipe_add_to_plan))
        ChipGroup(stringResource(R.string.plan_day)) {
            items(days, key = { it.toEpochDay() }) { date ->
                val label =
                    when (date) {
                        today -> todayLabel
                        today.plusDays(1) -> tomorrowLabel
                        else -> shortDayLabel(date, locale)
                    }
                AromaFilterChip(selected = day == date.toEpochDay(), label = label, onClick = { day = date.toEpochDay() })
            }
        }
        ChipGroup(stringResource(R.string.plan_meal), Modifier.padding(top = Space.l, bottom = Space.l)) {
            items(MealType.entries) { option ->
                AromaFilterChip(selected = type == option, label = mealTypeLabel(option), onClick = { type = option })
            }
        }
        SheetFooter {
            PrimaryButton(
                text = stringResource(R.string.action_plan),
                onClick = { onPlan(LocalDate.ofEpochDay(day), type) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A kicker over a row of chips that scrolls from edge to edge but starts on the margin. */
@Composable
private fun ChipGroup(label: String, modifier: Modifier = Modifier, chips: LazyListScope.() -> Unit) {
    Column(modifier) {
        Kicker(label, Modifier.padding(horizontal = ScreenMargin))
        LazyRow(
            modifier = Modifier.padding(top = Space.xs),
            contentPadding = PaddingValues(horizontal = ScreenMargin),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            content = chips,
        )
    }
}

/**
 * Five large stars to rate the recipe. Once a star is tapped, the sheet waits for the stars to
 * fill in, then closes by itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RatingSheet(rating: Double?, onRate: (Int) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(rating) }
    AromaBottomSheet(onDismissRequest = onDismiss, modifier = modifier, sheetState = sheetState) {
        SheetTitle(stringResource(R.string.recipe_rate))
        RatingStars(
            rating = chosen,
            starSize = STAR_SIZE,
            onRate = { stars ->
                chosen = stars.toDouble()
                onRate(stars)
                scope
                    .launch {
                        delay(RATED_PAUSE_MILLIS)
                        sheetState.hide()
                    }.invokeOnCompletion { onDismiss() }
            },
            modifier =
                Modifier
                    .navigationBarsPadding()
                    .padding(start = ScreenMargin - ratingTouchInset(STAR_SIZE), bottom = Space.l),
        )
    }
}

/** Opens a sheet at its full height, so its pinned action is never below the screen's edge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberFullSheetState() = rememberModalBottomSheetState(skipPartiallyExpanded = true)

/** A sheet's serif title on the margin, 16 dp above what it introduces. */
@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, bottom = Space.m),
    )
}

/** The sheet's one action, pinned under a hairline above the navigation bar. */
@Composable
private fun SheetFooter(action: @Composable () -> Unit) {
    Column(Modifier.navigationBarsPadding()) {
        Hairline(Modifier.padding(horizontal = ScreenMargin))
        Box(Modifier.padding(horizontal = ScreenMargin, vertical = Space.m)) { action() }
    }
}

private fun shortDayLabel(date: LocalDate, locale: Locale): String {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    return "$weekday ${date.format(DateTimeFormatter.ofPattern("d.", locale))}"
}
