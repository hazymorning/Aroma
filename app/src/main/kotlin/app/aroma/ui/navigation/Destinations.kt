package app.aroma.ui.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import app.aroma.R
import app.aroma.checkNotificationPermission
import app.aroma.core.model.Filing
import app.aroma.core.model.Meal
import app.aroma.feature.cook.CookActions
import app.aroma.feature.cook.CookScreen
import app.aroma.feature.cook.CookViewModel
import app.aroma.feature.editor.AddRecipeActions
import app.aroma.feature.editor.AddRecipeScreen
import app.aroma.feature.editor.AddRecipeViewModel
import app.aroma.feature.editor.EditorActions
import app.aroma.feature.editor.EditorScreen
import app.aroma.feature.editor.EditorViewModel
import app.aroma.feature.editor.ImportOutcome
import app.aroma.feature.editor.WatchClipboard
import app.aroma.feature.editor.copiedText
import app.aroma.feature.editor.readPickedPhoto
import app.aroma.feature.editor.reworkReason
import app.aroma.feature.home.HomeActions
import app.aroma.feature.home.HomeEvent
import app.aroma.feature.home.HomeScreen
import app.aroma.feature.home.HomeViewModel
import app.aroma.feature.login.LoginActions
import app.aroma.feature.login.LoginScreen
import app.aroma.feature.login.LoginViewModel
import app.aroma.feature.mealplan.AddMealSheet
import app.aroma.feature.mealplan.MealPlanActions
import app.aroma.feature.mealplan.MealPlanEvent
import app.aroma.feature.mealplan.MealPlanScreen
import app.aroma.feature.mealplan.MealPlanViewModel
import app.aroma.feature.recipe.AddToListSheet
import app.aroma.feature.recipe.OrganizerSheet
import app.aroma.feature.recipe.PlanSheet
import app.aroma.feature.recipe.RecipeActions
import app.aroma.feature.recipe.RecipeActionsSheet
import app.aroma.feature.recipe.RecipeEvent
import app.aroma.feature.recipe.RecipeMenuSheet
import app.aroma.feature.recipe.RecipeMenuState
import app.aroma.feature.recipe.RecipeScreen
import app.aroma.feature.recipe.RecipeViewModel
import app.aroma.feature.recipes.CollectionActions
import app.aroma.feature.recipes.CollectionKind
import app.aroma.feature.recipes.CollectionScreen
import app.aroma.feature.recipes.RecipeFilter
import app.aroma.feature.recipes.RecipesActions
import app.aroma.feature.recipes.RecipesScreen
import app.aroma.feature.recipes.RecipesViewModel
import app.aroma.feature.settings.LicensesScreen
import app.aroma.feature.settings.SettingsActions
import app.aroma.feature.settings.SettingsScreen
import app.aroma.feature.settings.SettingsViewModel
import app.aroma.feature.shopping.ShoppingActions
import app.aroma.feature.shopping.ShoppingEvent
import app.aroma.feature.shopping.ShoppingScreen
import app.aroma.feature.shopping.ShoppingViewModel
import app.aroma.ui.appContainer
import app.aroma.ui.aromaViewModel
import app.aroma.ui.components.KeepScreenOn
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.formatDurationPlain
import app.aroma.ui.components.titleRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun LoginDestination() {
    val vm = aromaViewModel { LoginViewModel(auth) }
    val state by vm.state.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        actions =
            LoginActions(
                onServerChange = vm::onServerChange,
                onConnect = vm::connect,
                onChangeServer = vm::changeServer,
                onUsernameChange = vm::onUsernameChange,
                onPasswordChange = vm::onPasswordChange,
                onTokenChange = vm::onTokenChange,
                onToggleTokenMode = vm::toggleTokenMode,
                onSignIn = vm::signIn,
            ),
    )
}

@Composable
fun HomeDestination(nav: NavController, snackbar: SnackbarHostState, planFocus: PlanFocus, onSearch: () -> Unit) {
    val vm = aromaViewModel { HomeViewModel(recipes, mealPlans, shopping, settings, sessions, auth, ratingReminder, connection) }
    val state by vm.state.collectAsStateWithLifecycle()
    val menu = remember { RecipeMenuState() }
    val resources = LocalResources.current
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is HomeEvent.Planned -> launch { snackbar.showSnackbar(resources.getString(R.string.home_planned_today)) }
                is HomeEvent.Failed -> launch { snackbar.showSnackbar(resources.getString(event.error.titleRes())) }
            }
        }
    }
    HomeScreen(
        state = state,
        actions =
            HomeActions(
                onSearch = onSearch,
                onOpenRecipe = { nav.navigate(RecipeRoute(it)) },
                onCook = { nav.navigate(CookRoute(it, 1.0)) },
                onRecipeMenu = menu::open,
                onOpenSettings = { nav.navigate(SettingsRoute) },
                onShowAllRecipes = { nav.navigateToTab(Tab.Recipes) },
                onShowMeal = { meal, title -> nav.navigate(CollectionRoute(CollectionKind.Meal, title, meal.name)) },
                onOpenPlan = { day ->
                    planFocus.day = day ?: LocalDate.now()
                    nav.navigateToTab(Tab.MealPlan)
                },
                onPlanToday = vm::planToday,
                onOpenShopping = { nav.navigateToTab(Tab.Shopping) },
                onRefresh = vm::refresh,
                onRateCooked = vm::rateCooked,
                onDismissReminder = vm::dismissReminder,
            ),
    )
    RecipeMenu(menu, nav, snackbar)
}

/** The recipe tab: the library with its cookbooks; [focusSearch] asks once for the search field. */
@Composable
fun RecipesDestination(nav: NavController, snackbar: SnackbarHostState, focusSearch: Boolean, onFocusSearchConsume: () -> Unit) {
    val menu = remember { RecipeMenuState() }
    val vm = aromaViewModel { RecipesViewModel(recipes, connection, withCookbooks = true) }
    val state by vm.state.collectAsStateWithLifecycle()
    RecipesScreen(
        state = state,
        focusSearch = focusSearch,
        onFocusSearchConsume = onFocusSearchConsume,
        actions =
            RecipesActions(
                onQueryChange = vm::onQueryChange,
                onToggleMeal = vm::toggleMeal,
                onToggleQuick = vm::toggleQuick,
                onToggleFavorites = vm::toggleFavorites,
                onToggleLongAgo = vm::toggleLongAgo,
                onToggleOrganizer = vm::toggleOrganizer,
                onSortChange = vm::setSort,
                onClearFilters = vm::clearFilters,
                onResetFilters = vm::resetFilters,
                onOpenRecipe = { nav.navigate(RecipeRoute(it.slug)) },
                onRecipeMenu = menu::open,
                onOpenCookbook = { nav.navigate(CollectionRoute(CollectionKind.Cookbook, it.name, it.id, it.description)) },
                onLoadMore = vm::loadMore,
                onAddRecipe = { nav.navigate(AddRecipeRoute()) },
                onRefresh = vm::refresh,
                onRetry = vm::reload,
            ),
    )
    RecipeMenu(menu, nav, snackbar)
}

@Composable
fun CollectionDestination(route: CollectionRoute, nav: NavController, snackbar: SnackbarHostState) {
    val vm =
        aromaViewModel {
            when (route.kind) {
                CollectionKind.Cookbook -> {
                    RecipesViewModel(recipes, connection, cookbook = route.id)
                }

                CollectionKind.Meal -> {
                    RecipesViewModel(recipes, connection, RecipeFilter(meal = Meal.entries.firstOrNull { it.name == route.id }))
                }
            }
        }
    val state by vm.state.collectAsStateWithLifecycle()
    val menu = remember { RecipeMenuState() }
    CollectionScreen(
        kind = route.kind,
        name = route.name,
        description = route.description,
        state = state,
        actions =
            CollectionActions(
                onBack = { nav.popBackStack() },
                onOpenRecipe = { nav.navigate(RecipeRoute(it.slug)) },
                onRecipeMenu = menu::open,
                onLoadMore = vm::loadMore,
                onRefresh = vm::refresh,
                onRetry = vm::reload,
            ),
    )
    RecipeMenu(menu, nav, snackbar)
}

/** A long press on a recipe card: its actions, and the sheets to plan it or put it on a list. */
@Composable
fun RecipeMenu(menu: RecipeMenuState, nav: NavController, snackbar: SnackbarHostState) {
    val target = menu.recipe ?: return
    // One view model shows whichever recipe was pressed last, rather than one staying behind for every recipe pressed.
    val vm =
        aromaViewModel(key = "menu") {
            RecipeViewModel(target.slug, recipes, reworks, shopping, mealPlans, sessions, settings, connection, it)
        }
    LaunchedEffect(vm, target.slug) { vm.show(target.slug) }
    val state by vm.state.collectAsStateWithLifecycle()
    val lists by vm.shoppingLists.collectAsStateWithLifecycle()
    val preferredList by vm.preferredListId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    RecipeFeedback(vm, snackbar)
    when (menu.sheet) {
        RecipeMenuSheet.Actions -> {
            RecipeActionsSheet(
                recipe = target,
                isFavorite = state.isFavorite,
                onCook = { nav.navigate(CookRoute(target.slug, 1.0)) },
                onPlan = { menu.sheet = RecipeMenuSheet.Plan },
                onShop = {
                    vm.loadShoppingLists()
                    menu.sheet = RecipeMenuSheet.Shop
                },
                onToggleFavorite = vm::toggleFavorite,
                onShare = { vm.shareText()?.let { shareText(context, it) } },
                onDismiss = { if (menu.sheet == RecipeMenuSheet.Actions) menu.sheet = null },
            )
        }

        RecipeMenuSheet.Plan -> {
            PlanSheet(
                today = LocalDate.now(),
                onPlan = { date, type ->
                    vm.plan(date, type)
                    menu.sheet = null
                },
                onDismiss = { menu.sheet = null },
            )
        }

        RecipeMenuSheet.Shop -> {
            val recipe = state.recipe
            if (recipe != null) {
                AddToListSheet(
                    recipe = recipe,
                    scale = state.scale,
                    lists = lists,
                    preferredListId = preferredList,
                    onScaleChange = vm::setScale,
                    onAdd = { list, indexes ->
                        vm.addToList(list, indexes)
                        menu.sheet = null
                    },
                    onDismiss = { menu.sheet = null },
                )
            } else if (state.error != null) {
                // A recipe that could not be loaded has nothing to add.
                LaunchedEffect(state.error) { menu.sheet = null }
            }
        }

        null -> {}
    }
}

/** Says what became of an action on a recipe; [onDelete] leaves the recipe once it is gone. */
@Composable
private fun RecipeFeedback(vm: RecipeViewModel, snackbar: SnackbarHostState, onDelete: () -> Unit = {}) {
    val haptics = LocalHapticFeedback.current
    val resources = LocalResources.current
    val locale = currentLocale()
    val leave by rememberUpdatedState(onDelete)
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is RecipeEvent.AddedToList -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    launch { snackbar.showSnackbar(resources.getString(R.string.recipe_added_to_list, event.listName)) }
                }

                is RecipeEvent.Planned -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    val day = event.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
                    launch { snackbar.showSnackbar(resources.getString(R.string.recipe_planned, day)) }
                }

                RecipeEvent.Deleted -> {
                    leave()
                }

                is RecipeEvent.Unsaved -> {
                    showUndo(snackbar, resources, R.string.recipe_unsaved) { vm.resave(event.recipe) }
                }

                is RecipeEvent.Failed -> {
                    launch { snackbar.showSnackbar(resources.getString(event.error.titleRes())) }
                }

                is RecipeEvent.Reworked -> {
                    showUndo(snackbar, resources, R.string.recipe_reworked) { vm.undoRework(event.reworked) }
                }

                is RecipeEvent.ReworkFailed -> {
                    val reason = event.error.reworkReason(resources)
                    launch { snackbar.showSnackbar(resources.getString(R.string.recipe_rework_failed_because, reason)) }
                }

                is RecipeEvent.Unrated -> {
                    showUndo(snackbar, resources, R.string.rating_removed) { vm.rate(event.stars) }
                }
            }
        }
    }
}

@Composable
fun RecipeDestination(slug: String, nav: NavController, snackbar: SnackbarHostState) {
    val vm = aromaViewModel { RecipeViewModel(slug, recipes, reworks, shopping, mealPlans, sessions, settings, connection, it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val rework by vm.rework.collectAsStateWithLifecycle()
    val lists by vm.shoppingLists.collectAsStateWithLifecycle()
    val preferredList by vm.preferredListId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheet by rememberSaveable { mutableStateOf<RecipeMenuSheet?>(null) }
    var organizing by rememberSaveable { mutableStateOf(false) }
    val organizers by vm.organizers.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    RecipeFeedback(vm, snackbar, onDelete = { nav.popBackStack() })
    LaunchedEffect(sheet) { if (sheet == RecipeMenuSheet.Shop) vm.loadShoppingLists() }
    LaunchedEffect(organizing) { if (organizing) vm.loadOrganizers() }
    val reworked = rework?.outcome
    LaunchedEffect(reworked) { if (reworked != null) vm.onReworkShown(reworked) }
    // A rework may outlast the page; a notification then says how it ended.
    val askForNotifications = rememberNotificationPermissionRequest()
    // A translated name gives the recipe a new slug.
    val current = state.recipe?.slug ?: slug

    RecipeScreen(
        state = state.copy(rework = rework),
        actions =
            RecipeActions(
                onBack = { nav.popBackStack() },
                onToggleFavorite = vm::toggleFavorite,
                onRate = vm::rate,
                onScaleChange = vm::setScale,
                onMeasuresChange = vm::setMeasures,
                onToggleIngredient = vm::toggleIngredient,
                onAddToShoppingList = { sheet = RecipeMenuSheet.Shop },
                onAddToMealPlan = { sheet = RecipeMenuSheet.Plan },
                onStartCooking = { nav.navigate(CookRoute(current, state.scale)) },
                onEdit = { nav.navigate(EditorRoute(current)) },
                onShare = { vm.shareText()?.let { shareText(context, it) } },
                onOpenUrl = { openUrl(context, it) },
                onDelete = vm::delete,
                onRework = { translate ->
                    askForNotifications()
                    vm.rework(translate)
                },
                onOrganize = {
                    // Filing is saved in Mealie as it is chosen, so it waits for Mealie to answer.
                    if (state.isOffline) {
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.organizers_offline)) }
                    } else {
                        organizing = true
                    }
                },
                onRetry = vm::load,
            ),
    )
    val recipe = state.recipe
    when {
        recipe == null -> {}

        organizing -> {
            OrganizerSheet(
                filing = Filing(recipe.categories, recipe.tags),
                organizers = organizers,
                onFile = vm::file,
                onDismiss = { organizing = false },
            )
        }

        sheet == RecipeMenuSheet.Shop -> {
            AddToListSheet(
                recipe = recipe,
                scale = state.scale,
                lists = lists,
                preferredListId = preferredList,
                onScaleChange = vm::setScale,
                onAdd = { list, indexes ->
                    vm.addToList(list, indexes)
                    sheet = null
                },
                onDismiss = { sheet = null },
            )
        }

        sheet == RecipeMenuSheet.Plan -> {
            PlanSheet(
                today = LocalDate.now(),
                onPlan = { date, type ->
                    vm.plan(date, type)
                    sheet = null
                },
                onDismiss = { sheet = null },
            )
        }
    }
}

@Composable
fun CookDestination(slug: String, scale: Double, nav: NavController) {
    val vm =
        aromaViewModel { CookViewModel(slug, scale, recipes, timeline, timers, settings, ratingReminder, sessions.current?.userId, it) }
    val cook =
        appContainer()
            .sessions.current
            ?.let { it.fullName.ifBlank { it.username } }
            .orEmpty()
    val resources = LocalResources.current
    val state by vm.state.collectAsStateWithLifecycle()
    val keepScreenOn by vm.keepScreenOn.collectAsStateWithLifecycle()
    val context = LocalContext.current
    KeepScreenOn(keepScreenOn)
    // A timer that runs out rings in a notification.
    val askForNotifications = rememberNotificationPermissionRequest()
    // Back from the settings that allow exact alarms, the timers running meanwhile ring on time too.
    LifecycleResumeEffect(vm) {
        vm.useExactAlarms()
        onPauseOrDispose { }
    }
    CookScreen(
        state = state,
        actions =
            CookActions(
                onClose = { nav.popBackStack() },
                onStartTimer = { seconds, label ->
                    askForNotifications()
                    vm.startTimer(seconds, label)
                },
                onToggleIngredient = vm::toggleIngredient,
                onIngredientsInStepsChange = { vm.setIngredientsInSteps(it) },
                onToggleTimer = vm::toggleTimer,
                onDismissTimer = vm::dismissTimer,
                onRate = vm::rate,
                onMarkCooked = { vm.markCooked(resources.getString(R.string.timeline_made_this, cook)) },
                onFinished = vm::finished,
                onAllowExactAlarms = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val request = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                        context.startActivity(request)
                    }
                },
                onRetry = vm::load,
            ),
    )
}

@Composable
fun MealPlanDestination(nav: NavController, snackbar: SnackbarHostState, focus: PlanFocus) {
    val vm = aromaViewModel { MealPlanViewModel(mealPlans, recipes, shopping, settings, connection) }
    val state by vm.state.collectAsStateWithLifecycle()
    // A day asked for from elsewhere: its week first, then the day in view.
    val focusDay = focus.day
    LaunchedEffect(focusDay) { focusDay?.let(vm::showWeekOf) }
    val search by vm.search.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    var addFor by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is MealPlanEvent.AddedToList -> {
                    launch { snackbar.showSnackbar(resources.getString(R.string.recipe_added_to_list, event.listName)) }
                }

                is MealPlanEvent.WeekAddedToList -> {
                    launch {
                        snackbar.showSnackbar(
                            resources.getQuantityString(R.plurals.plan_week_added, event.count, event.count, event.listName),
                        )
                    }
                }

                is MealPlanEvent.Removed -> {
                    // A second removal replaces this snackbar instead of queueing behind it.
                    snackbar.currentSnackbarData?.dismiss()
                    showUndo(snackbar, resources, R.string.plan_removed, SnackbarDuration.Short) { vm.restore(event.entry) }
                }

                is MealPlanEvent.Failed -> {
                    launch { snackbar.showSnackbar(resources.getString(event.error.titleRes())) }
                }
            }
        }
    }
    MealPlanScreen(
        state = state,
        actions =
            MealPlanActions(
                onPreviousWeek = vm::previousWeek,
                onNextWeek = vm::nextWeek,
                onToday = vm::thisWeek,
                onAdd = { date ->
                    vm.onSearchChange("")
                    addFor = date.toEpochDay()
                },
                onOpenEntry = { entry -> entry.recipe?.let { nav.navigate(RecipeRoute(it.slug)) } },
                onDeleteEntry = vm::delete,
                onShopEntry = vm::shop,
                onShopWeek = vm::shopWeek,
                onMoveEntry = vm::move,
                onRefresh = vm::refresh,
            ),
        focus = focusDay,
        onFocusConsume = { focus.day = null },
    )
    addFor?.let { day ->
        AddMealSheet(
            date = LocalDate.ofEpochDay(day),
            search = search,
            onSearch = vm::onSearchChange,
            onAdd = { type, recipe, title, note ->
                vm.add(LocalDate.ofEpochDay(day), type, recipe, title, note)
                addFor = null
            },
            onDismiss = { addFor = null },
        )
    }
}

/** The shopping tab; [request] asks once for a widget's list, and with its "+" for the field that adds to it. */
@Composable
fun ShoppingDestination(request: LaunchRequest.Shopping?, onRequestConsume: () -> Unit) {
    val vm = aromaViewModel { ShoppingViewModel(shopping, settings, connection, mealPlans) }
    val state by vm.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    // Kept here, not saved: rotation, a restored process or coming back to the tab never asks again.
    var focusAdd by remember { mutableStateOf(false) }
    val consumeRequest by rememberUpdatedState(onRequestConsume)
    LaunchedEffect(request) {
        if (request != null) {
            consumeRequest()
            request.listId?.let(vm::select)
            focusAdd = request.add
        }
    }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ShoppingEvent.Deleted -> {
                    // A second deletion replaces this snackbar instead of queueing behind it.
                    snackbar.currentSnackbarData?.dismiss()
                    showUndo(snackbar, resources, R.string.shopping_item_deleted, SnackbarDuration.Short) { vm.undoDelete(event) }
                }

                is ShoppingEvent.Failed -> {
                    launch { snackbar.showSnackbar(resources.getString(event.error.titleRes())) }
                }

                is ShoppingEvent.AddedFromPlan -> {
                    launch {
                        snackbar.showSnackbar(
                            resources.getQuantityString(R.plurals.plan_week_added, event.count, event.count, event.listName),
                        )
                    }
                }
            }
        }
    }
    ShoppingScreen(
        state = state,
        snackbar = snackbar,
        actions =
            ShoppingActions(
                onNewItemChange = vm::onNewItemChange,
                onAddItem = vm::addItem,
                onToggle = vm::toggle,
                onDelete = vm::delete,
                onRenameItem = vm::rename,
                onClearChecked = vm::clearChecked,
                onSelectList = vm::select,
                onCreateList = { vm.createList(it) },
                onRenameList = vm::renameList,
                onDeleteList = vm::deleteList,
                onAddFromPlan = vm::addFromPlan,
                onRefresh = vm::refresh,
            ),
        focusAdd = focusAdd,
        onFocusAddConsume = { focusAdd = false },
    )
}

/** The add screen; [shared] is a recipe's text or photos another app shared, taken once, as [onSharedConsume] tells. */
@Composable
fun AddRecipeDestination(
    route: AddRecipeRoute,
    nav: NavController,
    shared: LaunchRequest.Shared? = null,
    onSharedConsume: () -> Unit = {},
) {
    val vm =
        aromaViewModel {
            AddRecipeViewModel(recipes, imports, settings, route.sharedUrl, route.job, it, shared?.text, shared?.photos.orEmpty())
        }
    // The screen that was made with the share took it; another one must not take it again.
    val sharedConsume by rememberUpdatedState(onSharedConsume)
    LaunchedEffect(vm) { if (shared != null) sharedConsume() }
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    WatchClipboard(vm::onClipboardChange)
    // Back from what another app shared returns to that app, which Aroma opened over; the arrow stays in Aroma.
    val activity = LocalActivity.current
    val fromShare = rememberSaveable { route.sharedUrl != null || shared != null }
    BackHandler(enabled = fromShare && activity?.isTaskRoot == false) { activity?.finish() }
    LaunchedEffect(vm) {
        vm.created.collect { slug -> nav.navigate(EditorRoute(slug)) { popUpTo<AddRecipeRoute> { inclusive = true } } }
    }
    val outcome = state.import?.outcome
    LaunchedEffect(outcome) {
        if (outcome != null) vm.onOutcomeShown()
        if (outcome is ImportOutcome.Added) nav.navigate(RecipeRoute(outcome.slug)) { popUpTo<AddRecipeRoute> { inclusive = true } }
    }
    // An import may outlast the screen; a notification then says how it ended. The screen asks once.
    val askForNotifications = rememberNotificationPermissionRequest()
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.isImporting) {
        if (state.isImporting && !askedForNotifications) askedForNotifications = askForNotifications()
    }
    AddRecipeScreen(
        state = state,
        actions =
            AddRecipeActions(
                onBack = { nav.popBackStack() },
                onInputChange = vm::onInputChange,
                onPaste = { scope.launch { clipboard.copiedText(context)?.let(vm::paste) } },
                onAddPhotos = vm::addPhotos,
                onRemovePhoto = vm::removePhoto,
                onUseAiChange = vm::onUseAiChange,
                onTranslateChange = vm::onTranslateChange,
                onImport = vm::import,
                onNameChange = vm::onNameChange,
                onCreate = vm::create,
            ),
    )
}

@Composable
fun EditorDestination(slug: String, nav: NavController) {
    val locale = currentLocale()
    val vm = aromaViewModel { EditorViewModel(slug, recipes, sessions.requireServerUrl(), locale, it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch { readPickedPhoto(context, uri)?.let { vm.setImage(uri, it.bytes, it.extension) } }
            }
        }
    LaunchedEffect(vm) {
        vm.saved.collect { newSlug ->
            val cameFromRecipe = nav.previousBackStackEntry?.destination?.hasRoute(RecipeRoute::class) == true
            if (cameFromRecipe) nav.popBackStack<RecipeRoute>(inclusive = true) else nav.popBackStack()
            nav.navigate(RecipeRoute(newSlug))
        }
    }
    EditorScreen(
        state = state,
        actions =
            EditorActions(
                onBack = { nav.popBackStack() },
                onSave = { vm.save { seconds -> resources.formatDurationPlain(seconds) } },
                onChange = vm::update,
                onPickImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRetry = vm::load,
            ),
    )
}

@Composable
fun SettingsDestination(nav: NavController) {
    val context = LocalContext.current
    val vm = aromaViewModel { SettingsViewModel(context.applicationContext, settings, sessions, recipes, updates, ::signOut, connection) }
    val state by vm.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        actions =
            SettingsActions(
                onBack = { nav.popBackStack() },
                onThemeModeChange = { vm.setThemeMode(it) },
                onDynamicColorChange = { vm.setDynamicColor(it) },
                onHomeSectionChange = { section, shown -> vm.setHomeSection(section, shown) },
                onLanguageChange = vm::setLanguage,
                onKeepScreenOnChange = { vm.setKeepScreenOn(it) },
                onShowNextStepChange = { vm.setShowNextStep(it) },
                onIngredientsInStepsChange = { vm.setIngredientsInSteps(it) },
                onAmountsInStepsChange = { vm.setAmountsInSteps(it) },
                onMeasuresChange = { vm.setMeasures(it) },
                onOfflineRecipesChange = { vm.setOfflineRecipes(it) },
                onCheckForUpdates = vm::checkForUpdates,
                onInstallUpdate = vm::installUpdate,
                onCheckUpdatesAtStartChange = { vm.setCheckUpdatesAtStart(it) },
                onOpenLicenses = { nav.navigate(LicensesRoute) },
                onSignOut = { vm.signOut() },
            ),
    )
}

@Composable
fun LicensesDestination(nav: NavController) {
    LicensesScreen(onBack = { nav.popBackStack() })
}

/**
 * Whether to ask for the permission to post notifications, which Android 13 brought. The version
 * check stays in sight here, so lint knows the permission's name exists where it is asked for.
 */
private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.checkNotificationPermission()

/**
 * Shows [message] with an undo, which runs [undo] once tapped. Launched, so the events go on being
 * collected and the next message never waits behind this one.
 */
private fun CoroutineScope.showUndo(
    snackbar: SnackbarHostState,
    resources: Resources,
    @StringRes message: Int,
    duration: SnackbarDuration = SnackbarDuration.Long,
    undo: () -> Unit,
) {
    launch {
        val result =
            snackbar.showSnackbar(
                message = resources.getString(message),
                actionLabel = resources.getString(R.string.action_undo),
                duration = duration,
            )
        if (result == SnackbarResult.ActionPerformed) undo()
    }
}

/**
 * Asks for leave to post notifications where Android wants it asked and it is not given yet; the
 * request it returns says whether it asked. Whatever the answer, the work goes on.
 */
@Composable
private fun rememberNotificationPermissionRequest(): () -> Boolean {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        {
            val needsPermission = needsNotificationPermission(context)
            if (needsPermission) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            needsPermission
        }
    }
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, url.toUri())
    }.onFailure { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
