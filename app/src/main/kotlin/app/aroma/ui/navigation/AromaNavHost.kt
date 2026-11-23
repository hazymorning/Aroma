package app.aroma.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
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
import app.aroma.ui.components.AromaSnackbarHost
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.LocalScrollToTop
import app.aroma.ui.theme.Motion
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
fun AromaNavHost(sharedUrl: String?, onSharedUrlConsume: () -> Unit, modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    // Hidden while typing, so it never rides on top of the keyboard.
    val showBar = destination?.isTab() == true && !WindowInsets.isImeVisible
    val reselected = remember { MutableSharedFlow<Tab>(extraBufferCapacity = 1) }
    val snackbar = remember { SnackbarHostState() }
    // Home's search opens the recipe tab with its search field focused, once.
    val focusSearch = remember { mutableStateOf(false) }

    val sharedUrlConsume by rememberUpdatedState(onSharedUrlConsume)
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            nav.navigate(AddRecipeRoute(sharedUrl))
            sharedUrlConsume()
        }
    }

    // The bar floats over the page instead of taking space from it, so showing and hiding it never
    // resizes the screen underneath, and tab pages scroll on below it.
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barInset = FloatingBarHeight + FloatingBarGap + navigationInset
    Box(modifier.fillMaxSize()) {
        // Only tab pages lie under the bar, so only they keep its space free.
        val underBar: @Composable (Tab, @Composable () -> Unit) -> Unit = { tab, page ->
            val scrollToTop = remember(tab) { reselected.filter { it == tab }.map { } }
            CompositionLocalProvider(LocalBottomBarInset provides barInset, LocalScrollToTop provides scrollToTop, content = page)
        }
        NavHost(
            nav,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = Transitions.enter,
            exitTransition = Transitions.exit,
            popEnterTransition = Transitions.popEnter,
            popExitTransition = Transitions.popExit,
            predictivePopEnterTransition = Transitions.predictivePopEnter,
            predictivePopExitTransition = Transitions.predictivePopExit,
        ) {
            composable<HomeRoute> {
                underBar(Tab.Home) {
                    HomeDestination(nav, snackbar) {
                        focusSearch.value = true
                        nav.navigateToTab(Tab.Recipes)
                    }
                }
            }
            composable<RecipesRoute> {
                underBar(
                    Tab.Recipes,
                ) { RecipesDestination(nav, snackbar, focusSearch.value, onFocusSearchConsume = { focusSearch.value = false }) }
            }
            composable<MealPlanRoute> { underBar(Tab.MealPlan) { MealPlanDestination(nav, snackbar) } }
            composable<ShoppingRoute> { underBar(Tab.Shopping) { ShoppingDestination() } }
            composable<RecipeRoute> { RecipeDestination(it.toRoute<RecipeRoute>().slug, nav, snackbar) }
            composable<CookRoute> { entry ->
                val route = entry.toRoute<CookRoute>()
                CookDestination(route.slug, route.scale, nav)
            }
            composable<CollectionRoute> { CollectionDestination(it.toRoute(), nav, snackbar) }
            composable<AddRecipeRoute> { AddRecipeDestination(it.toRoute<AddRecipeRoute>().sharedUrl, nav) }
            composable<EditorRoute> { EditorDestination(it.toRoute<EditorRoute>().slug, nav) }
            composable<SettingsRoute> { SettingsDestination(nav) }
            composable<LicensesRoute> { LicensesDestination(nav) }
        }
        // Content dissolves into the paper before it reaches the bar, instead of running into its edge.
        AnimatedVisibility(
            visible = showBar,
            enter = fadeIn(tween(Motion.STATE)),
            exit = fadeOut(tween(Motion.STATE)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BarFade(barInset)
        }
        AnimatedVisibility(
            visible = showBar,
            enter = fadeIn(tween(200)) + slideInVertically(tween(260, easing = Transitions.Settle)) { it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 24.dp, end = 24.dp, bottom = FloatingBarGap),
        ) {
            FloatingBar(
                selected = Tab.entries.firstOrNull { destination?.isTab(it) == true },
                onSelect = nav::navigateToTab,
                onReselect = { reselected.tryEmit(it) },
            )
        }
        UpdateMessages(appContainer().updates, snackbar)
        AromaSnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom =
                        when {
                            showBar -> barInset + 8.dp

                            // Above the recipe's floating cook button.
                            destination?.hasRoute(RecipeRoute::class) == true -> navigationInset + 16.dp + 52.dp + 8.dp

                            else -> navigationInset + 16.dp
                        },
                ),
        )
    }
}

private fun NavDestination.isTab(tab: Tab): Boolean = hasRoute(tab.route::class)

/** Switches tabs the way Android users expect: one copy per tab, state kept when coming back. */
fun NavController.navigateToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
