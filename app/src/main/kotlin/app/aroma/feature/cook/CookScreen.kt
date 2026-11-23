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
import app.aroma.core.format.MeasureSystem
import app.aroma.core.format.StepMeasures
import app.aroma.core.format.ingredientsByStep
import app.aroma.core.model.ImageSize
import app.aroma.core.model.Recipe
import app.aroma.core.model.Step
import app.aroma.data.AromaError
import app.aroma.data.settings.IngredientsInSteps
import app.aroma.feature.settings.labelRes
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.CardActionPadding
import app.aroma.ui.components.DetailTopBarHeight
import app.aroma.ui.components.EdgeActionPadding
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.GroupLabel
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.HeaderAction
import app.aroma.ui.components.IconAction
import app.aroma.ui.components.IngredientList
import app.aroma.ui.components.IngredientRow
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.QuietLink
import app.aroma.ui.components.RatingStars
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.SecondaryButton
import app.aroma.ui.components.SegmentedControl
import app.aroma.ui.components.SoftCard
import app.aroma.ui.components.TonalIconButton
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.formatClock
import app.aroma.ui.components.formatDuration
import app.aroma.ui.components.ratingTouchInset
import app.aroma.ui.components.rememberRichText
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.ContentMaxWidth
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Lines
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Motion.Glide
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
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
    val ingredientsInSteps: IngredientsInSteps = IngredientsInSteps.Used,
    val measures: MeasureSystem = MeasureSystem.AsWritten,
    val checkedIngredients: Set<Int> = emptySet(),
    val isLoading: Boolean = true,
    val error: AromaError? = null,
)

data class CookActions(
    val onClose: () -> Unit = {},
    val onStartTimer: (seconds: Int, label: String) -> Unit = { _, _ -> },
    val onToggleIngredient: (Int) -> Unit = {},
    val onIngredientsInSteps: (IngredientsInSteps) -> Unit = {},
    val onToggleTimer: (Long) -> Unit = {},
    val onDismissTimer: (Long) -> Unit = {},
    val onRate: (Int) -> Unit = {},
    val onMarkCooked: () -> Unit = {},
    val onFinished: () -> Unit = {},
    val onAllowExactAlarms: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** The step bar's segments, and the line under a duration in a step. */
private val ProgressHeight = 3.dp
private val LinkUnderline = 1.dp

private const val DONE_STAR_SIZE = 32

/** The recipe's photo on the page that ends cooking. */
private val DonePhotoSize = 112.dp

/** A timer's row, with room for its digits and its buttons. */
private val TimerRowHeight = 64.dp

@Composable
fun CookScreen(state: CookUiState, actions: CookActions, modifier: Modifier = Modifier, initialPage: Int = 0) {
    val steps = state.recipe?.steps.orEmpty()
    val pager = rememberPagerState(initialPage = initialPage) { steps.size + 1 }
    LaunchedEffect(pager.settledPage, steps.size) {
        if (steps.isNotEmpty() && pager.settledPage == steps.size) actions.onFinished()
    }
    var showIngredients by rememberSaveable { mutableStateOf(false) }
    val panel = rememberPanelState()
    val locale = currentLocale()
    val rows =
        remember(state.recipe, state.scale, state.measures, locale) {
            state.recipe
                ?.ingredients
                .orEmpty()
                .map { IngredientRow(it.section, IngredientFormatter.format(it, state.scale, locale, state.measures)) }
        }
    // Ingredients are ticked off by their place in the recipe, wherever they show.
    val byStep = remember(state.recipe, locale) { state.recipe?.ingredientsByStep(locale).orEmpty() }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .wrapContentWidth()
            .widthIn(max = ContentMaxWidth),
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
                // Without a step that names an ingredient, the panel would only ever say there is none.
                val hasPanel =
                    rows.isNotEmpty() &&
                        when (state.ingredientsInSteps) {
                            IngredientsInSteps.None -> false
                            IngredientsInSteps.Used -> byStep.any { it.isNotEmpty() }
                            IngredientsInSteps.All -> true
                        }
                StepsWithPanel(
                    panel = {
                        if (hasPanel) {
                            val step = pager.currentPage.coerceAtMost(steps.lastIndex)
                            IngredientsPanel(
                                ingredients =
                                    remember(rows, byStep, step, state.ingredientsInSteps) {
                                        stepIngredients(rows, byStep.getOrNull(step).orEmpty(), state.ingredientsInSteps)
                                    },
                                checked = state.checkedIngredients,
                                onToggle = actions.onToggleIngredient,
                                state = panel,
                                visible = pager.currentPage < steps.size,
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        if (page < steps.size) {
                            StepPage(
                                index = page,
                                count = steps.size,
                                step = steps[page],
                                measures = state.measures,
                                onStartTimer = actions.onStartTimer,
                                modifier = if (hasPanel) Modifier.abovePanel(panel) else Modifier,
                            )
                        } else {
                            DonePage(state, actions)
                        }
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

/** Close on the margin and the recipe's name beside it, set as on the recipe page's bar, with all the room the button leaves. */
@Composable
private fun TopBar(title: String, hasIngredients: Boolean, onClose: () -> Unit, onIngredients: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(DetailTopBarHeight)
                .padding(start = EdgeActionPadding, end = ScreenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderAction(R.drawable.ic_close, stringResource(R.string.cook_close), onClose)
        Text(
            text = title,
            style = AromaType.Name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = Space.xs, end = Space.s),
        )
        if (hasIngredients) {
            SecondaryButton(stringResource(R.string.recipe_ingredients), onIngredients, compact = true)
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
                .padding(start = ScreenMargin, end = ScreenMargin, top = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        repeat(steps) { index ->
            val fill by animateFloatAsState(if (index <= current) 1f else 0f, tween(Motion.EXPAND, easing = Glide), label = "stepFill")
            val done = MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .weight(1f)
                    .height(ProgressHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
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
    measures: MeasureSystem,
    onStartTimer: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val stepLabel = stringResource(R.string.cook_step_of, index + 1, count)
    val locale = currentLocale()
    val text = remember(step.text, measures, locale) { StepMeasures.convert(step.text, measures, locale) }
    val durations =
        remember(step) {
            (DurationDetector.detect(step.text).map { it.seconds } + listOfNotNull(step.durationSeconds?.takeIf { it > 0 })).distinct()
        }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = ScreenMargin, end = ScreenMargin, top = Space.xl, bottom = Space.xl),
    ) {
        Text(stepLabel, style = AromaType.Label, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(Space.s))
        if (!step.title.isNullOrBlank()) {
            Text(step.title, style = AromaType.SectionTitle)
            Spacer(Modifier.height(Space.xs))
        }
        StepText(rememberRichText(text, onDuration = { onStartTimer(it.seconds, stepLabel) }))
        if (durations.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = Space.l),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
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
    }
}

/**
 * The step in large type. Durations stay sage links in the step's own weight; their underline is
 * drawn by hand, because a text decoration would be as heavy as the type itself.
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
        style = AromaType.Reading,
        onTextLayout = { layout = it },
        modifier = Modifier.drawBehind { layout?.let { drawUnderlines(it, links, linkColor.copy(alpha = 0.5f)) } },
    )
}

private fun DrawScope.drawUnderlines(layout: TextLayoutResult, links: List<AnnotatedString.Range<LinkAnnotation>>, color: Color) {
    val below = Space.xxs.toPx()
    val stroke = LinkUnderline.toPx()
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
            .padding(horizontal = ScreenMargin, vertical = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        RecipeImage(
            image = state.recipe?.image,
            size = ImageSize.Medium,
            shape = MaterialTheme.shapes.large,
            fallbackIconSize = Glyph.xl,
            modifier = Modifier.size(DonePhotoSize),
        )
        Spacer(Modifier.height(Space.xl))
        Text(
            text = stringResource(R.string.cook_finished_title),
            style = AromaType.PageTitle,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            text = stringResource(R.string.cook_rate_question),
            style = AromaType.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // The stars' touch targets reach beyond the glyphs; the gaps are measured to the glyphs.
        Spacer(Modifier.height(Space.m - ratingTouchInset(DONE_STAR_SIZE)))
        RatingStars(state.rating, starSize = DONE_STAR_SIZE, onRate = actions.onRate)
        Spacer(Modifier.height(Space.xl - ratingTouchInset(DONE_STAR_SIZE)))
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
                        .heightIn(min = TouchTarget)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Glyph.m),
                )
                Spacer(Modifier.width(Space.xs))
                Text(stringResource(R.string.cook_marked), style = AromaType.BodyStrong)
            }
        } else {
            SecondaryButton(stringResource(R.string.cook_mark_cooked), onMark, icon = R.drawable.ic_check)
        }
    }
    if (failed && !marked) {
        Text(
            text = stringResource(R.string.cook_mark_failed),
            style = AromaType.Small,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .padding(top = Space.xs)
                    .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Running timers on a soft card above the step buttons, one row each. */
@Composable
private fun Timers(timers: List<CookTimerUi>, actions: CookActions) {
    Box(
        Modifier
            .fillMaxWidth()
            .animateContentSize(tween(Motion.EXPAND, easing = Glide)),
    ) {
        if (timers.isNotEmpty()) {
            SoftCard(Modifier.padding(top = Space.s)) {
                timers.forEachIndexed { index, timer ->
                    if (index > 0) Hairline(Modifier.padding(horizontal = CardPadding))
                    TimerRow(timer, onToggle = { actions.onToggleTimer(timer.id) }, onDismiss = { actions.onDismissTimer(timer.id) })
                }
            }
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
                .height(TimerRowHeight)
                .padding(start = CardPadding, end = CardActionPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_timer),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Glyph.m),
        )
        Spacer(Modifier.width(Space.s))
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = pulse?.value ?: 1f },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = timer.label,
                style = AromaType.Small,
                color = if (finished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (finished) stringResource(R.string.cook_timer_done) else formatClock(timer.remainingSeconds),
                style = AromaType.Timer,
                color = timeColor,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(Space.xs))
        if (!finished) {
            IconAction(
                icon = if (timer.isRunning) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                contentDescription = stringResource(if (timer.isRunning) R.string.cook_timer_pause else R.string.cook_timer_resume),
                onClick = onToggle,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconAction(R.drawable.ic_close, stringResource(R.string.cook_timer_stop), onDismiss, tint = MaterialTheme.colorScheme.onSurface)
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
            style = AromaType.Small,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = Space.m),
        )
        QuietLink(stringResource(R.string.cook_exact_alarm_allow), onAllow)
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
                .padding(horizontal = ScreenMargin, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TonalIconButton(
            icon = R.drawable.ic_arrow_back,
            contentDescription = stringResource(R.string.cook_previous),
            onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
            enabled = page > 0,
        )
        Spacer(Modifier.width(Space.s))
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
    inSteps: IngredientsInSteps,
    onInSteps: (IngredientsInSteps) -> Unit,
    onDismiss: () -> Unit,
) {
    AromaBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.recipe_ingredients)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.l),
        ) {
            IngredientList(rows, Modifier.padding(horizontal = ScreenMargin), checked, onToggle)
            GroupLabel(stringResource(R.string.settings_ingredients_in_steps), Modifier.padding(top = Space.l, bottom = Space.xs))
            SegmentedControl(
                options = IngredientsInSteps.entries.map { stringResource(it.labelRes()) },
                selectedIndex = inSteps.ordinal,
                onSelect = { onInSteps(IngredientsInSteps.entries[it]) },
                modifier = Modifier.padding(horizontal = ScreenMargin),
            )
        }
    }
}
