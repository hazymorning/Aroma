package app.aroma.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.format.MeasureSystem
import app.aroma.core.model.ServerInfo
import app.aroma.core.model.User
import app.aroma.data.settings.AppLanguage
import app.aroma.data.settings.HomeSection
import app.aroma.data.settings.IngredientsInSteps
import app.aroma.data.settings.ThemeMode
import app.aroma.data.update.UpdateState
import app.aroma.ui.components.AromaAlertDialog
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.Avatar
import app.aroma.ui.components.ChoiceRow
import app.aroma.ui.components.ChoiceSheet
import app.aroma.ui.components.DetailPage
import app.aroma.ui.components.GroupHead
import app.aroma.ui.components.ProfileAvatarSize
import app.aroma.ui.components.SecondaryButton
import app.aroma.ui.components.Separator
import app.aroma.ui.components.SettingsRow
import app.aroma.ui.components.SettingsTextEdge
import app.aroma.ui.components.Text
import app.aroma.ui.components.TonalBox
import app.aroma.ui.components.rememberFullSheetState
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.SectionGap
import app.aroma.ui.theme.Space

data class SettingsUiState(
    val user: User? = null,
    val server: ServerInfo? = null,
    val connected: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val dynamicColorAvailable: Boolean = false,
    val homeSections: Set<HomeSection> = HomeSection.SHOWN_BY_DEFAULT,
    val language: AppLanguage = AppLanguage.System,
    val keepScreenOn: Boolean = true,
    val showNextStep: Boolean = true,
    val ingredientsInSteps: IngredientsInSteps = IngredientsInSteps.Used,
    val amountsInSteps: Boolean = false,
    val measures: MeasureSystem = MeasureSystem.AsWritten,
    val offlineRecipes: Boolean = false,
    val offlineCount: Int = 0,
    val version: String = "",
    val update: UpdateState = UpdateState.Unknown,
    val checkUpdatesAtStart: Boolean = false,
)

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onThemeModeChange: (ThemeMode) -> Unit = {},
    val onDynamicColorChange: (Boolean) -> Unit = {},
    val onHomeSectionChange: (HomeSection, Boolean) -> Unit = { _, _ -> },
    val onLanguageChange: (AppLanguage) -> Unit = {},
    val onKeepScreenOnChange: (Boolean) -> Unit = {},
    val onShowNextStepChange: (Boolean) -> Unit = {},
    val onIngredientsInStepsChange: (IngredientsInSteps) -> Unit = {},
    val onAmountsInStepsChange: (Boolean) -> Unit = {},
    val onMeasuresChange: (MeasureSystem) -> Unit = {},
    val onOfflineRecipesChange: (Boolean) -> Unit = {},
    val onCheckForUpdates: () -> Unit = {},
    val onInstallUpdate: (UpdateState.Available) -> Unit = {},
    val onCheckUpdatesAtStartChange: (Boolean) -> Unit = {},
    val onOpenLicenses: () -> Unit = {},
    val onSignOut: () -> Unit = {},
)

/** A sheet the settings open: the values of one setting, what Home shows, or everything for cooking. */
private enum class SettingsSheet { Theme, HomeSections, Language, Cooking }

@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<SettingsSheet?>(null) }
    DetailPage(title = stringResource(R.string.settings_title), onBack = actions.onBack, modifier = modifier) {
        Column(Modifier.padding(horizontal = PageMargin)) {
            state.user?.let { user -> Profile(user, state.server, state.connected) }
            Group(stringResource(R.string.settings_appearance)) {
                SettingsRow(
                    title = stringResource(R.string.settings_theme),
                    icon = R.drawable.ic_contrast,
                    value = stringResource(state.themeMode.labelRes()),
                    onClick = { sheet = SettingsSheet.Theme },
                )
                if (state.dynamicColorAvailable) {
                    Separator(start = SettingsTextEdge)
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        icon = R.drawable.ic_drop,
                        description = stringResource(R.string.settings_dynamic_color_hint),
                        checked = state.dynamicColor,
                        onCheckedChange = actions.onDynamicColorChange,
                    )
                }
                Separator(start = SettingsTextEdge)
                SettingsRow(
                    title = stringResource(R.string.settings_home),
                    icon = R.drawable.ic_home,
                    description = stringResource(R.string.settings_home_description),
                    value = stringResource(R.string.settings_home_shown, state.homeSections.size, HomeSection.entries.size),
                    onClick = { sheet = SettingsSheet.HomeSections },
                )
                Separator(start = SettingsTextEdge)
                SettingsRow(
                    title = stringResource(R.string.settings_language),
                    icon = R.drawable.ic_globe,
                    value = stringResource(state.language.labelRes()),
                    onClick = { sheet = SettingsSheet.Language },
                )
            }
            Group(stringResource(R.string.settings_recipes)) {
                SettingsRow(
                    title = stringResource(R.string.settings_cooking),
                    icon = R.drawable.ic_cooking,
                    onClick = { sheet = SettingsSheet.Cooking },
                )
                Separator(start = SettingsTextEdge)
                SwitchRow(
                    title = stringResource(R.string.settings_offline_all),
                    icon = R.drawable.ic_download,
                    description =
                        if (state.offlineCount > 0) {
                            pluralStringResource(R.plurals.settings_offline_count, state.offlineCount, state.offlineCount)
                        } else {
                            stringResource(R.string.settings_offline_all_hint)
                        },
                    checked = state.offlineRecipes,
                    onCheckedChange = actions.onOfflineRecipesChange,
                )
            }
            Group(stringResource(R.string.settings_about)) {
                UpdateRow(state.version, state.update, actions.onCheckForUpdates, actions.onInstallUpdate)
                Separator(start = SettingsTextEdge)
                SwitchRow(
                    title = stringResource(R.string.settings_updates_at_start),
                    icon = R.drawable.ic_refresh,
                    description = stringResource(R.string.settings_updates_at_start_hint),
                    checked = state.checkUpdatesAtStart,
                    onCheckedChange = actions.onCheckUpdatesAtStartChange,
                )
                Separator(start = SettingsTextEdge)
                SettingsRow(
                    title = stringResource(R.string.settings_licenses),
                    icon = R.drawable.ic_document,
                    onClick = actions.onOpenLicenses,
                )
            }
            TonalBox(Modifier.padding(top = SectionGap), contentPadding = PaddingValues()) {
                SettingsRow(
                    title = stringResource(R.string.settings_sign_out),
                    icon = R.drawable.ic_logout,
                    titleColor = AromaTheme.colors.error,
                    chevron = false,
                    onClick = { confirmSignOut = true },
                )
            }
            Signature(Modifier.padding(top = SignatureGap))
        }
    }
    when (sheet) {
        SettingsSheet.Theme -> {
            ChoiceSheet(
                title = stringResource(R.string.settings_theme),
                options = ThemeMode.entries,
                selected = state.themeMode,
                optionLabel = { stringResource(it.labelRes()) },
                onSelect = actions.onThemeModeChange,
                onDismiss = { sheet = null },
            )
        }

        SettingsSheet.HomeSections -> {
            HomeSectionsSheet(state.homeSections, actions.onHomeSectionChange) { sheet = null }
        }

        SettingsSheet.Language -> {
            ChoiceSheet(
                title = stringResource(R.string.settings_language),
                options = AppLanguage.entries,
                selected = state.language,
                optionLabel = { stringResource(it.labelRes()) },
                onSelect = actions.onLanguageChange,
                onDismiss = { sheet = null },
            )
        }

        SettingsSheet.Cooking -> {
            CookingSheet(state, actions, onDismiss = { sheet = null })
        }

        null -> {}
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

/**
 * Who is signed in where: their initial on a 56 dp circle, their name and address, and the server,
 * with a sage dot while it answers.
 */
@Composable
private fun Profile(user: User, server: ServerInfo?, connected: Boolean) {
    val name = user.fullName.ifBlank { user.username }
    val colors = AromaTheme.colors
    TonalBox {
        Row(
            Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name, ProfileAvatarSize)
            Spacer(Modifier.width(Space.s16))
            Column(Modifier.weight(1f)) {
                Text(name, style = AromaTheme.type.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (user.email.isNotBlank()) {
                    Text(user.email, style = AromaTheme.type.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        server?.let {
            Row(Modifier.padding(top = Space.s16), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(StatusDot)
                        .background(if (connected) colors.accent else colors.inkTertiary, CircleShape),
                )
                Spacer(Modifier.width(Space.s8))
                Text(
                    text =
                        stringResource(
                            if (connected) R.string.settings_server_connected else R.string.settings_server_offline,
                            it.version,
                        ),
                    style = AromaTheme.type.meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = it.url.removePrefix("https://").removePrefix("http://"),
                style = AromaTheme.type.meta,
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = StatusDot + Space.s8),
            )
        }
    }
}

/** The dot that says whether the server answers. */
private val StatusDot = 8.dp

/** Aroma's mark over its name, quiet and small in the middle, where the page ends. */
@Composable
private fun Signature(modifier: Modifier = Modifier) {
    val tint = AromaTheme.colors.inkTertiary
    Column(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.aroma_mark_reduced),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(SignatureMark),
        )
        Spacer(Modifier.height(Space.s12))
        Icon(
            painterResource(R.drawable.aroma_wordmark),
            contentDescription = stringResource(R.string.app_name),
            tint = tint,
            modifier = Modifier.height(SignatureName),
        )
    }
}

/** Room above the signature, so it closes the page rather than joins its last box; its mark and its name. */
private val SignatureGap = Space.s64
private val SignatureMark = 40.dp
private val SignatureName = 20.dp

/** A group of settings: its head, then the box that holds its rows. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    GroupHead(title, Modifier.padding(top = SectionGap, bottom = Space.s12))
    TonalBox(contentPadding = PaddingValues(), content = content)
}

/** What Home shows, each part ticked on or off; the sheet stays open until it is put away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeSectionsSheet(shown: Set<HomeSection>, onToggle: (HomeSection, Boolean) -> Unit, onDismiss: () -> Unit) {
    AromaBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.settings_home), sheetState = rememberFullSheetState()) {
        Column(Modifier.padding(bottom = Space.s16)) {
            HomeSection.entries.forEach { section ->
                val on = section in shown
                ChoiceRow(
                    label = stringResource(section.labelRes()),
                    chosen = on,
                    modifier = Modifier.toggleable(value = on, role = Role.Checkbox) { onToggle(section, it) },
                )
            }
        }
    }
}

private fun HomeSection.labelRes(): Int =
    when (this) {
        HomeSection.Today -> R.string.home_today
        HomeSection.Week -> R.string.home_this_week
        HomeSection.Shopping -> R.string.home_shopping_list
        HomeSection.Ideas -> R.string.home_ideas
        HomeSection.Quick -> R.string.home_quick
        HomeSection.Newest -> R.string.home_newest
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
                stringResource(
                    R.string.settings_updates_downloading,
                    update.update.version,
                    (
                        update.progress *
                            100
                    ).toInt(),
                )
            }
        }
    val canCheck = update == UpdateState.Unknown || update == UpdateState.Current || update == UpdateState.Failed
    SettingsRow(
        title = stringResource(R.string.settings_version, version),
        icon = R.drawable.ic_info,
        description = status,
        onClick = onCheck.takeIf { canCheck },
        chevron = false,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        trailing =
            when (update) {
                // Checking and downloading say so in the row's second line; a spinner is only ever a button's.
                is UpdateState.Available -> {
                    { SecondaryButton(stringResource(R.string.settings_updates_install), onClick = { onInstall(update) }, compact = true) }
                }

                else -> {
                    null
                }
            },
    )
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
