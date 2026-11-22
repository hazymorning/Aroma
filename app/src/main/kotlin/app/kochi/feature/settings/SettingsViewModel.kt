package app.kochi.feature.settings

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kochi.BuildConfig
import app.kochi.core.model.ServerInfo
import app.kochi.core.model.User
import app.kochi.data.repository.AuthRepository
import app.kochi.data.repository.RecipeRepository
import app.kochi.data.session.SessionManager
import app.kochi.data.settings.AppLanguage
import app.kochi.data.settings.SettingsRepository
import app.kochi.data.settings.ThemeMode
import app.kochi.data.sync.OfflineRecipesWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appContext: Context,
    private val settings: SettingsRepository,
    private val auth: AuthRepository,
    sessions: SessionManager,
    recipes: RecipeRepository,
) : ViewModel() {
    private val language = MutableStateFlow(settings.language)

    private val account =
        sessions.current?.let { session ->
            User(session.userId, session.username, session.fullName, session.email, session.isAdmin) to
                ServerInfo(session.serverUrl, session.serverVersion, session.allowPasswordLogin)
        }

    val state: StateFlow<SettingsUiState> =
        combine(settings.settings, language, recipes.offlineCount()) { app, lang, offline ->
            SettingsUiState(
                user = account?.first,
                server = account?.second,
                themeMode = app.themeMode,
                dynamicColor = app.dynamicColor,
                dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                language = lang,
                keepScreenOn = app.keepScreenOn,
                offlineRecipes = app.offlineRecipes,
                offlineCount = offline,
                version = BuildConfig.VERSION_NAME,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(user = account?.first, server = account?.second))

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }

    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { settings.setKeepScreenOn(enabled) }

    fun setOfflineRecipes(enabled: Boolean) =
        viewModelScope.launch {
            settings.setOfflineRecipes(enabled)
            OfflineRecipesWorker.schedule(appContext, enabled)
        }

    fun setLanguage(value: AppLanguage) {
        language.value = value
        settings.setLanguage(value)
    }

    fun signOut() =
        viewModelScope.launch {
            OfflineRecipesWorker.schedule(appContext, enabled = false)
            auth.signOut()
        }
}
