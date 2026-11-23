package app.aroma.feature.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.R
import app.aroma.core.model.Cookbook
import app.aroma.core.model.CookbookCover
import app.aroma.data.AromaError
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.toAromaError
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.GridColumnWidth
import app.aroma.ui.components.GridRowGap
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.MessageState
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PhotoCard
import app.aroma.ui.components.ScrollToTopOnReselect
import app.aroma.ui.components.StatusBarScrim
import app.aroma.ui.components.pageItem
import app.aroma.ui.components.recipeGridPlaceholders
import app.aroma.ui.theme.PageEndSpace
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.tabular
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CookbookShelfUiState(
    val cookbooks: List<CookbookCover> = emptyList(),
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val error: AromaError? = null,
)

class CookbookShelfViewModel(recipes: RecipeRepository) : ViewModel() {
    private val reloads = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CookbookShelfUiState> =
        reloads
            .flatMapLatest {
                recipes
                    .cookbooks()
                    .map { CookbookShelfUiState(it.data, isLoading = false, isOffline = it.isOffline) }
                    .onStart { emit(CookbookShelfUiState()) }
                    .catch { emit(CookbookShelfUiState(isLoading = false, error = it.toAromaError())) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CookbookShelfUiState())

    fun reload() = reloads.update { it + 1 }
}

/** The household's cookbooks as covers: the photo of each one's newest recipe, its name and size. */
@Composable
fun CookbookShelf(
    state: CookbookShelfUiState,
    header: @Composable () -> Unit,
    onOpen: (Cookbook) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = rememberLazyGridState()
    ScrollToTopOnReselect(grid)
    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = GridColumnWidth),
            state = grid,
            contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, bottom = LocalBottomBarInset.current + PageEndSpace),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
            modifier = Modifier.fillMaxSize(),
        ) {
            pageItem("header") {
                Column(Modifier.padding(bottom = Space.l)) {
                    header()
                    if (state.isOffline) OfflineNotice(Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m))
                }
            }
            when {
                state.isLoading && state.cookbooks.isEmpty() -> {
                    recipeGridPlaceholders(count = 4, aspectRatio = COVER_ASPECT_RATIO)
                }

                state.error != null && state.cookbooks.isEmpty() -> {
                    pageItem("error") { ErrorState(state.error, onRetry = onRetry) }
                }

                state.cookbooks.isEmpty() -> {
                    pageItem("empty") {
                        MessageState(
                            icon = R.drawable.ic_recipes,
                            title = stringResource(R.string.cookbooks_empty_title),
                            body = stringResource(R.string.cookbooks_empty_message),
                        )
                    }
                }
            }
            items(state.cookbooks, key = { it.cookbook.id }) { cover ->
                PhotoCard(
                    image = cover.photo,
                    title = cover.cookbook.name,
                    onClick = { onOpen(cover.cookbook) },
                    imageAspectRatio = COVER_ASPECT_RATIO,
                    fallbackIcon = R.drawable.ic_recipes,
                    modifier = Modifier.padding(bottom = GridRowGap),
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.recipe_count, cover.recipeCount, cover.recipeCount),
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
    }
}

/** Upright, like a book. */
private const val COVER_ASPECT_RATIO = 3f / 4f
