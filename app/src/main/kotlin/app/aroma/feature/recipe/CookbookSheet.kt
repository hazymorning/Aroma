package app.aroma.feature.recipe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.aroma.R
import app.aroma.core.model.Cookbook
import app.aroma.core.model.CookbookRule
import app.aroma.core.model.Filing
import app.aroma.core.model.Organizers
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.ChoiceRow
import app.aroma.ui.components.EmptyState
import app.aroma.ui.components.SkeletonLine
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.RowHeight
import app.aroma.ui.theme.Space

/**
 * A cookbook as the sheet that files a recipe into cookbooks offers it: whether it [held] the recipe,
 * where that is known, and the filing that takes the recipe in or out ([change]), where Aroma can make it.
 */
data class CookbookChoice(val cookbook: Cookbook, val held: Boolean?, val change: Filing?) {
    companion object {
        /**
         * [cookbook] for a recipe filed under [filing]. A cookbook of categories and tags takes the
         * recipe in by filing it under them, from the group's [organizers]; one with any other rule
         * only says whether it holds the recipe, as Mealie told ([heldByMealie]).
         */
        fun of(cookbook: Cookbook, filing: Filing, organizers: Organizers?, heldByMealie: Boolean?): CookbookChoice =
            when (val rule = cookbook.rule) {
                CookbookRule.Everything -> {
                    CookbookChoice(cookbook, held = true, change = null)
                }

                CookbookRule.Own -> {
                    CookbookChoice(cookbook, held = heldByMealie, change = null)
                }

                is CookbookRule.Filed -> {
                    val held = rule.holds(filing)
                    CookbookChoice(cookbook, held, if (held) rule.removing(filing) else organizers?.let { rule.adding(filing, it) })
                }
            }
    }
}

/**
 * The household's cookbooks, to put the recipe in or take it out of, as a list of ticks: a tap files
 * it at once, and the sheet stays for the next. A cookbook Mealie fills by a rule of its own, or that
 * holds every recipe, says so instead of taking a tap. [choices] are null while they load.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookbookSheet(
    choices: List<CookbookChoice>?,
    onToggle: (CookbookChoice) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    AromaBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.recipe_add_to_cookbook), modifier = modifier) {
        when {
            choices == null -> {
                CookbooksLoading()
            }

            choices.isEmpty() -> {
                EmptyState(text = stringResource(R.string.cookbooks_none), modifier = Modifier.padding(bottom = Space.s16))
            }

            else -> {
                LazyColumn(contentPadding = PaddingValues(bottom = Space.s16)) {
                    items(choices, key = { it.cookbook.id }) { choice ->
                        val held = choice.held == true
                        val change = choice.change
                        ChoiceRow(
                            label = choice.cookbook.name,
                            chosen = held,
                            description =
                                when (choice.cookbook.rule) {
                                    CookbookRule.Everything -> stringResource(R.string.cookbook_holds_all)
                                    CookbookRule.Own -> stringResource(R.string.cookbook_by_rule)
                                    is CookbookRule.Filed -> null
                                },
                            modifier =
                                if (change != null) {
                                    Modifier.toggleable(value = held, role = Role.Checkbox, onValueChange = {
                                        haptics.performHapticFeedback(
                                            if (held) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                                        )
                                        onToggle(choice)
                                    })
                                } else {
                                    Modifier
                                },
                        )
                    }
                }
            }
        }
    }
}

/** The outline of the cookbooks while they load: a line where each of the first names will stand. */
@Composable
private fun CookbooksLoading() {
    Column(Modifier.padding(start = PageMargin, end = PageMargin, bottom = Space.s16)) {
        LoadingWidths.forEach { width ->
            Box(Modifier.fillMaxWidth().height(RowHeight.oneLine), contentAlignment = Alignment.CenterStart) {
                SkeletonLine(Modifier.fillMaxWidth(width))
            }
        }
    }
}

/** How long the outline's lines are, as shares of the row. */
private val LoadingWidths = listOf(0.5f, 0.35f, 0.6f)
