package app.aroma.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.ServerInfo
import app.aroma.core.model.User
import app.aroma.data.settings.AppLanguage
import app.aroma.data.settings.ThemeMode
import app.aroma.ui.components.SwitchRow

data class SettingsUiState(
    val user: User? = null,
    val server: ServerInfo? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val dynamicColorAvailable: Boolean = false,
    val language: AppLanguage = AppLanguage.System,
    val keepScreenOn: Boolean = true,
    val offlineRecipes: Boolean = false,
    val offlineCount: Int = 0,
    val version: String = "",
)

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onThemeMode: (ThemeMode) -> Unit = {},
    val onDynamicColor: (Boolean) -> Unit = {},
    val onLanguage: (AppLanguage) -> Unit = {},
    val onKeepScreenOn: (Boolean) -> Unit = {},
    val onOfflineRecipes: (Boolean) -> Unit = {},
    val onOpenLicenses: () -> Unit = {},
    val onSignOut: () -> Unit = {},
)

@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 32.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
        }
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        )
        state.user?.let { AccountCard(it, state.server) }

        Group(stringResource(R.string.settings_appearance)) {
            Label(stringResource(R.string.settings_theme))
            Choice(
                options = ThemeMode.entries,
                selected = state.themeMode,
                label = { stringResource(it.labelRes()) },
                onSelect = actions.onThemeMode,
            )
            if (state.dynamicColorAvailable) {
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_hint),
                    checked = state.dynamicColor,
                    onChange = actions.onDynamicColor,
                )
            }
        }
        Group(stringResource(R.string.settings_language)) {
            Choice(
                options = AppLanguage.entries,
                selected = state.language,
                label = { stringResource(it.labelRes()) },
                onSelect = actions.onLanguage,
            )
        }
        Group(stringResource(R.string.settings_cooking)) {
            SwitchRow(
                title = stringResource(R.string.settings_keep_screen_on),
                subtitle = stringResource(R.string.settings_keep_screen_on_hint),
                checked = state.keepScreenOn,
                onChange = actions.onKeepScreenOn,
            )
        }
        Group(stringResource(R.string.settings_offline)) {
            SwitchRow(
                title = stringResource(R.string.settings_offline_all),
                subtitle =
                    if (state.offlineCount > 0) {
                        pluralStringResource(R.plurals.settings_offline_count, state.offlineCount, state.offlineCount)
                    } else {
                        stringResource(R.string.settings_offline_all_hint)
                    },
                checked = state.offlineRecipes,
                onChange = actions.onOfflineRecipes,
            )
        }
        Group(stringResource(R.string.settings_about)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.settings_version), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(state.version, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = actions.onOpenLicenses)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text(stringResource(R.string.settings_licenses), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = { confirmSignOut = true },
            modifier =
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .height(52.dp),
        ) {
            Icon(painterResource(R.drawable.ic_logout), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.settings_sign_out))
        }
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    actions.onSignOut()
                }) { Text(stringResource(R.string.settings_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun AccountCard(user: User, server: ServerInfo?) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier =
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (user.fullName.ifBlank { user.username }).firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(user.fullName.ifBlank { user.username }, style = MaterialTheme.typography.titleLarge)
                if (user.email.isNotBlank()) {
                    Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                server?.let {
                    Text(
                        text =
                            stringResource(
                                R.string.settings_server_line,
                                it.url.removePrefix("https://").removePrefix("http://"),
                                it.version,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 28.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .semantics { heading() },
        )
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun <T> Choice(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
            ) { Text(label(option), maxLines = 1) }
        }
    }
}

private fun ThemeMode.labelRes() =
    when (this) {
        ThemeMode.System -> R.string.settings_theme_system
        ThemeMode.Light -> R.string.settings_theme_light
        ThemeMode.Dark -> R.string.settings_theme_dark
    }

private fun AppLanguage.labelRes() =
    when (this) {
        AppLanguage.System -> R.string.settings_language_system
        AppLanguage.German -> R.string.settings_language_german
        AppLanguage.English -> R.string.settings_language_english
    }
