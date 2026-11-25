package app.aroma.feature.recipe

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.format.IngredientFormatter
import app.aroma.core.format.MeasureSystem
import app.aroma.core.format.Quantities
import app.aroma.core.format.StepAmount
import app.aroma.core.format.StepMention
import app.aroma.core.format.StyledText
import app.aroma.core.format.ingredientsByStep
import app.aroma.core.model.ImageSize
import app.aroma.core.model.NutritionKind
import app.aroma.core.model.NutritionValue
import app.aroma.core.model.Organizer
import app.aroma.core.model.Recipe
import app.aroma.core.model.RecipeNote
import app.aroma.core.model.Step
import app.aroma.data.AromaError
import app.aroma.data.settings.IngredientsInSteps
import app.aroma.feature.editor.ReworkJob
import app.aroma.feature.editor.WorkProgress
import app.aroma.ui.components.AromaAlertDialog
import app.aroma.ui.components.AromaDropdownMenu
import app.aroma.ui.components.AromaMenuItem
import app.aroma.ui.components.BackButton
import app.aroma.ui.components.BottomActionBar
import app.aroma.ui.components.ChoiceSheet
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.IconButton
import app.aroma.ui.components.IconButtonPlace
import app.aroma.ui.components.IconButtonReach
import app.aroma.ui.components.IngredientList
import app.aroma.ui.components.NARROW_NO_BREAK_SPACE
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.RatingStars
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.Ribbon
import app.aroma.ui.components.RibbonSize
import app.aroma.ui.components.SEPARATOR
import app.aroma.ui.components.SectionTitle
import app.aroma.ui.components.Skeleton
import app.aroma.ui.components.SkeletonLine
import app.aroma.ui.components.StatusBarCover
import app.aroma.ui.components.StatusBarOverPhoto
import app.aroma.ui.components.StepTexts
import app.aroma.ui.components.Stepper
import app.aroma.ui.components.Text
import app.aroma.ui.components.TextAction
import app.aroma.ui.components.TonalBox
import app.aroma.ui.components.TopBar
import app.aroma.ui.components.TopBarHeight
import app.aroma.ui.components.actionBarInset
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.labelRes
import app.aroma.ui.components.ratingDescription
import app.aroma.ui.components.recipeTimeText
import app.aroma.ui.components.reducedMotion
import app.aroma.ui.components.rememberIngredientRows
import app.aroma.ui.components.rememberRichText
import app.aroma.ui.components.rememberStepText
import app.aroma.ui.components.rememberStepTexts
import app.aroma.ui.components.totalTime
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.ContentMaxWidth
import app.aroma.ui.theme.EndSpace
import app.aroma.ui.theme.EyebrowGap
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.HeaderTop
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.NavigationGap
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.SectionGap
import app.aroma.ui.theme.SectionTitleGap
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.SubtitleGap
import app.aroma.ui.theme.TouchTarget
import app.aroma.ui.theme.tabular
import java.net.URI
import java.util.Locale

/**
 * The recipe page's state. Mealie's AI may rework the recipe where the server has it ([aiAvailable]),
 * translated by default where the user last chose so ([translate]); [rework] is one under way.
 * [isFavorite] is Mealie's favourite, which Aroma shows as a saved recipe with its ribbon.
 */
data class RecipeUiState(
    val recipe: Recipe? = null,
    val scale: Double = 1.0,
    val measures: MeasureSystem = MeasureSystem.AsWritten,
    val ingredientsInSteps: IngredientsInSteps = IngredientsInSteps.Used,
    val amountsInSteps: Boolean = false,
    val checkedIngredients: Set<Int> = emptySet(),
    val isFavorite: Boolean = false,
    val userRating: Double? = null,
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val error: AromaError? = null,
    val confirmed: QuickAction? = null,
    val aiAvailable: Boolean = false,
    val translate: Boolean = false,
    val rework: ReworkJob? = null,
)

/** The two icon buttons of the action bar; [RecipeUiState.confirmed] marks one that just went through. */
enum class QuickAction { Plan, Shop }

data class RecipeActions(
    val onBack: () -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
    val onRate: (Int) -> Unit = {},
    val onScaleChange: (Double) -> Unit = {},
    val onMeasuresChange: (MeasureSystem) -> Unit = {},
    val onToggleIngredient: (Int) -> Unit = {},
    val onAddToShoppingList: () -> Unit = {},
    val onAddToMealPlan: () -> Unit = {},
    val onStartCooking: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onOpenUrl: (String) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onRework: (translate: Boolean) -> Unit = {},
    val onOrganize: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

private const val HERO_KEY = "hero"
private const val TITLE_KEY = "title"

/** The hero's proportions; in landscape it takes at most [HERO_MAX_SHARE] of the screen's height. */
private const val HERO_RATIO = 4f / 3f
private const val HERO_MAX_SHARE = 0.6f

/** The plate that stands in for a missing photo at the head of the page. */
private val HeroPlateSize = 48.dp

/** The shade at the top of the photo, so the status bar and the buttons read on light photos too. */
private val PhotoShadeHeight = 120.dp

/** How dark the shade starts at the top edge; it fades to nothing at [PhotoShadeHeight]. */
private const val PHOTO_SHADE = 0.45f

/**
 * The recipe as a cookbook page: the photo to the edges, the title and what it is, the headnote,
 * the times, the ingredients and the steps, open on paper. Cooking is the page's one main action, in
 * the bar at its foot beside planning and shopping. All that manages the recipe waits in the menu.
 */
@Composable
fun RecipeScreen(state: RecipeUiState, actions: RecipeActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val recipe = state.recipe
    var rateOpen by rememberSaveable { mutableStateOf(false) }
    var reworkOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val barBottom = WindowInsets.statusBars.getTop(density) + with(density) { TopBarHeight.roundToPx() }
    // A recipe without a photo starts with its buttons and its title, as any pushed page does.
    val hasPhoto = recipe?.image != null
    val leadKey = if (hasPhoto) HERO_KEY else TITLE_KEY
    // The collapsed head takes over once the photo, or the title, has slid beneath it.
    val scrolledAway by remember(listState, barBottom, leadKey) {
        derivedStateOf { listState.bottomOf(leadKey)?.let { it <= barBottom } ?: (listState.firstVisibleItemIndex > 0) }
    }
    val collapsed = if (recipe != null) scrolledAway else !state.isLoading
    val overPhoto = if (recipe != null) hasPhoto && !scrolledAway else state.isLoading
    val menu =
        PageMenu(
            // One rework at a time: the next only once the page showed how the last ended.
            onRework = { reworkOpen = true }.takeIf { state.aiAvailable && state.rework == null },
            onRate = { rateOpen = true },
            onDelete = { confirmDelete = true },
        )
    val steps = rememberSteps(recipe, state)
    Box(modifier.fillMaxSize()) {
        when {
            recipe != null -> {
                LazyColumn(
                    state = listState,
                    // The last section ends 24 dp above the action bar.
                    contentPadding = PaddingValues(bottom = actionBarInset() + EndSpace),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = HERO_KEY) {
                        if (hasPhoto) {
                            Hero(
                                recipe,
                                saved = state.isFavorite,
                                onUnsave = actions.onToggleFavorite,
                                scrolled = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 0 },
                            )
                        } else {
                            PageButtons(state.isFavorite, actions, menu)
                        }
                    }
                    recipeBody(recipe, state, actions, steps, underPhoto = hasPhoto)
                }
            }

            state.isLoading -> {
                RecipeLoading()
            }

            state.error != null -> {
                ErrorState(
                    state.error,
                    onRetry = actions.onRetry,
                    modifier =
                        Modifier
                            .statusBarsPadding()
                            .padding(top = TopBarHeight + HeaderTop),
                )
            }
        }
        RecipeHead(recipe, collapsed, overPhoto, state.isFavorite, actions, menu)
        if (recipe != null) {
            RecipeActionBar(
                canShop = recipe.ingredients.isNotEmpty(),
                canCook = recipe.steps.isNotEmpty(),
                confirmed = state.confirmed,
                actions = actions,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    // Light icons over the photo's shade; the theme's own once the photo has gone beneath the head.
    StatusBarOverPhoto(overPhoto)
    if (rateOpen && recipe != null) {
        RatingSheet(rating = state.userRating, onRate = actions.onRate, onDismiss = { rateOpen = false })
    }
    if (reworkOpen && recipe != null) {
        ReworkSheet(translate = state.translate, onRework = actions.onRework, onDismiss = { reworkOpen = false })
    }
    if (confirmDelete && recipe != null) {
        AromaAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = stringResource(R.string.recipe_delete_title),
            text = stringResource(R.string.recipe_delete_message, recipe.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                actions.onDelete()
            },
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }
}

private fun LazyListState.bottomOf(key: Any): Int? = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { it.offset + it.size }

/** What the menu opens on the page itself: a rework where Mealie's AI is there, the rating and the question before deleting. */
private class PageMenu(val onRework: (() -> Unit)?, val onRate: () -> Unit, val onDelete: () -> Unit)

/** The steps as they read ([texts]), and the ingredients each [uses], as the list shows them. */
private class Steps(val texts: StepTexts, val uses: List<List<String>>)

@Composable
private fun rememberSteps(recipe: Recipe?, state: RecipeUiState): Steps {
    val locale = currentLocale()
    // What each step uses, scaled and in the chosen measures like the list above it.
    val uses =
        remember(recipe, state.scale, state.measures, locale) {
            if (recipe == null) return@remember emptyList()
            val lines =
                recipe.ingredients.map { ingredient ->
                    val line = IngredientFormatter.format(ingredient, state.scale, locale, state.measures)
                    // As the list sets it: the note after a comma.
                    listOfNotNull(line.measureText(NARROW_NO_BREAK_SPACE), line.text, line.note?.let { ", $it" })
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .replace(" ,", ",")
                }
            recipe.ingredientsByStep(locale).map { used -> used.mapNotNull(lines::getOrNull) }
        }
    val texts = rememberStepTexts(recipe, state.scale, state.measures, state.amountsInSteps)
    return remember(texts, uses) { Steps(texts, uses) }
}

/** The page's text keeps a reading measure on wide screens, centred under the photo. */
private fun Modifier.measure(): Modifier = widthIn(max = ContentMaxWidth).fillMaxWidth()

/**
 * Over the photo, back, save and more stand on paper circles; a saved recipe's ribbon hangs where
 * its save button stood. Once the photo has gone, or the title of a recipe without one, they become
 * the collapsed head every pushed screen has, with the recipe's name.
 */
@Composable
private fun RecipeHead(
    recipe: Recipe?,
    collapsed: Boolean,
    overPhoto: Boolean,
    saved: Boolean,
    actions: RecipeActions,
    menu: PageMenu,
) {
    // Without a photo, the page's own paper keeps the status bar clear until the head collapses.
    if (!collapsed && !overPhoto) StatusBarCover()
    AnimatedVisibility(visible = collapsed, enter = fadeIn(Motion.edge()), exit = fadeOut(Motion.edge())) {
        TopBar(
            title = recipe?.name.orEmpty(),
            collapsed = true,
            raised = true,
            navigation = { BackButton(actions.onBack) },
        ) {
            if (recipe != null) {
                SaveButton(saved, actions.onToggleFavorite, IconButtonPlace.Content)
                MoreMenu(actions, menu, IconButtonPlace.Content)
            }
        }
    }
    AnimatedVisibility(visible = overPhoto && !collapsed, enter = fadeIn(Motion.edge()), exit = fadeOut(Motion.edge())) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = PageMargin - IconButtonReach, vertical = Space.s4),
        ) {
            IconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), actions.onBack, place = IconButtonPlace.Photo)
            Spacer(Modifier.weight(1f))
            if (recipe != null) {
                AnimatedVisibility(visible = !saved, enter = fadeIn(Motion.edge()), exit = fadeOut(Motion.edge())) {
                    SaveButton(saved = false, onToggle = actions.onToggleFavorite, place = IconButtonPlace.Photo)
                }
                MoreMenu(actions, menu, IconButtonPlace.Photo)
            }
        }
    }
}

/** The head of a recipe without a photo: back, save and more, in the row a pushed page has above its title. */
@Composable
private fun PageButtons(saved: Boolean, actions: RecipeActions, menu: PageMenu) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(TopBarHeight)
            .padding(horizontal = PageMargin - IconButtonReach),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(actions.onBack)
        Spacer(Modifier.weight(1f))
        SaveButton(saved, actions.onToggleFavorite, IconButtonPlace.Content)
        MoreMenu(actions, menu, IconButtonPlace.Content)
    }
}

/** Saves the recipe, with a tap of the phone to feel it, or lets it go. */
@Composable
private fun SaveButton(saved: Boolean, onToggle: () -> Unit, place: IconButtonPlace) {
    val haptics = LocalHapticFeedback.current
    IconButton(
        icon = if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
        contentDescription = stringResource(if (saved) R.string.action_unfavorite else R.string.action_favorite),
        onClick = {
            if (!saved) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onToggle()
        },
        place = place,
    )
}

/** What manages the recipe, and is not needed to cook it: editing, filing, rating, reworking, sharing and deleting. */
@Composable
private fun MoreMenu(actions: RecipeActions, menu: PageMenu, place: IconButtonPlace) {
    var open by remember { mutableStateOf(false) }
    val choose: (() -> Unit) -> Unit = { action ->
        open = false
        action()
    }
    Box {
        IconButton(R.drawable.ic_more_horiz, stringResource(R.string.action_more), onClick = { open = true }, place = place)
        AromaDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AromaMenuItem(stringResource(R.string.action_edit), R.drawable.ic_edit, { choose(actions.onEdit) })
            AromaMenuItem(stringResource(R.string.organizers_title), R.drawable.ic_tag, { choose(actions.onOrganize) })
            AromaMenuItem(stringResource(R.string.recipe_rate), R.drawable.ic_star, { choose(menu.onRate) })
            menu.onRework?.let { AromaMenuItem(stringResource(R.string.recipe_rework), R.drawable.ic_sparkle, { choose(it) }) }
            AromaMenuItem(stringResource(R.string.action_share), R.drawable.ic_share, { choose(actions.onShare) })
            AromaMenuItem(stringResource(R.string.action_delete), R.drawable.ic_delete, { choose(menu.onDelete) }, destructive = true)
        }
    }
}

/**
 * Plan and shop as icon buttons, the two things people do with a recipe besides cooking it, and
 * cooking itself across the rest of the bar, the page's one main action. Shop is there where
 * there is something to buy; cooking, where there are steps to cook by. Once planning or shopping
 * went through, its button shows a check and the phone gives a tap to feel it.
 */
@Composable
private fun RecipeActionBar(
    canShop: Boolean,
    canCook: Boolean,
    confirmed: QuickAction?,
    actions: RecipeActions,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(confirmed) {
        if (confirmed != null) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    BottomActionBar(modifier) {
        IconButton(
            icon = R.drawable.ic_calendar_add,
            contentDescription = stringResource(R.string.recipe_action_plan),
            onClick = actions.onAddToMealPlan,
            place = IconButtonPlace.Bar,
            confirmed = confirmed == QuickAction.Plan,
        )
        if (canShop) {
            IconButton(
                icon = R.drawable.ic_basket,
                contentDescription = stringResource(R.string.recipe_action_shop),
                onClick = actions.onAddToShoppingList,
                place = IconButtonPlace.Bar,
                confirmed = confirmed == QuickAction.Shop,
            )
        }
        PrimaryButton(
            text = stringResource(R.string.recipe_start_cooking),
            onClick = actions.onStartCooking,
            icon = R.drawable.ic_cooking,
            enabled = canCook,
            modifier = Modifier.weight(1f),
        )
    }
}

/** How tall the hero stands: 4 : 3 to the edges, and no more than [HERO_MAX_SHARE] of the window in landscape. */
@Composable
private fun heroHeight(): Dp {
    val window = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { minOf(window.width.toDp() / HERO_RATIO, window.height.toDp() * HERO_MAX_SHARE) }
}

/**
 * The photo to the edges, with a shade at its top. A saved recipe's ribbon hangs from its top edge
 * where the save button stood, its foot level with the buttons; a tap on it lets the recipe go. As
 * the page scrolls, the photo slides away at half its pace, as if the page passed over it; how far
 * the page has [scrolled] past the photo's top is read while drawing, so it composes nothing anew.
 * With the system's animations off, the photo scrolls with the page.
 */
@Composable
private fun Hero(recipe: Recipe, saved: Boolean, onUnsave: () -> Unit, scrolled: () -> Int) {
    val statusBar = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val parallax = if (reducedMotion()) 0f else Motion.PARALLAX
    Box(
        Modifier
            .fillMaxWidth()
            .height(heroHeight())
            .clipToBounds(),
    ) {
        RecipeImage(
            image = recipe.image,
            size = ImageSize.Original,
            placeholder = ImageSize.Medium,
            fallbackIconSize = HeroPlateSize,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = scrolled() * parallax },
        )
        val scrim = AromaTheme.colors.scrim
        Box(
            Modifier
                .fillMaxWidth()
                .height(PhotoShadeHeight)
                .background(Brush.verticalGradient(listOf(scrim.copy(alpha = PHOTO_SHADE), Color.Transparent))),
        )
        Ribbon(
            saved = saved,
            size = RibbonSize.Hero,
            animated = true,
            // Behind the status bar it reaches up to the photo's edge; below it, it is as long as a hero's ribbon.
            hang = statusBar,
            onClick = onUnsave,
            onClickLabel = stringResource(R.string.action_unfavorite),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = RibbonEnd),
        )
    }
}

/** Where the ribbon hangs from the right edge: centred where the save button stood, left of the menu's. */
private val RibbonEnd = PageMargin - IconButtonReach + TouchTarget + (TouchTarget - RibbonSize.Hero.size.width) / 2

private fun LazyListScope.recipeBody(
    recipe: Recipe,
    state: RecipeUiState,
    actions: RecipeActions,
    steps: Steps,
    underPhoto: Boolean,
) {
    item(key = TITLE_KEY) {
        TitleBlock(
            recipe = recipe,
            rating = state.userRating ?: recipe.rating,
            isOffline = state.isOffline,
            onOpenUrl = actions.onOpenUrl,
            modifier =
                Modifier
                    .measure()
                    // 24 dp under the photo; without one, right under the buttons, as on any pushed page.
                    .padding(top = if (underPhoto) Space.s24 else NavigationGap),
        )
    }
    state.rework?.takeIf { it.outcome == null }?.let { job ->
        item(key = "rework") {
            Column(
                Modifier
                    .measure()
                    .padding(start = PageMargin, end = PageMargin, top = Space.s24),
            ) {
                WorkProgress(job.steps, job.startedAt, stringResource(R.string.recipe_rework_running), Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.recipe_rework_progress_hint),
                    style = AromaTheme.type.bodySmall,
                    modifier = Modifier.padding(top = Space.s12),
                )
            }
        }
    }
    if (recipe.description.isNotBlank()) {
        item(key = "headnote") {
            Text(
                text = rememberRichText(recipe.description),
                style = AromaTheme.type.headnote,
                modifier =
                    Modifier
                        .measure()
                        .padding(start = PageMargin, end = PageMargin, top = Space.s16),
            )
        }
    }
    item(key = "facts") {
        Facts(
            recipe,
            Modifier
                .measure()
                .padding(start = PageMargin, end = PageMargin, top = Space.s24),
        )
    }
    if (recipe.ingredients.isNotEmpty()) {
        item(key = "ingredients") {
            Ingredients(
                recipe,
                state,
                actions,
                Modifier
                    .measure()
                    .padding(start = PageMargin, end = PageMargin, top = SectionGap),
            )
        }
    }
    if (recipe.steps.isNotEmpty()) {
        item(key = "stepsHead") {
            SectionTitle(
                stringResource(R.string.recipe_steps),
                Modifier
                    .measure()
                    .padding(top = SectionGap, bottom = SectionTitleGap),
            )
        }
        itemsIndexed(recipe.steps, key = { index, step -> step.id ?: "step$index" }) { index, step ->
            StepItem(
                index = index,
                step = step,
                text = steps.texts.textOf(index, step),
                amounts = steps.texts.amountsOf(index),
                uses = if (state.ingredientsInSteps == IngredientsInSteps.None) emptyList() else steps.uses.getOrNull(index).orEmpty(),
                mentions = steps.texts.mentionsOf(index),
                checked = state.checkedIngredients,
                onToggle = actions.onToggleIngredient,
                modifier =
                    Modifier
                        .measure()
                        .padding(top = if (index == 0) 0.dp else Space.s24),
            )
        }
    }
    if (recipe.notes.isNotEmpty()) {
        item(key = "notes") { Notes(recipe.notes, Modifier.measure()) }
    }
    if (recipe.nutrition.isNotEmpty()) {
        item(key = "nutrition") { Nutrition(recipe.nutrition, Modifier.measure()) }
    }
}

/**
 * The title, and under it what the recipe is and where it is from: its category, two tags and the
 * source's domain, which opens the original. Stars follow on a line of their own where it is rated.
 */
@Composable
private fun TitleBlock(
    recipe: Recipe,
    rating: Double?,
    isOffline: Boolean,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = PageMargin)) {
        Text(
            recipe.name,
            style = AromaTheme.type.recipeTitle,
            modifier = Modifier.semantics { heading() },
        )
        Byline(recipe.categories, recipe.tags, recipe.sourceUrl, onOpenUrl, Modifier.padding(top = SubtitleGap))
        if (rating != null) Stars(rating, Modifier.padding(top = Space.s8))
        if (isOffline) OfflineNotice(Modifier.padding(top = Space.s8))
    }
}

/**
 * "Backen · Weihnachten · Plätzchen · chefkoch.de ↗": the source in sage, a link to the original
 * that keeps its domain on one line and moves to the next as a whole.
 */
@Composable
private fun Byline(
    categories: List<Organizer>,
    tags: List<Organizer>,
    sourceUrl: String?,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kinds = remember(categories, tags) { (categories.take(1) + tags.take(TAGS_SHOWN)).map { it.name } }
    val host = remember(sourceUrl) { sourceUrl?.let(::sourceHost) }
    if (kinds.isEmpty() && host == null) return
    FlowRow(modifier, itemVerticalAlignment = Alignment.CenterVertically) {
        if (kinds.isNotEmpty()) {
            Text(kinds.joinToString(SEPARATOR) + if (host != null) SEPARATOR else "", style = AromaTheme.type.subtitle)
        }
        if (host != null && sourceUrl != null) SourceLink(host, onClick = { onOpenUrl(sourceUrl) })
    }
}

/** The source's domain and an arrow out, in sage: opens the original page. */
@Composable
private fun SourceLink(host: String, onClick: () -> Unit) {
    val accent = AromaTheme.colors.accent
    Row(
        Modifier
            .clip(AromaTheme.shapes.round)
            .clickable(onClickLabel = stringResource(R.string.recipe_open_source), role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(host, style = AromaTheme.type.subtitle, color = accent, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Icon(
            painterResource(R.drawable.ic_arrow_up_right),
            contentDescription = null,
            tint = accent,
            modifier =
                Modifier
                    .padding(start = Space.s4)
                    .size(Glyph.meta),
        )
    }
}

private const val TAGS_SHOWN = 2

/** The source's domain, without "www.". */
private fun sourceHost(url: String): String? = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull()?.takeIf { it.isNotBlank() }

/** The rating as small sage stars and its figure; only shown where the recipe is rated. */
@Composable
private fun Stars(rating: Double, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val description = ratingDescription(rating)
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RatingStars(rating, Modifier.clearAndSetSemantics {})
        Text(
            text = remember(rating, locale) { "%.1f".format(locale, rating) },
            style = AromaTheme.type.meta,
            modifier =
                Modifier
                    .padding(start = Space.s8)
                    .clearAndSetSemantics {},
        )
    }
}

/** One of the times in the box of figures: its glyph, the time and what it is. */
private data class Fact(
    @param:DrawableRes val icon: Int,
    val value: String,
    val label: String,
)

/** Preparation, cooking or baking, and the total, side by side in a box; what is not known is left out, and with it all, the box. */
@Composable
private fun Facts(recipe: Recipe, modifier: Modifier = Modifier) {
    val bakes = remember(recipe.name, recipe.categories, recipe.tags) { recipe.bakes() }
    val facts =
        listOfNotNull(
            recipeTimeText(recipe.prepTime)?.let { Fact(R.drawable.ic_utensils, it, stringResource(R.string.recipe_prep_time)) },
            recipeTimeText(recipe.cookTime)?.let {
                Fact(R.drawable.ic_flame, it, stringResource(if (bakes) R.string.recipe_bake_time else R.string.recipe_cook_time))
            },
            recipeTimeText(totalTime(recipe.totalTime, recipe.prepTime, recipe.cookTime))?.let {
                Fact(R.drawable.ic_clock, it, stringResource(R.string.recipe_total_time))
            },
        )
    if (facts.isEmpty()) return
    TonalBox(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s12)) {
            facts.forEach { fact ->
                Column(
                    Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {},
                ) {
                    Icon(
                        painterResource(fact.icon),
                        contentDescription = null,
                        tint = AromaTheme.colors.inkSecondary,
                        modifier = Modifier.size(Glyph.control),
                    )
                    Text(fact.value, style = AromaTheme.type.label.tabular(), modifier = Modifier.padding(top = Space.s8))
                    Text(fact.label, style = AromaTheme.type.meta)
                }
            }
        }
    }
}

/**
 * The ingredients: the title with the stepper on the right, counting what the recipe yields; under
 * it the yield of its own, where there is one, and the measures to show; then the list.
 */
@Composable
private fun Ingredients(recipe: Recipe, state: RecipeUiState, actions: RecipeActions, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val rows = rememberIngredientRows(recipe, state.scale, state.measures)
    val yields = remember(recipe.servings, recipe.yieldText) { RecipeYield(recipe.servings, recipe.yieldText) }
    Column(modifier) {
        // With large text the stepper moves under the title rather than squeezing it.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Space.s8),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recipe_ingredients),
                style = AromaTheme.type.section,
                modifier = Modifier.semantics { heading() },
            )
            Stepper(
                label = servingsLabel(recipe.servings, state.scale, locale, yields.stepperUnit),
                onDecrease = { actions.onScaleChange(Servings.step(recipe.servings, state.scale, -1)) },
                onIncrease = { actions.onScaleChange(Servings.step(recipe.servings, state.scale, +1)) },
                canDecrease = Servings.canDecrease(recipe.servings, state.scale),
            )
        }
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
            yields.line(state.scale, locale)?.let { Text(it + SEPARATOR, style = AromaTheme.type.meta) }
            MeasuresLink(state.measures, actions.onMeasuresChange)
        }
        IngredientList(
            rows = rows,
            checked = state.checkedIngredients,
            onToggle = actions.onToggleIngredient,
            modifier = Modifier.padding(top = SectionTitleGap),
        )
        if (recipe.tools.isNotEmpty()) Tools(recipe.tools)
    }
}

/** The tools the recipe needs, after the ingredients like a group of them. */
@Composable
private fun Tools(tools: List<Organizer>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = stringResource(R.string.recipe_tools),
            style = AromaTheme.type.groupLabel,
            modifier =
                Modifier
                    .padding(top = Space.s24, bottom = Space.s8)
                    .semantics { heading() },
        )
        Text(text = tools.joinToString(", ") { it.name }, style = AromaTheme.type.body)
    }
}

/** Which measures the amounts show, "Units: as written", as a link in the line that opens the three choices on a sheet. */
@Composable
private fun MeasuresLink(system: MeasureSystem, onSelect: (MeasureSystem) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextAction(
        text = stringResource(R.string.measures_link, stringResource(system.shortLabelRes())),
        onClick = { open = true },
        inline = true,
    )
    if (open) {
        ChoiceSheet(
            title = stringResource(R.string.measures_title),
            options = MeasureSystem.entries,
            selected = system,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = onSelect,
            onDismiss = { open = false },
        )
    }
}

/** The choice of measures within a sentence: "as written", "metric", "US". */
private fun MeasureSystem.shortLabelRes(): Int =
    when (this) {
        MeasureSystem.AsWritten -> R.string.measures_short_as_written
        MeasureSystem.Metric -> R.string.measures_short_metric
        MeasureSystem.US -> R.string.measures_short_us
    }

/**
 * What the stepper shows: the servings scaled, "4 Portionen", or in the words of the recipe's yield
 * where that counts them ([unit], "1 Blech"); without servings, the factor the amounts are scaled by.
 */
@Composable
@ReadOnlyComposable
fun servingsLabel(baseServings: Double, scale: Double, locale: Locale, unit: String? = null): String {
    if (baseServings <= 0) {
        val text = Quantities.display(scale, preferFraction = true, locale = locale)?.toPlainText() ?: "1"
        return stringResource(R.string.recipe_multiplier, text)
    }
    val target = baseServings * scale
    val count = Math.round(target).toInt()
    val text = Quantities.display(target, preferFraction = false, locale = locale)?.toPlainText() ?: count.toString()
    return if (unit != null) "$text$NARROW_NO_BREAK_SPACE$unit" else pluralStringResource(R.plurals.servings, count, text)
}

/**
 * A step with its number in the gutter, level with its first line, and what it uses under it. A tap
 * on a word that names an ingredient ticks it off, as a tap on its line does.
 */
@Composable
private fun StepItem(
    index: Int,
    step: Step,
    text: StyledText,
    amounts: List<StepAmount>,
    uses: List<String>,
    mentions: List<StepMention>,
    checked: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val number = stringResource(R.string.editor_step, index + 1)
    Row(modifier.padding(horizontal = PageMargin)) {
        Text(
            text = (index + 1).toString(),
            style = AromaTheme.type.numeral,
            color = AromaTheme.colors.inkSecondary,
            modifier =
                Modifier
                    .width(StepNumberWidth)
                    .alignByBaseline()
                    .semantics { contentDescription = number },
        )
        Column(
            Modifier
                .weight(1f)
                .alignByBaseline(),
        ) {
            if (!step.title.isNullOrBlank()) {
                Text(step.title, style = AromaTheme.type.label, modifier = Modifier.padding(bottom = EyebrowGap))
            }
            Text(
                text =
                    rememberStepText(text, amounts, mentions = mentions, checked = checked, onIngredient = onToggle),
                style = AromaTheme.type.body,
            )
            // The ingredients the step uses, as the list shows them, so the step reads on its own.
            if (uses.isNotEmpty()) {
                Text(text = uses.joinToString(SEPARATOR), style = AromaTheme.type.meta, modifier = Modifier.padding(top = Space.s8))
            }
        }
    }
}

/** The gutter a step's number stands in, wide enough for two digits. */
private val StepNumberWidth = 40.dp

/** The recipe's notes, open on the page under a title of their own. */
@Composable
private fun Notes(notes: List<RecipeNote>, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionTitle(stringResource(R.string.recipe_notes), Modifier.padding(top = SectionGap, bottom = SectionTitleGap))
        notes.forEachIndexed { index, note ->
            Column(Modifier.padding(start = PageMargin, end = PageMargin, top = if (index == 0) 0.dp else Space.s24)) {
                if (note.title.isNotBlank()) {
                    Text(note.title, style = AromaTheme.type.label, modifier = Modifier.padding(bottom = Space.s4))
                }
                Text(text = rememberRichText(note.text), style = AromaTheme.type.body)
            }
        }
    }
}

/** The nutrition facts as an open list: what, and how much, with the figures aligned. */
@Composable
private fun Nutrition(values: List<NutritionValue>, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionTitle(stringResource(R.string.recipe_nutrition), Modifier.padding(top = SectionGap, bottom = SectionTitleGap))
        values.forEach { value ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = NutritionRowHeight)
                    .padding(horizontal = PageMargin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = nutritionLabel(value.kind),
                    style = AromaTheme.type.body,
                    color = AromaTheme.colors.inkSecondary,
                    modifier = Modifier.weight(1f),
                )
                val amount = value.value.trim()
                Text(
                    text = if (amount.toDoubleOrNull() != null) "$amount$NARROW_NO_BREAK_SPACE${value.kind.defaultUnit}" else amount,
                    style = AromaTheme.type.label.tabular(),
                )
            }
        }
    }
}

/** A line of the nutrition facts, as tall as a line of the ingredients. */
private val NutritionRowHeight = 40.dp

@Composable
@ReadOnlyComposable
private fun nutritionLabel(kind: NutritionKind): String =
    stringResource(
        when (kind) {
            NutritionKind.Calories -> R.string.nutrition_calories
            NutritionKind.Protein -> R.string.nutrition_protein
            NutritionKind.Fat -> R.string.nutrition_fat
            NutritionKind.SaturatedFat -> R.string.nutrition_saturated_fat
            NutritionKind.Carbohydrates -> R.string.nutrition_carbohydrates
            NutritionKind.Sugar -> R.string.nutrition_sugar
            NutritionKind.Fiber -> R.string.nutrition_fiber
            NutritionKind.Sodium -> R.string.nutrition_sodium
            NutritionKind.Cholesterol -> R.string.nutrition_cholesterol
        },
    )

/** How the outline's parts measure: the title's line, the box of figures. */
private val TitleSkeletonHeight = 32.dp
private val FactsSkeletonHeight = 96.dp
private const val TITLE_SHARE = 0.8f
private const val BYLINE_SHARE = 0.5f

/** The page's outline while the recipe loads: photo, title, byline, the box of figures and the headnote. */
@Composable
private fun RecipeLoading() {
    Column {
        Skeleton(
            Modifier
                .fillMaxWidth()
                .height(heroHeight()),
        )
        Column(Modifier.padding(horizontal = PageMargin)) {
            Skeleton(
                Modifier
                    .padding(top = Space.s24)
                    .fillMaxWidth(TITLE_SHARE)
                    .height(TitleSkeletonHeight),
                shape = AromaTheme.shapes.round,
            )
            SkeletonLine(
                Modifier
                    .padding(top = Space.s12)
                    .fillMaxWidth(BYLINE_SHARE),
            )
            Skeleton(
                Modifier
                    .padding(top = Space.s24)
                    .fillMaxWidth()
                    .height(FactsSkeletonHeight),
                shape = AromaTheme.shapes.box,
            )
            Column(Modifier.padding(top = Space.s24), verticalArrangement = Arrangement.spacedBy(Space.s16)) {
                listOf(1f, 1f, TITLE_SHARE).forEach { width -> SkeletonLine(Modifier.fillMaxWidth(width)) }
            }
        }
    }
}
