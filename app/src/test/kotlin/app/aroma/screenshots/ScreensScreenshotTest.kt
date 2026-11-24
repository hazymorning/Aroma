package app.aroma.screenshots

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.test.core.app.ApplicationProvider
import app.aroma.core.format.MeasureSystem
import app.aroma.core.model.Filing
import app.aroma.core.model.Meal
import app.aroma.core.model.Organizer
import app.aroma.core.model.Organizers
import app.aroma.core.model.ServerInfo
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
import app.aroma.feature.mealplan.MealPlanActions
import app.aroma.feature.mealplan.MealPlanDay
import app.aroma.feature.mealplan.MealPlanScreen
import app.aroma.feature.mealplan.MealPlanUiState
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
import app.aroma.ui.navigation.Tab
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.request.SuccessResult
import coil3.test.FakeImageLoaderEngine
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

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
                            date = sample.today,
                            week = sample.mealPlan.filter { it.date != sample.today },
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

    /** Nothing says yet what kind of dish it is: the line under the title names its tags alone. */
    @Test
    fun recipeWithoutMeal() {
        applyLocale()
        snapshot(
            "recipe_no_meal_${variant.suffix}",
            variant.dark,
            interact = { composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("facts") },
        ) {
            RecipeScreen(
                state = RecipeUiState(recipe = sample.recipe.copy(categories = emptyList()), isLoading = false),
                actions = RecipeActions(),
            )
        }
    }

    /** What the recipe is filed under, changed right on its page: the kind of dish first. */
    @Test
    fun recipeOrganizers() {
        applyLocale()
        val recipe = sample.recipe
        val german = variant.locale == "de"

        fun organizer(name: String) = Organizer(name.lowercase(), name, name.lowercase())
        val group =
            Organizers(
                categories =
                    recipe.categories +
                        listOf("Italienisch", "Backen", "Suppen").map { if (german) it else it.translated() }.map(::organizer),
                tags = recipe.tags + organizer(if (german) "Ofen" else "Oven"),
            )
        snapshot("recipe_organizers_${variant.suffix}", variant.dark) {
            RecipeScreen(state = RecipeUiState(recipe = recipe, isLoading = false), actions = RecipeActions())
            OrganizerSheet(
                filing = Filing(recipe.categories + group.categories[1], recipe.tags),
                organizers = group,
                onFile = {},
                onDismiss = {},
            )
        }
    }

    private fun String.translated() = mapOf("Italienisch" to "Italian", "Backen" to "Baking", "Suppen" to "Soups").getValue(this)

    /** The steps, each with the ingredients it uses under it. */
    @Test
    fun recipeSteps() {
        applyLocale()
        snapshot(
            "recipe_steps_${variant.suffix}",
            variant.dark,
            interact = { composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("stepsHead") },
        ) {
            RecipeScreen(state = RecipeUiState(recipe = sample.recipe, isLoading = false), actions = RecipeActions())
        }
    }

    /** The steps with the amounts written in, at six servings instead of four: "375 g Mehl", "¾ Banane". */
    @Test
    fun recipeStepsWithAmounts() {
        applyLocale()
        snapshot(
            "recipe_steps_amounts_${variant.suffix}",
            variant.dark,
            interact = { composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("stepsHead") },
        ) {
            RecipeScreen(
                state = RecipeUiState(recipe = sample.recipe, isLoading = false, scale = 1.5, amountsInSteps = true),
                actions = RecipeActions(),
            )
        }
    }

    /** The stars of a recipe rated before, with the line that says how to take the rating back. */
    @Test
    fun recipeRating() {
        applyLocale()
        snapshot("recipe_rating_${variant.suffix}", variant.dark) {
            RecipeScreen(state = RecipeUiState(recipe = sample.recipe, isLoading = false, userRating = 4.0), actions = RecipeActions())
            RatingSheet(rating = 4.0, onRate = {}, onDismiss = {})
        }
    }

    /** What a rework with Mealie's AI does, over the recipe it is opened from. */
    @Test
    fun recipeRework() {
        applyLocale()
        snapshot("recipe_rework_${variant.suffix}", variant.dark) {
            RecipeScreen(state = RecipeUiState(recipe = sample.recipe, isLoading = false, aiAvailable = true), actions = RecipeActions())
            ReworkSheet(translate = false, onRework = {}, onDismiss = {})
        }
    }

    /** A rework under way: Mealie's steps under the actions, while the recipe stays as it was. */
    @Test
    fun recipeReworking() {
        applyLocale()
        val german = variant.locale == "de"
        val rework =
            ReworkJob(
                id = UUID(0, 1),
                request = ReworkRequest(sample.recipe.id, sample.recipe.slug, sample.recipe.name, translateTo = "en-US"),
                // Half a second into the 13th, so the clock reads 0:12 however long drawing takes.
                startedAt = System.currentTimeMillis() - 12_500,
                steps =
                    listOf(
                        ImportStep.Waiting,
                        ImportStep.Server(if (german) "Rezept erstellen..." else "Creating recipe..."),
                        ImportStep.Server(if (german) "Rezept wird übersetzt..." else "Translating recipe..."),
                    ),
            )
        snapshot("recipe_reworking_${variant.suffix}", variant.dark) {
            RecipeScreen(
                state = RecipeUiState(recipe = sample.recipe, isLoading = false, aiAvailable = true, rework = rework),
                actions = RecipeActions(),
            )
        }
    }

    /** Amounts in US measures: the chip shows the choice, and grams and millilitres read as cups and ounces. */
    @Test
    fun recipeInUsMeasures() {
        applyLocale()
        snapshot(
            "recipe_us_${variant.suffix}",
            variant.dark,
            interact = { composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("facts") },
        ) {
            RecipeScreen(
                state = RecipeUiState(recipe = sample.recipe, isLoading = false, measures = MeasureSystem.US),
                actions = RecipeActions(),
            )
        }
    }

    /** The whole library: the meals and everyday filters, the cookbooks, then every recipe. */
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
                            categories = sample.categories,
                            tags = sample.tags,
                            cookbooks = sample.cookbookCovers,
                        ),
                    actions = RecipesActions(),
                )
            }
        }
    }

    /** Dinner and quick ones: the cookbooks give way to what matches. */
    @Test
    fun recipesFiltered() {
        applyLocale()
        snapshot("recipes_filtered_${variant.suffix}", variant.dark) {
            TabFrame(Tab.Recipes) {
                RecipesScreen(
                    state =
                        RecipesUiState(
                            recipes = sample.recipes.drop(2).take(3),
                            isLoading = false,
                            categories = sample.categories,
                            tags = sample.tags,
                            cookbooks = sample.cookbookCovers,
                            filter = RecipeFilter(meal = Meal.Dinner, quick = true),
                        ),
                    actions = RecipesActions(),
                )
            }
        }
    }

    /** A meal nothing is filed under yet, as on a new server: the page says how to file recipes there. */
    @Test
    fun recipesMealUnfiled() {
        applyLocale()
        snapshot("recipes_meal_unfiled_${variant.suffix}", variant.dark) {
            TabFrame(Tab.Recipes) {
                RecipesScreen(
                    state = RecipesUiState(isLoading = false, filter = RecipeFilter(meal = Meal.Lunch)),
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
        val more = if (variant.locale == "de") "Weitere Optionen" else "More options"
        val toggle = if (variant.locale == "de") "Einkaufsmodus starten" else "Start shopping mode"
        snapshot("shopping_mode_${variant.suffix}", variant.dark, interact = {
            composeRule.onNodeWithContentDescription(more).performClick()
            composeRule.onNodeWithText(toggle).performClick()
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

    /** All ingredients with their sections, in the step's box. */
    @Test
    fun cookStepWithIngredients() {
        applyLocale()
        snapshot("cook_ingredients_${variant.suffix}", variant.dark) {
            CookScreen(
                state =
                    CookUiState(
                        recipe = sample.recipe,
                        isLoading = false,
                        ingredientsInSteps = IngredientsInSteps.All,
                        checkedIngredients = setOf(0, 2),
                    ),
                actions = CookActions(),
                initialPage = 1,
            )
        }
    }

    /** The curry's step of nine ingredients with their amounts in the text, next to its timer. */
    @Test
    fun cookStepWithAmounts() {
        applyLocale()
        snapshot("cook_amounts_${variant.suffix}", variant.dark) {
            CookScreen(
                state =
                    CookUiState(
                        recipe = sample.curry,
                        isLoading = false,
                        amountsInSteps = true,
                        ingredientsInSteps = IngredientsInSteps.None,
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
    fun cookStepTwo() = cookStep("two", page = 0)

    @Test
    fun cookStepNine() = cookStep("nine", page = 1)

    @Test
    fun cookStepNone() = cookStep("none", page = 2)

    @Test
    fun cookStepLargeText() = cookStep("large_text", page = 1) { org.robolectric.RuntimeEnvironment.setFontScale(2f) }

    @Test
    fun cookStepLandscape() =
        cookStep("landscape", page = 1) {
            org.robolectric.RuntimeEnvironment.setQualifiers("+w891dp-h411dp-land")
        }

    /** A step of the curry, whose steps use two, nine, none and two ingredients, each in the step's own box. */
    private fun cookStep(name: String, page: Int, configure: () -> Unit = {}) {
        applyLocale()
        configure()
        snapshot("cook_step_${name}_${variant.suffix}", variant.dark) {
            CookScreen(
                state = CookUiState(recipe = sample.curry, isLoading = false, checkedIngredients = setOf(1, 6)),
                actions = CookActions(),
                initialPage = page,
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

    /** The add screen as it opens: one field, the AI's options as chips, and the ways in without a link below. */
    @Test
    fun addRecipe() {
        applyLocale()
        withCamera()
        snapshot("add_${variant.suffix}", variant.dark) {
            AddRecipeScreen(state = AddRecipeUiState(aiAvailable = true, photosAvailable = true), actions = AddRecipeActions())
        }
    }

    /** A link copied in another app, offered right under the empty field. */
    @Test
    fun addRecipeClipboard() {
        applyLocale()
        withCamera()
        snapshot("add_clipboard_${variant.suffix}", variant.dark) {
            AddRecipeScreen(
                state = AddRecipeUiState(aiAvailable = true, photosAvailable = true, copied = PasteOffer.Link),
                actions = AddRecipeActions(),
            )
        }
    }

    /** A link pasted, to be read with AI and translated; the caption copied since is offered to go below it. */
    @Test
    fun addRecipeLink() {
        applyLocale()
        withCamera()
        snapshot("add_link_${variant.suffix}", variant.dark) {
            AddRecipeScreen(
                state =
                    AddRecipeUiState(
                        input = sample.importLink,
                        aiAvailable = true,
                        photosAvailable = true,
                        translate = true,
                        copied = PasteOffer.Text,
                    ),
                actions = AddRecipeActions(),
            )
        }
    }

    /** A caption below its link, to be read without AI, which takes the translation along. */
    @Test
    fun addRecipeText() {
        applyLocale()
        withCamera()
        snapshot("add_text_${variant.suffix}", variant.dark) {
            AddRecipeScreen(
                state =
                    AddRecipeUiState(
                        input = "${sample.importPost}\n\n${sample.importCaption}",
                        aiAvailable = true,
                        photosAvailable = true,
                        useAi = false,
                    ),
                actions = AddRecipeActions(),
            )
        }
    }

    /** Two photos chosen for Mealie's AI, with tiles for more; the AI chip, needless with photos, stepped aside. */
    @Test
    fun addRecipeWithPhotos() {
        applyLocale()
        val context = ApplicationProvider.getApplicationContext<Context>()
        withCamera()
        val photos = sample.importPhotos.map { ImportPhoto(photoFile(context, it)) }
        showFiles(context)
        snapshot("add_photos_${variant.suffix}", variant.dark) {
            AddRecipeScreen(
                state = AddRecipeUiState(aiAvailable = true, photosAvailable = true, photos = photos),
                actions = AddRecipeActions(),
            )
        }
    }

    /** Photos shared from the gallery to a server that cannot read them: set aside, and the screen says why. */
    @Test
    fun addRecipePhotosUnreadable() {
        applyLocale()
        withCamera()
        snapshot("add_photos_unreadable_${variant.suffix}", variant.dark) {
            AddRecipeScreen(state = AddRecipeUiState(photosNote = PhotosNote.Unreadable), actions = AddRecipeActions())
        }
    }

    /** A shared post on its way: the import button has turned into its progress, and the rest stepped aside. */
    @Test
    fun addRecipeImporting() {
        applyLocale()
        withCamera()
        snapshot("add_importing_${variant.suffix}", variant.dark) {
            AddRecipeScreen(
                state =
                    AddRecipeUiState(
                        input = sample.importPost,
                        aiAvailable = true,
                        photosAvailable = true,
                        import =
                            ImportJob(
                                request = ImportRequest(sample.importPost, useAi = true),
                                // Half a second into the 24th, so the clock reads 0:23 however long drawing takes.
                                startedAt = System.currentTimeMillis() - 23_500,
                                steps =
                                    listOf(
                                        ImportStep.ReadingPost,
                                        ImportStep.Waiting,
                                        ImportStep.Server(if (variant.locale == "de") "Rezept erstellen..." else "Creating recipe..."),
                                    ),
                            ),
                    ),
                actions = AddRecipeActions(),
            )
        }
    }

    /** A post nobody could read: what to do about it below the button, and the caption copied since offered to paste. */
    @Test
    fun addRecipeError() {
        applyLocale()
        withCamera()
        snapshot("add_error_${variant.suffix}", variant.dark) {
            AddRecipeScreen(
                state =
                    AddRecipeUiState(
                        input = sample.importPost,
                        aiAvailable = true,
                        photosAvailable = true,
                        copied = PasteOffer.Text,
                        import =
                            ImportJob(
                                request = ImportRequest(sample.importPost, useAi = true),
                                startedAt = System.currentTimeMillis() - 41_000,
                                steps = listOf(ImportStep.ReadingPost, ImportStep.Waiting),
                                outcome = ImportOutcome.Failed(AromaError.ImportFailed(AromaError.ImportFailed.Reason.PostUnreadable)),
                            ),
                    ),
                actions = AddRecipeActions(),
            )
        }
    }

    /** A phone with a camera, which the add screen's camera actions ask for. */
    private fun withCamera() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
    }

    /** The photo [url] stands for as a file on the phone, where the add screen keeps the photos it imports. */
    private fun photoFile(context: Context, url: String) =
        File(context.cacheDir, url.substringAfterLast('/')).apply {
            outputStream().use { photoFor(url).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }

    /** Lets images load from files as well as from the server, drawn at once like the server's. */
    @OptIn(DelicateCoilApi::class)
    private fun showFiles(context: Context) {
        val engine =
            FakeImageLoaderEngine
                .Builder()
                .intercept({ it is File }) { chain ->
                    val photo = BitmapFactory.decodeFile((chain.request.data as File).path)
                    SuccessResult(image = photo.asImage(), request = chain.request, dataSource = DataSource.DISK)
                }.build()
        SingletonImageLoader.setUnsafe(
            ImageLoader
                .Builder(context)
                .components { add(engine) }
                .coroutineContext(Dispatchers.Unconfined)
                .build(),
        )
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

    /** The kind of dish under the recipe's facts, as Mealie keeps it: a category. */
    @Test
    fun editorMeal() {
        applyLocale()
        val ingredients = if (variant.locale == "de") "Zutaten" else "Ingredients"
        snapshot(
            "editor_meal_${variant.suffix}",
            variant.dark,
            interact = { composeRule.onNodeWithText(ingredients).performScrollTo() },
        ) {
            EditorScreen(state = editorState(), actions = EditorActions())
        }
    }

    /** Categories and tags at the end of the editor, each with a field to name a new one. */
    @Test
    fun editorOrganizers() {
        applyLocale()
        val newTag = if (variant.locale == "de") "Neues Schlagwort" else "New tag"
        snapshot(
            "editor_organizers_${variant.suffix}",
            variant.dark,
            interact = { composeRule.onNodeWithText(newTag).performScrollTo() },
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
