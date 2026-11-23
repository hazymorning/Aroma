package app.aroma.screenshots

import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import app.aroma.core.model.Meal
import app.aroma.core.model.ServerInfo
import app.aroma.core.model.ShoppingListInfo
import app.aroma.core.model.User
import app.aroma.data.update.UpdateState
import app.aroma.feature.cook.CookActions
import app.aroma.feature.cook.CookScreen
import app.aroma.feature.cook.CookTimerUi
import app.aroma.feature.cook.CookUiState
import app.aroma.feature.editor.AddRecipeActions
import app.aroma.feature.editor.AddRecipeScreen
import app.aroma.feature.editor.AddRecipeUiState
import app.aroma.feature.editor.EditorActions
import app.aroma.feature.editor.EditorForm
import app.aroma.feature.editor.EditorScreen
import app.aroma.feature.editor.EditorUiState
import app.aroma.feature.editor.ImportJob
import app.aroma.feature.editor.ImportRequest
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
import app.aroma.feature.recipe.RecipeActionsSheet
import app.aroma.feature.recipe.RecipeScreen
import app.aroma.feature.recipe.RecipeUiState
import app.aroma.feature.recipes.CollectionActions
import app.aroma.feature.recipes.CollectionKind
import app.aroma.feature.recipes.CollectionScreen
import app.aroma.feature.recipes.CookbookShelf
import app.aroma.feature.recipes.CookbookShelfUiState
import app.aroma.feature.recipes.RecipeFilter
import app.aroma.feature.recipes.RecipesActions
import app.aroma.feature.recipes.RecipesHeader
import app.aroma.feature.recipes.RecipesScreen
import app.aroma.feature.recipes.RecipesSection
import app.aroma.feature.recipes.RecipesUiState
import app.aroma.feature.recipes.Timeline
import app.aroma.feature.recipes.TimelineUiState
import app.aroma.feature.settings.LicensesScreen
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
            TabFrame(Tab.Home) {
                HomeScreen(
                    state =
                        HomeUiState(
                            greeting = Greeting.Evening,
                            firstName = "Mara",
                            photo = "${SampleData.SERVER}/api/media/users/1/profile.webp?cacheKey=1",
                            today = sample.mealPlan.take(1),
                            recent = sample.recipes,
                            favorites = sample.recipes.reversed(),
                            meal = Meal.Dinner,
                            suggestions = sample.recipes.drop(2),
                            suggestionsForMeal = true,
                            cookedLongAgo = sample.recipes.take(3),
                            toRate = sample.recipes.first().let { RateReminder(it.id, it.slug, it.name, it.image) },
                            isLoading = false,
                        ),
                    actions = HomeActions(),
                )
            }
        }
    }

    /** Nothing planned today: the card that says so stands on the page's edge like every other. */
    @Test
    fun homeNothingPlanned() {
        applyLocale()
        snapshot("home_empty_${variant.suffix}", variant.dark) {
            TabFrame(Tab.Home) {
                HomeScreen(
                    state =
                        HomeUiState(
                            greeting = Greeting.Morning,
                            firstName = "Mara",
                            recent = sample.recipes,
                            meal = Meal.Breakfast,
                            suggestions = sample.recipes.take(4),
                            suggestionsForMeal = true,
                            isLoading = false,
                        ),
                    actions = HomeActions(),
                )
            }
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
    fun recipeScrolled() {
        applyLocale()
        snapshot(
            "recipe_scrolled_${variant.suffix}",
            variant.dark,
            interact = { composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("facts") },
        ) {
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
            TabFrame(Tab.Recipes) {
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
            TabFrame(Tab.MealPlan) {
                MealPlanScreen(
                    state = MealPlanUiState(today = sample.today, weekStart = sample.today, days = days, isLoading = false),
                    actions = MealPlanActions(),
                )
            }
        }
    }

    @Test
    fun shopping() {
        applyLocale()
        snapshot("shopping_${variant.suffix}", variant.dark) {
            TabFrame(Tab.Shopping) {
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
                            // Just checked: drawn and struck through, a moment before it moves into the basket.
                            settlingChecks = mapOf(sample.shoppingList.items[1].id to true),
                        ),
                    actions = ShoppingActions(),
                )
            }
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
                        timers =
                            listOf(
                                CookTimerUi(
                                    1,
                                    if (variant.locale ==
                                        "de"
                                    ) {
                                        "Schritt 2"
                                    } else {
                                        "Step 2"
                                    },
                                    remainingSeconds = 432,
                                    isRunning = true,
                                ),
                            ),
                    ),
                actions = CookActions(),
                initialPage = 1,
            )
        }
    }

    @Test
    fun cookStepWithIngredients() {
        applyLocale()
        snapshot("cook_ingredients_${variant.suffix}", variant.dark) {
            CookScreen(
                state =
                    CookUiState(
                        recipe = sample.recipe,
                        isLoading = false,
                        ingredientsInSteps = true,
                        checkedIngredients = setOf(0, 2),
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
                        user = User("1", "mara", "Mara Berger", "mara@example.com"),
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
    fun settingsUpdate() {
        applyLocale()
        snapshot(
            "settings_update_${variant.suffix}",
            variant.dark,
            interact = { composeRule.onNodeWithText("Version 0.3.0").performScrollTo() },
        ) {
            SettingsScreen(
                state =
                    SettingsUiState(
                        version = "0.3.0",
                        update = UpdateState.Available("0.4.0", "", 1),
                        checkUpdatesAtStart = true,
                    ),
                actions = SettingsActions(),
            )
        }
    }

    @Test
    fun licenses() {
        applyLocale()
        snapshot("licenses_${variant.suffix}", variant.dark) { LicensesScreen(onBack = {}) }
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
                        import =
                            ImportJob(
                                request = ImportRequest("https://www.instagram.com/p/AromaTest01/", useAi = true),
                                // Half a second into the 24th, so the clock reads 0:23 however long drawing takes.
                                startedAt = System.currentTimeMillis() - 23_500,
                                steps =
                                    listOf(
                                        ImportStep.ReadingPost,
                                        ImportStep.Waiting,
                                        ImportStep.Server(if (variant.locale == "de") "Rezept erstellen..." else "Creating recipe..."),
                                        ImportStep.Server(
                                            if (variant.locale ==
                                                "de"
                                            ) {
                                                "Rezept wird übersetzt..."
                                            } else {
                                                "Translating recipe..."
                                            },
                                        ),
                                    ),
                            ),
                    ),
                actions = AddRecipeActions(),
            )
        }
    }

    @Test
    fun editor() {
        applyLocale()
        snapshot("editor_${variant.suffix}", variant.dark) {
            EditorScreen(state = editorState(), actions = EditorActions())
        }
    }

    @Test
    fun editorRows() {
        applyLocale()
        val firstStep = sample.recipe.steps[0].text
        snapshot(
            "editor_rows_${variant.suffix}",
            variant.dark,
            interact = { composeRule.onNodeWithText(firstStep).performScrollTo() },
        ) {
            EditorScreen(state = editorState(), actions = EditorActions())
        }
    }

    private fun editorState(): EditorUiState {
        val recipe = sample.recipe
        val german = variant.locale == "de"
        return EditorUiState(
            form =
                EditorForm(
                    name = recipe.name,
                    description = recipe.description,
                    servings = "4",
                    prepMinutes = "10",
                    cookMinutes = "15",
                    totalMinutes = "25",
                    ingredients =
                        if (german) {
                            "# Für den Teig\n250 g Mehl\n2 EL Zucker\n1 ½ TL Backpulver\n1 Prise Salz\n2 Eier\n300 ml Milch"
                        } else {
                            "# For the batter\n250 g flour\n2 tbsp sugar\n1 ½ tsp baking powder\n1 pinch salt\n2 eggs\n300 ml milk"
                        },
                    steps = recipe.steps.map { it.text },
                    categories = recipe.categories,
                    tags = recipe.tags,
                ),
            allCategories = recipe.categories,
            allTags = recipe.tags + recipe.tools,
            currentImage = recipe.image,
            isLoading = false,
        )
    }

    @Test
    fun cookbook() {
        applyLocale()
        snapshot("cookbook_${variant.suffix}", variant.dark) {
            CollectionScreen(
                kind = CollectionKind.Cookbook,
                name = sample.cookbooks.first().name,
                description = sample.cookbooks.first().description,
                state = RecipesUiState(recipes = sample.recipes.take(4), total = 4, isLoading = false),
                actions = CollectionActions(),
            )
        }
    }

    @Test
    fun cookbookShelf() {
        applyLocale()
        snapshot("cookbooks_${variant.suffix}", variant.dark) {
            TabFrame(Tab.Recipes) {
                CookbookShelf(
                    state = CookbookShelfUiState(sample.cookbookCovers, isLoading = false),
                    header = { RecipesHeader(RecipesSection.Cookbooks, onSection = {}, onAddRecipe = {}) },
                    onOpen = {},
                    onRetry = {},
                )
            }
        }
    }

    @Test
    fun timeline() {
        applyLocale()
        snapshot("timeline_${variant.suffix}", variant.dark) {
            TabFrame(Tab.Recipes) {
                Timeline(
                    state = TimelineUiState(sample.timeline, isLoading = false),
                    header = { RecipesHeader(RecipesSection.Timeline, onSection = {}, onAddRecipe = {}) },
                    onOpenRecipe = {},
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }
    }

    @Test
    fun recipeMenu() {
        applyLocale()
        snapshot("recipe_menu_${variant.suffix}", variant.dark) {
            TabFrame(Tab.Recipes) {
                RecipesScreen(
                    state = RecipesUiState(recipes = sample.recipes, total = sample.recipes.size, isLoading = false),
                    actions = RecipesActions(),
                )
            }
            RecipeActionsSheet(
                recipe = sample.recipes[1],
                isFavorite = true,
                onCook = {},
                onPlan = {},
                onShop = {},
                onToggleFavorite = {},
                onShare = {},
                onDismiss = {},
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
