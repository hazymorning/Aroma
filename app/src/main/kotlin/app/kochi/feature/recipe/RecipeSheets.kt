package app.kochi.feature.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.kochi.R
import app.kochi.core.format.IngredientFormatter
import app.kochi.core.model.MealType
import app.kochi.core.model.Recipe
import app.kochi.core.model.ShoppingListInfo
import app.kochi.ui.components.KochiFilterChip
import app.kochi.ui.components.currentLocale
import app.kochi.ui.components.mealTypeLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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
) {
    val locale = currentLocale()
    var selected by rememberSaveable { mutableStateOf(recipe.ingredients.indices.toSet()) }
    var listId by rememberSaveable { mutableStateOf<String?>(null) }
    val list = lists?.firstOrNull { it.id == listId } ?: lists?.firstOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.recipe_add_to_list),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (lists != null && lists.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(lists, key = { it.id }) { option ->
                        KochiFilterChip(selected = option.id == list?.id, label = option.name, onClick = { listId = option.id })
                    }
                }
            }
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    selected = if (selected.size == recipe.ingredients.size) emptySet() else recipe.ingredients.indices.toSet()
                }) {
                    val allSelected = selected.size == recipe.ingredients.size
                    Text(stringResource(if (allSelected) R.string.select_none else R.string.select_all))
                }
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(recipe.ingredients) { index, ingredient ->
                    if (ingredient.note.isBlank() && !ingredient.isParsed && ingredient.display.isBlank()) return@itemsIndexed
                    val checked = index in selected
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .toggleable(checked, role = Role.Checkbox) {
                                    selected = if (checked) selected - index else selected + index
                                }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(
                            text = IngredientFormatter.format(ingredient, scale, locale).toPlainText(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            if (lists != null && lists.isEmpty()) {
                Text(
                    text = stringResource(R.string.shopping_no_lists_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            Button(
                onClick = { list?.let { onAdd(it, selected) } },
                enabled = list != null && selected.isNotEmpty(),
                modifier =
                    Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                        .height(52.dp),
            ) {
                if (lists == null) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                } else {
                    Text(pluralStringResource(R.plurals.add_ingredients, selected.size, selected.size))
                }
            }
        }
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
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.recipe_add_to_plan),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                stringResource(R.string.plan_day),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 24.dp, top = 20.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(days, key = { it.toEpochDay() }) { date ->
                    val label =
                        when (date) {
                            today -> todayLabel
                            today.plusDays(1) -> tomorrowLabel
                            else -> shortDayLabel(date, locale)
                        }
                    KochiFilterChip(selected = day == date.toEpochDay(), label = label, onClick = { day = date.toEpochDay() })
                }
            }
            Text(
                stringResource(R.string.plan_meal),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 24.dp, top = 12.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(MealType.entries) { option ->
                    KochiFilterChip(selected = type == option, label = mealTypeLabel(option), onClick = { type = option })
                }
            }
            Button(
                onClick = { onPlan(LocalDate.ofEpochDay(day), type) },
                modifier =
                    Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                        .height(52.dp),
            ) { Text(stringResource(R.string.action_plan)) }
        }
    }
}

private fun shortDayLabel(date: LocalDate, locale: Locale): String {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    return "$weekday ${date.format(DateTimeFormatter.ofPattern("d.", locale))}"
}
