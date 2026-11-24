package app.aroma.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.ImageSize
import app.aroma.core.model.Meal
import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.MealType
import app.aroma.core.model.RecipeImage
import app.aroma.core.model.RecipeSummary
import app.aroma.data.AromaError
import app.aroma.data.settings.HomeSection
import app.aroma.ui.components.AromaPullToRefreshBox
import app.aroma.ui.components.BoxHeader
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.IconButton
import app.aroma.ui.components.IconButtonReach
import app.aroma.ui.components.IconCircleMark
import app.aroma.ui.components.LargeHeader
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.MetaLine
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.ProfileButton
import app.aroma.ui.components.RatingStars
import app.aroma.ui.components.RecipeCard
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.RecipeMetaLine
import app.aroma.ui.components.RecipeRow
import app.aroma.ui.components.ScrollToTopOnReselect
import app.aroma.ui.components.SecondaryButton
import app.aroma.ui.components.SectionTitle
import app.aroma.ui.components.Skeleton
import app.aroma.ui.components.SkeletonLine
import app.aroma.ui.components.StatusBarCover
import app.aroma.ui.components.Subtitle
import app.aroma.ui.components.Text
import app.aroma.ui.components.TextAction
import app.aroma.ui.components.TonalBox
import app.aroma.ui.components.animateRow
import app.aroma.ui.components.breakable
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.mealLabel
import app.aroma.ui.components.mealTypeLabel
import app.aroma.ui.components.ratingTouchInset
import app.aroma.ui.components.recipeTime
import app.aroma.ui.components.rememberTitleLines
import app.aroma.ui.components.weekdayShort
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.BoxGap
import app.aroma.ui.theme.BoxPadding
import app.aroma.ui.theme.ContentMaxWidth
import app.aroma.ui.theme.EndSpace
import app.aroma.ui.theme.EyebrowGap
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.GridColumnGap
import app.aroma.ui.theme.HeaderGap
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.SectionGap
import app.aroma.ui.theme.SectionTitleGap
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import app.aroma.ui.theme.tabular
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Greeting { Morning, Afternoon, Evening }

/** A recipe cooked lately that still waits for the user's rating; [stars] once tapped. */
data class RateReminder(
    val recipeId: String,
    val slug: String,
    val name: String,
    val image: RecipeImage?,
    val stars: Int? = null,
)

/**
 * The start page's state. [date] is today; [week] the plan of its calendar week, Monday to Sunday.
 * [suggestions] are recipes for [meal], or any recipes where the household has no category or tag
 * for it, the same all day; [quick] are ready in half an hour; [recent] are new in the collection.
 */
data class HomeUiState(
    val greeting: Greeting = Greeting.Afternoon,
    /** For the greeting and the initial on the profile button. */
    val firstName: String? = null,
    val date: LocalDate = LocalDate.now(),
    val week: List<MealPlanEntry> = emptyList(),
    val meal: Meal = Meal.Dinner,
    val suggestions: List<RecipeSummary> = emptyList(),
    val suggestionsForMeal: Boolean = false,
    val quick: List<RecipeSummary> = emptyList(),
    val recent: List<RecipeSummary> = emptyList(),
    /** What is still to buy on the shopping list in use. */
    val openItems: Int = 0,
    /** What Home shows, as chosen in the settings. */
    val sections: Set<HomeSection> = HomeSection.SHOWN_BY_DEFAULT,
    val toRate: RateReminder? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val error: AromaError? = null,
) {
    val weekStart: LocalDate get() = date.with(DayOfWeek.MONDAY)

    /** Today's meals, in the order of the day. */
    val todays: List<MealPlanEntry> get() = week.filter { it.date == date }.sortedBy { it.type.hour }
}

data class HomeActions(
    val onSearch: () -> Unit = {},
    val onOpenRecipe: (String) -> Unit = {},
    val onCook: (String) -> Unit = {},
    val onRecipeMenu: (RecipeSummary) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    /** All recipes for a meal, on a page under the given title. */
    val onShowMeal: (Meal, String) -> Unit = { _, _ -> },
    val onShowAllRecipes: () -> Unit = {},
    /** The plan, at a day of it, or at today. */
    val onOpenPlan: (LocalDate?) -> Unit = {},
    /** Plans a suggestion for today's meal. */
    val onPlanToday: (RecipeSummary) -> Unit = {},
    val onOpenShopping: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRateCooked: (Int) -> Unit = {},
    val onDismissReminder: () -> Unit = {},
)

/** Where in the day a meal comes, to tell today's next one. */
private val MealType.hour: Int
    get() =
        when (this) {
            MealType.Breakfast -> 0
            MealType.Lunch -> 1
            MealType.Snack -> 2
            MealType.Dinner, MealType.Side -> 3
            MealType.Dessert, MealType.Drink -> 4
        }

private val Meal.hour: Int
    get() =
        when (this) {
            Meal.Breakfast -> 0
            Meal.Lunch -> 1
            Meal.Snack -> 2
            Meal.Dinner, Meal.Side -> 3
            Meal.Dessert, Meal.Drink -> 4
        }

/**
 * The start page: the greeting, today's meal as a box to cook from, the week at a glance, the
 * shopping list where something is still to buy, and inspiration in three formats below, each
 * recipe only once on the page; the settings choose which of them it shows.
 */
@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(listState)
    val inspiration = remember(state.week, state.date, state.suggestions, state.quick, state.recent, state.sections) { state.inspiration() }
    AromaPullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = actions.onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + EndSpace),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "header") { HomeHeader(state, actions, Modifier.measure()) }
            when {
                state.isLoading -> {
                    item(key = "loading") { HomeLoading(Modifier.measure()) }
                }

                state.error != null && state.recent.isEmpty() && state.week.isEmpty() -> {
                    item(key = "error") { ErrorState(state.error, onRetry = actions.onRefresh, modifier = Modifier.measure()) }
                }

                else -> {
                    val sections = state.sections
                    // Boxes stand 12 dp apart; the first under the head needs no more than the head leaves.
                    val today = HomeSection.Today in sections
                    val week = HomeSection.Week in sections
                    if (today) {
                        item(key = "today") {
                            TodayBox(
                                state = state,
                                suggestions = inspiration.forToday,
                                actions = actions,
                                modifier =
                                    Modifier
                                        .animateRow(this)
                                        .measure()
                                        .padding(horizontal = PageMargin),
                            )
                        }
                    }
                    if (week) {
                        item(key = "week") {
                            WeekBox(
                                state = state,
                                onOpenPlan = actions.onOpenPlan,
                                modifier =
                                    Modifier
                                        .animateRow(this)
                                        .measure()
                                        .padding(start = PageMargin, end = PageMargin, top = if (today) BoxGap else 0.dp),
                            )
                        }
                    }
                    if (HomeSection.Shopping in sections && state.openItems > 0) {
                        item(key = "shopping") {
                            ShoppingBox(
                                open = state.openItems,
                                onOpen = actions.onOpenShopping,
                                modifier =
                                    Modifier
                                        .animateRow(this)
                                        .measure()
                                        .padding(start = PageMargin, end = PageMargin, top = if (today || week) BoxGap else 0.dp),
                            )
                        }
                    }
                    item(key = "rate") {
                        RatePromptSlot(
                            state.toRate,
                            actions,
                            Modifier
                                .animateRow(this)
                                .measure(),
                        )
                    }
                    if (inspiration.ideas.isNotEmpty()) {
                        item(key = "ideas") {
                            val mealName = mealLabel(state.meal)
                            Ideas(
                                recipes = inspiration.ideas,
                                actions = actions,
                                onShowAll =
                                    if (state.suggestionsForMeal) {
                                        { actions.onShowMeal(state.meal, mealName) }
                                    } else {
                                        actions.onShowAllRecipes
                                    },
                                modifier =
                                    Modifier
                                        .animateRow(this)
                                        .measure(),
                            )
                        }
                    }
                    if (inspiration.quick.isNotEmpty()) {
                        item(key = "quick") {
                            Quick(
                                inspiration.quick,
                                actions,
                                Modifier
                                    .animateRow(this)
                                    .measure(),
                            )
                        }
                    }
                    inspiration.newest?.let { newest ->
                        item(key = "newest") {
                            Newest(
                                newest,
                                actions,
                                Modifier
                                    .animateRow(this)
                                    .measure(),
                            )
                        }
                    }
                }
            }
        }
        StatusBarCover()
    }
}

/** The page keeps a reading measure on wide screens. */
private fun Modifier.measure(): Modifier = widthIn(max = ContentMaxWidth).fillMaxWidth()

/** What the inspiration shows: suggestions for an unplanned today, the ideas, the quick ones and the newest, none twice. */
internal data class Inspiration(
    val forToday: List<RecipeSummary>,
    val ideas: List<RecipeSummary>,
    val quick: List<RecipeSummary>,
    val newest: RecipeSummary?,
)

/**
 * The page's recipes from the top down, each only once; a section that cannot show three gives way,
 * and the large one needs one. Today's planned meals come first and are shown in their box.
 */
internal fun HomeUiState.inspiration(): Inspiration {
    val seen = if (HomeSection.Today in sections) todays.mapNotNull { it.recipe?.id }.toMutableSet() else mutableSetOf()

    fun pick(from: List<RecipeSummary>, most: Int, least: Int): List<RecipeSummary> {
        val picked = from.filter { it.id !in seen }.distinctBy { it.id }.take(most)
        if (picked.size < least) return emptyList()
        seen += picked.map { it.id }
        return picked
    }
    // A section left out of Home leaves its recipes to the others.
    val forToday = if (HomeSection.Today in sections && todays.isEmpty()) pick(suggestions, TODAY_SUGGESTIONS, 1) else emptyList()
    val ideas = if (HomeSection.Ideas in sections) pick(suggestions, IDEAS, SECTION_LEAST) else emptyList()
    val quick = if (HomeSection.Quick in sections) pick(quick, QUICK_ROWS, SECTION_LEAST) else emptyList()
    val newest = if (HomeSection.Newest in sections) pick(recent, 1, 1).firstOrNull() else null
    return Inspiration(forToday, ideas, quick, newest)
}

private const val TODAY_SUGGESTIONS = 3
private const val IDEAS = 8
private const val QUICK_ROWS = 3
private const val SECTION_LEAST = 3

/**
 * "Guten Abend, Küchengenie" and today's date, with the search and the profile as icon buttons. The
 * greeting calls the user by a kitchen nickname of the day, one for everyone whoever they are, each
 * long enough to balance the greeting on its own line; it changes from day to day, not within one.
 */
@Composable
private fun HomeHeader(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val name = state.firstName?.takeIf { it.isNotBlank() }
    val nicknames = stringArrayResource(R.array.greeting_nicknames)
    val nickname = nicknames[Math.floorMod(state.date.toEpochDay(), nicknames.size.toLong()).toInt()]
    val greeting =
        when (state.greeting) {
            Greeting.Morning -> stringResource(R.string.greeting_morning_name, nickname)
            Greeting.Afternoon -> stringResource(R.string.greeting_afternoon_name, nickname)
            Greeting.Evening -> stringResource(R.string.greeting_evening_name, nickname)
        }
    val locale = currentLocale()
    val date = remember(state.date, locale) { longDate(state.date, locale) }
    val buttons: @Composable () -> Unit = {
        IconButton(R.drawable.ic_search, stringResource(R.string.search_recipes_hint), actions.onSearch)
        ProfileButton(name, stringResource(R.string.home_open_profile), actions.onOpenSettings)
    }
    Column(modifier.padding(bottom = HeaderGap)) {
        // "Guten Morgen," does not fit beside the buttons on the narrowest phones: they then stand in
        // a row of their own above it, as a pushed page's back button does, and the greeting keeps
        // its two lines instead of breaking into three.
        BoxWithConstraints {
            val measurer = rememberTextMeasurer()
            val style = AromaTheme.type.display
            val beside = maxWidth - PageMargin - (PageMargin - IconButtonReach) - Space.s8 - TouchTarget * 2
            val firstLine = greeting.substringBefore('\n')
            val fits =
                with(LocalDensity.current) {
                    remember(firstLine, style, beside) {
                        measurer
                            .measure(
                                firstLine.breakable(),
                                style,
                                constraints = Constraints(maxWidth = beside.roundToPx()),
                            ).lineCount ==
                            1
                    }
                }
            if (fits) {
                LargeHeader(title = greeting, subtitle = { Subtitle(date) }) { buttons() }
            } else {
                LargeHeader(
                    title = greeting,
                    subtitle = { Subtitle(date) },
                    navigation = {
                        Row(
                            Modifier.fillMaxWidth().padding(end = PageMargin - IconButtonReach),
                            Arrangement.End,
                        ) { buttons() }
                    },
                )
            }
        }
        if (state.isOffline) OfflineNotice(Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s12))
    }
}

/** "Dienstag, 24. November" in German, "Tuesday, November 24" in English. */
private fun longDate(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern(if (locale.language == "de") "EEEE, d. MMMM" else "EEEE, MMMM d", locale))

/**
 * Today: the next meal planned, its photo across the top of the box, ready to cook, and the other
 * meals of the day as rows below. With nothing planned, three suggestions to plan for today.
 */
@Composable
private fun TodayBox(state: HomeUiState, suggestions: List<RecipeSummary>, actions: HomeActions, modifier: Modifier = Modifier) {
    val todays = state.todays
    val next = todays.firstOrNull { it.type.hour >= state.meal.hour } ?: todays.lastOrNull()
    if (next == null) {
        NothingPlanned(suggestions, actions, modifier)
        return
    }
    val recipe = next.recipe
    TonalBox(modifier, contentPadding = PaddingValues()) {
        if (recipe != null) {
            RecipeImage(
                image = recipe.image,
                size = ImageSize.Medium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(TODAY_PHOTO)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.home_open_recipe),
                        ) { actions.onOpenRecipe(recipe.slug) }
                        // The photo is a button: TalkBack names it by its recipe.
                        .semantics { contentDescription = recipe.name },
            )
        }
        Column(Modifier.padding(BoxPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = todayMealLabel(next.type),
                    style = AromaTheme.type.label,
                    color = AromaTheme.colors.inkSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (recipe != null) recipeTime(recipe)?.let { MetaLine(listOf(it), icon = R.drawable.ic_clock) }
            }
            Text(
                text = recipe?.name ?: next.title,
                style = AromaTheme.type.section,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .padding(top = EyebrowGap)
                        .semantics { heading() },
            )
            if (recipe == null && next.note.isNotBlank()) {
                Text(next.note, style = AromaTheme.type.bodySmall, modifier = Modifier.padding(top = Space.s4))
            }
            if (recipe != null) {
                // With large text the second button moves under the first instead of being cut short.
                FlowRow(
                    Modifier.padding(top = Space.s12),
                    horizontalArrangement = Arrangement.spacedBy(Space.s8),
                    verticalArrangement = Arrangement.spacedBy(Space.s8),
                ) {
                    PrimaryButton(
                        stringResource(R.string.recipe_start_cooking),
                        { actions.onCook(recipe.slug) },
                        compact = true,
                        icon = R.drawable.ic_cooking,
                    )
                    SecondaryButton(stringResource(R.string.home_recipe), { actions.onOpenRecipe(recipe.slug) }, compact = true)
                }
            }
        }
        val others = todays - next
        if (others.isNotEmpty()) {
            Column(
                Modifier.padding(start = BoxPadding, end = BoxPadding, bottom = BoxPadding),
                verticalArrangement = Arrangement.spacedBy(Space.s12),
            ) {
                others.forEach { entry -> PlannedRow(entry, actions) }
            }
        }
    }
}

/** Today's other meals, with what they are and their time under the name. */
@Composable
private fun PlannedRow(entry: MealPlanEntry, actions: HomeActions) {
    val recipe = entry.recipe
    RecipeRow(
        title = recipe?.name ?: entry.title,
        image = recipe?.image,
        onClick = { if (recipe != null) actions.onOpenRecipe(recipe.slug) else actions.onOpenPlan(entry.date) },
        meta = listOfNotNull(mealTypeLabel(entry.type), recipe?.let { recipeTime(it) }),
        fallbackIcon = if (recipe == null) R.drawable.ic_notes else R.drawable.ic_plate,
    )
}

/** "Heute Abend": the meal as today names it. */
@Composable
private fun todayMealLabel(type: MealType): String =
    when (type) {
        MealType.Breakfast -> stringResource(R.string.home_today_breakfast)
        MealType.Lunch -> stringResource(R.string.home_today_lunch)
        MealType.Dinner -> stringResource(R.string.home_today_dinner)
        else -> stringResource(R.string.home_today_other, mealTypeLabel(type))
    }

/** Nothing planned for today: three suggestions as rows, each to plan with a tap. */
@Composable
private fun NothingPlanned(suggestions: List<RecipeSummary>, actions: HomeActions, modifier: Modifier = Modifier) {
    TonalBox(modifier) {
        BoxHeader(stringResource(R.string.home_today_empty))
        if (suggestions.isEmpty()) {
            TextAction(stringResource(R.string.home_today_plan), { actions.onOpenPlan(null) }, icon = R.drawable.ic_add)
        } else {
            Column(Modifier.padding(top = Space.s12), verticalArrangement = Arrangement.spacedBy(Space.s12)) {
                suggestions.forEach { recipe ->
                    RecipeRow(
                        recipe = recipe,
                        onClick = { actions.onOpenRecipe(recipe.slug) },
                        onLongClick = { actions.onRecipeMenu(recipe) },
                        trailing = { TextAction(stringResource(R.string.recipe_action_plan), { actions.onPlanToday(recipe) }) },
                    )
                }
            }
        }
    }
}

/**
 * The week at a glance: seven columns from Monday, today's figure in an ink circle, a sage dot under
 * every day with something planned, past days faded. A day opens the plan there; the head, at today.
 */
@Composable
private fun WeekBox(state: HomeUiState, onOpenPlan: (LocalDate?) -> Unit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val planned = remember(state.week) { state.week.map { it.date }.toSet() }
    val days = remember(state.weekStart) { (0L until DAYS).map { state.weekStart.plusDays(it) } }
    TonalBox(modifier, contentPadding = PaddingValues(start = BoxPadding, end = BoxPadding, bottom = Space.s12)) {
        BoxHeader(
            title = stringResource(R.string.home_this_week),
            meta = days.count { it in planned }.let { count -> pluralStringResource(R.plurals.home_week_planned, count, count, DAYS) },
            chevron = true,
            modifier =
                Modifier
                    .heightIn(min = TouchTarget)
                    .clickable(role = Role.Button) { onOpenPlan(null) },
        )
        Row(Modifier.padding(top = Space.s4)) {
            days.forEach { day ->
                WeekDay(
                    day = day,
                    today = state.date,
                    planned = day in planned,
                    locale = locale,
                    onClick = { onOpenPlan(day) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private const val DAYS = 7L

/** One day of the week's strip: its short name over its figure, and a dot where something is planned. */
@Composable
private fun WeekDay(
    day: LocalDate,
    today: LocalDate,
    planned: Boolean,
    locale: Locale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AromaTheme.colors
    val past = day.isBefore(today)
    val isToday = day == today
    val name = remember(day, locale) { weekdayShort(day, locale) }
    val described = stringResource(if (planned) R.string.home_week_day_planned else R.string.home_week_day_free, name, day.dayOfMonth)
    Column(
        modifier
            .clip(AromaTheme.shapes.box)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Space.s4)
            .semantics(mergeDescendants = true) {
                contentDescription = described
                selected = isToday
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, style = AromaTheme.type.meta, color = if (past) colors.inkTertiary else colors.inkSecondary)
        Box(
            Modifier
                .padding(top = Space.s4)
                .size(TodayCircle)
                .then(if (isToday) Modifier.background(colors.ink, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = AromaTheme.type.cardTitle.tabular(),
                color =
                    when {
                        isToday -> colors.onInk
                        past -> colors.inkTertiary
                        else -> colors.ink
                    },
                textAlign = TextAlign.Center,
            )
        }
        Box(
            Modifier
                .padding(top = Space.s4)
                .size(PlannedDot)
                .then(if (planned) Modifier.background(colors.accent, CircleShape) else Modifier),
        )
    }
}

/** Today's figure stands in a circle this large; a planned day has a dot this large under it. */
private val TodayCircle = 32.dp
private val PlannedDot = 4.dp

/** The shopping list, while something is still to buy on it. */
@Composable
private fun ShoppingBox(open: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    TonalBox(modifier, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircleMark(R.drawable.ic_basket)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = Space.s12),
            ) {
                Text(stringResource(R.string.home_shopping_list), style = AromaTheme.type.label)
                Text(pluralStringResource(R.plurals.home_shopping_open, open, open), style = AromaTheme.type.meta)
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = AromaTheme.colors.inkSecondary,
                modifier = Modifier.size(Glyph.control),
            )
        }
    }
}

/** Ideas for today: a carousel of cards 148 dp wide, the third cut by the edge so the row reads as more. */
@Composable
private fun Ideas(recipes: List<RecipeSummary>, actions: HomeActions, onShowAll: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = SectionGap)) {
        SectionTitle(
            title = stringResource(R.string.home_ideas),
            actionLabel = stringResource(R.string.action_show_all),
            onAction = onShowAll,
            modifier = Modifier.padding(bottom = SectionTitleGap),
        )
        val lines = rememberTitleLines(recipes.map { it.name }, CarouselCardWidth)
        LazyRow(
            contentPadding = PaddingValues(horizontal = PageMargin),
            horizontalArrangement = Arrangement.spacedBy(GridColumnGap),
        ) {
            items(recipes, key = { it.id }) { recipe ->
                RecipeCard(
                    recipe = recipe,
                    onClick = { actions.onOpenRecipe(recipe.slug) },
                    onLongClick = { actions.onRecipeMenu(recipe) },
                    titleLines = lines,
                    modifier = Modifier.width(CarouselCardWidth),
                )
            }
        }
    }
}

private val CarouselCardWidth = 148.dp

/** Quick ones: three rows of recipes on the table in half an hour. */
@Composable
private fun Quick(recipes: List<RecipeSummary>, actions: HomeActions, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = SectionGap)) {
        SectionTitle(stringResource(R.string.home_quick), Modifier.padding(bottom = SectionTitleGap))
        Column(Modifier.padding(horizontal = PageMargin), verticalArrangement = Arrangement.spacedBy(Space.s12)) {
            recipes.forEach { recipe ->
                RecipeRow(recipe = recipe, onClick = { actions.onOpenRecipe(recipe.slug) }, onLongClick = { actions.onRecipeMenu(recipe) })
            }
        }
    }
}

/** The newest recipe in the collection, large: its photo across the page, its name, two lines of its headnote. */
@Composable
private fun Newest(recipe: RecipeSummary, actions: HomeActions, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = SectionGap)) {
        SectionTitle(stringResource(R.string.home_newest), Modifier.padding(bottom = SectionTitleGap))
        Column(
            Modifier
                .padding(horizontal = PageMargin)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.home_open_recipe),
                ) { actions.onOpenRecipe(recipe.slug) },
        ) {
            RecipeImage(
                image = recipe.image,
                size = ImageSize.Medium,
                shape = AromaTheme.shapes.cardImage,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(NEWEST_PHOTO),
            )
            Text(
                recipe.name,
                style = AromaTheme.type.section,
                modifier = Modifier.padding(top = Space.s12),
            )
            if (recipe.description.isNotBlank()) {
                Text(
                    text = recipe.description,
                    style = AromaTheme.type.headnote,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Space.s4),
                )
            }
            RecipeMetaLine(recipe, Modifier.padding(top = Space.s8))
        }
    }
}

private const val TODAY_PHOTO = 16f / 10f
private const val NEWEST_PHOTO = 4f / 3f

/**
 * Holds the rate prompt. Answered or dismissed, the box fades where it stands and gives up its room
 * once it has all but faded, and the page below glides in. A new prompt takes its room at once and
 * fades in as the page glides aside.
 */
@Composable
private fun RatePromptSlot(toRate: RateReminder?, actions: HomeActions, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = toRate,
        contentKey = { it?.recipeId },
        transitionSpec = {
            val arrive = fadeIn(Motion.state()) + scaleIn(Motion.state(), RATE_SCALE)
            val leave = fadeOut(Motion.edge()) + scaleOut(Motion.edge(), RATE_SCALE)
            arrive togetherWith leave using
                SizeTransform(clip = false) { initial, target -> if (target.height < initial.height) snap(Motion.EDGE / 2) else snap() }
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
    TonalBox(Modifier.padding(start = PageMargin, end = PageMargin, top = BoxGap), contentPadding = PaddingValues()) {
        Box {
            // As much room under the stars' ink as above the photo; their own targets take most of it.
            Row(Modifier.padding(start = BoxPadding, top = Space.s12, bottom = Space.s16 - ratingTouchInset(RateStarSize))) {
                RecipeImage(
                    image = toRate.image,
                    size = ImageSize.Thumbnail,
                    shape = AromaTheme.shapes.thumbnail,
                    fallbackIconSize = Glyph.control,
                    modifier =
                        Modifier
                            .padding(top = Space.s4)
                            .size(app.aroma.ui.theme.ThumbnailSize)
                            .clip(AromaTheme.shapes.thumbnail)
                            .clickable(onClickLabel = stringResource(R.string.home_open_recipe)) { actions.onOpenRecipe(toRate.slug) }
                            .semantics { contentDescription = toRate.name },
                )
                Spacer(Modifier.width(Space.s16))
                // The words keep clear of the close button; the stars may pass under it, so five fit on a small phone.
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.cook_rate_question),
                        style = AromaTheme.type.bodySmall,
                        modifier = Modifier.padding(end = TouchTarget, bottom = EyebrowGap),
                    )
                    Text(
                        text = toRate.name,
                        style = AromaTheme.type.cardTitle,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = TouchTarget),
                    )
                    RatingStars(
                        rating = toRate.stars?.toDouble(),
                        starSize = RateStarSize,
                        onRate = actions.onRateCooked,
                        // The stars' touch areas reach past the text column; the first star's ink stands on it.
                        modifier = Modifier.offset(x = -ratingTouchInset(RateStarSize)),
                    )
                }
            }
            IconButton(
                R.drawable.ic_close,
                stringResource(R.string.home_rate_dismiss),
                actions.onDismissReminder,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = Space.s4, end = Space.s4),
            )
        }
    }
}

private val RateStarSize = 28.dp

/** The page's outline while it loads: today's box with its photo, and the week's. */
@Composable
private fun HomeLoading(modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = PageMargin)) {
        Skeleton(
            Modifier
                .fillMaxWidth()
                .aspectRatio(TODAY_PHOTO),
            shape = AromaTheme.shapes.box,
        )
        SkeletonLine(
            Modifier
                .padding(top = Space.s16)
                .fillMaxWidth(TITLE_SHARE),
        )
        Skeleton(
            Modifier
                .padding(top = BoxGap)
                .fillMaxWidth()
                .height(WeekSkeletonHeight),
            shape = AromaTheme.shapes.box,
        )
    }
}

private const val TITLE_SHARE = 0.6f
private val WeekSkeletonHeight = 120.dp
