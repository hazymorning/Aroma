package app.aroma.feature.mealplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.MealType
import app.aroma.core.model.RecipeSummary
import app.aroma.ui.components.AromaFilterChip
import app.aroma.ui.components.PillTextField
import app.aroma.ui.components.RecipeListItem
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.mealTypeLabel
import java.time.LocalDate
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
    val locale = currentLocale()
    var type by rememberSaveable { mutableStateOf(MealType.Dinner) }
    var noteMode by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(
                text = stringResource(R.string.plan_for_day, date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(MealType.entries) { option ->
                    AromaFilterChip(selected = type == option, label = mealTypeLabel(option), onClick = { type = option })
                }
            }
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                SegmentedButton(
                    selected = !noteMode,
                    onClick = { noteMode = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = {},
                ) {
                    Text(stringResource(R.string.plan_mode_recipe))
                }
                SegmentedButton(
                    selected = noteMode,
                    onClick = { noteMode = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = {},
                ) {
                    Text(stringResource(R.string.plan_mode_note))
                }
            }
            if (noteMode) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.plan_note_title)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.plan_note_text)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onAdd(type, null, title, note) },
                        enabled = title.isNotBlank(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                    ) { Text(stringResource(R.string.action_plan)) }
                }
            } else {
                PillTextField(
                    value = search.query,
                    onValueChange = onSearch,
                    placeholder = stringResource(R.string.search_recipes_hint),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                Text(
                    text = stringResource(R.string.plan_tap_recipe),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 460.dp),
                ) {
                    if (search.isLoading && search.results.isEmpty()) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center).padding(24.dp))
                    }
                    LazyColumn {
                        items(search.results, key = { it.id }) { recipe ->
                            RecipeListItem(recipe = recipe, onClick = { onAdd(type, recipe, "", "") })
                        }
                    }
                }
            }
        }
    }
}
