package app.aroma.feature.recipe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aroma.R
import app.aroma.core.model.Filing
import app.aroma.core.model.Organizers
import app.aroma.feature.editor.MealChips
import app.aroma.feature.editor.OrganizerChips
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.GroupLabel
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space

/**
 * What the recipe is filed under, the kind of dish first, then its categories and tags; each choice
 * holds at once. The group's [organizers] are null until they have loaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerSheet(
    filing: Filing,
    organizers: Organizers?,
    onFile: (Filing) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = organizers?.categories.orEmpty()
    AromaBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.organizers_title), modifier = modifier) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = Space.s, bottom = Space.l),
        ) {
            GroupLabel(stringResource(R.string.organizers_meal), start = ScreenMargin)
            MealChips(filing.meals, onToggle = { meal, label -> onFile(filing.toggle(meal, categories, label)) })
            GroupLabel(stringResource(R.string.recipes_categories), Modifier.padding(top = Space.l), start = ScreenMargin)
            OrganizerChips(
                options = categories,
                selected = filing.categories,
                onChange = { onFile(filing.copy(categories = it)) },
                newLabel = stringResource(R.string.organizers_new_category),
            )
            GroupLabel(stringResource(R.string.recipes_tags), Modifier.padding(top = Space.l), start = ScreenMargin)
            OrganizerChips(
                options = organizers?.tags.orEmpty(),
                selected = filing.tags,
                onChange = { onFile(filing.copy(tags = it)) },
                newLabel = stringResource(R.string.organizers_new_tag),
            )
        }
    }
}
