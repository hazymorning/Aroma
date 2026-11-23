package app.aroma.feature.cook

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.format.DurationDetector
import app.aroma.core.format.IngredientFormatter
import app.aroma.core.model.ImageSize
import app.aroma.core.model.Recipe
import app.aroma.core.model.Step
import app.aroma.data.AromaError
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.HairlineIconButton
import app.aroma.ui.components.HeaderAction
import app.aroma.ui.components.IngredientList
import app.aroma.ui.components.IngredientRow
import app.aroma.ui.components.Kicker
import app.aroma.ui.components.LinkTone
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.QuietLink
import app.aroma.ui.components.RatingStars
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.SecondaryButton
import app.aroma.ui.components.SwitchRow
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.formatClock
import app.aroma.ui.components.formatDuration
import app.aroma.ui.components.fullBleed
import app.aroma.ui.components.ratingTouchInset
import app.aroma.ui.components.rememberRichText
import app.aroma.ui.theme.CookStep
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Motion.Settle
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TimerDigits
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
    val ingredientsInSteps: Boolean = false,
    val checkedIngredients: Set<Int> = emptySet(),
    val isLoading: Boolean = true,
    val error: AromaError? = null,
)

data class CookActions(
    val onClose: () -> Unit = {},
    val onStartTimer: (seconds: Int, label: String) -> Unit = { _, _ -> },
    val onToggleIngredient: (Int) -> Unit = {},
    val onIngredientsInSteps: (Boolean) -> Unit = {},
    val onToggleTimer: (Long) -> Unit = {},
    val onDismissTimer: (Long) -> Unit = {},
    val onRate: (Int) -> Unit = {},
    val onMarkCooked: () -> Unit = {},
    val onFinished: () -> Unit = {},
    val onAllowExactAlarms: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** A 40 dp hairline circle sits this far inside its 48 dp touch target. */
private val CircleInset = 4.dp

private const val DONE_STAR_SIZE = 32

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
    // Ingredients are ticked off by their place in the recipe, wherever they show.
    val byReference =
        remember(state.recipe) {
            state.recipe
                ?.ingredients
                .orEmpty()
                .mapIndexedNotNull { index, ingredient -> ingredient.referenceId?.let { it to index } }
                .toMap()
        }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .wrapContentWidth()
            .widthIn(max = 600.dp),
    ) {
        TopBar(
            title = state.recipe?.name.orEmpty(),
            // The finished page has nothing left to look up.
            hasIngredients = rows.isNotEmpty() && pager.currentPage < steps.size,
            onClose = actions.onClose,
            onIngredients = { showIngredients = true },
        )
        when {
            state.error != null -> {
                ErrorState(state.error, onRetry = actions.onRetry, modifier = Modifier.weight(1f).fillMaxWidth())
            }

            state.isLoading -> {
                Spacer(Modifier.weight(1f))
            }

            else -> {
                StepProgress(pager.currentPage, steps.size)
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                    if (page < steps.size) {
                        val step = steps[page]
                        StepPage(
                            index = page,
                            count = steps.size,
                            step = step,
                            ingredients =
                                remember(rows, step, byReference, state.ingredientsInSteps) {
                                    stepIngredients(rows, step, byReference, all = state.ingredientsInSteps)
                                },
                            checked = state.checkedIngredients,
                            onToggle = actions.onToggleIngredient,
                            onStartTimer = actions.onStartTimer,
                        )
                    } else {
                        DonePage(state, actions)
                    }
                }
                Timers(state.timers, actions)
                if (state.exactAlarmHint) ExactAlarmHint(actions.onAllowExactAlarms)
                BottomActions(pager, steps.size, actions.onClose)
            }
        }
    }
    if (showIngredients) {
        IngredientsSheet(
            rows = rows,
            checked = state.checkedIngredients,
            onToggle = actions.onToggleIngredient,
            inSteps = state.ingredientsInSteps,
            onInSteps = actions.onIngredientsInSteps,
            onDismiss = { showIngredients = false },
        )
    }
}

/** The ingredients a step shows, with their places in the recipe, by which they are ticked off. */
private class StepIngredients(val rows: List<IngredientRow>, val indexes: List<Int>, val all: Boolean)

/** All ingredients with their sections when the cook wants them under every step, else those Mealie links to it. */
private fun stepIngredients(rows: List<IngredientRow>, step: Step, byReference: Map<String, Int>, all: Boolean): StepIngredients {
    if (all) return StepIngredients(rows, rows.indices.toList(), all = true)
    val linked = step.ingredientReferences.mapNotNull { byReference[it] }
    return StepIngredients(linked.map { rows[it].copy(section = null) }, linked, all = false)
}

@Composable
private fun TopBar(title: String, hasIngredients: Boolean, onClose: () -> Unit, onIngredients: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 8.dp, end = ScreenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderAction(R.drawable.ic_close, stringResource(R.string.cook_close), onClose)
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 16.dp),
        )
        if (hasIngredients) {
            QuietLink(stringResource(R.string.recipe_ingredients), onIngredients, tone = LinkTone.Ink)
        }
    }
}

/** One fine segment per step; the current one fills from the left as the step comes up. */
@Composable
private fun StepProgress(current: Int, steps: Int) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = ScreenMargin, end = ScreenMargin, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(steps) { index ->
            val fill by animateFloatAsState(if (index <= current) 1f else 0f, tween(Motion.EXPAND, easing = Settle), label = "stepFill")
            val done = MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .weight(1f)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .drawBehind { drawRect(done, size = size.copy(width = size.width * fill)) },
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
    ingredients: StepIngredients,
    checked: Set<Int>,
    onToggle: (Int) -> Unit,
    onStartTimer: (Int, String) -> Unit,
) {
    val resources = LocalResources.current
    val stepLabel = stringResource(R.string.cook_step_of, index + 1, count)
    val durations =
        remember(step) {
            (DurationDetector.detect(step.text).map { it.seconds } + listOfNotNull(step.durationSeconds?.takeIf { it > 0 })).distinct()
        }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = ScreenMargin, end = ScreenMargin, top = 32.dp, bottom = 32.dp),
    ) {
        Kicker(stepLabel)
        Spacer(Modifier.height(16.dp))
        if (!step.title.isNullOrBlank()) {
            Text(step.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
        }
        StepText(rememberRichText(step.text, onDuration = { onStartTimer(it.seconds, stepLabel) }))
        if (durations.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                durations.forEach { seconds ->
                    SecondaryButton(
                        text = stringResource(R.string.cook_start_timer, resources.formatDuration(seconds)),
                        onClick = { onStartTimer(seconds, stepLabel) },
                        icon = R.drawable.ic_timer,
                        compact = true,
                    )
                }
            }
        }
        if (ingredients.rows.isNotEmpty()) {
            val used = ingredients.indexes.count { it in checked }
            Kicker(
                text =
                    if (ingredients.all) {
                        pluralStringResource(R.plurals.cook_ingredients_used, used, used, ingredients.rows.size)
                    } else {
                        stringResource(R.string.cook_step_ingredients)
                    },
                modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
            )
            IngredientList(
                rows = ingredients.rows,
                checked = ingredients.indexes.indices.filterTo(mutableSetOf()) { ingredients.indexes[it] in checked },
                onToggle = { onToggle(ingredients.indexes[it]) },
                onCards = true,
                modifier = Modifier.fullBleed(ScreenMargin),
            )
        }
    }
}

/**
 * The step in large serif type. Durations stay sage links in the step's own weight; their
 * underline is drawn by hand, because a text decoration would be as heavy as the type itself.
 */
@Composable
private fun StepText(text: AnnotatedString) {
    val linkColor = MaterialTheme.colorScheme.primary
    val styled =
        remember(text, linkColor) {
            val quiet = TextLinkStyles(SpanStyle(color = linkColor))
            text.mapAnnotations { range ->
                when (val link = range.item) {
                    is LinkAnnotation.Clickable -> AnnotatedString.Range(link.copy(styles = quiet), range.start, range.end)
                    is LinkAnnotation.Url -> AnnotatedString.Range(link.copy(styles = quiet), range.start, range.end)
                    else -> range
                }
            }
        }
    val links = remember(styled) { styled.getLinkAnnotations(0, styled.length) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = styled,
        style = CookStep,
        onTextLayout = { layout = it },
        modifier = Modifier.drawBehind { layout?.let { drawUnderlines(it, links, linkColor.copy(alpha = 0.5f)) } },
    )
}

private fun DrawScope.drawUnderlines(layout: TextLayoutResult, links: List<AnnotatedString.Range<LinkAnnotation>>, color: Color) {
    val below = 4.dp.toPx()
    val stroke = 1.dp.toPx()
    links.forEach { link ->
        for (line in layout.getLineForOffset(link.start)..layout.getLineForOffset(link.end - 1)) {
            val start = maxOf(link.start, layout.getLineStart(line))
            val end = minOf(link.end, layout.getLineEnd(line, visibleEnd = true))
            if (end <= start) continue
            val y = layout.getLineBaseline(line) + below
            drawLine(color, Offset(layout.getBoundingBox(start).left, y), Offset(layout.getBoundingBox(end - 1).right, y), stroke)
        }
    }
}

/** The one centred layout of the app, set a little above the middle, where the eye rests. */
@Composable
private fun DonePage(state: CookUiState, actions: CookActions) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenMargin, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        RecipeImage(
            image = state.recipe?.image,
            size = ImageSize.Medium,
            shape = MaterialTheme.shapes.large,
            fallbackIconSize = 32.dp,
            modifier = Modifier.size(112.dp),
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.cook_finished_title),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.cook_rate_question),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // The stars' touch targets reach beyond the glyphs; the gaps are measured to the glyphs.
        Spacer(Modifier.height(16.dp - ratingTouchInset(DONE_STAR_SIZE)))
        RatingStars(state.rating, starSize = DONE_STAR_SIZE, onRate = actions.onRate)
        Spacer(Modifier.height(32.dp - ratingTouchInset(DONE_STAR_SIZE)))
        MarkCooked(state.markedCooked, state.markFailed, actions.onMarkCooked)
        Spacer(Modifier.weight(2f))
    }
}

@Composable
private fun MarkCooked(marked: Boolean, failed: Boolean, onMark: () -> Unit) {
    AnimatedContent(
        targetState = marked,
        transitionSpec = { fadeIn(tween(Motion.STATE)) togetherWith fadeOut(tween(Motion.STATE)) },
        contentAlignment = Alignment.Center,
        label = "markCooked",
    ) { done ->
        if (done) {
            Row(
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cook_marked), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            SecondaryButton(stringResource(R.string.cook_mark_cooked), onMark, icon = R.drawable.ic_check)
        }
    }
    if (failed && !marked) {
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

@Composable
private fun Timers(timers: List<CookTimerUi>, actions: CookActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(tween(Motion.EXPAND, easing = Settle)),
    ) {
        if (timers.isNotEmpty()) Hairline(Modifier.padding(horizontal = ScreenMargin))
        timers.forEach { timer ->
            TimerRow(timer, onToggle = { actions.onToggleTimer(timer.id) }, onDismiss = { actions.onDismissTimer(timer.id) })
        }
    }
}

@Composable
private fun TimerRow(timer: CookTimerUi, onToggle: () -> Unit, onDismiss: () -> Unit) {
    val finished = timer.isFinished
    val pulse = if (finished) finishedPulse() else null
    val timeColor by animateColorAsState(
        when {
            finished -> MaterialTheme.colorScheme.primary
            timer.isRunning -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        tween(Motion.STATE),
        label = "timerTime",
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = ScreenMargin, end = ScreenMargin - CircleInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_timer),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = pulse?.value ?: 1f },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = timer.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (finished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (finished) stringResource(R.string.cook_timer_done) else formatClock(timer.remainingSeconds),
                style = TimerDigits,
                color = timeColor,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(16.dp - CircleInset))
        if (!finished) {
            HairlineIconButton(
                icon = if (timer.isRunning) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                contentDescription = stringResource(if (timer.isRunning) R.string.cook_timer_pause else R.string.cook_timer_resume),
                onClick = onToggle,
            )
        }
        HairlineIconButton(R.drawable.ic_close, stringResource(R.string.cook_timer_stop), onDismiss)
    }
}

/** A finished timer breathes slowly, so it catches the eye from across the kitchen. */
@Composable
private fun finishedPulse(): State<Float> =
    rememberInfiniteTransition(label = "timerDone").animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(Motion.PULSE), RepeatMode.Reverse),
        label = "timerPulse",
    )

@Composable
private fun ExactAlarmHint(onAllow: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.cook_exact_alarm_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
        )
        QuietLink(stringResource(R.string.cook_exact_alarm_allow), onAllow, tone = LinkTone.Ink)
    }
}

@Composable
private fun BottomActions(pager: PagerState, steps: Int, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenMargin, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HairlineIconButton(
            icon = R.drawable.ic_arrow_back,
            contentDescription = stringResource(R.string.cook_previous),
            onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
            size = 52.dp,
            enabled = page > 0,
        )
        Spacer(Modifier.width(12.dp))
        PrimaryButton(
            text =
                stringResource(
                    when {
                        page >= steps -> R.string.action_close
                        page == steps - 1 -> R.string.action_done
                        else -> R.string.cook_next
                    },
                ),
            onClick = { if (page >= steps) onClose() else scope.launch { pager.animateScrollToPage(page + 1) } },
            trailingIcon = R.drawable.ic_arrow_forward.takeIf { page < steps - 1 },
            modifier = Modifier.weight(1f),
        )
    }
}

/** All ingredients, ticked off as in the steps, and the choice to show them under every step. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientsSheet(
    rows: List<IngredientRow>,
    checked: Set<Int>,
    onToggle: (Int) -> Unit,
    inSteps: Boolean,
    onInSteps: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AromaBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.recipe_ingredients)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.l),
        ) {
            IngredientList(rows, Modifier.padding(horizontal = ScreenMargin), checked, onToggle)
            Hairline(Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = 16.dp))
            SwitchRow(
                title = stringResource(R.string.settings_ingredients_in_steps),
                subtitle = stringResource(R.string.settings_ingredients_in_steps_hint),
                checked = inSteps,
                onChange = onInSteps,
            )
        }
    }
}
