package app.aroma.feature.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
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
import app.aroma.ui.components.Kicker
import app.aroma.ui.components.LabeledTextField
import app.aroma.ui.components.LargeTitle
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.SecondaryButton
import app.aroma.ui.components.SwitchRow
import app.aroma.ui.components.messageRes
import app.aroma.ui.components.titleRes
import app.aroma.ui.navigation.Transitions.Settle
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space

enum class AddMode { Import, Create }

/** Something that went wrong, shown under the button of the [mode] that started it. */
data class AddFailure(val mode: AddMode, val error: AromaError)

data class AddRecipeUiState(
    val url: String = "",
    val text: String = "",
    val name: String = "",
    val aiAvailable: Boolean = false,
    val useAi: Boolean = true,
    val translate: Boolean = false,
    val working: AddMode? = null,
    val step: ImportStep? = null,
    val failure: AddFailure? = null,
) {
    /** AI is only offered when the server has a provider set up for the group. */
    val usesAi: Boolean get() = aiAvailable && useAi

    val canImport: Boolean get() = working == null && (url.isNotBlank() || (usesAi && text.isNotBlank()))
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

@Composable
fun AddRecipeScreen(state: AddRecipeUiState, actions: AddRecipeActions, modifier: Modifier = Modifier) {
    // An import can wait minutes for Mealie; a sleeping phone would cut the connection meanwhile.
    val view = LocalView.current
    DisposableEffect(view, state.working) {
        view.keepScreenOn = state.working != null
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
    val importing = state.working == AddMode.Import
    val inputAlpha by animateFloatAsState(if (importing) 0.6f else 1f, tween(Motion.STATE, easing = LinearEasing), label = "importInputs")
    Column {
        SectionIntro(stringResource(R.string.add_from_web), stringResource(R.string.add_from_web_hint))
        Spacer(Modifier.height(Space.l))
        Column(Modifier.alpha(inputAlpha)) {
            LabeledTextField(
                value = state.url,
                onValueChange = actions.onUrlChange,
                label = stringResource(R.string.add_url_label),
                enabled = state.working == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { actions.onImport() }),
                trailingIcon = {
                    IconButton(onClick = actions.onPaste, enabled = state.working == null) {
                        Icon(
                            painterResource(R.drawable.ic_content_paste),
                            contentDescription = stringResource(R.string.add_paste),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
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
        if (importing) {
            ImportProgress(
                step = state.step,
                usesAi = state.usesAi,
                modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m),
            )
        }
        state.failure?.takeIf { it.mode == AddMode.Import }?.let {
            InlineError(it.error, Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m))
        }
    }
}

/** Mealie's smarter reading, set between two hairlines; its extra fields unfold below the switch. */
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
            Column {
                LabeledTextField(
                    value = state.text,
                    onValueChange = actions.onTextChange,
                    label = stringResource(R.string.add_ai_text_label),
                    placeholder = stringResource(R.string.add_ai_text_hint),
                    singleLine = false,
                    minLines = 3,
                    enabled = state.working == null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xxs),
                )
                SwitchRow(
                    title = stringResource(R.string.add_ai_translate),
                    subtitle = null,
                    checked = state.translate,
                    onChange = actions.onTranslateChange,
                )
            }
        }
        Hairline(Modifier.padding(horizontal = ScreenMargin))
    }
}

/** What Mealie is doing right now, so a slow import never looks like a frozen screen. */
@Composable
private fun ImportProgress(step: ImportStep?, usesAi: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Crossfade(targetState = stepText(step), animationSpec = tween(Motion.STATE), label = "importStep") { text ->
            Kicker(text)
        }
        Spacer(Modifier.height(Space.xs))
        LinearProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(2.dp),
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = stringResource(if (usesAi) R.string.add_progress_ai_hint else R.string.add_importing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun stepText(step: ImportStep?): String =
    when (step) {
        is ImportStep.Server -> step.message.trimEnd('.', '…', ' ')
        ImportStep.Rebuilding -> stringResource(R.string.add_progress_rebuilding)
        ImportStep.Tidying -> stringResource(R.string.add_progress_tidying)
        ImportStep.StillWorking -> stringResource(R.string.add_progress_still_working)
        ImportStep.Waiting, null -> stringResource(R.string.add_progress_waiting)
    }

@Composable
private fun WriteYourself(state: AddRecipeUiState, actions: AddRecipeActions) {
    val creating = state.working == AddMode.Create
    Column {
        SectionIntro(stringResource(R.string.add_manual), stringResource(R.string.add_manual_hint))
        Spacer(Modifier.height(Space.l))
        LabeledTextField(
            value = state.name,
            onValueChange = actions.onNameChange,
            label = stringResource(R.string.editor_name),
            enabled = state.working == null,
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
                enabled = state.name.isNotBlank() && (state.working == null || creating),
                modifier = Modifier.fillMaxWidth(),
            )
            if (creating) {
                CircularProgressIndicator(
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        state.failure?.takeIf { it.mode == AddMode.Create }?.let {
            InlineError(it.error, Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m))
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
                    .padding(top = 1.dp)
                    .size(16.dp),
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
