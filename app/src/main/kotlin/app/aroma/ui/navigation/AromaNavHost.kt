package app.aroma.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute

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

    Scaffold(
        bottomBar = {
            // Grows and shrinks instead of popping in, so the screen above changes height smoothly.
            AnimatedVisibility(
                visible = showBar,
                enter = expandVertically(tween(BAR_DURATION), expandFrom = Alignment.Top) + fadeIn(tween(BAR_DURATION)),
                exit = shrinkVertically(tween(BAR_DURATION), shrinkTowards = Alignment.Top) + fadeOut(tween(BAR_DURATION)),
            ) {
                BottomBar(destination, nav)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
    ) { padding ->
        NavHost(
            nav,
            startDestination = HomeRoute,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = Transitions.enter,
            exitTransition = Transitions.exit,
            popEnterTransition = Transitions.popEnter,
            popExitTransition = Transitions.popExit,
            predictivePopEnterTransition = Transitions.predictivePopEnter,
            predictivePopExitTransition = Transitions.predictivePopExit,
        ) {
            composable<HomeRoute> { HomeDestination(nav) }
            composable<RecipesRoute> { RecipesDestination(it.toRoute(), nav) }
            composable<MealPlanRoute> { MealPlanDestination(nav, snackbar) }
            composable<ShoppingRoute> { ShoppingDestination() }
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

@Composable
private fun BottomBar(destination: NavDestination?, nav: NavController) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Tab.entries.forEach { tab ->
            val selected = destination?.isTab(tab) == true
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) nav.navigateToTab(tab) },
                icon = { Icon(painterResource(if (selected) tab.selectedIcon else tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                colors =
                    NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}
