package app.aroma.screenshots

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.aroma.core.model.ServerInfo
import app.aroma.core.model.ShoppingListInfo
import app.aroma.core.model.User
import app.aroma.feature.cook.CookActions
import app.aroma.feature.cook.CookScreen
import app.aroma.feature.cook.CookTimerUi
import app.aroma.feature.cook.CookUiState
import app.aroma.feature.cookbook.CookbookScreen
import app.aroma.feature.editor.AddMode
import app.aroma.feature.editor.AddRecipeActions
import app.aroma.feature.editor.AddRecipeScreen
import app.aroma.feature.editor.AddRecipeUiState
import app.aroma.feature.editor.ImportStep
import app.aroma.feature.home.Greeting
import app.aroma.feature.home.HomeActions
import app.aroma.feature.home.HomeScreen
import app.aroma.feature.home.HomeUiState
import app.aroma.feature.home.RateReminder
import app.aroma.feature.login.LoginActions
import app.aroma.feature.login.LoginScreen
import app.aroma.feature.login.LoginStep
import app.aroma.feature.login.LoginUiState
import app.aroma.feature.mealplan.MealPlanActions
import app.aroma.feature.mealplan.MealPlanDay
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
open class ScreensScreenshotTest(private val variant: Variant) : ScreenshotTest() {
    data class Variant(val locale: String, val dark: Boolean) {
        val suffix = "${if (dark) "dark" else "light"}_$locale"

        override fun toString() = suffix
    }

    private val sample = SampleData(german = variant.locale == "de")

    /** Height of the rendered window; the default matches a typical phone. */
    protected open val screenHeightDp = 891

    @Test
    fun home() {
        applyLocale()
        snapshot("home_${variant.suffix}", variant.dark) {
            HomeScreen(
                state =
                    HomeUiState(
                        greeting = Greeting.Evening,
                        firstName = "Mara",
                        today = sample.mealPlan.take(1),
                        recent = sample.recipes,
                        favorites = sample.recipes.reversed(),
                        cookbooks = sample.cookbooks,
                        toRate = sample.recipes.first().let { RateReminder(it.id, it.slug, it.name, it.image) },
                        isLoading = false,
                    ),
                actions = HomeActions(),
            )
        }
    }

    @Test
    fun recipe() {
        applyLocale()
        snapshot("recipe_${variant.suffix}", variant.dark) {
            RecipeScreen(
                state = RecipeUiState(recipe = sample.recipe, isLoading = false, isFavorite = true, checkedIngredients = setOf(1)),
                actions = RecipeActions(),
            )
        }
    }

    @Test
    fun recipes() {
        applyLocale()
        snapshot("recipes_${variant.suffix}", variant.dark) {
            RecipesScreen(
                state =
                    RecipesUiState(
                        recipes = sample.recipes,
                        total = sample.recipes.size,
                        isLoading = false,
                        categories = sample.recipe.categories,
                        tags = sample.recipe.tags,
                        tools = sample.recipe.tools,
                        filter =
                            RecipeFilter(
                                tags =
                                    sample.recipe.tags
                                        .take(1)
                                        .toSet(),
                            ),
                    ),
                actions = RecipesActions(),
            )
        }
    }

    @Test
    fun mealPlan() {
        applyLocale()
        val days =
            (0L until 7L).map { offset ->
                val day = sample.today.plusDays(offset)
                MealPlanDay(day, sample.mealPlan.filter { it.date == day })
            }
        snapshot("mealplan_${variant.suffix}", variant.dark) {
            MealPlanScreen(
                state = MealPlanUiState(today = sample.today, weekStart = sample.today, days = days, isLoading = false),
                actions = MealPlanActions(),
            )
        }
    }

    @Test
    fun shopping() {
        applyLocale()
        snapshot("shopping_${variant.suffix}", variant.dark) {
            ShoppingScreen(
                state =
                    ShoppingUiState(
                        lists =
                            listOf(
                                ShoppingListInfo(sample.shoppingList.id, sample.shoppingList.name),
                                ShoppingListInfo("2", "Drogerie"),
                            ),
                        list = sample.shoppingList,
                        isLoading = false,
                    ),
                actions = ShoppingActions(),
            )
        }
    }

    @Test
    fun shoppingMode() {
        applyLocale()
        val toggle = if (variant.locale == "de") "Einkaufsmodus starten" else "Start shopping mode"
        snapshot("shopping_mode_${variant.suffix}", variant.dark, interact = {
            composeRule.onNodeWithContentDescription(toggle).performClick()
        }) {
            ShoppingScreen(
                state =
                    ShoppingUiState(
                        lists = listOf(ShoppingListInfo(sample.shoppingList.id, sample.shoppingList.name)),
                        list = sample.shoppingList,
                        isLoading = false,
                    ),
                actions = ShoppingActions(),
            )
        }
    }

    @Test
    fun cookStep() {
        applyLocale()
        snapshot("cook_${variant.suffix}", variant.dark) {
            CookScreen(
                state =
                    CookUiState(
                        recipe = sample.recipe,
                        isLoading = false,
                        timers = listOf(CookTimerUi(1, "Schritt 2", remainingSeconds = 432, isRunning = true)),
                    ),
                actions = CookActions(),
                initialPage = 1,
            )
        }
    }

    @Test
    fun cookFinished() {
        applyLocale()
        snapshot("cookdone_${variant.suffix}", variant.dark) {
            CookScreen(
                state = CookUiState(recipe = sample.recipe, isLoading = false, rating = 4.0),
                actions = CookActions(),
                initialPage = sample.recipe.steps.size,
            )
        }
    }

    @Test
    fun loginServer() {
        applyLocale()
        snapshot("login_${variant.suffix}", variant.dark) {
            LoginScreen(state = LoginUiState(serverInput = "mealie.example.com"), actions = LoginActions())
        }
    }

    @Test
    fun loginCredentials() {
        applyLocale()
        snapshot("login2_${variant.suffix}", variant.dark) {
            LoginScreen(
                state =
                    LoginUiState(
                        step = LoginStep.Credentials,
                        server = ServerInfo("https://mealie.example.com", "3.31.0", allowPasswordLogin = true),
                        username = "mara@example.com",
                        password = "secret",
                    ),
                actions = LoginActions(),
            )
        }
    }

    @Test
    fun settings() {
        applyLocale()
        snapshot("settings_${variant.suffix}", variant.dark) {
            SettingsScreen(
                state =
                    SettingsUiState(
                        user = User("1", "mara", "Mara Berger", "mara@example.com", isAdmin = false),
                        server = ServerInfo("https://mealie.example.com", "3.31.0", true),
                        dynamicColorAvailable = true,
                        offlineCount = 18,
                        version = "0.1.0",
                    ),
                actions = SettingsActions(),
            )
        }
    }

    @Test
    fun addRecipe() {
        applyLocale()
        snapshot("add_${variant.suffix}", variant.dark) {
            AddRecipeScreen(state = AddRecipeUiState(url = "https://example.com/leek-fritters"), actions = AddRecipeActions())
        }
    }

    @Test
    fun addRecipeWithAi() {
        applyLocale()
        snapshot("add_ai_${variant.suffix}", variant.dark) {
            AddRecipeScreen(
                state =
                    AddRecipeUiState(
                        url = "https://www.instagram.com/p/AromaTest01/",
                        aiAvailable = true,
                        text =
                            if (variant.locale ==
                                "de"
                            ) {
                                "Zutaten: 500 g Gnocchi, 250 g Kirschtomaten …"
                            } else {
                                "Ingredients: 500 g gnocchi, 250 g cherry tomatoes …"
                            },
                    ),
                actions = AddRecipeActions(),
            )
        }
    }

    @Test
    fun addRecipeImporting() {
        applyLocale()
        snapshot("add_importing_${variant.suffix}", variant.dark) {
            AddRecipeScreen(
                state =
                    AddRecipeUiState(
                        url = "https://www.instagram.com/p/AromaTest01/",
                        aiAvailable = true,
                        working = AddMode.Import,
                        step = ImportStep.Server(if (variant.locale == "de") "Lese Quelle mit KI..." else "Reading source with AI..."),
                    ),
                actions = AddRecipeActions(),
            )
        }
    }

    @Test
    fun cookbook() {
        applyLocale()
        snapshot("cookbook_${variant.suffix}", variant.dark) {
            CookbookScreen(
                name = sample.cookbooks.first().name,
                description = sample.cookbooks.first().description,
                state = RecipesUiState(recipes = sample.recipes.take(4), isLoading = false),
                onBack = {},
                onOpenRecipe = {},
                onLoadMore = {},
                onRetry = {},
            )
        }
    }

    @Test
    fun emptyShopping() {
        applyLocale()
        snapshot("shopping_empty_${variant.suffix}", variant.dark) {
            ShoppingScreen(state = ShoppingUiState(isLoading = false), actions = ShoppingActions())
        }
    }

    private fun applyLocale() {
        org.robolectric.RuntimeEnvironment.setQualifiers("${variant.locale}-w411dp-h${screenHeightDp}dp-xxhdpi")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants() =
            listOf(
                Variant("de", dark = false),
                Variant("de", dark = true),
                Variant("en", dark = false),
                Variant("en", dark = true),
            )
    }
}
