package app.aroma.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.aroma.ui.components.AromaSnackbarHost
import app.aroma.ui.components.LocalBottomBarInset

@Composable
fun AromaNavHost(sharedUrl: String?, onSharedUrlConsume: () -> Unit, modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = destination?.isTab() == true
    val snackbar = remember { SnackbarHostState() }

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
        val underBar: @Composable (@Composable () -> Unit) -> Unit = { page ->
            CompositionLocalProvider(LocalBottomBarInset provides barInset, content = page)
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
            composable<HomeRoute> { underBar { HomeDestination(nav) } }
            composable<RecipesRoute> { entry -> underBar { RecipesDestination(entry.toRoute(), nav) } }
            composable<MealPlanRoute> { underBar { MealPlanDestination(nav, snackbar) } }
            composable<ShoppingRoute> { underBar { ShoppingDestination() } }
            composable<RecipeRoute> { RecipeDestination(it.toRoute<RecipeRoute>().slug, nav, snackbar) }
            composable<CookRoute> { entry ->
                val route = entry.toRoute<CookRoute>()
                CookDestination(route.slug, route.scale, nav)
            }
            composable<CookbookRoute> { CookbookDestination(it.toRoute(), nav) }
            composable<AddRecipeRoute> { AddRecipeDestination(it.toRoute<AddRecipeRoute>().sharedUrl, nav) }
            composable<EditorRoute> { EditorDestination(it.toRoute<EditorRoute>().slug, nav) }
            composable<SettingsRoute> { SettingsDestination(nav) }
            composable<LicensesRoute> { LicensesDestination(nav) }
        }
        AnimatedVisibility(
            visible = showBar,
            enter = fadeIn(tween(BAR_DURATION)) + slideInVertically(tween(BAR_DURATION, easing = Transitions.Settle)) { it / 2 },
            exit = fadeOut(tween(BAR_DURATION / 2)) + slideOutVertically(tween(BAR_DURATION)) { it / 2 },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 24.dp, end = 24.dp, bottom = FloatingBarGap),
        ) {
            FloatingBar(
                selected = Tab.entries.firstOrNull { destination?.isTab(it) == true },
                onSelect = nav::navigateToTab,
            )
        }
        AromaSnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showBar) barInset else navigationInset),
        )
    }
}

private const val BAR_DURATION = 260

private fun NavDestination.isTab(tab: Tab): Boolean = hasRoute(tab.route::class)

/** Switches tabs the way Android users expect: one copy per tab, state kept when coming back. */
fun NavController.navigateToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
