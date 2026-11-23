package app.aroma.feature.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aroma.R
import app.aroma.core.model.ServerInfo
import app.aroma.core.model.User
import app.aroma.data.settings.AppLanguage
import app.aroma.data.settings.ThemeMode
import app.aroma.data.update.UpdateState
import app.aroma.ui.components.AromaAlertDialog
import app.aroma.ui.components.Avatar
import app.aroma.ui.components.DetailPage
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.InlineProgress
import app.aroma.ui.components.Kicker
import app.aroma.ui.components.SecondaryButton
import app.aroma.ui.components.SegmentedControl
import app.aroma.ui.components.SoftCard
import app.aroma.ui.components.SwitchRow
import app.aroma.ui.components.currentLocale
import app.aroma.ui.theme.Lines
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.extras
import app.aroma.ui.theme.tabular

data class SettingsUiState(
    val user: User? = null,
    val server: ServerInfo? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val dynamicColorAvailable: Boolean = false,
    val language: AppLanguage = AppLanguage.System,
    val keepScreenOn: Boolean = true,
    val ingredientsInSteps: Boolean = false,
    val offlineRecipes: Boolean = false,
    val offlineCount: Int = 0,
    val version: String = "",
    val update: UpdateState = UpdateState.Unknown,
    val checkUpdatesAtStart: Boolean = false,
)

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onThemeMode: (ThemeMode) -> Unit = {},
    val onDynamicColor: (Boolean) -> Unit = {},
    val onLanguage: (AppLanguage) -> Unit = {},
    val onKeepScreenOn: (Boolean) -> Unit = {},
    val onIngredientsInSteps: (Boolean) -> Unit = {},
    val onOfflineRecipes: (Boolean) -> Unit = {},
    val onCheckForUpdates: () -> Unit = {},
    val onInstallUpdate: (UpdateState.Available) -> Unit = {},
    val onCheckUpdatesAtStart: (Boolean) -> Unit = {},
    val onOpenLicenses: () -> Unit = {},
    val onSignOut: () -> Unit = {},
)

@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    DetailPage(title = stringResource(R.string.settings_title), onBack = actions.onBack, modifier = modifier) {
        state.user?.let { user -> SoftCard(Modifier.padding(top = Space.l)) { Account(user, state.server) } }
        Group(stringResource(R.string.settings_appearance)) {
            Choice(
                label = stringResource(R.string.settings_theme),
                options = ThemeMode.entries,
                selected = state.themeMode,
                optionLabel = { stringResource(it.labelRes()) },
                onSelect = actions.onThemeMode,
            )
            if (state.dynamicColorAvailable) {
                RowDivider()
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
                label = null,
                options = AppLanguage.entries,
                selected = state.language,
                optionLabel = { stringResource(it.labelRes()) },
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
            RowDivider()
            SwitchRow(
                title = stringResource(R.string.settings_ingredients_in_steps),
                subtitle = stringResource(R.string.settings_ingredients_in_steps_hint),
                checked = state.ingredientsInSteps,
                onChange = actions.onIngredientsInSteps,
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
            UpdateRow(state.version, state.update, actions.onCheckForUpdates, actions.onInstallUpdate)
            RowDivider()
            SwitchRow(
                title = stringResource(R.string.settings_updates_at_start),
                subtitle = stringResource(R.string.settings_updates_at_start_hint),
                checked = state.checkUpdatesAtStart,
                onChange = actions.onCheckUpdatesAtStart,
            )
            RowDivider()
            NavigationRow(stringResource(R.string.settings_licenses), actions.onOpenLicenses)
        }
        SoftCard(Modifier.padding(top = Space.xl)) { SignOutRow(onClick = { confirmSignOut = true }) }
    }
    if (confirmSignOut) {
        AromaAlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = stringResource(R.string.settings_sign_out_title),
            text = stringResource(R.string.settings_sign_out_message),
            confirmLabel = stringResource(R.string.settings_sign_out),
            onConfirm = {
                confirmSignOut = false
                actions.onSignOut()
            },
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }
}

/** Who is signed in where, set as plain text beside the initial; no card around it. */
@Composable
private fun Account(user: User, server: ServerInfo?) {
    val name = user.fullName.ifBlank { user.username }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin, vertical = Space.s)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, user.photo, 56.dp)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (user.email.isNotBlank()) {
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            server?.let {
                Text(
                    text =
                        stringResource(
                            R.string.settings_server_line,
                            it.url.removePrefix("https://").removePrefix("http://"),
                            it.version,
                        ),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Space.xxs),
                )
            }
        }
    }
}

/** A group of settings: a kicker above a soft card that holds its rows. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = Space.xl)) {
        Kicker(
            text = title,
            modifier =
                Modifier
                    .padding(start = ScreenMargin, end = ScreenMargin, bottom = Space.s)
                    .semantics { heading() },
        )
        SoftCard(content = content)
    }
}

@Composable
private fun <T> Choice(
    label: String?,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin, vertical = Space.s),
    ) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Space.xs))
        }
        SegmentedControl(
            options = options.map { optionLabel(it) },
            selectedIndex = options.indexOf(selected),
            onSelect = { onSelect(options[it]) },
        )
    }
}

/**
 * The version and where its updates stand. Tapping the row looks for a newer one; once one is
 * found, its button downloads it and opens Android's installer.
 */
@Composable
private fun UpdateRow(version: String, update: UpdateState, onCheck: () -> Unit, onInstall: (UpdateState.Available) -> Unit) {
    val status =
        when (update) {
            UpdateState.Unknown -> {
                stringResource(R.string.settings_updates_check)
            }

            UpdateState.Checking -> {
                stringResource(R.string.settings_updates_checking)
            }

            UpdateState.Current -> {
                stringResource(R.string.settings_updates_current)
            }

            UpdateState.Failed -> {
                stringResource(R.string.settings_updates_failed)
            }

            is UpdateState.Available -> {
                stringResource(R.string.settings_updates_available, update.version)
            }

            is UpdateState.Downloading -> {
                stringResource(R.string.settings_updates_downloading, update.update.version, (update.progress * 100).toInt())
            }
        }
    val canCheck = update == UpdateState.Unknown || update == UpdateState.Current || update == UpdateState.Failed
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(enabled = canCheck, role = Role.Button, onClick = onCheck)
            .padding(horizontal = ScreenMargin, vertical = Space.s)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.bodyLarge.tabular())
            Text(status, style = MaterialTheme.typography.bodyMedium.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (update) {
            is UpdateState.Available -> {
                SecondaryButton(stringResource(R.string.settings_updates_install), onClick = { onInstall(update) }, compact = true)
            }

            UpdateState.Checking, is UpdateState.Downloading -> {
                InlineProgress()
            }

            else -> {}
        }
    }
}

@Composable
private fun NavigationRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ScreenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Signing out is a plain row in red, on a card of its own rather than a button. */
@Composable
private fun SignOutRow(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.error
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ScreenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_logout), contentDescription = null, tint = color)
        Spacer(Modifier.width(Space.m))
        Text(stringResource(R.string.settings_sign_out), style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

/** The hairline between rows of one group, inset to the text column. */
@Composable
private fun RowDivider() {
    Hairline(Modifier.padding(horizontal = ScreenMargin))
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
