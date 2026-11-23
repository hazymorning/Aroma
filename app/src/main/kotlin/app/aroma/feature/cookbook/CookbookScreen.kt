package app.aroma.feature.cookbook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.RecipeSummary
import app.aroma.feature.recipes.RecipeCount
import app.aroma.feature.recipes.RecipesUiState
import app.aroma.feature.recipes.pageItem
import app.aroma.ui.components.DetailTopBar
import app.aroma.ui.components.DetailTopBarHeight
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.MessageState
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.RecipeGrid
import app.aroma.ui.components.recipeGridPlaceholders
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Standfirst

/** The height of [DetailTopBar] below the status bar; the grid starts under it and scrolls beneath. */

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
    val grid = rememberLazyGridState()
    var titleEnd by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val collapsed by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > titleEnd } }
    Box(modifier.fillMaxSize()) {
        RecipeGrid(
            recipes = state.recipes,
            onOpen = onOpenRecipe,
            state = grid,
            isLoadingMore = state.isLoadingMore,
            canLoadMore = state.canLoadMore,
            onLoadMore = onLoadMore,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = ScreenMargin,
                    end = ScreenMargin,
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + DetailTopBarHeight,
                    bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            header = {
                pageItem("header") { Header(name, description, state, onTitleLaidOut = { titleEnd = it }) }
                when {
                    state.isLoading -> {
                        recipeGridPlaceholders()
                    }

                    state.error != null && state.recipes.isEmpty() -> {
                        pageItem("error") { ErrorState(state.error, onRetry = onRetry) }
                    }

                    state.recipes.isEmpty() -> {
                        pageItem("empty") {
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
        DetailTopBar(onBack = onBack, title = name, collapsed = collapsed)
    }
}

/** The cookbook's name as the large title, its description as a serif lead, then the count. */
@Composable
private fun Header(name: String, description: String, state: RecipesUiState, onTitleLaidOut: (Int) -> Unit) {
    Column {
        Text(
            text = name,
            style = MaterialTheme.typography.displaySmall,
            modifier =
                Modifier
                    .onSizeChanged { onTitleLaidOut(it.height) }
                    .padding(start = ScreenMargin, end = ScreenMargin, top = 8.dp)
                    .semantics { heading() },
        )
        if (description.isNotBlank()) {
            Text(
                text = description,
                style = Standfirst,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = 8.dp),
            )
        }
        if (state.isOffline) OfflineNotice(Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = 16.dp))
        RecipeCount(state)
    }
}
