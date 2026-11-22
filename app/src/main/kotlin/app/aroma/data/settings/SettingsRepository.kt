package app.aroma.data.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { System, Light, Dark }

enum class AppLanguage(val tag: String?) { System(null), German("de"), English("en") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val keepScreenOn: Boolean = true,
    val offlineRecipes: Boolean = false,
    val lastShoppingList: String? = null,
    val importWithAi: Boolean = true,
    val translateImports: Boolean = false,
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val offlineRecipes = booleanPreferencesKey("offline_recipes")
        val lastShoppingList = stringPreferencesKey("last_shopping_list")
        val importWithAi = booleanPreferencesKey("import_with_ai")
        val translateImports = booleanPreferencesKey("translate_imports")
    }

    val settings: Flow<AppSettings> =
        dataStore.data.map { prefs ->
            AppSettings(
                themeMode = prefs[Keys.theme]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System,
                dynamicColor = prefs[Keys.dynamicColor] ?: false,
                keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
                offlineRecipes = prefs[Keys.offlineRecipes] ?: false,
                lastShoppingList = prefs[Keys.lastShoppingList],
                importWithAi = prefs[Keys.importWithAi] ?: true,
                translateImports = prefs[Keys.translateImports] ?: false,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[Keys.theme] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = dataStore.edit { it[Keys.dynamicColor] = enabled }

    suspend fun setKeepScreenOn(enabled: Boolean) = dataStore.edit { it[Keys.keepScreenOn] = enabled }

    suspend fun setOfflineRecipes(enabled: Boolean) = dataStore.edit { it[Keys.offlineRecipes] = enabled }

    /** Forgets what belonged to the signed-out account; look and feel stay as they are. */
    suspend fun forgetAccount() =
        dataStore.edit {
            it.remove(Keys.offlineRecipes)
            it.remove(Keys.lastShoppingList)
        }

    suspend fun setImportWithAi(enabled: Boolean) = dataStore.edit { it[Keys.importWithAi] = enabled }

    suspend fun setTranslateImports(enabled: Boolean) = dataStore.edit { it[Keys.translateImports] = enabled }

    suspend fun setLastShoppingList(id: String?) =
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.lastShoppingList) else prefs[Keys.lastShoppingList] = id
        }

    /** The app language is stored by AppCompat so it also applies before the first screen is drawn. */
    val language: AppLanguage
        get() {
            val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            return AppLanguage.entries.firstOrNull { it.tag != null && tag.startsWith(it.tag) } ?: AppLanguage.System
        }

    fun setLanguage(language: AppLanguage) {
        val locales = language.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
