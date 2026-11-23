package app.aroma.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.ImageSize
import app.aroma.core.model.Meal
import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.RecipeImage
import app.aroma.core.model.RecipeSummary
import app.aroma.data.AromaError
import app.aroma.ui.components.AromaPullToRefreshBox
import app.aroma.ui.components.Avatar
import app.aroma.ui.components.EdgeActionPadding
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.IconAction
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PageHeader
import app.aroma.ui.components.PhotoPress
import app.aroma.ui.components.Placeholder
import app.aroma.ui.components.PlaceholderLine
import app.aroma.ui.components.RatingStars
import app.aroma.ui.components.RecipeCard
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.RecipeMeta
import app.aroma.ui.components.ScrollToTopOnReselect
import app.aroma.ui.components.SearchPill
import app.aroma.ui.components.SectionHeader
import app.aroma.ui.components.SoftCard
import app.aroma.ui.components.StatusBarScrim
import app.aroma.ui.components.mealTypeLabel
import app.aroma.ui.components.ratingTouchInset
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Motion.Settle
import app.aroma.ui.theme.PageEndSpace
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.SectionSpace
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.ThumbnailSize
import app.aroma.ui.theme.TouchTarget
import app.aroma.ui.theme.extras

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
    val photo: String? = null,
    val today: List<MealPlanEntry> = emptyList(),
    val meal: Meal = Meal.Dinner,
    /** Recipes for [meal], or any recipes where the household has no category or tag for it. */
    val suggestions: List<RecipeSummary> = emptyList(),
    val suggestionsForMeal: Boolean = false,
    val cookedLongAgo: List<RecipeSummary> = emptyList(),
    val recent: List<RecipeSummary> = emptyList(),
    val favorites: List<RecipeSummary> = emptyList(),
    val toRate: RateReminder? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val error: AromaError? = null,
)

data class HomeActions(
    val onSearch: () -> Unit = {},
    val onOpenRecipe: (String) -> Unit = {},
    val onRecipeMenu: (RecipeSummary) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onShowAllRecipes: () -> Unit = {},
    val onShowFavorites: () -> Unit = {},
    val onOpenMealPlan: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRateCooked: (Int) -> Unit = {},
    val onDismissReminder: () -> Unit = {},
)

private const val RATE_STAR_SIZE = 28

/** The profile button, which grows beside a greeting of two lines. */
private val ProfileSize = 40.dp
private val LargeProfileSize = 56.dp
private val CarouselCardWidth = 160.dp

@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(listState)
    AromaPullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = actions.onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + PageEndSpace),
        ) {
            item(key = "header") {
                HomeHeader(state, actions)
            }
            item(key = "rate") {
                RatePromptSlot(state.toRate, actions)
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
                    if (state.suggestions.isNotEmpty()) {
                        item(key = "suggestions") {
                            RecipeCarousel(
                                title = stringResource(if (state.suggestionsForMeal) state.meal.titleRes() else R.string.home_ideas),
                                recipes = state.suggestions,
                                actions = actions,
                            )
                        }
                    }
                    if (state.recent.isNotEmpty()) {
                        item(key = "recent") {
                            RecipeCarousel(
                                stringResource(R.string.home_recent),
                                state.recent,
                                actions,
                                onShowAll = actions.onShowAllRecipes,
                            )
                        }
                    }
                    if (state.favorites.isNotEmpty()) {
                        item(key = "favorites") {
                            RecipeCarousel(
                                stringResource(R.string.home_favorites),
                                state.favorites,
                                actions,
                                onShowAll = actions.onShowFavorites,
                            )
                        }
                    }
                    if (state.cookedLongAgo.isNotEmpty()) {
                        item(key = "cookedLongAgo") {
                            RecipeCarousel(stringResource(R.string.home_cooked_long_ago), state.cookedLongAgo, actions)
                        }
                    }
                }
            }
        }
        StatusBarScrim()
    }
}

/** The greeting with the user's photo beside it, and the search that opens on the Recipes tab. */
@Composable
private fun HomeHeader(state: HomeUiState, actions: HomeActions) {
    val greeting =
        stringResource(
            when (state.greeting) {
                Greeting.Morning -> R.string.greeting_morning
                Greeting.Afternoon -> R.string.greeting_afternoon
                Greeting.Evening -> R.string.greeting_evening
            },
        )
    val name = state.firstName?.takeIf { it.isNotBlank() }
    Column {
        PageHeader(title = if (name == null) greeting else "$greeting,", accent = name) { tall ->
            ProfileButton(name, state.photo, large = tall, onClick = actions.onOpenSettings)
        }
        SearchPill(
            placeholder = stringResource(R.string.search_recipes_hint),
            onClick = actions.onSearch,
            modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.l),
        )
        if (state.isOffline) OfflineNotice(Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.s))
    }
}

/**
 * The user's photo or initial, which grows beside a greeting of two lines. With the header's end
 * padding, the circle ends on the right margin.
 */
@Composable
private fun ProfileButton(name: String?, photo: String?, large: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.home_open_profile)
    val size by animateDpAsState(if (large) LargeProfileSize else ProfileSize, tween(Motion.STATE, easing = Settle), label = "profileSize")
    Box(
        modifier =
            Modifier
                .padding(start = Space.s, end = ScreenMargin - EdgeActionPadding)
                .size(size)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (name != null) {
            Avatar(name, photo, size)
        } else {
            Box(Modifier.size(size).background(extras.card, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = null, modifier = Modifier.size(Glyph.m))
            }
        }
    }
}

/** Holds the rate prompt, so it can fade and fold away once it is answered or dismissed. */
@Composable
private fun RatePromptSlot(toRate: RateReminder?, actions: HomeActions) {
    AnimatedContent(
        targetState = toRate,
        contentKey = { it?.recipeId },
        transitionSpec = {
            fadeIn(tween(Motion.EXPAND, easing = Settle)) togetherWith fadeOut(tween(Motion.EXPAND, easing = Settle)) using
                SizeTransform(clip = true) { _, _ -> tween(Motion.EXPAND, easing = Settle) }
        },
        modifier = Modifier.fillMaxWidth(),
        label = "ratePrompt",
    ) { reminder ->
        if (reminder != null) RatePrompt(reminder, actions)
    }
}

/** Asks, without a notification, how the last recipe cooked turned out. */
@Composable
private fun RatePrompt(toRate: RateReminder, actions: HomeActions) {
    SoftCard(Modifier.padding(top = Space.l)) {
        Box {
            Row(Modifier.padding(start = CardPadding, top = Space.s, bottom = Space.xs)) {
                RecipeImage(
                    image = toRate.image,
                    size = ImageSize.Thumbnail,
                    shape = MaterialTheme.shapes.small,
                    fallbackIconSize = Glyph.m,
                    modifier =
                        Modifier
                            .padding(top = Space.xxs)
                            .size(ThumbnailSize)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClickLabel = stringResource(R.string.home_rate_open)) { actions.onOpenRecipe(toRate.slug) },
                )
                Spacer(Modifier.width(Space.m))
                // The words keep clear of the close button; the stars may pass under it, so five fit on a small phone.
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.cook_rate_question),
                        style = AromaType.Small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = TouchTarget),
                    )
                    Text(
                        text = toRate.name,
                        style = AromaType.Name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = TouchTarget),
                    )
                    RatingStars(
                        rating = toRate.stars?.toDouble(),
                        starSize = RATE_STAR_SIZE,
                        onRate = actions.onRateCooked,
                        // The stars' touch areas reach past the text column; the first glyph stands on it.
                        modifier = Modifier.offset(x = -ratingTouchInset(RATE_STAR_SIZE)),
                    )
                }
            }
            IconAction(
                R.drawable.ic_close,
                stringResource(R.string.home_rate_dismiss),
                actions.onDismissReminder,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = Space.s),
            )
        }
    }
}

@Composable
private fun TodaySection(entries: List<MealPlanEntry>, actions: HomeActions) {
    Column {
        SectionHeader(
            title = stringResource(R.string.home_today),
            actionLabel = stringResource(R.string.home_open_plan),
            onAction = actions.onOpenMealPlan,
        )
        Spacer(Modifier.height(Space.xs))
        val open = { entry: MealPlanEntry -> entry.recipe?.let { actions.onOpenRecipe(it.slug) } ?: actions.onOpenMealPlan() }
        when (entries.size) {
            0 -> EmptyToday(onPlan = actions.onOpenMealPlan)
            1 -> TodayCard(entries.single(), onClick = { open(entries.single()) }, Modifier.padding(horizontal = ScreenMargin))
            else -> TodayCarousel(entries, open)
        }
    }
}

/** Several meals today: each a column wide less 32 dp, so the next one shows at the edge. */
@Composable
private fun TodayCarousel(entries: List<MealPlanEntry>, onOpen: (MealPlanEntry) -> Unit) {
    val rowState = rememberLazyListState()
    BoxWithConstraints {
        val itemWidth = maxWidth - ScreenMargin * 2 - Space.xl
        LazyRow(
            state = rowState,
            flingBehavior = rememberSnapFlingBehavior(rowState, SnapPosition.Start),
            contentPadding = PaddingValues(horizontal = ScreenMargin),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            items(entries, key = { it.id }) { entry ->
                TodayCard(entry, onClick = { onOpen(entry) }, Modifier.width(itemWidth), holdTwoLines = true)
            }
        }
    }
}

/**
 * Today's meal as a 3:2 photo with its caption set underneath; pressing dims the photo. With
 * [holdTwoLines], the card is always as tall as a two-line title needs, so a row of meals keeps
 * one height while it scrolls.
 */
@Composable
private fun TodayCard(entry: MealPlanEntry, onClick: () -> Unit, modifier: Modifier = Modifier, holdTwoLines: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {},
    ) {
        RecipeImage(
            image = entry.recipe?.image,
            size = ImageSize.Medium,
            shape = MaterialTheme.shapes.medium,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f)
                    .indication(interaction, PhotoPress),
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = mealTypeLabel(entry.type),
            style = AromaType.Small,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.recipe?.name ?: entry.title,
            style = AromaType.SectionTitle,
            maxLines = 2,
            minLines = if (holdTwoLines) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        val recipe = entry.recipe
        if (recipe != null) {
            RecipeMeta(recipe, Modifier.padding(top = Space.xxs))
        } else if (entry.note.isNotBlank()) {
            Text(
                text = entry.note,
                style = AromaType.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.xxs),
            )
        }
    }
}

/** Nothing planned yet: a card that says so and leads to the plan. */
@Composable
private fun EmptyToday(onPlan: () -> Unit) {
    SoftCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onPlan)
                .padding(horizontal = CardPadding, vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_today_empty), style = AromaType.Body)
                Text(stringResource(R.string.home_today_plan), style = AromaType.Small, color = MaterialTheme.colorScheme.primary)
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Glyph.m),
            )
        }
    }
}

@Composable
private fun RecipeCarousel(title: String, recipes: List<RecipeSummary>, actions: HomeActions, onShowAll: (() -> Unit)? = null) {
    Column {
        SectionHeader(title = title, actionLabel = onShowAll?.let { stringResource(R.string.action_show_all) }, onAction = onShowAll)
        Spacer(Modifier.height(Space.xs))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenMargin),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            items(recipes, key = { it.id }) { recipe ->
                RecipeCard(
                    recipe = recipe,
                    onClick = { actions.onOpenRecipe(recipe.slug) },
                    onLongClick = { actions.onRecipeMenu(recipe) },
                    imageAspectRatio = 4f / 5f,
                    modifier = Modifier.width(CarouselCardWidth),
                )
            }
        }
    }
}

/** How long the outline's lines are: under Today's photo a meal, a title and the meta line; under a card's, its title and meta. */
private val TodayLineWidths = listOf(72.dp, 240.dp, 96.dp)
private val CardLineWidths = listOf(128.dp, 72.dp)
private val SectionTitleWidth = 120.dp

/** The page's outline while it loads: the Today photo and caption, then a carousel of cards. */
@Composable
private fun HomeLoading() {
    Column {
        SectionTitlePlaceholder()
        Spacer(Modifier.height(Space.xs))
        Column(Modifier.padding(horizontal = ScreenMargin)) {
            Placeholder(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f),
            )
            Spacer(Modifier.height(Space.s))
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                TodayLineWidths.forEach { PlaceholderLine(Modifier.width(it)) }
            }
        }
        SectionTitlePlaceholder()
        Spacer(Modifier.height(Space.xs))
        Row(
            modifier =
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .padding(horizontal = ScreenMargin),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            repeat(3) {
                Column(Modifier.width(CarouselCardWidth)) {
                    Placeholder(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 5f),
                    )
                    Spacer(Modifier.height(Space.s))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        CardLineWidths.forEach { PlaceholderLine(Modifier.width(it)) }
                    }
                }
            }
        }
    }
}

/** Stands in for a [SectionHeader], in the same place. */
@Composable
private fun SectionTitlePlaceholder() {
    Box(
        Modifier
            .padding(start = ScreenMargin, end = ScreenMargin, top = SectionSpace)
            .height(TouchTarget),
        contentAlignment = Alignment.CenterStart,
    ) { PlaceholderLine(Modifier.width(SectionTitleWidth)) }
}

private fun Meal.titleRes(): Int =
    when (this) {
        Meal.Breakfast -> R.string.home_for_breakfast
        Meal.Lunch -> R.string.home_for_lunch
        Meal.Snack -> R.string.home_for_snack
        Meal.Dinner -> R.string.home_for_dinner
    }
