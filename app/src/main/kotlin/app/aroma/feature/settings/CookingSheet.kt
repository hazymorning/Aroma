package app.aroma.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import app.aroma.R
import app.aroma.core.format.MeasureSystem
import app.aroma.data.settings.IngredientsInSteps
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.SelectionRow
import app.aroma.ui.components.Text
import app.aroma.ui.components.labelRes
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space

/**
 * The settings for cooking, on a sheet of their own: the measures, then what the steps show, then
 * what cooking mode does. A setting with a few values offers them on a row of words, chosen right
 * there, so no second sheet opens over this one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CookingSheet(state: SettingsUiState, actions: SettingsActions, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AromaBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.settings_cooking), modifier = modifier) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.s16),
        ) {
            SheetChoice(
                title = stringResource(R.string.measures_title),
                hint = stringResource(R.string.measures_hint),
                options = MeasureSystem.entries.map { stringResource(it.choiceLabelRes()) },
                selectedIndex = state.measures.ordinal,
                onSelect = { actions.onMeasuresChange(MeasureSystem.entries[it]) },
            )
            SheetChoice(
                title = stringResource(R.string.settings_ingredients_in_steps),
                hint = stringResource(R.string.settings_ingredients_in_steps_hint),
                options = IngredientsInSteps.entries.map { stringResource(it.labelRes()) },
                selectedIndex = state.ingredientsInSteps.ordinal,
                onSelect = { actions.onIngredientsInStepsChange(IngredientsInSteps.entries[it]) },
                modifier = Modifier.padding(top = Space.s12),
            )
            SwitchRow(
                title = stringResource(R.string.settings_amounts_in_steps),
                description = stringResource(R.string.settings_amounts_in_steps_hint),
                checked = state.amountsInSteps,
                onCheckedChange = actions.onAmountsInStepsChange,
                onSheet = true,
                // As far from the words above as each setting stands from the next.
                modifier = Modifier.padding(top = Space.s8),
            )
            SwitchRow(
                title = stringResource(R.string.settings_keep_screen_on),
                description = stringResource(R.string.settings_keep_screen_on_hint),
                checked = state.keepScreenOn,
                onCheckedChange = actions.onKeepScreenOnChange,
                onSheet = true,
            )
            SwitchRow(
                title = stringResource(R.string.settings_next_step),
                description = stringResource(R.string.settings_next_step_hint),
                checked = state.showNextStep,
                onCheckedChange = actions.onShowNextStepChange,
                onSheet = true,
            )
        }
    }
}

/**
 * A setting with a few values on a sheet: its title and [hint] on the margin, then the values as a
 * row of words, the chosen one on its pill.
 */
@Composable
private fun SheetChoice(
    title: String,
    hint: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(top = Space.s12)) {
        Column(
            Modifier
                .padding(horizontal = PageMargin)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(title, style = AromaTheme.type.body)
            Text(hint, style = AromaTheme.type.bodySmall)
        }
        SelectionRow(options = options, selectedIndex = selectedIndex, onSelect = onSelect, modifier = Modifier.padding(top = Space.s4))
    }
}

/** The measures as a row of words: "Wie im Rezept", "Metrisch", "US". */
@StringRes
private fun MeasureSystem.choiceLabelRes(): Int =
    when (this) {
        MeasureSystem.AsWritten -> R.string.measures_as_written
        MeasureSystem.Metric -> R.string.settings_measures_metric
        MeasureSystem.US -> R.string.measures_short_us
    }
