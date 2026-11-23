package app.aroma.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.aroma.R
import app.aroma.core.format.MeasureSystem
import app.aroma.core.model.ServerInfo
import app.aroma.core.model.User
import app.aroma.data.settings.AppLanguage
import app.aroma.data.settings.IngredientsInSteps
import app.aroma.data.settings.ThemeMode
import app.aroma.data.update.UpdateState
import app.aroma.feature.recipe.labelRes
import app.aroma.ui.components.AromaAlertDialog
import app.aroma.ui.components.AromaDropdownMenu
import app.aroma.ui.components.AromaMenuItem
import app.aroma.ui.components.Avatar
import app.aroma.ui.components.CardGroup
import app.aroma.ui.components.DetailPage
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.InlineProgress
import app.aroma.ui.components.SecondaryButton
import app.aroma.ui.components.SoftCard
import app.aroma.ui.components.SwitchRow
import app.aroma.ui.components.currentLocale
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.RowHeight
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.ThumbnailSize
import app.aroma.ui.theme.tabular

data class SettingsUiState(
    val user: User? = null,
    val server: ServerInfo? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val dynamicColorAvailable: Boolean = false,
    val language: AppLanguage = AppLanguage.System,
    val keepScreenOn: Boolean = true,
    val ingredientsInSteps: IngredientsInSteps = IngredientsInSteps.Used,
    val measures: MeasureSystem = MeasureSystem.AsWritten,
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
    val onIngredientsInSteps: (IngredientsInSteps) -> Unit = {},
    val onMeasures: (MeasureSystem) -> Unit = {},
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
                title = stringResource(R.string.settings_theme),
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
                title = stringResource(R.string.settings_app_language),
                options = AppLanguage.entries,
                selected = state.language,
                optionLabel = { stringResource(it.labelRes()) },
                onSelect = actions.onLanguage,
            )
        }
        Group(stringResource(R.string.settings_cooking)) {
            Choice(
                title = stringResource(R.string.measures_title),
                subtitle = stringResource(R.string.measures_hint),
                options = MeasureSystem.entries,
                selected = state.measures,
                optionLabel = { stringResource(it.labelRes()) },
                onSelect = actions.onMeasures,
            )
            RowDivider()
            SwitchRow(
                title = stringResource(R.string.settings_keep_screen_on),
                subtitle = stringResource(R.string.settings_keep_screen_on_hint),
                checked = state.keepScreenOn,
                onChange = actions.onKeepScreenOn,
            )
            RowDivider()
            Choice(
                title = stringResource(R.string.settings_ingredients_in_steps),
                subtitle = stringResource(R.string.settings_ingredients_in_steps_hint),
                options = IngredientsInSteps.entries,
                selected = state.ingredientsInSteps,
                optionLabel = { stringResource(it.labelRes()) },
                onSelect = actions.onIngredientsInSteps,
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
        SoftCard(Modifier.padding(top = Space.l)) { SignOutRow(onClick = { confirmSignOut = true }) }
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

/** Who is signed in where: their initial beside their name, address and server. */
@Composable
private fun Account(user: User, server: ServerInfo?) {
    val name = user.fullName.ifBlank { user.username }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = CardPadding, vertical = Space.m)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, ThumbnailSize, onCard = true)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(name, style = AromaType.Name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (user.email.isNotBlank()) {
                Text(
                    text = user.email,
                    style = AromaType.Small,
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
                    style = AromaType.Small.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A group of settings: its name in sage above the card that holds its rows. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    CardGroup(title, Modifier.padding(top = Space.l), content = content)
}

/** A setting with a few values: its name and the current value; a tap opens the others in a menu. */
@Composable
private fun <T> Choice(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (subtitle == null) RowHeight.oneLine else RowHeight.twoLines)
                .clickable(role = Role.DropdownList, onClick = { open = true })
                .padding(horizontal = CardPadding, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = AromaType.Body)
                if (subtitle != null) Text(subtitle, style = AromaType.Small, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Space.m))
            Text(optionLabel(selected), style = AromaType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Space.xxs))
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Glyph.s),
            )
        }
        AromaDropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.align(Alignment.TopEnd)) {
            options.forEach { option ->
                AromaMenuItem(
                    text = optionLabel(option),
                    icon = null,
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                    selected = option == selected,
                )
            }
        }
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
            .heightIn(min = RowHeight.twoLines)
            .clickable(enabled = canCheck, role = Role.Button, onClick = onCheck)
            .padding(horizontal = CardPadding, vertical = Space.s)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_version, version), style = AromaType.Body.tabular())
            Text(status, style = AromaType.Small.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (update) {
            is UpdateState.Available -> {
                SecondaryButton(
                    stringResource(R.string.settings_updates_install),
                    onClick = { onInstall(update) },
                    compact = true,
                    onCard = true,
                )
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
            .heightIn(min = RowHeight.oneLine)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = AromaType.Body, modifier = Modifier.weight(1f))
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Glyph.s),
        )
    }
}

/** Signing out is a row in red, on a card of its own rather than a button. */
@Composable
private fun SignOutRow(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.error
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight.oneLine)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_logout), contentDescription = null, tint = color, modifier = Modifier.size(Glyph.m))
        Spacer(Modifier.width(Space.m))
        Text(stringResource(R.string.settings_sign_out), style = AromaType.Body, color = color)
    }
}

/** The hairline between rows of one group, inset to their text. */
@Composable
private fun RowDivider() {
    Hairline(Modifier.padding(horizontal = CardPadding))
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

/** The name of a choice of ingredients under the steps, as the settings and cook mode show it. */
fun IngredientsInSteps.labelRes(): Int =
    when (this) {
        IngredientsInSteps.None -> R.string.ingredients_in_steps_none
        IngredientsInSteps.Used -> R.string.ingredients_in_steps_used
        IngredientsInSteps.All -> R.string.ingredients_in_steps_all
    }
