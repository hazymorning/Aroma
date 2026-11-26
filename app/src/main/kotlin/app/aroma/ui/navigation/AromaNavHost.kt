package app.aroma.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.aroma.ui.appContainer
import app.aroma.ui.components.ActionBarHeight
import app.aroma.ui.components.AromaSnackbarHost
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.LocalScrollToTop
import app.aroma.ui.components.reducedMotion
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Space
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
fun AromaNavHost(launch: LaunchRequest?, onLaunchConsume: () -> Unit, modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    // A message stands above the tab bar where there is one. The bar hides while typing.
    val showBar = destination?.isTab() == true && !WindowInsets.isImeVisible
    val reselected = remember { MutableSharedFlow<Tab>(extraBufferCapacity = 1) }
    val snackbar = remember { SnackbarHostState() }

    // Home's search opens the recipe tab with its search field focused, once.
    val focusSearch = remember { mutableStateOf(false) }

    // A shopping widget opens its list on the shopping tab, once; its "+" ready to add to the list.
    val shoppingRequest = remember { mutableStateOf<LaunchRequest.Shopping?>(null) }

    // A recipe's text or photos shared to Aroma go to the add screen they open, which takes them once.
    val sharedRecipe = remember { mutableStateOf<LaunchRequest.Shared?>(null) }
    val launchConsume by rememberUpdatedState(onLaunchConsume)
    LaunchedEffect(launch) {
        when (launch) {
            is LaunchRequest.Import -> {
                nav.navigate(AddRecipeRoute(launch.url))
            }

            is LaunchRequest.Shared -> {
                sharedRecipe.value = launch
                nav.navigate(AddRecipeRoute())
            }

            LaunchRequest.AddRecipe -> {
                nav.navigate(AddRecipeRoute())
            }

            // An add screen already open would otherwise stay underneath and show the same import twice.
            is LaunchRequest.ShowImport -> {
                nav.navigate(AddRecipeRoute(job = launch.job.toString())) { popUpTo<AddRecipeRoute> { inclusive = true } }
            }

            is LaunchRequest.Recipe -> {
                nav.navigate(RecipeRoute(launch.slug)) { popUpTo<AddRecipeRoute> { inclusive = true } }
            }

            LaunchRequest.Search -> {
                focusSearch.value = true
                nav.navigateToTab(Tab.Recipes)
            }

            LaunchRequest.MealPlan -> {
                nav.navigateToTab(Tab.MealPlan)
            }

            is LaunchRequest.Shopping -> {
                shoppingRequest.value = launch
                nav.navigateToTab(Tab.Shopping)
            }

            null -> {
                return@LaunchedEffect
            }
        }
        launchConsume()
    }

    // The bar lies over the page instead of taking space from it, so showing and hiding it never
    // resizes the screen underneath, and tab pages scroll on below it.
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barInset = TabBarHeight + navigationInset
    // A phone on its side has its navigation bar or cutout at a side edge. Keeping clear of them once,
    // here, keeps every page's actions and margins out from under them.
    Box(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        // Only tab pages lie under the bar, so only they keep its space free. Every page tells the bar
        // that it is in view, and a tab page how far it shows.
        val presence = remember { PagesInView() }
        val underBar: @Composable AnimatedVisibilityScope.(NavBackStackEntry, Tab, @Composable () -> Unit) -> Unit = { entry, tab, page ->
            TabPage(entry, tab, presence, reselected, barInset, page)
        }
        val pushed: @Composable (NavBackStackEntry, @Composable () -> Unit) -> Unit = { entry, page -> PushedPage(entry, presence, page) }
        val planFocus = remember { PlanFocus() }
        // With the system's animations off, pages only cross-fade.
        val quiet = reducedMotion()
        NavHost(
            nav,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = if (quiet) Transitions.fadeIn else Transitions.enter,
            exitTransition = if (quiet) Transitions.fadeOut else Transitions.exit,
            popEnterTransition = if (quiet) Transitions.fadeIn else Transitions.popEnter,
            popExitTransition = if (quiet) Transitions.fadeOut else Transitions.popExit,
            predictivePopEnterTransition = if (quiet) Transitions.predictiveFadeIn else Transitions.predictivePopEnter,
            predictivePopExitTransition = if (quiet) Transitions.predictiveFadeOut else Transitions.predictivePopExit,
        ) {
            composable<HomeRoute> { entry ->
                underBar(entry, Tab.Home) {
                    HomeDestination(nav, snackbar, planFocus) {
                        focusSearch.value = true
                        nav.navigateToTab(Tab.Recipes)
                    }
                }
            }
            composable<RecipesRoute> { entry ->
                underBar(entry, Tab.Recipes) {
                    RecipesDestination(nav, snackbar, focusSearch.value, onFocusSearchConsume = { focusSearch.value = false })
                }
            }
            composable<MealPlanRoute> { entry -> underBar(entry, Tab.MealPlan) { MealPlanDestination(nav, snackbar, planFocus) } }
            composable<ShoppingRoute> { entry ->
                underBar(entry, Tab.Shopping) {
                    ShoppingDestination(nav, shoppingRequest.value, onRequestConsume = { shoppingRequest.value = null })
                }
            }
            composable<ShoppingModeRoute> { entry ->
                pushed(entry) { ShoppingModeDestination(entry.toRoute<ShoppingModeRoute>().listId, nav) }
            }
            composable<RecipeRoute> { entry ->
                pushed(entry) {
                    // As soon as another page is on top, the recipe gives the status bar back.
                    RecipeDestination(entry.toRoute<RecipeRoute>().slug, nav, snackbar, onTop = backStack?.id == entry.id)
                }
            }
            composable<CookRoute> { entry ->
                val route = entry.toRoute<CookRoute>()
                pushed(entry) { CookDestination(route.slug, route.scale, nav) }
            }
            composable<CollectionRoute> { entry -> pushed(entry) { CollectionDestination(entry.toRoute(), nav, snackbar) } }
            composable<AddRecipeRoute> { entry ->
                pushed(entry) { AddRecipeDestination(entry.toRoute(), nav, sharedRecipe.value) { sharedRecipe.value = null } }
            }
            composable<EditorRoute> { entry -> pushed(entry) { EditorDestination(entry.toRoute<EditorRoute>().slug, nav) } }
            composable<SettingsRoute> { entry -> pushed(entry) { SettingsDestination(nav) } }
            composable<LicensesRoute> { entry -> pushed(entry) { LicensesDestination(nav) } }
        }
        // The bar is there while a tab page is, and shows as much as that page does: it leaves with
        // its page and comes back with it, also while the back gesture brings the page back.
        val tabPage = presence.tabPages.values.firstOrNull()
        if (tabPage != null) {
            // Between two tabs it stays; between a tab and a pushed page it follows the tab page.
            val betweenTabs = presence.pushedPages.isEmpty()
            val follow = if (betweenTabs) null else tabPage.shown
            val barTab = if (betweenTabs) destination?.tab() else tabPage.tab
            // Hidden while typing, so it never rides on top of the keyboard.
            AnimatedVisibility(
                visible = !WindowInsets.isImeVisible,
                enter = fadeIn(Motion.edge()),
                exit = fadeOut(Motion.edge()),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                TabBar(
                    selected = barTab,
                    onSelect = nav::navigateToTab,
                    navigationInset = navigationInset,
                    onReselect = { reselected.tryEmit(it) },
                    modifier = Modifier.graphicsLayer { alpha = if (betweenTabs) 1f else follow?.value ?: 0f },
                )
            }
        }
        UpdateMessages(appContainer().updates, snackbar)
        // A message still showing as the page changes glides to its place on the new page.
        val snackbarLift by animateDpAsState(
            when {
                showBar -> barInset + Space.s8

                // Above the recipe's action bar.
                destination?.hasRoute(RecipeRoute::class) == true -> navigationInset + ActionBarHeight + Space.s8

                else -> navigationInset + Space.s16
            },
            tween(Motion.STATE, easing = Motion.Decelerate),
            label = "snackbarLift",
        )
        AromaSnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, -snackbarLift.roundToPx()) },
        )
    }
}

private fun NavDestination.isTab(tab: Tab): Boolean = hasRoute(tab.route::class)

private fun NavDestination.tab(): Tab? = Tab.entries.firstOrNull { isTab(it) }

/**
 * A tab page: it keeps the bar's space free, scrolls back to its top when its tab is tapped again,
 * and tells [presence] how far it shows while it comes and goes.
 */
@Composable
private fun AnimatedVisibilityScope.TabPage(
    entry: NavBackStackEntry,
    tab: Tab,
    presence: PagesInView,
    reselected: Flow<Tab>,
    barInset: Dp,
    page: @Composable () -> Unit,
) {
    val shown =
        transition.animateFloat(transitionSpec = { Transitions.barFade(this) }, label = "tabBar") { state ->
            if (state == EnterExitState.Visible) 1f else 0f
        }
    DisposableEffect(presence, entry.id, tab, shown) {
        presence.tabPages[entry.id] = TabPageShown(tab, shown)
        onDispose { presence.tabPages.remove(entry.id) }
    }
    val scrollToTop = remember(tab, reselected) { reselected.filter { it == tab }.map { } }
    CompositionLocalProvider(LocalBottomBarInset provides barInset, LocalScrollToTop provides scrollToTop, content = page)
}

/** A page pushed over the tabs, which tells [presence] while it is in view. */
@Composable
private fun PushedPage(entry: NavBackStackEntry, presence: PagesInView, page: @Composable () -> Unit) {
    DisposableEffect(presence, entry.id) {
        presence.pushedPages += entry.id
        onDispose { presence.pushedPages -= entry.id }
    }
    page()
}

/**
 * The pages in view, as the navigation draws them, also those coming and going and the one a back
 * gesture brings back: tab pages with how far they show, and pages pushed over them. The tab bar
 * follows them.
 */
@Stable
private class PagesInView {
    val tabPages = mutableStateMapOf<String, TabPageShown>()
    val pushedPages = mutableStateSetOf<String>()
}

/** A tab page in view, and how far it shows. */
@Immutable
private class TabPageShown(val tab: Tab, val shown: State<Float>)

/** Switches tabs the way Android users expect: one copy per tab, state kept when coming back. */
fun NavController.navigateToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
