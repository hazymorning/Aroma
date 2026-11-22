package app.kochi.feature.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kochi.R
import app.kochi.core.model.ServerInfo
import app.kochi.data.KochiError
import app.kochi.ui.components.messageRes
import app.kochi.ui.components.rememberSyncedTextFieldState
import app.kochi.ui.components.titleRes

enum class LoginStep { Server, Credentials }

data class LoginUiState(
    val step: LoginStep = LoginStep.Server,
    val serverInput: String = "",
    val server: ServerInfo? = null,
    val username: String = "",
    val password: String = "",
    val token: String = "",
    val useToken: Boolean = false,
    val isWorking: Boolean = false,
    val error: KochiError? = null,
)

data class LoginActions(
    val onServerChange: (String) -> Unit = {},
    val onConnect: () -> Unit = {},
    val onChangeServer: () -> Unit = {},
    val onUsernameChange: (String) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onTokenChange: (String) -> Unit = {},
    val onToggleTokenMode: () -> Unit = {},
    val onSignIn: () -> Unit = {},
)

@Composable
fun LoginScreen(state: LoginUiState, actions: LoginActions, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.login_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        Column(Modifier.widthIn(max = 460.dp)) {
            AnimatedContent(targetState = state.step, label = "loginStep") { step ->
                when (step) {
                    LoginStep.Server -> ServerStep(state, actions)
                    LoginStep.Credentials -> CredentialsStep(state, actions)
                }
            }
        }
    }
}

@Composable
private fun ServerStep(state: LoginUiState, actions: LoginActions) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = state.serverInput,
            onValueChange = actions.onServerChange,
            label = { Text(stringResource(R.string.login_server_label)) },
            placeholder = { Text(stringResource(R.string.login_server_placeholder)) },
            supportingText = { Text(stringResource(R.string.login_server_hint)) },
            singleLine = true,
            isError = state.error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { actions.onConnect() }),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorMessage(state.error)
        PrimaryButton(
            stringResource(R.string.action_continue),
            state.isWorking,
            enabled = state.serverInput.isNotBlank(),
            onClick = actions.onConnect,
        )
    }
}

@Composable
private fun CredentialsStep(state: LoginUiState, actions: LoginActions) {
    var showSecret by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        state.server?.let { server -> ServerCard(server, actions.onChangeServer) }
        if (state.useToken) {
            OutlinedSecureTextField(
                state = rememberSyncedTextFieldState(state.token, actions.onTokenChange),
                label = { Text(stringResource(R.string.login_token_label)) },
                supportingText = { Text(stringResource(R.string.login_token_hint)) },
                textObfuscationMode = if (showSecret) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
                trailingIcon = { VisibilityToggle(showSecret) { showSecret = !showSecret } },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                onKeyboardAction = { actions.onSignIn() },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = state.username,
                onValueChange = actions.onUsernameChange,
                label = { Text(stringResource(R.string.login_username_label)) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false,
                    ),
                shape = MaterialTheme.shapes.medium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Username + ContentType.EmailAddress },
            )
            OutlinedSecureTextField(
                state = rememberSyncedTextFieldState(state.password, actions.onPasswordChange),
                label = { Text(stringResource(R.string.login_password_label)) },
                textObfuscationMode = if (showSecret) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
                trailingIcon = { VisibilityToggle(showSecret) { showSecret = !showSecret } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                onKeyboardAction = { actions.onSignIn() },
                shape = MaterialTheme.shapes.medium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Password },
            )
        }
        ErrorMessage(state.error)
        val canSubmit = if (state.useToken) state.token.isNotBlank() else state.username.isNotBlank() && state.password.isNotEmpty()
        PrimaryButton(stringResource(R.string.login_sign_in), state.isWorking, enabled = canSubmit, onClick = actions.onSignIn)
        if (state.server?.allowPasswordLogin != false) {
            TextButton(onClick = actions.onToggleTokenMode, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(if (state.useToken) R.string.login_use_password else R.string.login_use_token))
            }
        } else {
            Text(
                text = stringResource(R.string.login_password_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerCard(server: ServerInfo, onChange: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_check_circle_filled), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(stringResource(R.string.login_server_found, server.version), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = server.url.removePrefix("https://").removePrefix("http://"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (server.url.startsWith("http://")) {
                    Text(
                        text = stringResource(R.string.login_unencrypted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            TextButton(onClick = onChange) { Text(stringResource(R.string.login_change_server)) }
        }
    }
}

@Composable
private fun VisibilityToggle(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
            contentDescription = stringResource(if (visible) R.string.login_hide_secret else R.string.login_show_secret),
        )
    }
}

@Composable
private fun ErrorMessage(error: KochiError?) {
    if (error == null) return
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                stringResource(error.titleRes()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun PrimaryButton(label: String, working: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = { if (!working) onClick() },
        enabled = enabled || working,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
    ) {
        if (working) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
