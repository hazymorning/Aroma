package app.aroma.feature.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.ServerInfo
import app.aroma.data.AromaError
import app.aroma.data.remote.ServerAddress
import app.aroma.ui.components.ErrorNote
import app.aroma.ui.components.IconButton
import app.aroma.ui.components.LabeledSecureTextField
import app.aroma.ui.components.LabeledTextField
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.Text
import app.aroma.ui.components.TextAction
import app.aroma.ui.components.TonalBox
import app.aroma.ui.components.rememberSyncedTextFieldState
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Motion.Decelerate
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space

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
    val error: AromaError? = null,
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

/** The space above the wordmark, as a share of the height left beside the keyboard. */
private const val TOP_SPACE_FRACTION = 0.18f

/** Signing in is a narrow column, also on a tablet; the tagline under the name a narrower one. */
private val FormWidth = 460.dp
private val TaglineWidth = 320.dp

/** The logo's height: its bowl as large as a page title is tall, the wordmark beside it. */
private val LogoHeight = 64.dp

@Composable
fun LoginScreen(state: LoginUiState, actions: LoginActions, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        // Measured beside the keyboard, so the form moves up when it opens and the button stays in view.
        val topSpace = maxHeight * TOP_SPACE_FRACTION
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = PageMargin, end = PageMargin, top = topSpace, bottom = Space.s24),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = FormWidth)
                    .fillMaxWidth(),
            ) {
                // The logo, mark and wordmark; on a dark ground its version for dark grounds.
                val appName = stringResource(R.string.app_name)
                Image(
                    painter = painterResource(if (AromaTheme.colors.isDark) R.drawable.aroma_logo_on_dark else R.drawable.aroma_logo),
                    contentDescription = appName,
                    modifier =
                        Modifier
                            .height(LogoHeight)
                            .semantics { heading() },
                )
                Spacer(Modifier.height(Space.s16))
                Text(
                    text = stringResource(R.string.login_tagline),
                    style = AromaTheme.type.body,
                    color = AromaTheme.colors.inkSecondary,
                    modifier = Modifier.widthIn(max = TaglineWidth),
                )
                Spacer(Modifier.height(Space.s48))
                AnimatedContent(
                    targetState = state.step,
                    transitionSpec = { fadeIn(tween(Motion.STATE, easing = Motion.Decelerate)) togetherWith fadeOut(tween(Motion.STATE)) },
                    label = "loginStep",
                ) { step ->
                    when (step) {
                        LoginStep.Server -> ServerStep(state, actions)
                        LoginStep.Credentials -> CredentialsStep(state, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerStep(state: LoginUiState, actions: LoginActions) {
    Column {
        LabeledTextField(
            value = state.serverInput,
            onValueChange = actions.onServerChange,
            label = stringResource(R.string.login_server_label),
            placeholder = stringResource(R.string.login_server_placeholder),
            // While an error explains what went wrong, the hint makes room for it.
            supportingText = if (state.error == null) stringResource(R.string.login_server_hint) else null,
            isError = state.error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { actions.onConnect() }),
        )
        // Plain http is only tried at home; a server elsewhere without https has to be typed with it.
        val onlyHttpsTried = remember(state.serverInput) { ServerAddress.candidates(state.serverInput).singleOrNull()?.isHttps == true }
        val httpHint =
            state.error.let { it is AromaError.Certificate || it is AromaError.Offline } &&
                onlyHttpsTried &&
                !state.serverInput.startsWith("https", ignoreCase = true)
        state.error?.let {
            ErrorNote(it, Modifier.padding(top = Space.s16), hint = if (httpHint) stringResource(R.string.login_http_hint) else null)
        }
        Spacer(Modifier.height(Space.s24))
        PrimaryButton(
            text = stringResource(R.string.action_continue),
            onClick = actions.onConnect,
            enabled = state.serverInput.isNotBlank(),
            loading = state.isWorking,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CredentialsStep(state: LoginUiState, actions: LoginActions) {
    var showSecret by rememberSaveable { mutableStateOf(false) }
    val obfuscation = if (showSecret) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped
    Column {
        state.server?.let { server ->
            ServerCard(server, actions.onChangeServer)
            Spacer(Modifier.height(Space.s24))
        }
        if (state.useToken) {
            LabeledSecureTextField(
                state = rememberSecretState(state.token, actions.onTokenChange),
                label = stringResource(R.string.login_token_label),
                supportingText = stringResource(R.string.login_token_hint),
                textObfuscationMode = obfuscation,
                trailingIcon = { VisibilityToggle(showSecret) { showSecret = !showSecret } },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                onKeyboardAction = { actions.onSignIn() },
            )
        } else {
            LabeledTextField(
                value = state.username,
                onValueChange = actions.onUsernameChange,
                label = stringResource(R.string.login_username_label),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false,
                    ),
                fieldModifier = Modifier.semantics { contentType = ContentType.Username + ContentType.EmailAddress },
            )
            Spacer(Modifier.height(Space.s16))
            LabeledSecureTextField(
                state = rememberSecretState(state.password, actions.onPasswordChange),
                label = stringResource(R.string.login_password_label),
                textObfuscationMode = obfuscation,
                trailingIcon = { VisibilityToggle(showSecret) { showSecret = !showSecret } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                onKeyboardAction = { actions.onSignIn() },
                fieldModifier = Modifier.semantics { contentType = ContentType.Password },
            )
        }
        state.error?.let { ErrorNote(it, Modifier.padding(top = Space.s16)) }
        Spacer(Modifier.height(Space.s24))
        val canSubmit = if (state.useToken) state.token.isNotBlank() else state.username.isNotBlank() && state.password.isNotEmpty()
        PrimaryButton(
            text = stringResource(R.string.login_sign_in),
            onClick = actions.onSignIn,
            enabled = canSubmit,
            loading = state.isWorking,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.server?.allowPasswordLogin != false) {
            Spacer(Modifier.height(Space.s4))
            TextAction(
                text = stringResource(if (state.useToken) R.string.login_use_password else R.string.login_use_token),
                onClick = actions.onToggleTokenMode,
            )
        } else {
            Text(
                text = stringResource(R.string.login_password_disabled),
                style = AromaTheme.type.bodySmall,
                color = AromaTheme.colors.inkSecondary,
                modifier = Modifier.padding(top = Space.s16),
            )
        }
    }
}

/**
 * Like [rememberSyncedTextFieldState] for a password or token, but kept out of the screen's saved
 * state, which the system holds outside the app. The view model keeps it across rotation and only
 * clears it once signed in, when this screen goes away, so nothing needs writing back.
 */
@Composable
private fun rememberSecretState(value: String, onValueChange: (String) -> Unit): TextFieldState {
    val state = remember { TextFieldState(value) }
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestValue by rememberUpdatedState(value)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text -> if (text != latestValue) latestOnChange(text) }
    }
    return state
}

/** The server that answered, on a card with a quiet way back to change it. */
@Composable
private fun ServerCard(server: ServerInfo, onChange: () -> Unit) {
    TonalBox {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = AromaTheme.colors.accent,
                    modifier = Modifier.size(Glyph.control),
                )
                Spacer(Modifier.width(Space.s16))
                Column(
                    Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {},
                ) {
                    Text(stringResource(R.string.login_server_found, server.version), style = AromaTheme.type.label)
                    Text(
                        text = server.url.removePrefix("https://").removePrefix("http://"),
                        style = AromaTheme.type.bodySmall,
                        color = AromaTheme.colors.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(Space.s16))
                TextAction(stringResource(R.string.login_change_server), onChange)
            }
            if (server.url.startsWith("http://")) {
                Text(
                    text = stringResource(R.string.login_unencrypted),
                    style = AromaTheme.type.bodySmall,
                    color = AromaTheme.colors.inkSecondary,
                    modifier = Modifier.padding(start = Glyph.control + Space.s16, top = Space.s4),
                )
            }
        }
    }
}

@Composable
private fun VisibilityToggle(visible: Boolean, onToggle: () -> Unit) {
    IconButton(
        if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
        stringResource(if (visible) R.string.login_hide_secret else R.string.login_show_secret),
        onToggle,
    )
}
