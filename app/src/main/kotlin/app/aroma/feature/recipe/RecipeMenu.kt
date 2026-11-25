package app.aroma.feature.recipe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aroma.R
import app.aroma.core.model.RecipeSummary
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.RecipeMetaLine
import app.aroma.ui.components.SheetAction
import app.aroma.ui.components.hideThen
import app.aroma.ui.components.rememberFullSheetState
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Space

/** Which sheet a long press on a recipe card has open. */
enum class RecipeMenuSheet { Actions, Plan, Shop, Cookbooks }

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
    onCookbook: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    val choose = { action: () -> Unit ->
        scope.hideThen(sheetState) {
            onDismiss()
            action()
        }
    }
    AromaBottomSheet(
        onDismissRequest = onDismiss,
        title = recipe.name,
        titleStyle = AromaTheme.type.cardTitle,
        modifier = modifier,
        sheetState = sheetState,
        meta = { RecipeMetaLine(recipe) },
    ) {
        Column(Modifier.padding(bottom = Space.s16)) {
            SheetAction(R.drawable.ic_cooking, stringResource(R.string.recipe_start_cooking), onClick = { choose(onCook) })
            SheetAction(R.drawable.ic_calendar_add, stringResource(R.string.recipe_add_to_plan), onClick = { choose(onPlan) })
            SheetAction(R.drawable.ic_basket_add, stringResource(R.string.recipe_add_to_list), onClick = { choose(onShop) })
            SheetAction(R.drawable.ic_book_add, stringResource(R.string.recipe_add_to_cookbook), onClick = { choose(onCookbook) })
            SheetAction(
                icon = if (isFavorite) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
                label = stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite),
                onClick = { choose(onToggleFavorite) },
            )
            SheetAction(R.drawable.ic_share, stringResource(R.string.action_share), onClick = { choose(onShare) })
        }
    }
}
