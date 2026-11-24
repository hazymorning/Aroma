package app.aroma.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.aroma.R
import app.aroma.core.model.Meal
import app.aroma.core.model.Organizer
import app.aroma.ui.components.IconButton
import app.aroma.ui.components.PillField
import app.aroma.ui.components.Segment
import app.aroma.ui.components.SegmentInset
import app.aroma.ui.components.mealLabel
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space

/**
 * The kinds of dish in one row that scrolls from edge to edge, the [chosen] ones on the sage wash.
 * [onToggle] gets the label too: a category made for the kind of dish is called that.
 */
@Composable
fun MealChips(chosen: Set<Meal>, onToggle: (Meal, String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = PageMargin - SegmentInset),
    ) {
        items(Meal.entries) { meal ->
            val label = mealLabel(meal)
            Segment(text = label, selected = meal in chosen, onClick = { onToggle(meal, label) })
        }
    }
}

/**
 * The group's [options] as chips, the [selected] ones on the sage wash, over a field that names a new
 * one, chosen at once; Mealie makes it when the recipe is saved. Those named for a kind of dish are
 * left to [MealChips], so nothing shows twice.
 */
@Composable
fun OrganizerChips(
    options: List<Organizer>,
    selected: List<Organizer>,
    onChange: (List<Organizer>) -> Unit,
    newLabel: String,
    modifier: Modifier = Modifier,
) {
    val shown =
        remember(options, selected) {
            (options + selected.filter { chosen -> options.none(chosen::sameAs) })
                .filterNot { organizer -> Meal.entries.any { it.matches(organizer) } }
        }
    var name by rememberSaveable { mutableStateOf("") }
    val add = {
        val typed = name.trim()
        if (typed.isNotEmpty()) {
            // A name the group has already is that one, whatever its case.
            val organizer = options.firstOrNull { it.sameAs(Organizer.new(typed)) } ?: Organizer.new(typed)
            if (selected.none(organizer::sameAs)) onChange(selected + organizer)
            name = ""
        }
    }
    Column(modifier.padding(horizontal = PageMargin)) {
        if (shown.isNotEmpty()) {
            FlowRow(modifier = Modifier.padding(bottom = Space.s8).offset(x = -SegmentInset)) {
                shown.forEach { option ->
                    val isSelected = selected.any(option::sameAs)
                    Segment(
                        text = option.name,
                        selected = isSelected,
                        onClick = { onChange(if (isSelected) selected.filterNot(option::sameAs) else selected + option) },
                    )
                }
            }
        }
        PillField(
            value = name,
            onValueChange = { name = it },
            placeholder = newLabel,
            leadingIcon = R.drawable.ic_add,
            imeAction = ImeAction.Done,
            onSubmit = add,
            capitalization = KeyboardCapitalization.Sentences,
            trailing =
                if (name.isNotBlank()) {
                    { IconButton(R.drawable.ic_check, stringResource(R.string.organizers_add, name.trim()), add) }
                } else {
                    null
                },
        )
    }
}
