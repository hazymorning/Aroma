package app.aroma.feature.home

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import app.aroma.R
import app.aroma.core.model.Cookbook
import app.aroma.core.model.ImageSize
import app.aroma.core.model.Meal
import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.RecipeImage
import app.aroma.core.model.RecipeSummary
import app.aroma.data.AromaError
import app.aroma.ui.components.AromaPullToRefreshBox
import app.aroma.ui.components.Avatar
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.HeaderAction
import app.aroma.ui.components.Kicker
import app.aroma.ui.components.LinkTone
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PageEndSpace
import app.aroma.ui.components.PageHeader
import app.aroma.ui.components.Placeholder
import app.aroma.ui.components.QuietLink
import app.aroma.ui.components.RatingStars
import app.aroma.ui.components.RecipeCard
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.RecipeMeta
import app.aroma.ui.components.ScrollToTopOnReselect
import app.aroma.ui.components.SectionHeader
import app.aroma.ui.components.SoftCard
import app.aroma.ui.components.StatusBarScrim
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.mealTypeLabel
import app.aroma.ui.components.ratingTouchInset
import app.aroma.ui.navigation.Transitions.Settle
import app.aroma.ui.theme.CardTitle
import app.aroma.ui.theme.Lines
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.extras
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    val date: LocalDate? = null,
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
private val DismissGlyph = 20.dp
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
                    if (state.cookedLongAgo.isNotEmpty()) {
                        item(key = "cookedLongAgo") {
                            RecipeCarousel(stringResource(R.string.home_cooked_long_ago), state.cookedLongAgo, actions)
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
                }
            }
        }
        StatusBarScrim()
    }
}

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
        PageHeader(
            title = if (name == null) greeting else "$greeting,",
            kicker = state.date?.let { dateline(it) },
            accent = name,
        ) { tall ->
            HeaderAction(R.drawable.ic_search, stringResource(R.string.search_recipes_hint), actions.onSearch)
            ProfileButton(name, state.photo, large = tall, onClick = actions.onOpenSettings)
        }
        if (state.isOffline) OfflineNotice(Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xs))
    }
}

/** The weekday and date as a cookbook would print them: "Montag, 23. November", "Monday, November 23". */
@Composable
private fun dateline(date: LocalDate): String {
    val locale = currentLocale()
    return remember(date, locale) {
        date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale))
    }
}

/**
 * The user's photo or initial in a fine ring, which grows beside a greeting of two lines; with the
 * header's end padding, the ring ends on the right margin.
 */
@Composable
private fun ProfileButton(name: String?, photo: String?, large: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.home_open_profile)
    val size by animateDpAsState(if (large) 56.dp else 40.dp, tween(Motion.STATE, easing = Settle), label = "profileSize")
    Box(
        modifier =
            Modifier
                .padding(end = Space.s)
                .size(size + Space.xs)
                .clickable(
                    interactionSource = null,
                    indication = ripple(bounded = false, radius = size / 2),
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (name != null) {
            Avatar(name, photo, size)
        } else {
            Box(Modifier.size(size).border(Lines.control, extras.hairlineStrong, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = null, modifier = Modifier.size(20.dp))
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

/** Asks, without a notification, how the last recipe cooked turned out, on a card washed in sage. */
@Composable
private fun RatePrompt(toRate: RateReminder, actions: HomeActions) {
    SoftCard(Modifier.padding(top = Space.l), highlight = true) {
        Row(Modifier.padding(horizontal = ScreenMargin, vertical = Space.s)) {
            RecipeImage(
                image = toRate.image,
                size = ImageSize.Thumbnail,
                shape = MaterialTheme.shapes.small,
                fallbackIconSize = 20.dp,
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClickLabel = stringResource(R.string.home_rate_open)) { actions.onOpenRecipe(toRate.slug) },
            )
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Kicker(stringResource(R.string.cook_rate_question))
                Spacer(Modifier.height(Space.xxs))
                Text(
                    text = toRate.name,
                    style = CardTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Space.s))
                RatingStars(
                    rating = toRate.stars?.toDouble(),
                    starSize = RATE_STAR_SIZE,
                    onRate = actions.onRateCooked,
                    // The first star's tip is drawn 4 dp inside its box; the nudge sets it on the text column.
                    modifier =
                        Modifier
                            .touchOutdent(ratingTouchInset(RATE_STAR_SIZE))
                            .offset(x = -Space.xxs),
                )
            }
            Spacer(Modifier.width(Space.m))
            IconButton(
                onClick = actions.onDismissReminder,
                // The cross is drawn 4 dp inside its box; the nudge puts its arms on the right margin.
                modifier =
                    Modifier
                        .touchOutdent((48.dp - DismissGlyph) / 2)
                        .offset(x = Space.xxs),
            ) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.home_rate_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(DismissGlyph),
                )
            }
        }
    }
}

@Composable
private fun TodaySection(entries: List<MealPlanEntry>, actions: HomeActions) {
    Column(Modifier.padding(top = Space.xxl)) {
        SectionHeader(
            title = stringResource(R.string.home_today),
            actionLabel = stringResource(R.string.home_open_plan),
            onAction = actions.onOpenMealPlan,
        )
        Spacer(Modifier.height(Space.m))
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
    val pressed by interaction.collectIsPressedAsState()
    val photoAlpha by animateFloatAsState(if (pressed) 0.88f else 1f, tween(if (pressed) Motion.PRESS else Motion.STATE), label = "press")
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
                    .graphicsLayer { alpha = photoAlpha },
        )
        Spacer(Modifier.height(Space.m))
        Box {
            if (holdTwoLines) {
                TodayCaption(
                    entry,
                    titleLines = 2,
                    modifier =
                        Modifier
                            .alpha(0f)
                            .clearAndSetSemantics {},
                )
            }
            TodayCaption(entry)
        }
    }
}

/** Meal type, title, and the recipe's time and rating (or the note of a meal without a recipe). */
@Composable
private fun TodayCaption(entry: MealPlanEntry, modifier: Modifier = Modifier, titleLines: Int = 1) {
    Column(modifier) {
        Kicker(mealTypeLabel(entry.type))
        Spacer(Modifier.height(Space.xxs))
        Text(
            text = entry.recipe?.name ?: entry.title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 2,
            minLines = titleLines,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Space.xxs))
        val recipe = entry.recipe
        if (recipe != null) {
            RecipeMeta(recipe)
        } else {
            Text(
                text = entry.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyToday(onPlan: () -> Unit) {
    SoftCard {
        Column(Modifier.padding(horizontal = ScreenMargin, vertical = Space.xs)) {
            Text(
                text = stringResource(R.string.home_today_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QuietLink(stringResource(R.string.action_plan), onPlan, tone = LinkTone.Ink, chevron = true)
        }
    }
}

@Composable
private fun RecipeCarousel(title: String, recipes: List<RecipeSummary>, actions: HomeActions, onShowAll: (() -> Unit)? = null) {
    Column(Modifier.padding(top = Space.xxl)) {
        SectionHeader(title = title, actionLabel = onShowAll?.let { stringResource(R.string.action_show_all) }, onAction = onShowAll)
        Spacer(Modifier.height(Space.m))
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

/** The page's outline while it loads: the Today photo and caption, then a carousel of cards. */
@Composable
private fun HomeLoading() {
    Column(Modifier.padding(top = Space.xxl)) {
        SectionTitlePlaceholder()
        Spacer(Modifier.height(Space.m))
        Column(Modifier.padding(horizontal = ScreenMargin)) {
            Placeholder(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f),
            )
            Spacer(Modifier.height(Space.m))
            TextLinePlaceholder(72.dp)
            Spacer(Modifier.height(Space.xs))
            TextLinePlaceholder(240.dp)
            Spacer(Modifier.height(Space.xs))
            TextLinePlaceholder(96.dp)
        }
        Spacer(Modifier.height(Space.xxl))
        SectionTitlePlaceholder()
        Spacer(Modifier.height(Space.m))
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
                    TextLinePlaceholder(128.dp)
                    Spacer(Modifier.height(Space.xs))
                    TextLinePlaceholder(72.dp)
                }
            }
        }
    }
}

/** Stands in for a [SectionHeader], centred in the same 48 dp. */
@Composable
private fun SectionTitlePlaceholder() {
    Box(
        Modifier
            .padding(horizontal = ScreenMargin)
            .height(48.dp),
        contentAlignment = Alignment.CenterStart,
    ) { TextLinePlaceholder(120.dp) }
}

@Composable
private fun TextLinePlaceholder(width: Dp) {
    Placeholder(
        Modifier
            .width(width)
            .height(12.dp),
        shape = MaterialTheme.shapes.extraSmall,
    )
}

/**
 * Lets a control's touch padding hang over its surroundings, so the drawn glyph rather than the
 * 48 dp target lines up with the text column and the margins.
 */
private fun Modifier.touchOutdent(inset: Dp): Modifier =
    layout { measurable, constraints ->
        val px = inset.roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = 2 * px, vertical = 2 * px))
        layout(placeable.width - 2 * px, placeable.height - 2 * px) { placeable.place(-px, -px) }
    }

private fun Meal.titleRes(): Int =
    when (this) {
        Meal.Breakfast -> R.string.home_for_breakfast
        Meal.Lunch -> R.string.home_for_lunch
        Meal.Snack -> R.string.home_for_snack
        Meal.Dinner -> R.string.home_for_dinner
    }
