package app.aroma.feature.mealplan

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.MealType
import app.aroma.core.model.RecipeSummary
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.AromaFilterChip
import app.aroma.ui.components.LabeledTextField
import app.aroma.ui.components.PillTextField
import app.aroma.ui.components.Placeholder
import app.aroma.ui.components.PlaceholderLine
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.RecipeListItem
import app.aroma.ui.components.SegmentedControl
import app.aroma.ui.components.SheetFooter
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.hideThen
import app.aroma.ui.components.mealTypeLabel
import app.aroma.ui.components.rememberFullSheetState
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Motion.Settle
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.ThumbnailSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Plan a meal for [date]: pick the meal, then tap a recipe or write a note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealSheet(
    date: LocalDate,
    search: PlanSearchState,
    onSearch: (String) -> Unit,
    onAdd: (MealType, RecipeSummary?, String, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var type by rememberSaveable { mutableStateOf(MealType.Dinner) }
    var noteMode by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    val plan = { recipe: RecipeSummary?, planTitle: String, planNote: String ->
        scope.hideThen(sheetState) { onAdd(type, recipe, planTitle, planNote) }
    }
    val locale = currentLocale()
    // The date stands under the title, so a day of next week is never mistaken for this one's.
    val day =
        remember(date, locale) { date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMd"), locale)) }
    AromaBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.plan_for_day, date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)),
        modifier = modifier,
        sheetState = sheetState,
        meta = { Text(day, style = AromaType.Small, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    ) {
        MealTypeChips(selected = type, onSelect = { type = it })
        SegmentedControl(
            options = listOf(stringResource(R.string.plan_mode_recipe), stringResource(R.string.plan_mode_note)),
            selectedIndex = if (noteMode) 1 else 0,
            onSelect = { noteMode = it == 1 },
            modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m),
        )
        // The sheet grows or shrinks with the form instead of jumping to its new height.
        AnimatedContent(
            targetState = noteMode,
            transitionSpec = {
                fadeIn(tween(Motion.STATE)) togetherWith fadeOut(tween(Motion.STATE)) using
                    SizeTransform(clip = false) { _, _ -> tween(Motion.EXPAND, easing = Settle) }
            },
            label = "addMealMode",
        ) { notes ->
            if (notes) {
                NoteForm(
                    title = title,
                    onTitleChange = { title = it },
                    note = note,
                    onNoteChange = { note = it },
                    onPlan = { plan(null, title, note) },
                )
            } else {
                RecipePicker(search, onSearch, onPick = { plan(it, "", "") })
            }
        }
    }
}

@Composable
private fun MealTypeChips(selected: MealType, onSelect: (MealType) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenMargin),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        items(MealType.entries) { option ->
            AromaFilterChip(selected = selected == option, label = mealTypeLabel(option), onClick = { onSelect(option) })
        }
    }
}

@Composable
private fun RecipePicker(search: PlanSearchState, onSearch: (String) -> Unit, onPick: (RecipeSummary) -> Unit) {
    Column(Modifier.padding(top = Space.l)) {
        PillTextField(
            value = search.query,
            onValueChange = onSearch,
            placeholder = stringResource(R.string.search_recipes_hint),
            modifier = Modifier.padding(horizontal = ScreenMargin),
        )
        Text(
            text = stringResource(R.string.plan_tap_recipe),
            style = AromaType.Small,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xs, bottom = Space.xxs),
        )
        when {
            search.isLoading && search.results.isEmpty() -> {
                ResultsPlaceholder()
            }

            search.results.isEmpty() -> {
                Text(
                    text = stringResource(R.string.plan_search_empty),
                    style = AromaType.Small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m, bottom = Space.l),
                )
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = Space.xs),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = ResultsMaxHeight),
                ) {
                    items(search.results, key = { it.id }) { recipe ->
                        RecipeListItem(recipe = recipe, onClick = { onPick(recipe) })
                    }
                }
            }
        }
    }
}

/** The search results scroll within this height, so the sheet stops growing. */
private val ResultsMaxHeight = 460.dp

/** How long the line of a result's name is in the outline. */
private val ResultWidth = 160.dp

/** Rows shaped like the results, while the first ones load. */
@Composable
private fun ResultsPlaceholder() {
    Column(Modifier.padding(bottom = Space.xs)) {
        repeat(3) {
            Row(Modifier.padding(horizontal = ScreenMargin, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                Placeholder(Modifier.size(ThumbnailSize), shape = MaterialTheme.shapes.small)
                Spacer(Modifier.width(Space.m))
                PlaceholderLine(Modifier.width(ResultWidth))
            }
        }
    }
}

/** A meal without a recipe: a title and an optional note, planned with the one button at the bottom. */
@Composable
private fun NoteForm(
    title: String,
    onTitleChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    onPlan: () -> Unit,
) {
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { titleFocus.requestFocus() }
    Column {
        Column(
            Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.l, bottom = Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            LabeledTextField(
                value = title,
                onValueChange = onTitleChange,
                label = stringResource(R.string.plan_note_title),
                fieldModifier = Modifier.focusRequester(titleFocus),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
            LabeledTextField(
                value = note,
                onValueChange = onNoteChange,
                label = stringResource(R.string.plan_note_text),
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
        SheetFooter {
            PrimaryButton(
                text = stringResource(R.string.action_plan),
                onClick = onPlan,
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
