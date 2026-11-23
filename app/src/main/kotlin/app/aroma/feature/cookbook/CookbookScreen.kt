package app.aroma.feature.cookbook

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.RecipeSummary
import app.aroma.feature.recipes.RecipesUiState
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.MessageState
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.RecipeGrid
import app.aroma.ui.components.recipeGridPlaceholders

@Composable
fun CookbookScreen(
    name: String,
    description: String,
    state: RecipesUiState,
    onBack: () -> Unit,
    onOpenRecipe: (RecipeSummary) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RecipeGrid(
        recipes = state.recipes,
        onOpen = onOpenRecipe,
        isLoadingMore = state.isLoadingMore,
        canLoadMore = state.canLoadMore,
        onLoadMore = onLoadMore,
        modifier = modifier.fillMaxSize(),
        header = {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Column(Modifier.statusBarsPadding()) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                    Text(name, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 8.dp))
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (state.isOffline) OfflineNotice(Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(20.dp))
                }
            }
            when {
                state.isLoading -> {
                    recipeGridPlaceholders()
                }

                state.error != null && state.recipes.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
                        ErrorState(state.error, onRetry = onRetry)
                    }
                }

                state.recipes.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                        MessageState(
                            icon = R.drawable.ic_recipes,
                            title = stringResource(R.string.cookbook_empty_title),
                            body = stringResource(R.string.cookbook_empty_message),
                        )
                    }
                }
            }
        },
    )
}
