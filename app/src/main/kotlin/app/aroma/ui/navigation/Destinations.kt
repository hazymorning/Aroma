package app.aroma.ui.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import app.aroma.R
import app.aroma.core.model.Organizer
import app.aroma.core.model.OrganizerKind
import app.aroma.feature.cook.CookActions
import app.aroma.feature.cook.CookScreen
import app.aroma.feature.cook.CookViewModel
import app.aroma.feature.cookbook.CookbookScreen
import app.aroma.feature.editor.AddRecipeActions
import app.aroma.feature.editor.AddRecipeResult
import app.aroma.feature.editor.AddRecipeScreen
import app.aroma.feature.editor.AddRecipeViewModel
import app.aroma.feature.editor.EditorActions
import app.aroma.feature.editor.EditorScreen
import app.aroma.feature.editor.EditorViewModel
import app.aroma.feature.editor.RecipeImporter
import app.aroma.feature.editor.readPickedPhoto
import app.aroma.feature.home.HomeActions
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
import app.aroma.feature.recipe.PlanSheet
import app.aroma.feature.recipe.RecipeActions
import app.aroma.feature.recipe.RecipeEvent
import app.aroma.feature.recipe.RecipeScreen
import app.aroma.feature.recipe.RecipeViewModel
import app.aroma.feature.recipes.RecipeFilter
import app.aroma.feature.recipes.RecipesActions
import app.aroma.feature.recipes.RecipesScreen
import app.aroma.feature.recipes.RecipesViewModel
import app.aroma.feature.settings.LicensesScreen
import app.aroma.feature.settings.SettingsActions
import app.aroma.feature.settings.SettingsScreen
import app.aroma.feature.settings.SettingsViewModel
import app.aroma.feature.shopping.ShoppingActions
import app.aroma.feature.shopping.ShoppingScreen
import app.aroma.feature.shopping.ShoppingViewModel
import app.aroma.ui.aromaViewModel
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.formatDuration
import app.aroma.ui.components.titleRes
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
fun HomeDestination(nav: NavController) {
    val vm = aromaViewModel { HomeViewModel(recipes, mealPlans, sessions, ratingReminder) }
    val state by vm.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        actions =
            HomeActions(
                onSearch = { nav.navigate(RecipesRoute(focusSearch = true)) },
                onOpenRecipe = { nav.navigate(RecipeRoute(it)) },
                onOpenSettings = { nav.navigate(SettingsRoute) },
                onShowAllRecipes = { nav.navigateToTab(Tab.Recipes) },
                onShowFavorites = { nav.navigate(RecipesRoute(favorites = true)) },
                onOpenMealPlan = { nav.navigateToTab(Tab.MealPlan) },
                onOpenCookbook = { nav.navigate(CookbookRoute(it.slug, it.name, it.description)) },
                onRefresh = vm::refresh,
                onRateCooked = vm::rateCooked,
                onDismissReminder = vm::dismissReminder,
            ),
    )
}

@Composable
fun RecipesDestination(route: RecipesRoute, nav: NavController) {
    val vm =
        aromaViewModel {
            val organizer = route.organizerId?.let { setOf(Organizer(it, route.organizerName.orEmpty(), "")) }.orEmpty()
            val initial =
                RecipeFilter(
                    favoritesOnly = route.favorites,
                    categories = if (route.organizerKind == OrganizerKind.Category.name) organizer else emptySet(),
                    tags = if (route.organizerKind == OrganizerKind.Tag.name) organizer else emptySet(),
                )
            RecipesViewModel(recipes, initial)
        }
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(route.focusSearch) { if (route.focusSearch) runCatching { focus.requestFocus() } }
    RecipesScreen(
        state = state,
        searchFocus = focus,
        actions =
            RecipesActions(
                onQueryChange = vm::onQueryChange,
                onToggleFavorites = vm::toggleFavorites,
                onToggleOrganizer = vm::toggleOrganizer,
                onSortChange = vm::setSort,
                onClearFilters = vm::clearFilters,
                onOpenRecipe = { nav.navigate(RecipeRoute(it.slug)) },
                onLoadMore = vm::loadMore,
                onAddRecipe = { nav.navigate(AddRecipeRoute()) },
                onRetry = vm::reload,
            ),
    )
}

@Composable
fun CookbookDestination(route: CookbookRoute, nav: NavController) {
    val vm = aromaViewModel(key = "cookbook-${route.slug}") { RecipesViewModel(recipes, cookbook = route.slug) }
    val state by vm.state.collectAsStateWithLifecycle()
    CookbookScreen(
        name = route.name,
        description = route.description,
        state = state,
        onBack = { nav.popBackStack() },
        onOpenRecipe = { nav.navigate(RecipeRoute(it.slug)) },
        onLoadMore = vm::loadMore,
        onRetry = vm::reload,
    )
}

@Composable
fun RecipeDestination(slug: String, nav: NavController, snackbar: SnackbarHostState) {
    val vm = aromaViewModel { RecipeViewModel(slug, recipes, shopping, mealPlans, sessions, settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val lists by vm.shoppingLists.collectAsStateWithLifecycle()
    val preferredList by vm.preferredListId.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val locale = currentLocale()
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is RecipeEvent.AddedToList -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    snackbar.showSnackbar(
                        resources.getString(R.string.recipe_added_to_list, event.listName),
                    )
                }

                is RecipeEvent.Planned -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    snackbar.showSnackbar(
                        resources.getString(
                            R.string.recipe_planned,
                            event.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
                        ),
                    )
                }

                RecipeEvent.Deleted -> {
                    nav.popBackStack()
                }

                is RecipeEvent.Failed -> {
                    snackbar.showSnackbar(resources.getString(event.error.titleRes()))
                }
            }
        }
    }

    RecipeScreen(
        state = state,
        actions =
            RecipeActions(
                onBack = { nav.popBackStack() },
                onToggleFavorite = vm::toggleFavorite,
                onRate = vm::rate,
                onScale = vm::setScale,
                onToggleIngredient = vm::toggleIngredient,
                onAddToShoppingList = {
                    vm.loadShoppingLists()
                    sheet = "list"
                },
                onAddToMealPlan = { sheet = "plan" },
                onStartCooking = { nav.navigate(CookRoute(slug, state.scale)) },
                onEdit = { nav.navigate(EditorRoute(slug)) },
                onShare = { vm.shareText()?.let { shareText(context, it) } },
                onOpenUrl = { openUrl(context, it) },
                onDelete = vm::delete,
                onOpenRecipe = { nav.navigate(RecipeRoute(it)) },
                onOpenOrganizer = { kind, organizer ->
                    val route = RecipesRoute(organizerKind = kind.name, organizerId = organizer.id, organizerName = organizer.name)
                    nav.navigate(route)
                },
                onRetry = vm::load,
            ),
    )
    val recipe = state.recipe
    when {
        recipe == null -> {}

        sheet == "list" -> {
            AddToListSheet(
                recipe = recipe,
                scale = state.scale,
                lists = lists,
                preferredListId = preferredList,
                onScale = vm::setScale,
                onAdd = { list, indexes ->
                    vm.addToList(list, indexes)
                    sheet = null
                },
                onDismiss = { sheet = null },
            )
        }

        sheet == "plan" -> {
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
    val vm = aromaViewModel { CookViewModel(slug, scale, recipes, timers, settings, ratingReminder, sessions.current?.userId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val keepScreenOn by vm.keepScreenOn.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    CookScreen(
        state = state,
        actions =
            CookActions(
                onClose = { nav.popBackStack() },
                onStartTimer = { seconds, label ->
                    val needsPermission =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    vm.startTimer(seconds, label)
                },
                onToggleTimer = vm::toggleTimer,
                onDismissTimer = vm::dismissTimer,
                onRate = vm::rate,
                onMarkCooked = vm::markCooked,
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
fun MealPlanDestination(nav: NavController, snackbar: SnackbarHostState) {
    val vm = aromaViewModel { MealPlanViewModel(mealPlans, recipes, shopping, settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    var addFor by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is MealPlanEvent.AddedToList -> {
                    snackbar.showSnackbar(
                        resources.getString(R.string.recipe_added_to_list, event.listName),
                    )
                }

                is MealPlanEvent.Failed -> {
                    snackbar.showSnackbar(resources.getString(event.error.titleRes()))
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
                onRefresh = vm::refresh,
            ),
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

@Composable
fun ShoppingDestination() {
    val vm = aromaViewModel { ShoppingViewModel(shopping, settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    ShoppingScreen(
        state = state,
        actions =
            ShoppingActions(
                onNewItemChange = vm::onNewItemChange,
                onAddItem = vm::addItem,
                onToggle = vm::toggle,
                onDelete = vm::delete,
                onUndoDelete = vm::undoDelete,
                onDeleteHandled = vm::deleteHandled,
                onEdit = vm::rename,
                onClearChecked = vm::clearChecked,
                onSelectList = vm::select,
                onCreateList = { vm.createList(it) },
                onRenameList = vm::renameList,
                onDeleteList = vm::deleteList,
                onRefresh = vm::refresh,
                onNoticeShown = vm::noticeShown,
            ),
    )
}

@Composable
fun AddRecipeDestination(sharedUrl: String?, nav: NavController) {
    val untitled = stringResource(R.string.add_untitled)
    val vm = aromaViewModel { AddRecipeViewModel(recipes, RecipeImporter(recipes, untitled), settings, sharedUrl) }
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm) {
        vm.results.collect { result ->
            val target =
                when (result) {
                    is AddRecipeResult.Imported -> RecipeRoute(result.slug)
                    is AddRecipeResult.Created -> EditorRoute(result.slug)
                }
            nav.navigate(target) { popUpTo<AddRecipeRoute> { inclusive = true } }
        }
    }
    AddRecipeScreen(
        state = state,
        actions =
            AddRecipeActions(
                onBack = { nav.popBackStack() },
                onUrlChange = vm::onUrlChange,
                onPaste = {
                    scope.launch {
                        val text =
                            clipboard
                                .getClipEntry()
                                ?.clipData
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.text
                                ?.toString()
                        if (!text.isNullOrBlank()) vm.onUrlChange(text.trim())
                    }
                },
                onTextChange = vm::onTextChange,
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
    val vm = aromaViewModel { EditorViewModel(slug, recipes, sessions.requireServerUrl(), locale) }
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
                onSave = { vm.save { seconds -> resources.formatDuration(seconds) } },
                onChange = vm::update,
                onPickImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRetry = vm::load,
            ),
    )
}

@Composable
fun SettingsDestination(nav: NavController) {
    val context = LocalContext.current
    val vm = aromaViewModel { SettingsViewModel(context.applicationContext, settings, sessions, recipes, ::signOut) }
    val state by vm.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        actions =
            SettingsActions(
                onBack = { nav.popBackStack() },
                onThemeMode = { vm.setThemeMode(it) },
                onDynamicColor = { vm.setDynamicColor(it) },
                onLanguage = vm::setLanguage,
                onKeepScreenOn = { vm.setKeepScreenOn(it) },
                onOfflineRecipes = { vm.setOfflineRecipes(it) },
                onOpenLicenses = { nav.navigate(LicensesRoute) },
                onSignOut = { vm.signOut() },
            ),
    )
}

@Composable
fun LicensesDestination(nav: NavController) {
    LicensesScreen(onBack = { nav.popBackStack() })
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

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
