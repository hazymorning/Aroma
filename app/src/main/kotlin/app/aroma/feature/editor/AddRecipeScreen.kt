package app.aroma.feature.editor

import android.content.res.Resources
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.data.AromaError
import app.aroma.ui.components.DetailTopBar
import app.aroma.ui.components.DetailTopBarHeight
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.InlineProgress
import app.aroma.ui.components.Kicker
import app.aroma.ui.components.LabeledTextField
import app.aroma.ui.components.LargeTitle
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.SecondaryButton
import app.aroma.ui.components.SoftCard
import app.aroma.ui.components.SwitchRow
import app.aroma.ui.components.messageRes
import app.aroma.ui.components.titleRes
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Motion.Settle
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.tabular
import kotlinx.coroutines.delay

/**
 * The add screen's state. [starting] covers the moment before an import exists; from then on
 * [import] is the import the screen follows, running or ended.
 */
data class AddRecipeUiState(
    val url: String = "",
    val text: String = "",
    val name: String = "",
    val aiAvailable: Boolean = false,
    val useAi: Boolean = true,
    val translate: Boolean = false,
    val starting: Boolean = false,
    val creating: Boolean = false,
    val import: ImportJob? = null,
    val createError: AromaError? = null,
) {
    /** AI is only offered when the server has a provider set up for the group. */
    val usesAi: Boolean get() = aiAvailable && useAi

    val isImporting: Boolean get() = starting || (import != null && import.outcome == null)

    val canImport: Boolean get() = !isImporting && !creating && (url.isNotBlank() || text.isNotBlank())

    val importError: AromaError? get() = (import?.outcome as? ImportOutcome.Failed)?.error
}

data class AddRecipeActions(
    val onBack: () -> Unit = {},
    val onUrlChange: (String) -> Unit = {},
    val onPaste: () -> Unit = {},
    val onTextChange: (String) -> Unit = {},
    val onUseAiChange: (Boolean) -> Unit = {},
    val onTranslateChange: (Boolean) -> Unit = {},
    val onImport: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onCreate: () -> Unit = {},
)

/** Content wider than this is centred, so lines stay readable on tablets. */
internal val FormMaxWidth = 600.dp

/** The tick or the spinner in front of an import step, as large as [InlineProgress]. */
private val StepMark = 20.dp

private val PasteGlyph = 20.dp

@Composable
fun AddRecipeScreen(state: AddRecipeUiState, actions: AddRecipeActions, modifier: Modifier = Modifier) {
    // While the screen is watched, a sleeping phone would only hide the progress; Aroma keeps it lit.
    val view = LocalView.current
    val busy = state.isImporting || state.creating
    DisposableEffect(view, busy) {
        view.keepScreenOn = busy
        onDispose { view.keepScreenOn = false }
    }
    val title = stringResource(R.string.recipes_add)
    val scroll = rememberScrollState()
    var titleEnd by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val collapsed by remember { derivedStateOf { scroll.value > titleEnd } }
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scroll)
                .statusBarsPadding()
                .padding(top = DetailTopBarHeight)
                .navigationBarsPadding()
                .padding(bottom = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = FormMaxWidth)
                    .fillMaxWidth(),
            ) {
                LargeTitle(title, Modifier.onSizeChanged { titleEnd = it.height }, bottomSpacing = 0.dp)
                Spacer(Modifier.height(Space.l))
                FromWebsite(state, actions)
                Spacer(Modifier.height(Space.xxl))
                Hairline(Modifier.padding(horizontal = ScreenMargin))
                Spacer(Modifier.height(Space.xxl))
                WriteYourself(state, actions)
            }
        }
        DetailTopBar(onBack = actions.onBack, title = title, collapsed = collapsed)
    }
}

@Composable
private fun FromWebsite(state: AddRecipeUiState, actions: AddRecipeActions) {
    val importing = state.isImporting
    val editable = !importing && !state.creating
    val inputAlpha by animateFloatAsState(if (importing) 0.6f else 1f, tween(Motion.STATE, easing = LinearEasing), label = "importInputs")
    Column {
        SectionIntro(stringResource(R.string.add_from_web), stringResource(R.string.add_from_web_hint))
        Spacer(Modifier.height(Space.l))
        Column(Modifier.alpha(inputAlpha)) {
            LabeledTextField(
                value = state.url,
                onValueChange = actions.onUrlChange,
                label = stringResource(R.string.add_url_label),
                enabled = editable,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { actions.onImport() }),
                trailingIcon = {
                    IconButton(onClick = actions.onPaste, enabled = editable) {
                        Icon(
                            painterResource(R.drawable.ic_content_paste),
                            contentDescription = stringResource(R.string.add_paste),
                            modifier = Modifier.size(PasteGlyph),
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = ScreenMargin),
            )
            Spacer(Modifier.height(Space.m))
            LabeledTextField(
                value = state.text,
                onValueChange = actions.onTextChange,
                label = stringResource(R.string.add_text_label),
                placeholder = stringResource(R.string.add_text_hint),
                singleLine = false,
                minLines = 3,
                enabled = editable,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.padding(horizontal = ScreenMargin),
            )
            if (state.aiAvailable) {
                Spacer(Modifier.height(Space.m))
                AiOptions(state, actions)
            }
        }
        Spacer(Modifier.height(Space.l))
        PrimaryButton(
            text = stringResource(R.string.add_import),
            onClick = actions.onImport,
            enabled = state.canImport,
            loading = importing,
            modifier =
                Modifier
                    .padding(horizontal = ScreenMargin)
                    .fillMaxWidth(),
        )
        state.import?.takeIf { importing }?.let { job ->
            ImportProgress(job, state.usesAi, Modifier.padding(top = Space.m))
        }
        state.importError?.let {
            InlineError(it, Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m))
        }
    }
}

/** Mealie's smarter reading, set between two hairlines; translating unfolds below the switch. */
@Composable
private fun AiOptions(state: AddRecipeUiState, actions: AddRecipeActions) {
    Column {
        Hairline(Modifier.padding(horizontal = ScreenMargin))
        SwitchRow(
            title = stringResource(R.string.add_ai_title),
            subtitle = stringResource(R.string.add_ai_hint),
            checked = state.useAi,
            onChange = actions.onUseAiChange,
        )
        AnimatedVisibility(
            visible = state.useAi,
            enter = expandVertically(tween(Motion.EXPAND, easing = Settle)) + fadeIn(tween(Motion.EXPAND, easing = LinearEasing)),
            exit = shrinkVertically(tween(Motion.EXPAND, easing = Settle)) + fadeOut(tween(Motion.STATE, easing = LinearEasing)),
        ) {
            SwitchRow(
                title = stringResource(R.string.add_ai_translate),
                subtitle = null,
                checked = state.translate,
                onChange = actions.onTranslateChange,
            )
        }
        Hairline(Modifier.padding(horizontal = ScreenMargin))
    }
}

/**
 * How far the import has come, as a checklist on a card with the time it has taken, so a slow
 * import never looks like a frozen screen and never ends without a word.
 */
@Composable
private fun ImportProgress(job: ImportJob, usesAi: Boolean, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val steps = job.steps.ifEmpty { listOf(ImportStep.Waiting) }
    Column(modifier) {
        SoftCard {
            Column(
                Modifier
                    .padding(vertical = Space.m)
                    .animateContentSize(tween(Motion.EXPAND, easing = Settle)),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Row(Modifier.padding(horizontal = ScreenMargin), verticalAlignment = Alignment.CenterVertically) {
                    Kicker(stringResource(R.string.import_running), Modifier.weight(1f))
                    Elapsed(job.startedAt)
                }
                steps.forEachIndexed { index, step ->
                    StepRow(step.label(resources), done = index < steps.lastIndex)
                }
            }
        }
        Text(
            text = stringResource(if (usesAi) R.string.add_progress_ai_hint else R.string.add_progress_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.s),
        )
    }
}

@Composable
private fun StepRow(label: String, done: Boolean) {
    Row(Modifier.padding(horizontal = ScreenMargin), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(StepMark), contentAlignment = Alignment.Center) {
            if (done) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else {
                InlineProgress(color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(Space.s))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            // The step under way is announced as it changes; finished ones and the clock are not.
            modifier = if (done) Modifier else Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** The time since [startedAt], in the format of a notification's clock. */
@Composable
private fun Elapsed(startedAt: Long) {
    val seconds by produceState(secondsSince(startedAt), startedAt) {
        while (true) {
            delay(1000 - (System.currentTimeMillis() - startedAt).mod(1000L))
            value = secondsSince(startedAt)
        }
    }
    Text(
        text = DateUtils.formatElapsedTime(seconds),
        style = MaterialTheme.typography.labelMedium.tabular(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun secondsSince(startedAt: Long): Long = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)

/** What the checklist and the notification call a step; Mealie's own words come without its trailing dots. */
internal fun ImportStep.label(resources: Resources): String =
    when (this) {
        is ImportStep.Server -> message.trimEnd('.', '…', ' ')
        ImportStep.ReadingPost -> resources.getString(R.string.add_progress_reading_post)
        ImportStep.Waiting -> resources.getString(R.string.add_progress_waiting)
        ImportStep.Saving -> resources.getString(R.string.add_progress_saving)
        ImportStep.StillWorking -> resources.getString(R.string.add_progress_still_working)
        ImportStep.AddingPhoto -> resources.getString(R.string.add_progress_photo)
    }

@Composable
private fun WriteYourself(state: AddRecipeUiState, actions: AddRecipeActions) {
    val creating = state.creating
    Column {
        SectionIntro(stringResource(R.string.add_manual), stringResource(R.string.add_manual_hint))
        Spacer(Modifier.height(Space.l))
        LabeledTextField(
            value = state.name,
            onValueChange = actions.onNameChange,
            label = stringResource(R.string.editor_name),
            enabled = !creating && !state.isImporting,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { actions.onCreate() }),
            modifier = Modifier.padding(horizontal = ScreenMargin),
        )
        Spacer(Modifier.height(Space.m))
        Box(
            Modifier
                .padding(horizontal = ScreenMargin)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // While the recipe is created, a spinner takes the label's place inside the same outline.
            SecondaryButton(
                text = if (creating) "" else stringResource(R.string.add_create),
                onClick = { if (!creating) actions.onCreate() },
                enabled = state.name.isNotBlank() && !state.isImporting,
                modifier = Modifier.fillMaxWidth(),
            )
            if (creating) InlineProgress(color = MaterialTheme.colorScheme.onSurface)
        }
        state.createError?.let {
            InlineError(it, Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m))
        }
    }
}

@Composable
private fun SectionIntro(title: String, body: String) {
    Column(Modifier.padding(horizontal = ScreenMargin)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.xs))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An error set as text where it happened: a small glyph and title in the error colour, the reason below. */
@Composable
internal fun InlineError(error: AromaError, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Icon(
            painterResource(R.drawable.ic_error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            // Centres the glyph on the title's 18 sp line.
            modifier =
                Modifier
                    .padding(top = ErrorGlyphNudge)
                    .size(ErrorGlyph),
        )
        Column {
            Text(stringResource(error.titleRes()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            Text(
                text = (error as? AromaError.ImportFailed)?.detail ?: stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val ErrorGlyph = 16.dp
private val ErrorGlyphNudge = 1.dp
