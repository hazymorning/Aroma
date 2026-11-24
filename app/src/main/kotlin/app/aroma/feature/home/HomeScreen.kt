package app.aroma.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.ripple
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
import app.aroma.ui.components.animateRow
import app.aroma.ui.components.mealLabel
import app.aroma.ui.components.mealTypeLabel
import app.aroma.ui.components.ratingInkBelow
import app.aroma.ui.components.ratingInkStart
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Motion.Glide
import app.aroma.ui.theme.Motion.Settle
import app.aroma.ui.theme.PageEndSpace
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.SectionSpace
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.ThumbnailSize
import app.aroma.ui.theme.TouchTarget

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
    /** For the initial on the profile button; the greeting itself names no one. */
    val firstName: String? = null,
    val today: List<MealPlanEntry> = emptyList(),
    val meal: Meal = Meal.Dinner,
    /** Recipes for [meal], or any recipes where the household has no category or tag for it. */
    val suggestions: List<RecipeSummary> = emptyList(),
    val suggestionsForMeal: Boolean = false,
    /** Recipes ready in half an hour, all in, which work without any categories. */
    val quick: List<RecipeSummary> = emptyList(),
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
    /** All recipes for a meal, on a page under the given title. */
    val onShowMeal: (Meal, String) -> Unit = { _, _ -> },
    /** All the quick recipes, on a page under the given title. */
    val onShowQuick: (String) -> Unit = {},
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
                RatePromptSlot(state.toRate, actions, Modifier.animateRow(this))
            }
            // The outline and the error give way at once; the sections fade in where they land.
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
                        TodaySection(state.today, actions, Modifier.animateRow(this))
                    }
                    // From what suits the hour to what is worth remembering; each only once it has something to show.
                    if (state.suggestions.isNotEmpty()) {
                        item(key = "suggestions") {
                            val mealName = mealLabel(state.meal)
                            RecipeCarousel(
                                title = if (state.suggestionsForMeal) mealTitle(state.meal) else stringResource(R.string.home_ideas),
                                recipes = state.suggestions,
                                actions = actions,
                                onShowAll = { actions.onShowMeal(state.meal, mealName) }.takeIf { state.suggestionsForMeal },
                                modifier = Modifier.animateRow(this),
                            )
                        }
                    }
                    if (state.quick.isNotEmpty()) {
                        item(key = "quick") {
                            val title = stringResource(R.string.home_quick)
                            RecipeCarousel(
                                title = title,
                                recipes = state.quick,
                                actions = actions,
                                onShowAll = { actions.onShowQuick(title) },
                                modifier = Modifier.animateRow(this),
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
                                modifier = Modifier.animateRow(this),
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
                                modifier = Modifier.animateRow(this),
                            )
                        }
                    }
                    if (state.cookedLongAgo.isNotEmpty()) {
                        item(key = "cookedLongAgo") {
                            RecipeCarousel(
                                stringResource(R.string.home_cooked_long_ago),
                                state.cookedLongAgo,
                                actions,
                                modifier = Modifier.animateRow(this),
                            )
                        }
                    }
                }
            }
        }
        StatusBarScrim()
    }
}

/** The greeting, which names no one, with the user's initial beside it, and the search that opens on the Recipes tab. */
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
        PageHeader(title = greeting) { tall ->
            ProfileButton(name, large = tall, onClick = actions.onOpenSettings)
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
 * The user's initial, or a person while there is no name, which grows beside a greeting of two
 * lines. With the header's end padding, the circle ends on the right margin; its target reaches
 * 48 dp around a smaller circle, and a press shows on the circle alone.
 */
@Composable
private fun ProfileButton(name: String?, large: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.home_open_profile)
    val size by animateDpAsState(if (large) LargeProfileSize else ProfileSize, tween(Motion.STATE, easing = Glide), label = "profileSize")
    val target = maxOf(size, TouchTarget)
    // The paddings give back what the target reaches beyond the circle, so the circle keeps its place.
    val reach = (target - size) / 2
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .padding(start = Space.s - reach, end = ScreenMargin - EdgeActionPadding - reach)
                .size(target)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Avatar(
            name,
            size,
            Modifier
                .clip(CircleShape)
                .indication(interaction, ripple()),
        )
    }
}

/**
 * Holds the rate prompt. Answered or dismissed, the card fades where it stands and gives up its room
 * once it has all but faded, and the page below glides in; the glide starts easy, so nothing passes
 * over the card while it can still be seen. A new prompt takes its room at once and fades in as the
 * page glides aside.
 */
@Composable
private fun RatePromptSlot(toRate: RateReminder?, actions: HomeActions, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = toRate,
        contentKey = { it?.recipeId },
        transitionSpec = {
            val arrive = fadeIn(tween(Motion.STATE, easing = Settle)) + scaleIn(tween(Motion.STATE, easing = Settle), RATE_SCALE)
            val leave = fadeOut(tween(Motion.PRESS)) + scaleOut(tween(Motion.PRESS), RATE_SCALE)
            arrive togetherWith leave using
                SizeTransform(clip = false) { initial, target -> if (target.height < initial.height) snap(Motion.PRESS / 2) else snap() }
        },
        modifier = modifier.fillMaxWidth(),
        label = "ratePrompt",
    ) { reminder ->
        if (reminder != null) RatePrompt(reminder, actions)
    }
}

/** How small the rate prompt is as it starts to fade in, and once it has faded out. */
private const val RATE_SCALE = 0.96f

/** Asks, without a notification, how the last recipe cooked turned out. */
@Composable
private fun RatePrompt(toRate: RateReminder, actions: HomeActions) {
    SoftCard(Modifier.padding(top = Space.l)) {
        Box {
            // As much space under the stars' ink as above the photo; their own targets take most of it.
            Row(Modifier.padding(start = CardPadding, top = Space.s, bottom = Space.s + Space.xxs - ratingInkBelow(RATE_STAR_SIZE))) {
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
                        // The stars' touch areas reach past the text column; the first star's ink stands on it.
                        modifier = Modifier.offset(x = -ratingInkStart(RATE_STAR_SIZE)),
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
private fun TodaySection(entries: List<MealPlanEntry>, actions: HomeActions, modifier: Modifier = Modifier) {
    Column(modifier) {
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
private fun RecipeCarousel(
    title: String,
    recipes: List<RecipeSummary>,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    onShowAll: (() -> Unit)? = null,
) {
    Column(modifier) {
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

/** The suggestions' titles for the meals that have one of their own; any other meal goes by its name. */
private val MealTitles =
    mapOf(
        Meal.Breakfast to R.string.home_for_breakfast,
        Meal.Lunch to R.string.home_for_lunch,
        Meal.Snack to R.string.home_for_snack,
        Meal.Dinner to R.string.home_for_dinner,
    )

@Composable
private fun mealTitle(meal: Meal): String = MealTitles[meal]?.let { stringResource(it) } ?: mealLabel(meal)
