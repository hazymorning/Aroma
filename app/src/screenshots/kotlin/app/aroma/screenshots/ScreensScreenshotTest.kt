package app.aroma.screenshots

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.aroma.core.format.MeasureSystem
import app.aroma.core.model.Filing
import app.aroma.core.model.Meal
import app.aroma.core.model.Organizer
import app.aroma.core.model.Organizers
import app.aroma.core.model.Recipe
import app.aroma.core.model.ServerInfo
import app.aroma.core.model.ShoppingList
import app.aroma.core.model.ShoppingListInfo
import app.aroma.core.model.User
import app.aroma.data.AromaError
import app.aroma.data.settings.IngredientsInSteps
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
import app.aroma.feature.editor.ImportOutcome
import app.aroma.feature.editor.ImportPhoto
import app.aroma.feature.editor.ImportRequest
import app.aroma.feature.editor.ImportStep
import app.aroma.feature.editor.PasteOffer
import app.aroma.feature.editor.PhotosNote
import app.aroma.feature.editor.ReworkJob
import app.aroma.feature.editor.ReworkRequest
import app.aroma.feature.home.Greeting
import app.aroma.feature.home.HomeActions
import app.aroma.feature.home.HomeScreen
import app.aroma.feature.home.HomeUiState
import app.aroma.feature.home.RateReminder
import app.aroma.feature.login.LoginActions
import app.aroma.feature.login.LoginScreen
import app.aroma.feature.login.LoginStep
import app.aroma.feature.login.LoginUiState
import app.aroma.feature.mealplan.AddMealSheet
import app.aroma.feature.mealplan.MealPlanActions
import app.aroma.feature.mealplan.MealPlanScreen
import app.aroma.feature.mealplan.MealPlanUiState
import app.aroma.feature.mealplan.PlanSearchState
import app.aroma.feature.recipe.OrganizerSheet
import app.aroma.feature.recipe.RatingSheet
import app.aroma.feature.recipe.RecipeActions
import app.aroma.feature.recipe.RecipeActionsSheet
import app.aroma.feature.recipe.RecipeScreen
import app.aroma.feature.recipe.RecipeUiState
import app.aroma.feature.recipe.ReworkSheet
import app.aroma.feature.recipes.CollectionActions
import app.aroma.feature.recipes.CollectionKind
import app.aroma.feature.recipes.CollectionScreen
import app.aroma.feature.recipes.RecipeFilter
import app.aroma.feature.recipes.RecipesActions
import app.aroma.feature.recipes.RecipesScreen
import app.aroma.feature.recipes.RecipesUiState
import app.aroma.feature.settings.LicensesScreen
import app.aroma.feature.settings.SettingsActions
import app.aroma.feature.settings.SettingsScreen
import app.aroma.feature.settings.SettingsUiState
import app.aroma.feature.shopping.ShoppingActions
import app.aroma.feature.shopping.ShoppingScreen
import app.aroma.feature.shopping.ShoppingUiState
import app.aroma.onNodeWithWords
import app.aroma.ui.components.AromaSnackbarHost
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.navigation.Tab
import app.aroma.ui.navigation.TabBar
import app.aroma.ui.navigation.TabBarHeight
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Every screen and sheet in its states, with the sample content and with the design brief's test
 * texts (see [Brief]), in each [Look]. Pages that scroll are also shown whole, in a window tall
 * enough to hold them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreensScreenshotTest(private val look: Look) : ScreenshotTest() {
    private val german = look.locale == "de"
    private val sample = SampleData(german)

    private fun t(de: String, en: String) = if (german) de else en

    @Before
    fun applyLook() {
        RuntimeEnvironment.setQualifiers("${look.locale}-w${look.width}dp-h${Look.PHONE_HEIGHT}dp-xxhdpi")
        RuntimeEnvironment.setFontScale(look.fontScale)
        // The add screen offers the camera on a phone that has one.
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
    }

    private fun shoot(name: String, interact: () -> Unit = {}, content: @Composable () -> Unit) =
        snapshot("${name}_$look", look, interact, content)

    /** The whole page in one image, in a window tall enough to hold it. */
    private fun shootWhole(name: String, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("+h${WHOLE_PAGE}dp")
        shoot("${name}_whole", content = content)
    }

    private fun scrollTo(key: String): () -> Unit = { composeRule.onNode(hasScrollToKeyAction()).performScrollToKey(key) }

    /** Scrolls a page that is no list as far down as it goes. */
    private fun scrollToEnd() {
        composeRule.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, SCROLL_TO_END) }
    }

    private val homeFull
        get() =
            HomeUiState(
                greeting = Greeting.Evening,
                firstName = "Mara",
                date = sample.today,
                week = sample.mealPlan,
                recent = sample.recipes.reversed(),
                meal = Meal.Dinner,
                suggestions = sample.recipes,
                suggestionsForMeal = true,
                quick = sample.recipes.reversed(),
                openItems = 8,
                toRate = sample.recipes.first().let { RateReminder(it.id, it.slug, it.name, it.image) },
                isLoading = false,
            )

    @Test
    fun home() = shoot("home") { TabFrame(Tab.Home) { HomeScreen(homeFull, HomeActions()) } }

    /** Nothing planned today: the card that says so stands on the page's edge like every other. */
    @Test
    fun homeNothingPlanned() =
        shoot("home_empty") {
            val state =
                HomeUiState(
                    greeting = Greeting.Morning,
                    firstName = "Mara",
                    date = sample.today,
                    week = sample.mealPlan.filter { it.date != sample.today },
                    recent = sample.recipes,
                    meal = Meal.Breakfast,
                    suggestions = sample.recipes.take(4),
                    suggestionsForMeal = true,
                    isLoading = false,
                )
            TabFrame(Tab.Home) { HomeScreen(state, HomeActions()) }
        }

    /** The longest titles of the brief for today and the suggestions, under a long name, all of it. */
    @Test
    fun homeExtreme() =
        shootWhole("home_extreme") {
            val state =
                homeFull.copy(
                    firstName = "Maximiliane",
                    date = Brief.today,
                    week = Brief.entries,
                    suggestions = Brief.recipes,
                    openItems = 12,
                    toRate = Brief.recipes[5].let { RateReminder(it.id, it.slug, it.name, it.image) },
                )
            TabFrame(Tab.Home) { HomeScreen(state, HomeActions()) }
        }

    private fun recipe(state: RecipeUiState): @Composable () -> Unit = { RecipeScreen(state, RecipeActions()) }

    private fun recipeState(recipe: Recipe) = RecipeUiState(recipe = recipe, isLoading = false)

    /** The pancakes, saved, with an ingredient ticked off. */
    private val pancakes get() = recipeState(sample.recipe).copy(isFavorite = true, checkedIngredients = setOf(1))

    @Test
    fun recipe() = shoot("recipe", content = recipe(pancakes))

    /** Scrolled down to the facts, under the collapsed head. */
    @Test
    fun recipeScrolled() = shoot("recipe_scrolled", scrollTo("facts"), recipe(pancakes))

    /** Everything a recipe can have, saved, with 30 ingredients in four groups and a step with a title. */
    @Test
    fun recipeFull() = shootWhole("recipe_full", recipe(recipeState(Brief.full).copy(isFavorite = true)))

    /** The longest title, a yield of "1 Blech" and times as free text. */
    @Test
    fun recipeExtreme() = shoot("recipe_extreme", content = recipe(recipeState(Brief.longTitle)))

    /** Nothing but a name: no photo, times, headnote or rating. */
    @Test
    fun recipeBare() = shoot("recipe_bare", content = recipe(recipeState(Brief.bare)))

    @Test
    fun recipeLoading() = shoot("recipe_loading", content = recipe(RecipeUiState(isLoading = true)))

    /** The steps with the amounts written in, at six servings instead of four: "375 g Mehl", "¾ Banane". */
    @Test
    fun recipeStepsWithAmounts() =
        shoot("recipe_steps_amounts", scrollTo("stepsHead"), recipe(recipeState(sample.recipe).copy(scale = 1.5, amountsInSteps = true)))

    /** Amounts in US measures: the chip shows the choice, and grams and millilitres read as cups and ounces. */
    @Test
    fun recipeInUsMeasures() = shoot("recipe_us", scrollTo("facts"), recipe(recipeState(sample.recipe).copy(measures = MeasureSystem.US)))

    /** What the recipe is filed under, changed right on its page: the kind of dish first. */
    @Test
    fun recipeOrganizers() {
        fun organizer(name: String) = Organizer(name.lowercase(), name, name.lowercase())
        val recipe = sample.recipe
        val group =
            Organizers(
                categories =
                    recipe.categories + listOf(t("Italienisch", "Italian"), t("Backen", "Baking"), t("Suppen", "Soups")).map(::organizer),
                tags = recipe.tags + organizer(t("Ofen", "Oven")),
            )
        shoot("recipe_organizers") {
            RecipeScreen(recipeState(recipe), RecipeActions())
            OrganizerSheet(Filing(recipe.categories + group.categories[1], recipe.tags), group, onFile = {}, onDismiss = {})
        }
    }

    /** The stars of a recipe rated before, with the line that says how to take the rating back. */
    @Test
    fun recipeRating() =
        shoot("recipe_rating") {
            RecipeScreen(recipeState(sample.recipe).copy(userRating = 4.0), RecipeActions())
            RatingSheet(rating = 4.0, onRate = {}, onDismiss = {})
        }

    /** What a rework with Mealie's AI does, over the recipe it is opened from. */
    @Test
    fun recipeRework() =
        shoot("recipe_rework") {
            RecipeScreen(recipeState(sample.recipe).copy(aiAvailable = true), RecipeActions())
            ReworkSheet(translate = false, onRework = {}, onDismiss = {})
        }

    /** A rework under way: Mealie's steps under the actions, while the recipe stays as it was. */
    @Test
    fun recipeReworking() {
        val recipe = sample.recipe
        val rework =
            ReworkJob(
                id = UUID(0, 1),
                request = ReworkRequest(recipe.id, recipe.slug, recipe.name, translateTo = "en-US"),
                // Half a second into the 13th, so the clock reads 0:12 however long drawing takes.
                startedAt = System.currentTimeMillis() - 12_500,
                steps =
                    listOf(
                        ImportStep.Waiting,
                        ImportStep.Server(t("Rezept erstellen...", "Creating recipe...")),
                        ImportStep.Server(t("Rezept wird übersetzt...", "Translating recipe...")),
                    ),
            )
        shoot("recipe_reworking", content = recipe(recipeState(recipe).copy(aiAvailable = true, rework = rework)))
    }

    /** A recipe's menu, held down in the list. */
    @Test
    fun recipeMenu() =
        shoot("recipe_menu") {
            TabFrame(Tab.Recipes) {
                RecipesScreen(RecipesUiState(recipes = sample.recipes, total = sample.recipes.size, isLoading = false), RecipesActions())
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

    private fun recipes(state: RecipesUiState): @Composable () -> Unit =
        { TabFrame(Tab.Recipes) { RecipesScreen(state, RecipesActions()) } }

    /** The whole library: the meals and everyday filters, the cookbooks, then every recipe. */
    @Test
    fun recipes() =
        shoot(
            "recipes",
            content =
                recipes(
                    RecipesUiState(
                        recipes = sample.recipes,
                        total = sample.recipes.size,
                        isLoading = false,
                        categories = sample.categories,
                        tags = sample.tags,
                        cookbooks = sample.cookbookCovers,
                    ),
                ),
        )

    /** Dinner and quick ones: the cookbooks give way to what matches. */
    @Test
    fun recipesFiltered() =
        shoot(
            "recipes_filtered",
            content =
                recipes(
                    RecipesUiState(
                        recipes = sample.recipes.drop(2).take(3),
                        isLoading = false,
                        categories = sample.categories,
                        tags = sample.tags,
                        cookbooks = sample.cookbookCovers,
                        filter = RecipeFilter(meal = Meal.Dinner, quick = true),
                    ),
                ),
        )

    /** A meal nothing is filed under yet, as on a new server: the page says how to file recipes there. */
    @Test
    fun recipesMealUnfiled() =
        shoot("recipes_meal_unfiled", content = recipes(RecipesUiState(isLoading = false, filter = RecipeFilter(meal = Meal.Lunch))))

    /** The brief's titles in the grid, two of them saved. */
    @Test
    fun recipesExtreme() =
        shoot(
            "recipes_extreme",
            content =
                recipes(
                    RecipesUiState(recipes = Brief.recipes, total = Brief.recipes.size, saved = setOf("k0", "k5"), isLoading = false),
                ),
        )

    /** A search: what it finds as rows. */
    @Test
    fun recipesSearch() =
        shoot(
            "recipes_search",
            content =
                recipes(
                    RecipesUiState(filter = RecipeFilter(query = "Blech"), recipes = Brief.recipes.take(3), total = 3, isLoading = false),
                ),
        )

    /** A search that finds nothing. */
    @Test
    fun recipesEmpty() =
        shoot(
            "recipes_empty",
            content =
                recipes(
                    RecipesUiState(filter = RecipeFilter(query = "Zwetschgendatschi"), recipes = emptyList(), total = 0, isLoading = false),
                ),
        )

    @Test
    fun cookbook() =
        shoot("cookbook") {
            CollectionScreen(
                kind = CollectionKind.Cookbook,
                name = sample.cookbooks.first().name,
                description = sample.cookbooks.first().description,
                state = RecipesUiState(recipes = sample.recipes.take(4), total = 4, isLoading = false),
                actions = CollectionActions(),
            )
        }

    private fun plan(state: MealPlanUiState): @Composable () -> Unit =
        { TabFrame(Tab.MealPlan) { MealPlanScreen(state, MealPlanActions()) } }

    @Test
    fun mealPlan() =
        shoot(
            "mealplan",
            content = plan(MealPlanUiState(today = sample.today, weekStart = sample.today, days = sample.week, isLoading = false)),
        )

    /** The brief's week under way, all of it: Monday folded away, today on its surface, empty days with their link. */
    @Test
    fun mealPlanExtreme() =
        shootWhole(
            "mealplan_extreme",
            plan(MealPlanUiState(today = Brief.today, weekStart = Brief.monday, days = Brief.week, weekItems = 46, isLoading = false)),
        )

    /** A week with nothing planned yet. */
    @Test
    fun mealPlanEmpty() =
        shoot(
            "mealplan_empty",
            content = plan(MealPlanUiState(today = Brief.today, weekStart = Brief.monday, days = Brief.emptyWeek, isLoading = false)),
        )

    /** The week across the turn of the month, with the link back to this week. */
    @Test
    fun mealPlanNextMonth() =
        shoot(
            "mealplan_next_month",
            content = plan(MealPlanUiState(today = Brief.today, weekStart = Brief.nextMonday, days = Brief.turnOfMonth, isLoading = false)),
        )

    /** Planning a dish for a day: the meal, recipe or note, and the recipes to tap, on the margin and 12 dp apart. */
    @Test
    fun mealPlanAdd() =
        shoot("mealplan_add") {
            AddMealSheet(
                date = sample.today.plusDays(3),
                search = PlanSearchState(results = sample.recipes),
                onSearch = {},
                onAdd = { _, _, _, _ -> },
                onDismiss = {},
            )
        }

    private fun shopping(list: ShoppingList?, settling: Map<String, Boolean> = emptyMap()): @Composable () -> Unit =
        {
            TabFrame(Tab.Shopping) {
                ShoppingScreen(
                    ShoppingUiState(
                        lists =
                            listOfNotNull(
                                list?.let { ShoppingListInfo(it.id, it.name) },
                                ShoppingListInfo("2", t("Drogerie", "Pharmacy")),
                            ),
                        list = list,
                        isLoading = false,
                        settlingChecks = settling,
                    ),
                    ShoppingActions(),
                )
            }
        }

    /** The list, one of it just checked: drawn and struck through, a moment before it moves into the basket. */
    @Test
    fun shopping() = shoot("shopping", content = shopping(sample.shoppingList, mapOf(sample.shoppingList.items[1].id to true)))

    @Test
    fun shoppingMode() =
        shoot(
            "shopping_mode",
            interact = {
                composeRule.onNodeWithContentDescription(t("Weitere Optionen", "More options")).performClick()
                // With very large text on a narrow phone the row may end in "…" after its first words.
                composeRule.onNodeWithWords(t("Einkaufsmodus", "Start shopping"), substring = true).performClick()
            },
            content = shopping(sample.shoppingList),
        )

    /** Forty things to buy in four groups and some without, three ticked off, all of them. */
    @Test
    fun shoppingExtreme() = shootWhole("shopping_extreme", shopping(Brief.longList))

    @Test
    fun shoppingEmptyList() = shoot("shopping_empty", content = shopping(sample.shoppingList.copy(items = emptyList())))

    /** No list at all yet. */
    @Test
    fun shoppingNoList() = shoot("shopping_no_list") { ShoppingScreen(ShoppingUiState(isLoading = false), ShoppingActions()) }

    /** The curry's step of nine ingredients, with their amounts in its text, two ticked off, beside a timer part way down. */
    @Test
    fun cook() =
        shoot("cook") {
            val timer = CookTimerUi(1, t("Schritt 2", "Step 2"), remainingSeconds = 432, isRunning = true, totalSeconds = 600)
            val state =
                CookUiState(
                    recipe = sample.curry,
                    isLoading = false,
                    amountsInSteps = true,
                    checkedIngredients = setOf(1, 6),
                    timers = listOf(timer),
                )
            CookScreen(state, CookActions(), initialPage = 1)
        }

    /** All ingredients with their sections, in the step's box. */
    @Test
    fun cookAllIngredients() =
        shoot("cook_all_ingredients") {
            val state =
                CookUiState(
                    recipe = sample.recipe,
                    isLoading = false,
                    ingredientsInSteps = IngredientsInSteps.All,
                    checkedIngredients = setOf(0, 2),
                )
            CookScreen(state, CookActions(), initialPage = 1)
        }

    /** The step with a title, of the recipe with 30 ingredients. */
    @Test
    fun cookTitled() =
        shoot("cook_titled") { CookScreen(CookUiState(recipe = Brief.full, isLoading = false), CookActions(), initialPage = 2) }

    @Test
    fun cookLandscape() {
        RuntimeEnvironment.setQualifiers("+w${Look.PHONE_HEIGHT}dp-h${look.width}dp-land")
        shoot("cook_landscape") { CookScreen(CookUiState(recipe = sample.curry, isLoading = false), CookActions(), initialPage = 1) }
    }

    @Test
    fun cookDone() =
        shoot("cook_done") {
            CookScreen(
                CookUiState(recipe = sample.recipe, isLoading = false, userRating = 4.0),
                CookActions(),
                initialPage = sample.recipe.steps.size,
            )
        }

    @Test
    fun login() = shoot("login") { LoginScreen(LoginUiState(serverInput = "mealie.example.com"), LoginActions()) }

    @Test
    fun loginCredentials() =
        shoot("login_credentials") {
            val state =
                LoginUiState(
                    step = LoginStep.Credentials,
                    server = ServerInfo("https://mealie.example.com", "3.31.0", allowPasswordLogin = true),
                    username = "mara@example.com",
                    password = "secret",
                )
            LoginScreen(state, LoginActions())
        }

    private fun settings(name: String, email: String) =
        SettingsUiState(
            user = User("1", "mara", name, email),
            server = ServerInfo("https://mealie.example.com", "3.31.0", true),
            dynamicColorAvailable = true,
            offlineCount = 18,
            version = "0.8.0",
        )

    @Test
    fun settings() = shoot("settings") { SettingsScreen(settings("Mara Berger", "mara@example.com"), SettingsActions()) }

    /** The end of the page: the signature, with room below it. */
    @Test
    fun settingsEnd() =
        shoot("settings_end", interact = ::scrollToEnd) { SettingsScreen(settings("Mara Berger", "mara@example.com"), SettingsActions()) }

    /** The settings for cooking on a sheet of their own, opened from the page. */
    @Test
    fun settingsCooking() =
        shoot("settings_cooking", interact = { composeRule.onNodeWithWords(t("Kochen", "Cooking")).performScrollTo().performClick() }) {
            SettingsScreen(settings("Mara Berger", "mara@example.com"), SettingsActions())
        }

    /** A long name and address, all of the page. */
    @Test
    fun settingsExtreme() =
        shootWhole("settings_extreme") {
            SettingsScreen(settings("Maximiliane Berger-Schönfeld", "maximiliane.berger-schoenfeld@example.com"), SettingsActions())
        }

    @Test
    fun settingsUpdate() =
        shoot("settings_update", interact = { composeRule.onNodeWithWords("Version 0.3.0").performScrollTo() }) {
            SettingsScreen(
                settings("Mara Berger", "mara@example.com").copy(
                    version = "0.3.0",
                    update = UpdateState.Available("0.4.0", "", 1),
                    checkUpdatesAtStart = true,
                ),
                SettingsActions(),
            )
        }

    @Test
    fun licenses() = shoot("licenses") { LicensesScreen(onBack = {}) }

    private fun add(state: AddRecipeUiState): @Composable () -> Unit = { AddRecipeScreen(state, AddRecipeActions()) }

    private val addReady = AddRecipeUiState(aiAvailable = true, photosAvailable = true)

    /** The add screen as it opens: one field, the AI's options as chips, and the ways in without a link below. */
    @Test
    fun addRecipe() = shoot("add", content = add(addReady))

    /** A link copied in another app, offered right under the empty field. */
    @Test
    fun addRecipeClipboard() = shoot("add_clipboard", content = add(addReady.copy(copied = PasteOffer.Link)))

    /** A link pasted, to be read with AI and translated; the caption copied since is offered to go below it. */
    @Test
    fun addRecipeLink() =
        shoot("add_link", content = add(addReady.copy(input = sample.importLink, translate = true, copied = PasteOffer.Text)))

    /** A caption below its link, to be read without AI, which takes the translation along. */
    @Test
    fun addRecipeText() =
        shoot("add_text", content = add(addReady.copy(input = "${sample.importPost}\n\n${sample.importCaption}", useAi = false)))

    /** Two photos chosen for Mealie's AI, with tiles for more; the AI chip, needless with photos, stepped aside. */
    @Test
    fun addRecipeWithPhotos() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val photos =
            sample.importPhotos.map { url ->
                val file = File(context.cacheDir, url.substringAfterLast('/'))
                file.outputStream().use { photo(url).compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                ImportPhoto(file)
            }
        shoot("add_photos", content = add(addReady.copy(photos = photos)))
    }

    /** Photos shared from the gallery to a server that cannot read them: set aside, and the screen says why. */
    @Test
    fun addRecipePhotosUnreadable() = shoot("add_photos_unreadable", content = add(AddRecipeUiState(photosNote = PhotosNote.Unreadable)))

    /** A shared post on its way: the import button has turned into its progress, and the rest stepped aside. */
    @Test
    fun addRecipeImporting() {
        val job =
            ImportJob(
                request = ImportRequest(sample.importPost, useAi = true),
                // Half a second into the 24th, so the clock reads 0:23 however long drawing takes.
                startedAt = System.currentTimeMillis() - 23_500,
                steps =
                    listOf(
                        ImportStep.ReadingPost,
                        ImportStep.Waiting,
                        ImportStep.Server(t("Rezept erstellen...", "Creating recipe...")),
                    ),
            )
        shoot("add_importing", content = add(addReady.copy(input = sample.importPost, import = job)))
    }

    /** A post nobody could read: what to do about it below the button, and the caption copied since offered to paste. */
    @Test
    fun addRecipeError() {
        val job =
            ImportJob(
                request = ImportRequest(sample.importPost, useAi = true),
                startedAt = System.currentTimeMillis() - 41_000,
                steps = listOf(ImportStep.ReadingPost, ImportStep.Waiting),
                outcome = ImportOutcome.Failed(AromaError.ImportFailed(AromaError.ImportFailed.Reason.PostUnreadable)),
            )
        shoot("add_error", content = add(addReady.copy(input = sample.importPost, copied = PasteOffer.Text, import = job)))
    }

    private val editorState: EditorUiState
        get() {
            val recipe = sample.recipe
            val form =
                EditorForm(
                    name = recipe.name,
                    description = recipe.description,
                    servings = "4",
                    prepMinutes = "10",
                    cookMinutes = "15",
                    totalMinutes = "25",
                    ingredients =
                        t(
                            "# Für den Teig\n250 g Mehl\n2 EL Zucker\n1 ½ TL Backpulver\n1 Prise Salz\n2 Eier\n300 ml Milch",
                            "# For the batter\n250 g flour\n2 tbsp sugar\n1 ½ tsp baking powder\n1 pinch salt\n2 eggs\n300 ml milk",
                        ),
                    steps = recipe.steps.map { it.text },
                    categories = recipe.categories,
                    tags = recipe.tags,
                )
            return EditorUiState(
                form = form,
                allCategories = recipe.categories,
                allTags = recipe.tags + recipe.tools,
                currentImage = recipe.image,
                isLoading = false,
            )
        }

    @Test
    fun editor() = shoot("editor") { EditorScreen(editorState, EditorActions()) }

    /** The whole form: the facts, the kind of dish, the ingredients and steps as rows, and the categories and tags to name. */
    @Test
    fun editorWhole() = shootWhole("editor") { EditorScreen(editorState, EditorActions()) }

    /** A message on its ink with its action: paper on ink, readable in light and dark. */
    @Test
    fun snackbar() =
        shoot("snackbar") {
            val state = remember { SnackbarHostState() }
            LaunchedEffect(state) {
                state.showSnackbar(
                    t("Nicht mehr gemerkt", "No longer saved"),
                    actionLabel = t("Rückgängig", "Undo"),
                    duration = SnackbarDuration.Indefinite,
                )
            }
            Box(Modifier.fillMaxSize()) { AromaSnackbarHost(state, Modifier.align(Alignment.BottomCenter).padding(bottom = SNACKBAR_LIFT)) }
        }

    /** A tab page as the app shows it: with the tab bar across its bottom edge, over a typical phone's gesture navigation. */
    @Composable
    private fun TabFrame(tab: Tab, page: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalBottomBarInset provides TabBarHeight + GESTURE_NAVIGATION, content = page)
            TabBar(selected = tab, onSelect = {}, navigationInset = GESTURE_NAVIGATION, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    companion object {
        private const val WHOLE_PAGE = 4200
        private const val SCROLL_TO_END = 100_000f
        private const val JPEG_QUALITY = 90
        private val SNACKBAR_LIFT = 96.dp
        private val GESTURE_NAVIGATION = 24.dp

        /**
         * German light and dark, whose words run longest; English; and the narrowest phone the brief
         * aims at, 360 dp, with large text: 130 %, Android's largest before version 14.
         */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks() =
            listOf(
                Look("de", dark = false),
                Look("de", dark = true),
                Look("en", dark = false),
                Look("de", dark = false, fontScale = 1.3f, width = 360),
            )
    }
}
