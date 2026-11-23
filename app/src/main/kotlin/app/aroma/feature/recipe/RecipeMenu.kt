package app.aroma.feature.recipe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.aroma.R
import app.aroma.core.model.RecipeSummary
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.RecipeMeta
import app.aroma.ui.components.SheetAction
import app.aroma.ui.components.hideThen
import app.aroma.ui.theme.IconKeyline
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space

/** Which sheet a long press on a recipe card has open. */
enum class RecipeMenuSheet { Actions, Plan, Shop }

/**
 * The recipe a long press was made on, and the sheet it has open. The recipe stays after the
 * sheets close, so word of what was done still arrives.
 */
@Stable
class RecipeMenuState {
    var recipe by mutableStateOf<RecipeSummary?>(null)
        private set
    var sheet by mutableStateOf<RecipeMenuSheet?>(null)

    fun open(recipe: RecipeSummary) {
        this.recipe = recipe
        sheet = RecipeMenuSheet.Actions
    }
}

/** What a long press on a recipe offers, the most used first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeActionsSheet(
    recipe: RecipeSummary,
    isFavorite: Boolean,
    onCook: () -> Unit,
    onPlan: () -> Unit,
    onShop: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val choose = { action: () -> Unit ->
        scope.hideThen(sheetState) {
            onDismiss()
            action()
        }
    }
    AromaBottomSheet(onDismissRequest = onDismiss, modifier = modifier, sheetState = sheetState) {
        Column(Modifier.padding(bottom = Space.m)) {
            Column(Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xs, bottom = Space.m)) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                RecipeMeta(recipe, Modifier.padding(top = Space.xxs))
            }
            SheetAction(R.drawable.ic_cooking, stringResource(R.string.recipe_start_cooking), onClick = { choose(onCook) })
            ActionDivider()
            SheetAction(R.drawable.ic_calendar_add, stringResource(R.string.recipe_add_to_plan), onClick = { choose(onPlan) })
            ActionDivider()
            SheetAction(R.drawable.ic_basket_add, stringResource(R.string.recipe_add_to_list), onClick = { choose(onShop) })
            ActionDivider()
            SheetAction(
                icon = if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite,
                label = stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite),
                onClick = { choose(onToggleFavorite) },
            )
            ActionDivider()
            SheetAction(R.drawable.ic_share, stringResource(R.string.action_share), onClick = { choose(onShare) })
        }
    }
}

@Composable
private fun ActionDivider() {
    Hairline(Modifier.padding(start = IconKeyline, end = ScreenMargin))
}
