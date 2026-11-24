package app.aroma.feature.cook

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
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
import app.aroma.core.format.Markdown
import app.aroma.core.format.MeasureSystem
import app.aroma.core.format.StepAmount
import app.aroma.core.format.StepMeasures
import app.aroma.core.format.StepMention
import app.aroma.core.format.StyledText
import app.aroma.core.format.UnitSpacing
import app.aroma.core.format.ingredientsByStep
import app.aroma.core.format.stepAmounts
import app.aroma.core.format.stepMentions
import app.aroma.core.model.ImageSize
import app.aroma.core.model.Recipe
import app.aroma.core.model.Step
import app.aroma.data.AromaError
import app.aroma.data.settings.IngredientsInSteps
import app.aroma.feature.settings.labelRes
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.BottomActionBar
import app.aroma.ui.components.BoxHeader
import app.aroma.ui.components.EmptyState
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.GroupHead
import app.aroma.ui.components.INGREDIENT_TAG
import app.aroma.ui.components.IconButton
import app.aroma.ui.components.IconButtonPlace
import app.aroma.ui.components.IconCircle
import app.aroma.ui.components.IngredientList
import app.aroma.ui.components.IngredientRow
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.RatingStars
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.SecondaryButton
import app.aroma.ui.components.SelectionRow
import app.aroma.ui.components.Separator
import app.aroma.ui.components.Text
import app.aroma.ui.components.TextAction
import app.aroma.ui.components.TonalBox
import app.aroma.ui.components.TopBarHeight
import app.aroma.ui.components.breakable
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.formatClock
import app.aroma.ui.components.formatDuration
import app.aroma.ui.components.ratingTouchInset
import app.aroma.ui.components.rememberStepText
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.BoxPadding
import app.aroma.ui.theme.ContentMaxWidth
import app.aroma.ui.theme.EndSpace
import app.aroma.ui.theme.EyebrowGap
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import kotlinx.coroutines.flow.drop
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
    val amountsInSteps: Boolean = false,
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

/** The stars at the end of cooking, large enough to hit with a thumb. */
private val DoneStarSize = 32.dp

/** The recipe's photo on the page that ends cooking. */
private val DonePhotoSize = 112.dp

/** How many lines of the next step show under "Next". */
private const val NEXT_LINES = 2

/**
 * Cook mode: one step at a time in large type, read at arm's length. A fine bar of segments says
 * how far the cooking has come, the step's number stands large, its text under it, then what the
 * step uses, to tick off, and the start of the step that follows. Back and next sit in a bar at the
 * foot, as large as targets hit from across the counter; a swipe turns the step too, with a tick.
 */
@Composable
fun CookScreen(state: CookUiState, actions: CookActions, modifier: Modifier = Modifier, initialPage: Int = 0) {
    val steps = state.recipe?.steps.orEmpty()
    val pager = rememberPagerState(initialPage = initialPage) { steps.size + 1 }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pager.settledPage, steps.size) {
        if (steps.isNotEmpty() && pager.settledPage == steps.size) actions.onFinished()
    }
    // Every step reached gives a tick, swiped to or tapped to.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }
    }
    var showIngredients by rememberSaveable { mutableStateOf(false) }
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
    // The steps as they read: temperatures and pan sizes in the chosen measures, and the amounts written in where chosen.
    val texts =
        remember(state.recipe, state.measures, locale) {
            steps.map { Markdown.parse(UnitSpacing.apply(StepMeasures.convert(it.text, state.measures, locale))) }
        }
    val amounts =
        remember(state.recipe, texts, state.scale, state.measures, locale, state.amountsInSteps) {
            val recipe = state.recipe
            if (recipe == null ||
                !state.amountsInSteps
            ) {
                emptyList()
            } else {
                recipe.stepAmounts(texts.map { it.text }, state.scale, locale, state.measures)
            }
        }
    // The words in the steps that tick off an ingredient with a tap.
    val mentions = remember(state.recipe, texts, locale) { state.recipe?.stepMentions(texts.map { it.text }, locale).orEmpty() }
    Column(
        modifier
            .fillMaxSize()
            .background(AromaTheme.colors.background),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding()
                .wrapContentWidth()
                .widthIn(max = ContentMaxWidth),
        ) {
            TopBar(
                // The finished page has nothing left to look up.
                hasIngredients = rows.isNotEmpty() && pager.currentPage < steps.size,
                onClose = actions.onClose,
                onIngredients = { showIngredients = true },
            )
            when {
                state.error != null -> {
                    ErrorState(state.error, onRetry = actions.onRetry)
                }

                state.isLoading -> {
                    Spacer(Modifier.weight(1f))
                }

                else -> {
                    StepProgress(pager.currentPage, steps.size)
                    // Without a step that names an ingredient, every box would only ever say there is none.
                    val showsIngredients =
                        rows.isNotEmpty() &&
                            when (state.ingredientsInSteps) {
                                IngredientsInSteps.None -> false
                                IngredientsInSteps.Used -> byStep.any { it.isNotEmpty() }
                                IngredientsInSteps.All -> true
                            }
                    HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                        if (page < steps.size) {
                            StepPage(
                                index = page,
                                count = steps.size,
                                step = steps[page],
                                text = texts.getOrNull(page) ?: StyledText(steps[page].text, emptyList()),
                                amounts = amounts.getOrNull(page).orEmpty(),
                                mentions = mentions.getOrNull(page).orEmpty(),
                                next = steps.getOrNull(page + 1),
                                onStartTimer = actions.onStartTimer,
                                ingredients =
                                    if (showsIngredients) {
                                        remember(rows, byStep, page, state.ingredientsInSteps) {
                                            stepIngredients(rows, byStep.getOrNull(page).orEmpty(), state.ingredientsInSteps)
                                        }
                                    } else {
                                        null
                                    },
                                checked = state.checkedIngredients,
                                onToggle = actions.onToggleIngredient,
                            )
                        } else {
                            DonePage(state, actions)
                        }
                    }
                    Timers(state.timers, actions)
                    if (state.exactAlarmHint) ExactAlarmHint(actions.onAllowExactAlarms)
                }
            }
        }
        if (state.error == null && !state.isLoading) BottomActions(pager, steps.size, actions.onClose)
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

/** Close on the left and "Ingredients" on the right, nothing between: the step is what matters. */
@Composable
private fun TopBar(hasIngredients: Boolean, onClose: () -> Unit, onIngredients: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TopBarHeight)
                .padding(start = PageMargin - (TouchTarget - IconCircle) / 2, end = PageMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(R.drawable.ic_close, stringResource(R.string.cook_close), onClose)
        Spacer(Modifier.weight(1f))
        if (hasIngredients) TextAction(stringResource(R.string.recipe_ingredients), onIngredients)
    }
}

/** One segment per step, fully round: the steps done and the one on screen in sage, the rest open. */
@Composable
private fun StepProgress(current: Int, steps: Int) {
    val colors = AromaTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = PageMargin, end = PageMargin, top = Space.s8)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        repeat(steps) { index ->
            val fill by animateFloatAsState(if (index <= current) 1f else 0f, Motion.state(), label = "stepFill")
            Box(
                Modifier
                    .weight(1f)
                    .height(ProgressHeight)
                    .clip(CircleShape)
                    .background(colors.surfacePressed)
                    .drawBehind { drawRect(colors.accent, size = size.copy(width = size.width * fill)) },
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
    text: StyledText,
    amounts: List<StepAmount>,
    mentions: List<StepMention>,
    next: Step?,
    onStartTimer: (Int, String) -> Unit,
    ingredients: StepIngredients?,
    checked: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val stepLabel = stringResource(R.string.cook_step_of, index + 1, count)
    val durations =
        remember(step) {
            (DurationDetector.detect(step.text).map { it.seconds } + listOfNotNull(step.durationSeconds?.takeIf { it > 0 })).distinct()
        }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = PageMargin, end = PageMargin, top = Space.s24, bottom = Space.s24),
    ) {
        // "3 von 6": the number large, the count small, on one baseline.
        Row(Modifier.semantics(mergeDescendants = true) { contentDescription = stepLabel }) {
            Text((index + 1).toString(), style = AromaTheme.type.numeralLarge, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.width(Space.s8))
            Text(stringResource(R.string.cook_of_count, count), style = AromaTheme.type.meta, modifier = Modifier.alignByBaseline())
        }
        Spacer(Modifier.height(Space.s16))
        if (!step.title.isNullOrBlank()) {
            Text(step.title, style = AromaTheme.type.label, modifier = Modifier.padding(bottom = EyebrowGap))
        }
        StepText(
            rememberStepText(
                text,
                amounts,
                onDuration = { onStartTimer(it.seconds, stepLabel) },
                mentions = mentions,
                checked = checked,
                onIngredient = { ingredient ->
                    haptics.performHapticFeedback(if (ingredient in checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onToggle(ingredient)
                },
            ),
        )
        if (durations.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = Space.s16),
                horizontalArrangement = Arrangement.spacedBy(Space.s8),
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
        if (ingredients != null && ingredients.rows.isNotEmpty()) {
            StepIngredientsBox(ingredients, checked, onToggle, Modifier.padding(top = Space.s24))
        }
        if (next != null) NextStep(next, Modifier.padding(top = Space.s24))
    }
}

/**
 * What the step uses, on a box as tall as its lines: how many of them are ticked off, then the lines
 * to tick off, as the recipe's list ticks them off.
 */
@Composable
private fun StepIngredientsBox(ingredients: StepIngredients, checked: Set<Int>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    val count = ingredients.rows.size
    val used = ingredients.indexes.count { it in checked }
    TonalBox(modifier) {
        BoxHeader(
            title = stringResource(if (ingredients.all) R.string.cook_all_ingredients else R.string.cook_for_this_step),
            meta = stringResource(R.string.cook_ticked_of, used, count),
        )
        IngredientList(
            rows = ingredients.rows,
            checked = ingredients.indexes.indices.filterTo(mutableSetOf()) { ingredients.indexes[it] in checked },
            onToggle = { onToggle(ingredients.indexes[it]) },
            checks = true,
            modifier = Modifier.padding(top = Space.s4),
        )
    }
}

/** The step after this one: "Next", then its first two lines in second ink. */
@Composable
private fun NextStep(next: Step, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(stringResource(R.string.cook_next_step), style = AromaTheme.type.label, color = AromaTheme.colors.inkSecondary)
        Spacer(Modifier.height(EyebrowGap))
        Text(
            text = remember(next) { Markdown.parse(next.title?.let { "$it. ${next.text}" } ?: next.text).text },
            style = AromaTheme.type.body,
            color = AromaTheme.colors.inkSecondary,
            maxLines = NEXT_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The step in large type. Durations stay sage links in the step's own weight; their underline is
 * drawn by hand, because a text decoration would be as heavy as the type itself.
 */
@Composable
private fun StepText(text: AnnotatedString) {
    val linkColor = AromaTheme.colors.accent
    val styled =
        remember(text, linkColor) {
            val quiet = TextLinkStyles(SpanStyle(color = linkColor))
            text
                .mapAnnotations { range ->
                    when (val link = range.item) {
                        // An ingredient's word keeps its own look: ink, faded once ticked off.
                        is LinkAnnotation.Clickable if link.tag == INGREDIENT_TAG -> range

                        is LinkAnnotation.Clickable -> AnnotatedString.Range(link.copy(styles = quiet), range.start, range.end)

                        is LinkAnnotation.Url -> AnnotatedString.Range(link.copy(styles = quiet), range.start, range.end)

                        else -> range
                    }
                }.breakable()
        }
    val links =
        remember(styled) {
            styled.getLinkAnnotations(0, styled.length).filterNot { (it.item as? LinkAnnotation.Clickable)?.tag == INGREDIENT_TAG }
        }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = styled,
        style = AromaTheme.type.cookStep,
        onTextLayout = { layout = it },
        modifier = Modifier.drawBehind { layout?.let { drawUnderlines(it, links, linkColor.copy(alpha = 0.5f)) } },
    )
}

private fun DrawScope.drawUnderlines(layout: TextLayoutResult, links: List<AnnotatedString.Range<LinkAnnotation>>, color: Color) {
    val below = Space.s4.toPx()
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

/** The one centred page of the app, set a little above the middle, where the eye rests. */
@Composable
private fun DonePage(state: CookUiState, actions: CookActions) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PageMargin, vertical = Space.s32),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        RecipeImage(
            image = state.recipe?.image,
            size = ImageSize.Medium,
            shape = AromaTheme.shapes.cardImage,
            modifier = Modifier.size(DonePhotoSize),
        )
        Spacer(Modifier.height(Space.s32))
        Text(text = stringResource(R.string.cook_finished_title), style = AromaTheme.type.display, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.s12))
        Text(
            text = stringResource(R.string.cook_rate_question),
            style = AromaTheme.type.body,
            color = AromaTheme.colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
        // The stars' touch targets reach beyond the glyphs; the gaps are measured to the glyphs.
        Spacer(Modifier.height(Space.s16 - ratingTouchInset(DoneStarSize)))
        RatingStars(state.rating, starSize = DoneStarSize, onRate = actions.onRate, clearable = true)
        Spacer(Modifier.height(Space.s32 - ratingTouchInset(DoneStarSize)))
        MarkCooked(state.markedCooked, state.markFailed, actions.onMarkCooked)
        Spacer(Modifier.weight(2f))
    }
}

@Composable
private fun MarkCooked(marked: Boolean, failed: Boolean, onMark: () -> Unit) {
    AnimatedContent(
        targetState = marked,
        transitionSpec = { fadeIn(Motion.state()) togetherWith fadeOut(Motion.state()) },
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
                    tint = AromaTheme.colors.accent,
                    modifier = Modifier.size(Glyph.control),
                )
                Spacer(Modifier.width(Space.s8))
                Text(stringResource(R.string.cook_marked), style = AromaTheme.type.label)
            }
        } else {
            SecondaryButton(stringResource(R.string.cook_mark_cooked), onMark, icon = R.drawable.ic_check)
        }
    }
    if (failed && !marked) {
        Text(
            text = stringResource(R.string.cook_mark_failed),
            style = AromaTheme.type.bodySmall,
            color = AromaTheme.colors.error,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .padding(top = Space.s8)
                    .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Running timers on a box above the step bar, one row each; they stay while the steps turn. */
@Composable
private fun Timers(timers: List<CookTimerUi>, actions: CookActions) {
    if (timers.isEmpty()) return
    TonalBox(Modifier.padding(start = PageMargin, end = PageMargin, bottom = EndSpace), contentPadding = PaddingValues()) {
        timers.forEachIndexed { index, timer ->
            if (index > 0) Separator(start = BoxPadding + Glyph.control + Space.s12)
            TimerRow(timer, onToggle = { actions.onToggleTimer(timer.id) }, onDismiss = { actions.onDismissTimer(timer.id) })
        }
    }
}

@Composable
private fun TimerRow(timer: CookTimerUi, onToggle: () -> Unit, onDismiss: () -> Unit) {
    val colors = AromaTheme.colors
    val finished = timer.isFinished
    val pulse = if (finished) finishedPulse() else null
    val timeColor by animateColorAsState(
        when {
            finished -> colors.accent
            timer.isRunning -> colors.ink
            else -> colors.inkSecondary
        },
        Motion.state(),
        label = "timerTime",
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = BoxPadding, end = Space.s8, top = Space.s8, bottom = Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_timer),
            contentDescription = null,
            tint = colors.inkSecondary,
            modifier = Modifier.size(Glyph.control),
        )
        Spacer(Modifier.width(Space.s12))
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = pulse?.value ?: 1f },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = timer.label,
                style = AromaTheme.type.meta,
                color = if (finished) colors.accent else colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (finished) stringResource(R.string.cook_timer_done) else formatClock(timer.remainingSeconds),
                style = AromaTheme.type.numeral,
                color = timeColor,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(Space.s8))
        if (!finished) {
            IconButton(
                icon = if (timer.isRunning) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                contentDescription = stringResource(if (timer.isRunning) R.string.cook_timer_pause else R.string.cook_timer_resume),
                onClick = onToggle,
            )
        }
        IconButton(R.drawable.ic_close, stringResource(R.string.cook_timer_stop), onDismiss)
    }
}

/** A finished timer breathes slowly, so it catches the eye from across the kitchen. */
@Composable
private fun finishedPulse(): State<Float> =
    rememberInfiniteTransition(label = "timerDone").animateFloat(
        initialValue = 1f,
        targetValue = PULSE_LOW,
        animationSpec = infiniteRepeatable(tween(Motion.PULSE / 2), RepeatMode.Reverse),
        label = "timerPulse",
    )

private const val PULSE_LOW = 0.55f

@Composable
private fun ExactAlarmHint(onAllow: () -> Unit) {
    EmptyState(
        text = stringResource(R.string.cook_exact_alarm_hint),
        actionLabel = stringResource(R.string.cook_exact_alarm_allow),
        onAction = onAllow,
        modifier = Modifier.padding(bottom = Space.s8),
    )
}

/**
 * The bar at the foot: back as a round button and next as the main one, both 56 dp tall for the
 * kitchen. On the last step next says "Done"; on the page after, "Close".
 */
@Composable
private fun BottomActions(pager: PagerState, steps: Int, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    BottomActionBar {
        IconButton(
            icon = R.drawable.ic_arrow_back,
            contentDescription = stringResource(R.string.cook_previous),
            onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
            enabled = page > 0,
            place = IconButtonPlace.TallBar,
        )
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
            tall = true,
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
                .padding(bottom = Space.s24),
        ) {
            IngredientList(rows, Modifier.padding(horizontal = PageMargin), checked, onToggle, checks = true)
            GroupHead(
                stringResource(R.string.settings_ingredients_in_steps),
                Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s24),
            )
            SelectionRow(
                options = IngredientsInSteps.entries.map { stringResource(it.labelRes()) },
                selectedIndex = inSteps.ordinal,
                onSelect = { onInSteps(IngredientsInSteps.entries[it]) },
            )
        }
    }
}
