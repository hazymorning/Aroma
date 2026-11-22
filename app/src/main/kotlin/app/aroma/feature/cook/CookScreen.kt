package app.aroma.feature.cook

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aroma.R
import app.aroma.core.format.DurationDetector
import app.aroma.core.format.IngredientFormatter
import app.aroma.core.model.Recipe
import app.aroma.core.model.Step
import app.aroma.data.AromaError
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.IngredientList
import app.aroma.ui.components.IngredientRow
import app.aroma.ui.components.RatingStars
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.formatClock
import app.aroma.ui.components.formatDuration
import app.aroma.ui.components.rememberRichText
import app.aroma.ui.theme.SerifText
import kotlinx.coroutines.launch

data class CookTimerUi(
    val id: Long,
    val label: String,
    val remainingSeconds: Long,
    val isRunning: Boolean,
) {
    val isFinished: Boolean get() = remainingSeconds <= 0
}

data class CookUiState(
    val recipe: Recipe? = null,
    val scale: Double = 1.0,
    val timers: List<CookTimerUi> = emptyList(),
    val rating: Double? = null,
    val ratedHere: Boolean = false,
    val markedCooked: Boolean = false,
    val markFailed: Boolean = false,
    val exactAlarmHint: Boolean = false,
    val isLoading: Boolean = true,
    val error: AromaError? = null,
)

data class CookActions(
    val onClose: () -> Unit = {},
    val onStartTimer: (seconds: Int, label: String) -> Unit = { _, _ -> },
    val onToggleTimer: (Long) -> Unit = {},
    val onDismissTimer: (Long) -> Unit = {},
    val onRate: (Int) -> Unit = {},
    val onMarkCooked: () -> Unit = {},
    val onFinished: () -> Unit = {},
    val onAllowExactAlarms: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

@Composable
fun CookScreen(state: CookUiState, actions: CookActions, modifier: Modifier = Modifier, initialPage: Int = 0) {
    val steps = state.recipe?.steps.orEmpty()
    val pager = rememberPagerState(initialPage = initialPage) { steps.size + 1 }
    LaunchedEffect(pager.settledPage, steps.size) {
        if (steps.isNotEmpty() && pager.settledPage == steps.size) actions.onFinished()
    }
    var showIngredients by rememberSaveable { mutableStateOf(false) }
    val locale = currentLocale()
    val rows =
        remember(state.recipe, state.scale, locale) {
            state.recipe
                ?.ingredients
                .orEmpty()
                .map { IngredientRow(it.section, IngredientFormatter.format(it, state.scale, locale)) }
        }
    val byReference =
        remember(state.recipe, rows) {
            state.recipe
                ?.ingredients
                .orEmpty()
                .mapIndexedNotNull { index, ingredient ->
                    ingredient.referenceId?.let { it to rows[index].copy(section = null) }
                }.toMap()
        }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(state.recipe?.name.orEmpty(), hasIngredients = rows.isNotEmpty(), onClose = actions.onClose, onIngredients = {
            showIngredients = true
        })
        when {
            state.error != null -> {
                ErrorState(state.error, onRetry = actions.onRetry, modifier = Modifier.weight(1f).fillMaxWidth())
            }

            state.isLoading -> {
                Spacer(Modifier.weight(1f))
            }

            else -> {
                Progress(pager, steps.size)
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                    if (page < steps.size) {
                        val step = steps[page]
                        StepPage(
                            index = page,
                            count = steps.size,
                            step = step,
                            ingredients = step.ingredientReferences.mapNotNull { byReference[it] },
                            onStartTimer = actions.onStartTimer,
                        )
                    } else {
                        FinishPage(state, actions)
                    }
                }
                Timers(state.timers, actions)
                if (state.exactAlarmHint) ExactAlarmHint(actions.onAllowExactAlarms)
                BottomButtons(pager, steps.size, actions.onClose)
            }
        }
    }
    if (showIngredients) {
        IngredientsSheet(rows) { showIngredients = false }
    }
}

@Composable
private fun TopBar(title: String, hasIngredients: Boolean, onClose: () -> Unit, onIngredients: () -> Unit) {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cook_close))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (hasIngredients) {
            TextButton(onClick = onIngredients) { Text(stringResource(R.string.recipe_ingredients)) }
        }
    }
}

@Composable
private fun Progress(pager: PagerState, steps: Int) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(steps) { index ->
            val color by animateColorAsState(
                if (index <= pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "progress",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepPage(
    index: Int,
    count: Int,
    step: Step,
    ingredients: List<IngredientRow>,
    onStartTimer: (Int, String) -> Unit,
) {
    val resources = LocalResources.current
    val stepLabel = stringResource(R.string.cook_step_of, index + 1, count)
    val durations =
        DurationDetector.detect(step.text).map { it.seconds } +
            listOfNotNull(step.durationSeconds?.takeIf { it > 0 })
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(stepLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (!step.title.isNullOrBlank()) {
            Text(step.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = rememberRichText(step.text, onDuration = { onStartTimer(it.seconds, stepLabel) }),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SerifText, fontSize = 26.sp, lineHeight = 38.sp),
        )
        if (durations.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                durations.distinct().forEach { seconds ->
                    FilledTonalButton(onClick = { onStartTimer(seconds, stepLabel) }) {
                        Icon(painterResource(R.drawable.ic_timer), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.cook_start_timer, resources.formatDuration(seconds)))
                    }
                }
            }
        }
        if (ingredients.isNotEmpty()) {
            Text(
                text = stringResource(R.string.cook_step_ingredients),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )
            IngredientList(ingredients, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun FinishPage(state: CookUiState, actions: CookActions) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(88.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_restaurant),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.cook_finished_title), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.cook_rate_question),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        RatingStars(state.rating, starSize = 30, onRate = actions.onRate)
        Spacer(Modifier.height(28.dp))
        if (state.markedCooked) {
            Text(
                text = stringResource(R.string.cook_marked),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            OutlinedButton(onClick = actions.onMarkCooked) { Text(stringResource(R.string.cook_mark_cooked)) }
            if (state.markFailed) {
                Text(
                    text = stringResource(R.string.cook_mark_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .padding(top = 8.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun Timers(timers: List<CookTimerUi>, actions: CookActions) {
    if (timers.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        timers.forEach { timer ->
            val finished = timer.isFinished
            val clock = formatClock(timer.remainingSeconds)
            Surface(
                shape = CircleShape,
                color = if (finished) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_timer), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(timer.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(
                        text = if (finished) stringResource(R.string.cook_timer_done) else clock,
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier.semantics { contentDescription = clock },
                    )
                    if (!finished) {
                        IconButton(onClick = { actions.onToggleTimer(timer.id) }) {
                            Icon(
                                painterResource(if (timer.isRunning) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                                contentDescription =
                                    stringResource(
                                        if (timer.isRunning) R.string.cook_timer_pause else R.string.cook_timer_resume,
                                    ),
                            )
                        }
                    }
                    IconButton(onClick = { actions.onDismissTimer(timer.id) }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cook_timer_stop))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExactAlarmHint(onAllow: () -> Unit) {
    Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.cook_exact_alarm_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAllow) { Text(stringResource(R.string.cook_exact_alarm_allow)) }
    }
}

@Composable
private fun BottomButtons(pager: PagerState, steps: Int, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
            enabled = page > 0,
            modifier =
                Modifier
                    .weight(1f)
                    .height(56.dp),
        ) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cook_previous))
        }
        Button(
            onClick = {
                if (page >= steps) onClose() else scope.launch { pager.animateScrollToPage(page + 1) }
            },
            modifier =
                Modifier
                    .weight(1f)
                    .height(56.dp),
        ) {
            Text(
                stringResource(
                    when {
                        page >= steps -> R.string.action_close
                        page == steps - 1 -> R.string.action_done
                        else -> R.string.cook_next
                    },
                ),
            )
            if (page < steps - 1) {
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientsSheet(rows: List<IngredientRow>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.recipe_ingredients), style = MaterialTheme.typography.headlineSmall)
            IngredientList(rows, Modifier.padding(top = 8.dp, bottom = 24.dp))
        }
    }
}
