package app.aroma.data.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.aroma.core.format.MeasureSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { System, Light, Dark }

enum class AppLanguage(val tag: String?) { System(null), German("de"), English("en") }

/** Which ingredients a step shows in cook mode and on the recipe page: none, those it uses, or all to tick off. */
enum class IngredientsInSteps { None, Used, All }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val keepScreenOn: Boolean = true,
    val ingredientsInSteps: IngredientsInSteps = IngredientsInSteps.Used,
    val amountsInSteps: Boolean = false,
    val offlineRecipes: Boolean = false,
    val lastShoppingList: String? = null,
    val importWithAi: Boolean = true,
    val translateImports: Boolean = false,
    val checkUpdatesAtStart: Boolean = false,
    val measureSystem: MeasureSystem = MeasureSystem.AsWritten,
    val homeSections: Set<HomeSection> = HomeSection.SHOWN_BY_DEFAULT,
)

/** What Home can show, in its order. The shopping list is left out until someone asks for it. */
enum class HomeSection {
    Today,
    Week,
    Shopping,
    Ideas,
    Quick,
    Newest,
    ;

    companion object {
        val SHOWN_BY_DEFAULT: Set<HomeSection> = entries.toSet() - Shopping
    }
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val ingredientsInSteps = stringPreferencesKey("ingredients_in_steps_shown")

        /** The switch that showed all ingredients under every step, before there was a choice. */
        val allIngredientsInSteps = booleanPreferencesKey("ingredients_in_steps")
        val amountsInSteps = booleanPreferencesKey("amounts_in_steps")
        val offlineRecipes = booleanPreferencesKey("offline_recipes")
        val lastShoppingList = stringPreferencesKey("last_shopping_list")
        val importWithAi = booleanPreferencesKey("import_with_ai")
        val translateImports = booleanPreferencesKey("translate_imports")
        val checkUpdatesAtStart = booleanPreferencesKey("check_updates_at_start")
        val measureSystem = stringPreferencesKey("measure_system")
        val homeSections = stringSetPreferencesKey("home_sections")
    }

    val settings: Flow<AppSettings> =
        dataStore.data.map { prefs ->
            AppSettings(
                themeMode = prefs[Keys.theme]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System,
                dynamicColor = prefs[Keys.dynamicColor] ?: false,
                keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
                ingredientsInSteps =
                    IngredientsInSteps.entries.firstOrNull { it.name == prefs[Keys.ingredientsInSteps] }
                        ?: if (prefs[Keys.allIngredientsInSteps] == true) IngredientsInSteps.All else IngredientsInSteps.Used,
                amountsInSteps = prefs[Keys.amountsInSteps] ?: false,
                offlineRecipes = prefs[Keys.offlineRecipes] ?: false,
                lastShoppingList = prefs[Keys.lastShoppingList],
                importWithAi = prefs[Keys.importWithAi] ?: true,
                translateImports = prefs[Keys.translateImports] ?: false,
                checkUpdatesAtStart = prefs[Keys.checkUpdatesAtStart] ?: false,
                measureSystem = MeasureSystem.entries.firstOrNull { it.name == prefs[Keys.measureSystem] } ?: MeasureSystem.AsWritten,
                homeSections = homeSections(prefs),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.edit { it[Keys.theme] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = dataStore.edit { it[Keys.dynamicColor] = enabled }

    suspend fun setKeepScreenOn(enabled: Boolean) = dataStore.edit { it[Keys.keepScreenOn] = enabled }

    suspend fun setIngredientsInSteps(shown: IngredientsInSteps) = dataStore.edit { it[Keys.ingredientsInSteps] = shown.name }

    suspend fun setAmountsInSteps(enabled: Boolean) = dataStore.edit { it[Keys.amountsInSteps] = enabled }

    suspend fun setOfflineRecipes(enabled: Boolean) = dataStore.edit { it[Keys.offlineRecipes] = enabled }

    suspend fun setCheckUpdatesAtStart(enabled: Boolean) = dataStore.edit { it[Keys.checkUpdatesAtStart] = enabled }

    suspend fun setMeasureSystem(system: MeasureSystem) = dataStore.edit { it[Keys.measureSystem] = system.name }

    suspend fun setHomeSection(section: HomeSection, shown: Boolean) =
        dataStore.edit { prefs ->
            val sections = homeSections(prefs).let { if (shown) it + section else it - section }
            prefs[Keys.homeSections] = sections.map { it.name }.toSet()
        }

    private fun homeSections(prefs: Preferences): Set<HomeSection> =
        prefs[Keys.homeSections]?.mapNotNull { name -> HomeSection.entries.firstOrNull { it.name == name } }?.toSet()
            ?: HomeSection.SHOWN_BY_DEFAULT

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

/**
 * This context in the language chosen in Aroma's settings. Before Android 13 only screens follow
 * that choice by themselves; notifications, built from the app's context, need this.
 */
fun Context.inAppLanguage(): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return this
    val configuration = Configuration(resources.configuration).apply { setLocales(LocaleList.forLanguageTags(locales.toLanguageTags())) }
    return createConfigurationContext(configuration)
}
