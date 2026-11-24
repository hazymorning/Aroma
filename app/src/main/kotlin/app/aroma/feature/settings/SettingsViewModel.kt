package app.aroma.feature.settings

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aroma.BuildConfig
import app.aroma.core.format.MeasureSystem
import app.aroma.core.model.ServerInfo
import app.aroma.core.model.User
import app.aroma.data.repository.RecipeRepository
import app.aroma.data.session.SessionManager
import app.aroma.data.settings.AppLanguage
import app.aroma.data.settings.IngredientsInSteps
import app.aroma.data.settings.SettingsRepository
import app.aroma.data.settings.ThemeMode
import app.aroma.data.sync.OfflineRecipesWorker
import app.aroma.data.update.UpdateState
import app.aroma.data.update.Updates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appContext: Context,
    private val settings: SettingsRepository,
    sessions: SessionManager,
    recipes: RecipeRepository,
    private val updates: Updates,
    private val signOutAndForget: () -> Unit,
) : ViewModel() {
    private val language = MutableStateFlow(settings.language)

    private val account =
        sessions.current?.let { session ->
            User(session.userId, session.username, session.fullName, session.email) to
                ServerInfo(session.serverUrl, session.serverVersion, session.allowPasswordLogin)
        }

    val state: StateFlow<SettingsUiState> =
        combine(settings.settings, language, recipes.offlineCount(), updates.state) { app, lang, offline, update ->
            SettingsUiState(
                user = account?.first,
                server = account?.second,
                themeMode = app.themeMode,
                dynamicColor = app.dynamicColor,
                dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                language = lang,
                keepScreenOn = app.keepScreenOn,
                ingredientsInSteps = app.ingredientsInSteps,
                amountsInSteps = app.amountsInSteps,
                measures = app.measureSystem,
                offlineRecipes = app.offlineRecipes,
                offlineCount = offline,
                version = BuildConfig.VERSION_NAME,
                update = update,
                checkUpdatesAtStart = app.checkUpdatesAtStart,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(user = account?.first, server = account?.second))

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }

    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { settings.setKeepScreenOn(enabled) }

    fun setIngredientsInSteps(shown: IngredientsInSteps) = viewModelScope.launch { settings.setIngredientsInSteps(shown) }

    fun setAmountsInSteps(enabled: Boolean) = viewModelScope.launch { settings.setAmountsInSteps(enabled) }

    fun setMeasures(system: MeasureSystem) = viewModelScope.launch { settings.setMeasureSystem(system) }

    fun setOfflineRecipes(enabled: Boolean) =
        viewModelScope.launch {
            settings.setOfflineRecipes(enabled)
            OfflineRecipesWorker.schedule(appContext, enabled)
        }

    fun setCheckUpdatesAtStart(enabled: Boolean) = viewModelScope.launch { settings.setCheckUpdatesAtStart(enabled) }

    fun checkForUpdates() = updates.checkNow()

    fun installUpdate(update: UpdateState.Available) = updates.install(update)

    fun setLanguage(value: AppLanguage) {
        language.value = value
        settings.setLanguage(value)
    }

    fun signOut() = signOutAndForget()
}
