package app.aroma.feature.recipe

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
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
import app.aroma.ui.components.CheckCircle
import app.aroma.ui.components.GroupHead
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.RatingStars
import app.aroma.ui.components.SelectionRow
import app.aroma.ui.components.SettingsRow
import app.aroma.ui.components.SheetFooter
import app.aroma.ui.components.Stepper
import app.aroma.ui.components.Text
import app.aroma.ui.components.TextAction
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.hideThen
import app.aroma.ui.components.mealTypeLabel
import app.aroma.ui.components.ratingTouchInset
import app.aroma.ui.components.rememberFullSheetState
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.BoxPadding
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.RowHeight
import app.aroma.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The rating sheet's stars, large enough to hit with a thumb. */
private val StarSize = 28.dp

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
    AromaBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.recipe_add_to_list), modifier = modifier) {
        if (lists != null && lists.size > 1) {
            SelectionRow(
                options = lists.map { it.name },
                selectedIndex = lists.indexOf(list),
                onSelect = { listId = lists[it].id },
                modifier = Modifier.padding(bottom = Space.s16),
            )
        }
        // The row on the recipe sits above the servings, so they can be chosen here as well.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PageMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The minus circle, not its touch area, starts on the margin.
            Stepper(
                label = servingsLabel(recipe.servings, scale, locale),
                onDecrease = { onScale(Servings.step(recipe.servings, scale, -1)) },
                onIncrease = { onScale(Servings.step(recipe.servings, scale, +1)) },
                canDecrease = Servings.canDecrease(recipe.servings, scale),
                modifier = Modifier.offset(x = -Space.s4),
            )
            Spacer(Modifier.weight(1f))
            TextAction(
                text = stringResource(if (allSelected) R.string.deselect_all else R.string.select_all),
                onClick = { selected = if (allSelected) emptySet() else recipe.ingredients.indices.toSet() },
            )
        }
        LazyColumn(
            Modifier
                .weight(1f, fill = false)
                .padding(top = Space.s8),
        ) {
            items(shown, key = { (index, _) -> index }) { (index, ingredient) ->
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
                style = AromaTheme.type.bodySmall,
                color = AromaTheme.colors.inkSecondary,
                modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s16),
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

/** An ingredient to take along or leave out; what stays at home reads a shade softer. */
@Composable
private fun IngredientChoice(line: IngredientLine, checked: Boolean, onToggle: () -> Unit) {
    val color by animateColorAsState(
        if (checked) AromaTheme.colors.ink else AromaTheme.colors.inkSecondary,
        tween(Motion.STATE),
        label = "ingredientChoice",
    )
    val noteColor = AromaTheme.colors.inkSecondary
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
                .heightIn(min = RowHeight.oneLine)
                .toggleable(checked, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = PageMargin, vertical = Space.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s16),
    ) {
        CheckCircle(checked)
        Text(text, style = AromaTheme.type.body, color = color)
    }
}

/**
 * Day and meal for planning a recipe, or for moving a planned one ([title], [actionLabel] and the
 * current [day] and [type] then say so); days are the coming week.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSheet(
    today: LocalDate,
    onPlan: (LocalDate, MealType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.recipe_add_to_plan),
    actionLabel: String = stringResource(R.string.action_plan),
    day: LocalDate = today,
    type: MealType = MealType.Dinner,
) {
    val locale = currentLocale()
    var chosenDay by rememberSaveable { mutableLongStateOf(day.toEpochDay()) }
    var chosenType by rememberSaveable { mutableStateOf(type) }
    val days = remember(today) { (0L until 7L).map { today.plusDays(it) } }
    val todayLabel = stringResource(R.string.home_today)
    val tomorrowLabel = stringResource(R.string.plan_tomorrow)
    AromaBottomSheet(onDismissRequest = onDismiss, title = title, modifier = modifier) {
        val dayLabels =
            days.map { date ->
                when (date) {
                    today -> todayLabel
                    today.plusDays(1) -> tomorrowLabel
                    else -> shortDayLabel(date, locale)
                }
            }
        ChoiceGroup(stringResource(R.string.plan_day)) {
            SelectionRow(
                options = dayLabels,
                selectedIndex = days.indexOfFirst { it.toEpochDay() == chosenDay },
                onSelect = { chosenDay = days[it].toEpochDay() },
            )
        }
        ChoiceGroup(stringResource(R.string.plan_meal), Modifier.padding(top = Space.s16, bottom = Space.s16)) {
            SelectionRow(
                options = MealType.entries.map { mealTypeLabel(it) },
                selectedIndex = MealType.entries.indexOf(chosenType),
                onSelect = { chosenType = MealType.entries[it] },
            )
        }
        SheetFooter {
            PrimaryButton(
                text = actionLabel,
                onClick = { onPlan(LocalDate.ofEpochDay(chosenDay), chosenType) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A group's head over its row of choices, which scrolls from edge to edge but starts on the margin. */
@Composable
private fun ChoiceGroup(label: String, modifier: Modifier = Modifier, choices: @Composable () -> Unit) {
    Column(modifier) {
        GroupHead(label, Modifier.padding(horizontal = PageMargin))
        choices()
    }
}

/**
 * Five large stars to rate the recipe. Once a star is tapped, the sheet waits for the stars to
 * fill in, then closes by itself. Tapping the star already chosen takes the rating back, which a
 * line under the stars says while there is a rating to take back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RatingSheet(rating: Double?, onRate: (Int) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(rating) }
    // The line that says how to take a rating back stands under the stars, in the room they leave below.
    val below = if (rating == null) Space.s24 else Space.s4
    AromaBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.recipe_rate),
        modifier = modifier,
        sheetState = sheetState,
    ) {
        RatingStars(
            rating = chosen,
            starSize = StarSize,
            clearable = true,
            onRate = { stars ->
                chosen = stars.takeIf { it > 0 }?.toDouble()
                onRate(stars)
                scope
                    .launch {
                        delay(RATED_PAUSE_MILLIS)
                        sheetState.hide()
                    }.invokeOnCompletion { onDismiss() }
            },
            modifier = Modifier.padding(start = PageMargin - ratingTouchInset(StarSize), bottom = below),
        )
        if (rating != null) {
            Text(
                text = stringResource(R.string.rating_remove_hint),
                style = AromaTheme.type.bodySmall,
                color = AromaTheme.colors.inkSecondary,
                modifier = Modifier.padding(start = PageMargin, end = PageMargin, bottom = Space.s24),
            )
        }
    }
}

/**
 * What a rework with Mealie's AI does and what it leaves alone, the choice to translate the recipe as
 * well, starting from the choice made last, and the button that starts it once the sheet has gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReworkSheet(translate: Boolean, onRework: (translate: Boolean) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    var translated by rememberSaveable { mutableStateOf(translate) }
    AromaBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.recipe_rework),
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Text(
            text = stringResource(R.string.recipe_rework_hint),
            style = AromaTheme.type.body,
            color = AromaTheme.colors.inkSecondary,
            modifier = Modifier.padding(horizontal = PageMargin),
        )
        SettingsRow(
            title = stringResource(R.string.add_ai_translate),
            checked = translated,
            onClick = { translated = !translated },
            modifier = Modifier.padding(top = Space.s12, start = PageMargin - BoxPadding, end = PageMargin - BoxPadding),
        )
        SheetFooter {
            PrimaryButton(
                text = stringResource(R.string.recipe_rework_start),
                onClick = {
                    scope.hideThen(sheetState) {
                        onDismiss()
                        onRework(translated)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun shortDayLabel(date: LocalDate, locale: Locale): String {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    return "$weekday ${date.format(DateTimeFormatter.ofPattern("d.", locale))}"
}
