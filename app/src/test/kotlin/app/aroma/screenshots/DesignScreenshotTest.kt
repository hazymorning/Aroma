package app.aroma.screenshots

import androidx.compose.runtime.Composable
import app.aroma.core.model.Meal
import app.aroma.core.model.ServerInfo
import app.aroma.core.model.ShoppingList
import app.aroma.core.model.ShoppingListInfo
import app.aroma.core.model.User
import app.aroma.feature.cook.CookActions
import app.aroma.feature.cook.CookScreen
import app.aroma.feature.cook.CookUiState
import app.aroma.feature.home.Greeting
import app.aroma.feature.home.HomeActions
import app.aroma.feature.home.HomeScreen
import app.aroma.feature.home.HomeUiState
import app.aroma.feature.mealplan.MealPlanActions
import app.aroma.feature.mealplan.MealPlanScreen
import app.aroma.feature.mealplan.MealPlanUiState
import app.aroma.feature.recipe.RecipeActions
import app.aroma.feature.recipe.RecipeScreen
import app.aroma.feature.recipe.RecipeUiState
import app.aroma.feature.recipes.RecipeFilter
import app.aroma.feature.recipes.RecipesActions
import app.aroma.feature.recipes.RecipesScreen
import app.aroma.feature.recipes.RecipesUiState
import app.aroma.feature.settings.SettingsActions
import app.aroma.feature.settings.SettingsScreen
import app.aroma.feature.settings.SettingsUiState
import app.aroma.feature.shopping.ShoppingActions
import app.aroma.feature.shopping.ShoppingScreen
import app.aroma.feature.shopping.ShoppingUiState
import app.aroma.ui.navigation.Tab
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

/**
 * The screens of the design round with the brief's test texts (section 14), light and dark, at
 * 100, 130 and 200 % font size: full, bare and extreme data. Pages that scroll are also rendered
 * whole, in a window tall enough to hold them. The images land in build/outputs/roborazzi/kochbuch.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "de-w411dp-h891dp-xxhdpi")
class DesignScreenshotTest(private val look: Look) : ScreenshotTest() {
    data class Look(val dark: Boolean, val scale: Float) {
        val suffix = "${if (dark) "dunkel" else "hell"}_${(scale * PERCENT).roundToInt()}"

        override fun toString() = suffix
    }

    private fun shoot(name: String, height: Int = PHONE_HEIGHT, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("de-w411dp-h${height}dp-xxhdpi")
        RuntimeEnvironment.setFontScale(look.scale)
        snapshot("kochbuch/${name}_${look.suffix}", look.dark, content = content)
    }

    private fun recipe(state: RecipeUiState): @Composable () -> Unit = { RecipeScreen(state, RecipeActions()) }

    /** Everything a recipe can have, saved, with 30 ingredients in four groups. */
    @Test
    fun recipeFull() =
        shoot("rezept_voll", content = recipe(RecipeUiState(recipe = KochbuchSamples.full, isLoading = false, isFavorite = true)))

    @Test
    fun recipeFullWhole() =
        shoot(
            "rezept_voll_ganz",
            height = WHOLE_PAGE,
            content = recipe(RecipeUiState(recipe = KochbuchSamples.full, isLoading = false, isFavorite = true)),
        )

    /** Nothing but a name: no photo, times, headnote or rating. */
    @Test
    fun recipeBare() = shoot("rezept_leer", content = recipe(RecipeUiState(recipe = KochbuchSamples.bare, isLoading = false)))

    /** The longest title, a yield of "1 Blech" and times as free text. */
    @Test
    fun recipeExtreme() = shoot("rezept_extrem", content = recipe(RecipeUiState(recipe = KochbuchSamples.longTitle, isLoading = false)))

    @Test
    fun recipeExtremeWhole() =
        shoot(
            "rezept_extrem_ganz",
            height = WHOLE_PAGE,
            content = recipe(RecipeUiState(recipe = KochbuchSamples.longTitle, isLoading = false)),
        )

    @Test
    fun recipeLoading() = shoot("rezept_laden", content = recipe(RecipeUiState(isLoading = true)))

    private fun home(state: HomeUiState): @Composable () -> Unit = { TabFrame(Tab.Home) { HomeScreen(state, HomeActions()) } }

    private val homeFull =
        HomeUiState(
            greeting = Greeting.Evening,
            firstName = "Mara",
            date = KochbuchWeek.today,
            week = KochbuchWeek.entries,
            meal = Meal.Dinner,
            suggestions = KochbuchSamples.summaries,
            suggestionsForMeal = true,
            quick = SampleData(german = true).recipes,
            recent = SampleData(german = true).recipes.reversed(),
            openItems = 12,
            isLoading = false,
        )

    /** Today's dinner with the longest title, lunch below, the week, the list and all three kinds of inspiration. */
    @Test
    fun homeFull() = shoot("start_voll", content = home(homeFull))

    @Test
    fun homeFullWhole() = shoot("start_voll_ganz", height = WHOLE_PAGE, content = home(homeFull))

    /** Nothing planned and nothing to buy: the suggestions to plan for today, and no more. */
    @Test
    fun homeEmpty() =
        shoot(
            "start_leer",
            content =
                home(
                    HomeUiState(
                        greeting = Greeting.Morning,
                        firstName = "Mara",
                        date = KochbuchWeek.today,
                        meal = Meal.Breakfast,
                        suggestions = KochbuchSamples.summaries.take(3),
                        isLoading = false,
                    ),
                ),
        )

    /** A long name in the greeting, and the rate prompt after cooking. */
    @Test
    fun homeExtreme() =
        shoot(
            "start_extrem",
            content =
                home(
                    homeFull.copy(
                        firstName = "Maximiliane",
                        greeting = Greeting.Afternoon,
                        toRate =
                            KochbuchSamples.summaries[5].let {
                                app.aroma.feature.home.RateReminder(
                                    it.id,
                                    it.slug,
                                    it.name,
                                    it.image,
                                )
                            },
                    ),
                ),
        )

    private fun plan(state: MealPlanUiState): @Composable () -> Unit =
        { TabFrame(Tab.MealPlan) { MealPlanScreen(state, MealPlanActions()) } }

    /** The week under way: Monday folded away, today on its surface, empty days with their link, the week's ingredients. */
    @Test
    fun planFull() =
        shoot(
            "wochenplan_voll",
            content =
                plan(
                    MealPlanUiState(
                        today = KochbuchWeek.today,
                        weekStart = KochbuchWeek.monday,
                        days = KochbuchWeek.days,
                        weekItems = 46,
                        isLoading = false,
                    ),
                ),
        )

    @Test
    fun planFullWhole() =
        shoot(
            "wochenplan_voll_ganz",
            height = WHOLE_PAGE,
            content =
                plan(
                    MealPlanUiState(
                        today = KochbuchWeek.today,
                        weekStart = KochbuchWeek.monday,
                        days = KochbuchWeek.days,
                        weekItems = 46,
                        isLoading = false,
                    ),
                ),
        )

    /** Thursday: Monday to Wednesday folded into one line above today. */
    @Test
    fun planFolded() =
        shoot(
            "wochenplan_gefaltet",
            content =
                plan(
                    MealPlanUiState(
                        today = KochbuchWeek.monday.plusDays(3),
                        weekStart = KochbuchWeek.monday,
                        days = KochbuchWeek.days,
                        weekItems = 46,
                        isLoading = false,
                    ),
                ),
        )

    /** A week with nothing planned yet. */
    @Test
    fun planEmpty() =
        shoot(
            "wochenplan_leer",
            content =
                plan(
                    MealPlanUiState(
                        today = KochbuchWeek.today,
                        weekStart = KochbuchWeek.monday,
                        days = KochbuchWeek.emptyDays,
                        isLoading = false,
                    ),
                ),
        )

    /** The week across the turn of the month, with the link back to this week. */
    @Test
    fun planExtreme() =
        shoot(
            "wochenplan_extrem",
            content =
                plan(
                    MealPlanUiState(
                        today = KochbuchWeek.today,
                        weekStart = KochbuchWeek.nextMonday,
                        days = KochbuchWeek.turnOfMonth,
                        isLoading = false,
                    ),
                ),
        )

    private fun recipes(state: RecipesUiState): @Composable () -> Unit =
        { TabFrame(Tab.Recipes) { RecipesScreen(state, RecipesActions()) } }

    /** The library with the test titles, two of them saved. */
    @Test
    fun recipesFull() =
        shoot(
            "rezepte_voll",
            content =
                recipes(
                    RecipesUiState(
                        recipes = KochbuchSamples.summaries,
                        total = KochbuchSamples.summaries.size,
                        saved = setOf("k0", "k5"),
                        isLoading = false,
                    ),
                ),
        )

    /** A search: what it finds as rows. */
    @Test
    fun recipesSearch() =
        shoot(
            "rezepte_suche",
            content =
                recipes(
                    RecipesUiState(
                        filter = RecipeFilter(query = "Blech"),
                        recipes = KochbuchSamples.summaries.take(3),
                        total = 3,
                        isLoading = false,
                    ),
                ),
        )

    /** A search that finds nothing. */
    @Test
    fun recipesEmpty() =
        shoot(
            "rezepte_leer",
            content =
                recipes(
                    RecipesUiState(filter = RecipeFilter(query = "Zwetschgendatschi"), recipes = emptyList(), total = 0, isLoading = false),
                ),
        )

    private fun shopping(list: ShoppingList): @Composable () -> Unit =
        {
            TabFrame(Tab.Shopping) {
                ShoppingScreen(
                    ShoppingUiState(lists = listOf(ShoppingListInfo(list.id, list.name)), list = list, isLoading = false),
                    ShoppingActions(),
                )
            }
        }

    /** Forty things to buy in four groups and some without, three ticked off. */
    @Test
    fun shoppingFull() = shoot("einkauf_voll", content = shopping(KochbuchWeek.longList))

    @Test
    fun shoppingFullWhole() = shoot("einkauf_voll_ganz", height = WHOLE_PAGE, content = shopping(KochbuchWeek.longList))

    @Test
    fun shoppingEmpty() = shoot("einkauf_leer", content = shopping(KochbuchWeek.emptyShoppingList))

    /** Cook mode on the step with a title, of the recipe with 30 ingredients. */
    @Test
    fun cook() =
        shoot(
            "kochmodus",
            content = { CookScreen(CookUiState(recipe = KochbuchSamples.full, isLoading = false), CookActions(), initialPage = 2) },
        )

    /** Cook mode on the last step of a recipe with nothing but a name. */
    @Test
    fun cookBare() =
        shoot("kochmodus_leer", content = {
            CookScreen(CookUiState(recipe = KochbuchSamples.bare, isLoading = false), CookActions())
        })

    /** The settings, signed in and connected. */
    @Test
    fun settings() =
        shoot(
            "einstellungen",
            content = {
                SettingsScreen(
                    SettingsUiState(
                        user = User("1", "mara", "Mara Berger", "mara@example.com"),
                        server = ServerInfo("https://mealie.example.com", "3.31.0", true),
                        dynamicColorAvailable = true,
                        offlineCount = 18,
                        version = "0.7.0",
                    ),
                    SettingsActions(),
                )
            },
        )

    @Test
    fun settingsWhole() =
        shoot(
            "einstellungen_ganz",
            height = WHOLE_PAGE,
            content = {
                SettingsScreen(
                    SettingsUiState(
                        user = User("1", "mara", "Maximiliane Berger-Schönfeld", "maximiliane.berger-schoenfeld@example.com"),
                        server = ServerInfo("https://mealie.example.com", "3.31.0", true),
                        dynamicColorAvailable = true,
                        offlineCount = 18,
                        version = "0.7.0",
                    ),
                    SettingsActions(),
                )
            },
        )

    companion object {
        private const val PERCENT = 100
        private const val PHONE_HEIGHT = 891
        private const val WHOLE_PAGE = 4200

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks() = listOf(false, true).flatMap { dark -> listOf(1f, 1.3f, 2f).map { Look(dark, it) } }
    }
}
