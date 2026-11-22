package app.aroma.feature.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.data.AromaError
import app.aroma.ui.components.SwitchRow
import app.aroma.ui.components.messageRes
import app.aroma.ui.components.titleRes

enum class AddMode { Import, Create }

data class AddRecipeUiState(
    val url: String = "",
    val text: String = "",
    val name: String = "",
    val aiAvailable: Boolean = false,
    val useAi: Boolean = true,
    val translate: Boolean = false,
    val working: AddMode? = null,
    val step: ImportStep? = null,
    val error: AromaError? = null,
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

@Composable
fun AddRecipeScreen(state: AddRecipeUiState, actions: AddRecipeActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        IconButton(onClick = actions.onBack, modifier = Modifier.padding(8.dp)) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
        }
        Text(
            text = stringResource(R.string.recipes_add),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        )
        Section(
            icon = R.drawable.ic_link,
            title = stringResource(R.string.add_from_web),
            body = stringResource(R.string.add_from_web_hint),
        ) {
            OutlinedTextField(
                value = state.url,
                onValueChange = actions.onUrlChange,
                label = { Text(stringResource(R.string.add_url_label)) },
                singleLine = true,
                enabled = state.working == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { actions.onImport() }),
                trailingIcon = {
                    IconButton(onClick = actions.onPaste, enabled = state.working == null) {
                        Icon(painterResource(R.drawable.ic_content_paste), contentDescription = stringResource(R.string.add_paste))
                    }
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.aiAvailable) AiOptions(state, actions)
            if (state.working == AddMode.Import) {
                ImportProgress(step = state.step, usesAi = state.usesAi)
            } else {
                WorkingButton(
                    label = stringResource(R.string.add_import),
                    working = false,
                    enabled = state.canImport,
                    onClick = actions.onImport,
                )
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 28.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Section(
            icon = R.drawable.ic_edit,
            title = stringResource(R.string.add_manual),
            body = stringResource(R.string.add_manual_hint),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = actions.onNameChange,
                label = { Text(stringResource(R.string.editor_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { actions.onCreate() }),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            WorkingButton(
                label = stringResource(R.string.add_create),
                working = state.working == AddMode.Create,
                enabled = state.name.isNotBlank() && state.working == null,
                onClick = actions.onCreate,
                tonal = true,
            )
        }
        state.error?.let { ImportError(it) }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AiOptions(state: AddRecipeUiState, actions: AddRecipeActions) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            SwitchRow(
                title = stringResource(R.string.add_ai_title),
                subtitle = stringResource(R.string.add_ai_hint),
                checked = state.useAi,
                onChange = actions.onUseAiChange,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            )
            AnimatedVisibility(visible = state.useAi) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = actions.onTextChange,
                        label = { Text(stringResource(R.string.add_ai_text_label)) },
                        placeholder = { Text(stringResource(R.string.add_ai_text_hint)) },
                        minLines = 3,
                        maxLines = 8,
                        enabled = state.working == null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SwitchRow(
                        title = stringResource(R.string.add_ai_translate),
                        subtitle = null,
                        checked = state.translate,
                        onChange = actions.onTranslateChange,
                        contentPadding = PaddingValues(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** What Mealie is doing right now, so a slow import never looks like a frozen screen. */
@Composable
private fun ImportProgress(step: ImportStep?, usesAi: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.16f),
                modifier = Modifier.fillMaxWidth(),
            )
            Crossfade(targetState = stepText(step), label = "importStep") { text ->
                Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(
                text = stringResource(if (usesAi) R.string.add_progress_ai_hint else R.string.add_importing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun stepText(step: ImportStep?): String =
    when (step) {
        is ImportStep.Server -> step.message.trimEnd('.', '…', ' ')
        ImportStep.Rebuilding -> stringResource(R.string.add_progress_rebuilding)
        ImportStep.Tidying -> stringResource(R.string.add_progress_tidying)
        ImportStep.Waiting, null -> stringResource(R.string.add_progress_waiting)
    }

@Composable
private fun ImportError(error: AromaError) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier =
            Modifier
                .padding(20.dp)
                .fillMaxWidth(),
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                stringResource(error.titleRes()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                (error as? AromaError.ImportFailed)?.detail ?: stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun Section(icon: Int, title: String, body: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 12.dp))
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun WorkingButton(
    label: String,
    working: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tonal: Boolean = false,
) {
    val modifier =
        Modifier
            .fillMaxWidth()
            .height(52.dp)
    val content: @Composable () -> Unit = {
        if (working) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(label)
        }
    }
    if (tonal) {
        FilledTonalButton(onClick = { if (!working) onClick() }, enabled = enabled || working, modifier = modifier) { content() }
    } else {
        Button(onClick = { if (!working) onClick() }, enabled = enabled || working, modifier = modifier) { content() }
    }
}
