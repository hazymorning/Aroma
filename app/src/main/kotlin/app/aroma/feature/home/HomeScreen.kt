package app.aroma.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.Cookbook
import app.aroma.core.model.ImageSize
import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.RecipeImage
import app.aroma.core.model.RecipeSummary
import app.aroma.data.AromaError
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.Placeholder
import app.aroma.ui.components.RatingStars
import app.aroma.ui.components.RecipeCard
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.SectionHeader
import app.aroma.ui.components.mealTypeLabel

enum class Greeting { Morning, Afternoon, Evening }

/** A recipe cooked lately that still waits for the user's rating; [stars] once tapped. */
data class RateReminder(
    val recipeId: String,
    val slug: String,
    val name: String,
    val image: RecipeImage?,
    val stars: Int? = null,
)

data class HomeUiState(
    val greeting: Greeting = Greeting.Afternoon,
    val firstName: String? = null,
    val today: List<MealPlanEntry> = emptyList(),
    val recent: List<RecipeSummary> = emptyList(),
    val favorites: List<RecipeSummary> = emptyList(),
    val cookbooks: List<Cookbook> = emptyList(),
    val toRate: RateReminder? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val error: AromaError? = null,
)

data class HomeActions(
    val onSearch: () -> Unit = {},
    val onOpenRecipe: (String) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onShowAllRecipes: () -> Unit = {},
    val onShowFavorites: () -> Unit = {},
    val onOpenMealPlan: () -> Unit = {},
    val onOpenCookbook: (Cookbook) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRateCooked: (Int) -> Unit = {},
    val onDismissReminder: () -> Unit = {},
)

@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = actions.onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "header") {
                HomeHeader(state.greeting, state.firstName, actions.onOpenSettings)
            }
            item(key = "search") {
                SearchEntry(onClick = actions.onSearch)
            }
            if (state.isOffline) {
                item(key = "offline") { OfflineNotice(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            }
            state.toRate?.let { toRate ->
                item(key = "rate") {
                    RateCard(toRate, actions, Modifier.animateItem())
                }
            }
            when {
                state.isLoading -> {
                    item(key = "loading") { HomeLoading() }
                }

                state.error != null && state.recent.isEmpty() -> {
                    item(key = "error") {
                        ErrorState(state.error, onRetry = actions.onRefresh, modifier = Modifier.fillMaxWidth())
                    }
                }

                else -> {
                    item(key = "today") {
                        TodaySection(state.today, actions)
                    }
                    if (state.recent.isNotEmpty()) {
                        item(key = "recent") {
                            RecipeCarousel(
                                title = stringResource(R.string.home_recent),
                                recipes = state.recent,
                                onOpen = actions.onOpenRecipe,
                                actionLabel = stringResource(R.string.action_show_all),
                                onAction = actions.onShowAllRecipes,
                            )
                        }
                    }
                    if (state.favorites.isNotEmpty()) {
                        item(key = "favorites") {
                            RecipeCarousel(
                                title = stringResource(R.string.home_favorites),
                                recipes = state.favorites,
                                onOpen = actions.onOpenRecipe,
                                actionLabel = stringResource(R.string.action_show_all),
                                onAction = actions.onShowFavorites,
                            )
                        }
                    }
                    if (state.cookbooks.isNotEmpty()) {
                        item(key = "cookbooks") { CookbookRow(state.cookbooks, actions.onOpenCookbook) }
                    }
                }
            }
        }
    }
}

/** Asks, without a notification, how the last recipe cooked turned out. */
@Composable
private fun RateCard(toRate: RateReminder, actions: HomeActions, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            RecipeImage(
                image = toRate.image,
                size = ImageSize.Thumbnail,
                shape = MaterialTheme.shapes.medium,
                fallbackIconSize = 24.dp,
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClickLabel = stringResource(R.string.home_rate_open)) { actions.onOpenRecipe(toRate.slug) },
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.cook_rate_question),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = toRate.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                RatingStars(
                    rating = toRate.stars?.toDouble(),
                    starSize = 26,
                    onRate = actions.onRateCooked,
                    modifier = Modifier.offset(x = (-6).dp),
                )
            }
            IconButton(onClick = actions.onDismissReminder) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.home_rate_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(greeting: Greeting, firstName: String?, onOpenSettings: () -> Unit) {
    val greetingText =
        stringResource(
            when (greeting) {
                Greeting.Morning -> R.string.greeting_morning
                Greeting.Afternoon -> R.string.greeting_afternoon
                Greeting.Evening -> R.string.greeting_evening
            },
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (firstName.isNullOrBlank()) greetingText else "$greetingText,",
                style = MaterialTheme.typography.displaySmall,
            )
            if (!firstName.isNullOrBlank()) {
                Text(
                    text = firstName,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val profileLabel = stringResource(R.string.home_open_profile)
        Surface(
            onClick = onOpenSettings,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier =
                Modifier
                    .size(48.dp)
                    .semantics { contentDescription = profileLabel },
        ) {
            Box(contentAlignment = Alignment.Center) {
                val initial = firstName?.firstOrNull()?.uppercase()
                if (initial != null) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.search_recipes_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodaySection(entries: List<MealPlanEntry>, actions: HomeActions) {
    Column(Modifier.padding(top = 28.dp)) {
        SectionHeader(
            title = stringResource(R.string.home_today),
            actionLabel = stringResource(R.string.home_open_plan),
            onAction = actions.onOpenMealPlan,
        )
        Spacer(Modifier.height(8.dp))
        if (entries.isEmpty()) {
            EmptyToday(onPlan = actions.onOpenMealPlan)
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    TodayCard(
                        entry = entry,
                        onClick = { entry.recipe?.let { actions.onOpenRecipe(it.slug) } ?: actions.onOpenMealPlan() },
                        modifier = Modifier.fillParentMaxWidth(if (entries.size == 1) 1f else 0.86f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayCard(entry: MealPlanEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = entry.recipe?.name ?: entry.title
    Box(
        modifier =
            modifier
                .aspectRatio(1.45f)
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {},
    ) {
        RecipeImage(
            image = entry.recipe?.image,
            size = ImageSize.Medium,
            fallbackIconSize = 40.dp,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
        ) {
            Text(
                text = mealTypeLabel(entry.type),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyToday(onPlan: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_calendar_month),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.home_today_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = onPlan) { Text(stringResource(R.string.action_plan)) }
        }
    }
}

@Composable
private fun RecipeCarousel(
    title: String,
    recipes: List<RecipeSummary>,
    onOpen: (String) -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(Modifier.padding(top = 28.dp)) {
        SectionHeader(title = title, actionLabel = actionLabel, onAction = onAction)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(recipes, key = { it.id }) { recipe ->
                RecipeCard(recipe = recipe, onClick = { onOpen(recipe.slug) }, modifier = Modifier.width(176.dp))
            }
        }
    }
}

@Composable
private fun CookbookRow(cookbooks: List<Cookbook>, onOpen: (Cookbook) -> Unit) {
    Column(Modifier.padding(top = 28.dp)) {
        SectionHeader(title = stringResource(R.string.home_cookbooks))
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cookbooks, key = { it.id }) { cookbook ->
                Surface(
                    onClick = { onOpen(cookbook) },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.width(220.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_auto_stories),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(cookbook.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = cookbook.description.ifBlank { " " },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeLoading() {
    Column(Modifier.padding(top = 28.dp)) {
        Placeholder(
            Modifier
                .padding(horizontal = 20.dp)
                .width(140.dp)
                .height(24.dp),
        )
        Spacer(Modifier.height(16.dp))
        Placeholder(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .aspectRatio(1.45f),
            shape = MaterialTheme.shapes.extraLarge,
        )
        Spacer(Modifier.height(32.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(3) {
                Placeholder(
                    Modifier
                        .width(176.dp)
                        .aspectRatio(0.82f),
                    shape = MaterialTheme.shapes.large,
                )
            }
        }
    }
}
