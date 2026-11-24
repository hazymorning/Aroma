package app.aroma.feature.recipes

import androidx.annotation.Keep
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.aroma.R
import app.aroma.core.model.RecipeSummary
import app.aroma.ui.components.AromaPullToRefreshBox
import app.aroma.ui.components.BackButton
import app.aroma.ui.components.EmptyState
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.HeaderState
import app.aroma.ui.components.LargeHeader
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.RecipeGrid
import app.aroma.ui.components.SEPARATOR
import app.aroma.ui.components.Subtitle
import app.aroma.ui.components.Text
import app.aroma.ui.components.TopBar
import app.aroma.ui.components.recipeGridSkeletons
import app.aroma.ui.components.rememberHeaderState
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.EndSpace
import app.aroma.ui.theme.HeaderGap
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space

/** What a collection page holds; [app.aroma.ui.navigation.CollectionRoute] names one of these. */
@Keep
enum class CollectionKind { Cookbook, Favorites, Category, Tag, Tool, Meal, Quick }

data class CollectionActions(
    val onBack: () -> Unit = {},
    val onOpenRecipe: (RecipeSummary) -> Unit = {},
    val onRecipeMenu: (RecipeSummary) -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * Recipes that belong together, on a page of their own: a cookbook, the saved ones, one meal, the
 * quick ones, or one category, tag or tool. The name is the large title; under it, what kind of
 * collection it is and how many recipes it holds, then its description.
 */
@Composable
fun CollectionScreen(
    kind: CollectionKind,
    name: String,
    description: String,
    state: RecipesUiState,
    actions: CollectionActions,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    val header = rememberHeaderState()
    Box(modifier.fillMaxSize()) {
        AromaPullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = Modifier.fillMaxSize()) {
            RecipeGrid(
                recipes = state.recipes,
                onOpen = actions.onOpenRecipe,
                onLongPress = actions.onRecipeMenu,
                state = list,
                isLoadingMore = state.isLoadingMore,
                canLoadMore = state.canLoadMore,
                onLoadMore = actions.onLoadMore,
                saved = state.saved,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = EndSpace + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                header = {
                    item(key = "header") { Header(kind, name, description, state, header, actions.onBack) }
                    when {
                        state.isLoading -> {
                            recipeGridSkeletons()
                        }

                        state.error != null && state.recipes.isEmpty() -> {
                            item(key = "error") { ErrorState(state.error, onRetry = actions.onRetry) }
                        }

                        state.recipes.isEmpty() -> {
                            item(key = "empty") { EmptyState(text = stringResource(kind.emptyRes())) }
                        }
                    }
                },
            )
        }
        TopBar(
            title = name,
            collapsed = header.collapsed,
            raised = list.canScrollBackward,
            navigation = { BackButton(actions.onBack) },
        )
    }
}

/** The back button, the name as the large title, what kind of collection it is with its count, and its description. */
@Composable
private fun Header(
    kind: CollectionKind,
    name: String,
    description: String,
    state: RecipesUiState,
    header: HeaderState,
    onBack: () -> Unit,
) {
    val count = state.total?.takeIf { state.recipes.isNotEmpty() }?.let { pluralStringResource(R.plurals.recipe_count, it, it) }
    val kindName = kind.labelRes()?.let { stringResource(it) }
    Column(Modifier.padding(bottom = HeaderGap)) {
        LargeHeader(
            title = name,
            state = header,
            navigation = { BackButton(onBack) },
            subtitle =
                listOfNotNull(kindName, count).takeIf { it.isNotEmpty() }?.let { parts ->
                    { Subtitle(parts.joinToString(SEPARATOR)) }
                },
        )
        if (description.isNotBlank()) {
            Text(
                text = description,
                style = AromaTheme.type.bodySmall,
                modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s12),
            )
        }
        if (state.isOffline) OfflineNotice(Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s12))
    }
}

private fun CollectionKind.labelRes(): Int? =
    when (this) {
        CollectionKind.Cookbook -> R.string.collection_cookbook
        CollectionKind.Favorites -> null
        CollectionKind.Category -> R.string.collection_category
        CollectionKind.Tag -> R.string.collection_tag
        CollectionKind.Tool -> R.string.collection_tool
        CollectionKind.Meal, CollectionKind.Quick -> null
    }

/** What an empty collection says, in one line. */
private fun CollectionKind.emptyRes(): Int =
    when (this) {
        CollectionKind.Cookbook -> R.string.cookbook_empty_title
        CollectionKind.Meal -> R.string.recipes_meal_empty_title
        else -> R.string.recipes_no_results_title
    }
