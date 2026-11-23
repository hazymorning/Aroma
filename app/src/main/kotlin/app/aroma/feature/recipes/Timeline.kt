package app.aroma.feature.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.R
import app.aroma.core.model.ImageSize
import app.aroma.core.model.RecipeSummary
import app.aroma.core.model.TimelineEvent
import app.aroma.data.AromaError
import app.aroma.data.repository.TimelineRepository
import app.aroma.ui.components.CardPart
import app.aroma.ui.components.DayHeader
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.InlineProgress
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.MessageState
import app.aroma.ui.components.PlaceholderLines
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.ScrollToTopOnReselect
import app.aroma.ui.components.SoftCard
import app.aroma.ui.components.StatusBarScrim
import app.aroma.ui.components.currentLocale
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.PageEndSpace
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.ThumbnailSize
import app.aroma.ui.theme.tabular
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class TimelineUiState(
    val events: List<TimelineEvent> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: AromaError? = null,
)

class TimelineViewModel(private val timeline: TimelineRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(TimelineUiState())
    val state: StateFlow<TimelineUiState> = mutableState.asStateFlow()

    private var nextPage = 1
    private var loadJob: Job? = null

    init {
        reload()
        viewModelScope.launch { timeline.changes.collect { reload() } }
    }

    fun reload() {
        loadJob?.cancel()
        nextPage = 1
        mutableState.update { it.copy(isLoading = it.events.isEmpty(), error = null) }
        loadJob = viewModelScope.launch { loadPage(replace = true) }
    }

    fun loadMore() {
        val current = state.value
        if (current.isLoadingMore || !current.canLoadMore || loadJob?.isActive == true) return
        mutableState.update { it.copy(isLoadingMore = true) }
        loadJob = viewModelScope.launch { loadPage(replace = false) }
    }

    private suspend fun loadPage(replace: Boolean) {
        try {
            val page = timeline.page(nextPage)
            nextPage++
            mutableState.update {
                it.copy(
                    events = if (replace) page.events else (it.events + page.events).distinctBy { event -> event.id },
                    canLoadMore = page.hasMore,
                    isLoading = false,
                    isLoadingMore = false,
                    error = null,
                )
            }
        } catch (error: AromaError) {
            mutableState.update { it.copy(isLoading = false, isLoadingMore = false, canLoadMore = false, error = error) }
        }
    }
}

/** What the household cooked, added and noted, newest first and grouped by day, as Mealie's timeline keeps it. */
@Composable
fun Timeline(
    state: TimelineUiState,
    header: @Composable () -> Unit,
    onOpenRecipe: (RecipeSummary) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    ScrollToTopOnReselect(list)
    val nearEnd by remember(list) {
        derivedStateOf {
            (
                list.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            ) >= list.layoutInfo.totalItemsCount - 6
        }
    }
    val loadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(nearEnd, state.canLoadMore, state.events.size) {
        if (nearEnd && state.canLoadMore && !state.isLoadingMore) loadMore()
    }
    val days = remember(state.events) { state.events.groupBy { it.time.atZone(ZoneId.systemDefault()).toLocalDate() } }
    val today = LocalDate.now()
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + PageEndSpace),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { header() }
            when {
                state.isLoading -> {
                    item(
                        key = "loading",
                    ) { PlaceholderLines(6, Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xl)) }
                }

                state.error != null && state.events.isEmpty() -> {
                    item(key = "error") { ErrorState(state.error, onRetry = onRetry) }
                }

                state.events.isEmpty() -> {
                    item(key = "empty") {
                        MessageState(
                            icon = R.drawable.ic_recipes,
                            title = stringResource(R.string.timeline_empty_title),
                            body = stringResource(R.string.timeline_empty_message),
                        )
                    }
                }
            }
            // A day is one card, headed as a day of the plan is.
            days.entries.forEachIndexed { position, (day, events) ->
                // The first day stands as far under the tabs as the first cookbook does.
                val top = if (position == 0) Space.l else Space.s
                item(key = "day-$day") {
                    SoftCard(part = CardPart.of(0, events.size + 1), modifier = Modifier.padding(top = top)) { DayHeader(day, today) }
                }
                itemsIndexed(events, key = { _, event -> event.id }) { index, event ->
                    SoftCard(part = CardPart.of(index + 1, events.size + 1)) {
                        if (index > 0) Hairline(Modifier.padding(horizontal = CardPadding))
                        TimelineRow(event, onClick = { onOpenRecipe(event.recipe) })
                    }
                }
            }
            if (state.isLoadingMore) {
                item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(Space.l), contentAlignment = Alignment.Center) { InlineProgress() }
                }
            }
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
    }
}

/** The recipe's photo and name, what happened and when, and the note written with it. */
@Composable
private fun TimelineRow(event: TimelineEvent, onClick: () -> Unit) {
    val locale = currentLocale()
    val time =
        remember(event.time, locale) {
            DateTimeFormatter
                .ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault())
                .format(event.time)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = CardPadding, vertical = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        RecipeImage(
            image = event.recipe.image,
            size = ImageSize.Thumbnail,
            shape = MaterialTheme.shapes.small,
            fallbackIconSize = Glyph.m,
            modifier = Modifier.size(ThumbnailSize),
        )
        Column(Modifier.weight(1f)) {
            Text(event.recipe.name, style = AromaType.Name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = "${event.subject} · $time",
                style = AromaType.Small.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.xxs),
            )
            event.message?.let {
                Text(it, style = AromaType.Body, modifier = Modifier.padding(top = Space.xs))
            }
        }
    }
}
