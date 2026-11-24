package app.aroma.feature.recipe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.format.IngredientFormatter
import app.aroma.core.format.Markdown
import app.aroma.core.format.MeasureSystem
import app.aroma.core.format.Quantities
import app.aroma.core.format.StepAmount
import app.aroma.core.format.StepMeasures
import app.aroma.core.format.StyledText
import app.aroma.core.format.ingredientsByStep
import app.aroma.core.format.stepAmounts
import app.aroma.core.model.Filing
import app.aroma.core.model.ImageSize
import app.aroma.core.model.NutritionKind
import app.aroma.core.model.NutritionValue
import app.aroma.core.model.Organizer
import app.aroma.core.model.OrganizerKind
import app.aroma.core.model.Recipe
import app.aroma.core.model.RecipeNote
import app.aroma.core.model.Step
import app.aroma.data.AromaError
import app.aroma.data.settings.IngredientsInSteps
import app.aroma.feature.editor.ReworkJob
import app.aroma.feature.editor.WorkProgress
import app.aroma.ui.components.AromaAlertDialog
import app.aroma.ui.components.AromaBottomSheet
import app.aroma.ui.components.AromaDropdownMenu
import app.aroma.ui.components.AromaFilterChip
import app.aroma.ui.components.AromaMenuItem
import app.aroma.ui.components.BarIconButton
import app.aroma.ui.components.BottomActionBar
import app.aroma.ui.components.CardGroup
import app.aroma.ui.components.ChoiceRow
import app.aroma.ui.components.DetailTopBar
import app.aroma.ui.components.DetailTopBarHeight
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.HeaderAction
import app.aroma.ui.components.IconAction
import app.aroma.ui.components.IngredientList
import app.aroma.ui.components.IngredientRow
import app.aroma.ui.components.MenuChip
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.OnPhotoButton
import app.aroma.ui.components.Placeholder
import app.aroma.ui.components.PlaceholderLine
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.QuietLink
import app.aroma.ui.components.QuietPress
import app.aroma.ui.components.RatingStars
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.SecondaryButton
import app.aroma.ui.components.SectionHeader
import app.aroma.ui.components.SoftCard
import app.aroma.ui.components.StatusBarOverPhoto
import app.aroma.ui.components.Stepper
import app.aroma.ui.components.Text
import app.aroma.ui.components.actionBarInset
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.hideThen
import app.aroma.ui.components.ratingDescription
import app.aroma.ui.components.recipeTimeText
import app.aroma.ui.components.rememberFullSheetState
import app.aroma.ui.components.rememberRichText
import app.aroma.ui.components.rememberStepText
import app.aroma.ui.components.totalTime
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.ChipHeight
import app.aroma.ui.theme.ControlHeight
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.SectionSpace
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import app.aroma.ui.theme.extras
import app.aroma.ui.theme.tabular
import java.net.URI
import java.util.Locale

/**
 * The recipe page's state. Mealie's AI may rework the recipe where the server has it ([aiAvailable]),
 * translated by default where the user last chose so ([translate]); [rework] is one under way.
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

/** The two actions under the title; [RecipeUiState.confirmed] marks one that just went through. */
enum class QuickAction { Plan, Shop }

data class RecipeActions(
    val onBack: () -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
    val onRate: (Int) -> Unit = {},
    val onScale: (Double) -> Unit = {},
    val onMeasures: (MeasureSystem) -> Unit = {},
    val onToggleIngredient: (Int) -> Unit = {},
    val onAddToShoppingList: () -> Unit = {},
    val onAddToMealPlan: () -> Unit = {},
    val onStartCooking: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onOpenUrl: (String) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onRework: (translate: Boolean) -> Unit = {},
    val onOpenOrganizer: (OrganizerKind, Organizer) -> Unit = { _, _ -> },
    val onOrganize: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

private const val HERO_KEY = "hero"
private const val TITLE_KEY = "title"

/** The plate that stands in for a missing photo at the head of the page. */
private val HeroPlateSize = 48.dp

/** The shade at the top of the photo, so the status bar and the buttons read on light photos too. */
private val PhotoShadeHeight = 120.dp

/** How dark the shade starts at the top edge; it fades to nothing at [PhotoShadeHeight]. */
private const val PHOTO_SHADE = 0.45f

/** The glyph after the source, as small as the stars before it. */
private val SourceGlyph = 14.dp

@Composable
fun RecipeScreen(state: RecipeUiState, actions: RecipeActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val recipe = state.recipe
    var rateOpen by rememberSaveable { mutableStateOf(false) }
    var reworkOpen by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val barBottom = WindowInsets.statusBars.getTop(density) + with(density) { DetailTopBarHeight.roundToPx() }
    // The bar turns to paper once the photo has slid beneath it, and shows the title once that has too.
    val barOverPhoto by remember(listState, barBottom) {
        derivedStateOf { listState.bottomOf(HERO_KEY)?.let { it > barBottom } ?: (listState.firstVisibleItemIndex == 0) }
    }
    val titleGone by remember(listState, barBottom) {
        derivedStateOf { listState.bottomOf(TITLE_KEY)?.let { it <= barBottom } ?: (listState.firstVisibleItemIndex > 0) }
    }
    val canCook = recipe?.steps?.isNotEmpty() == true
    val locale = currentLocale()
    // What each step uses, scaled and in the chosen measures like the list above it.
    val stepUses =
        remember(recipe, state.scale, state.measures, locale) {
            if (recipe == null) return@remember emptyList()
            val lines =
                recipe.ingredients.map { ingredient ->
                    val line = IngredientFormatter.format(ingredient, state.scale, locale, state.measures)
                    // As the list sets it: the note after a comma.
                    listOfNotNull(line.measureText(), line.text, line.note?.let { ", $it" })
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .replace(" ,", ",")
                }
            recipe.ingredientsByStep(locale).map { used -> used.mapNotNull(lines::getOrNull) }
        }
    // The steps as they read: temperatures and pan sizes in the chosen measures, and the amounts written in where chosen.
    val stepTexts =
        remember(recipe, state.measures, locale) {
            recipe?.steps.orEmpty().map { Markdown.parse(StepMeasures.convert(it.text, state.measures, locale)) }
        }
    val stepAmounts =
        remember(recipe, stepTexts, state.scale, state.measures, locale, state.amountsInSteps) {
            if (recipe == null || !state.amountsInSteps) {
                emptyList()
            } else {
                recipe.stepAmounts(stepTexts.map { it.text }, state.scale, locale, state.measures)
            }
        }
    Box(modifier.fillMaxSize()) {
        when {
            recipe != null -> {
                LazyColumn(
                    state = listState,
                    // The last section ends clear above the action bar.
                    contentPadding = PaddingValues(bottom = actionBarInset() + Space.l),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = HERO_KEY) { Hero(recipe) }
                    recipeBody(recipe, state, actions, Steps(stepTexts, stepAmounts, stepUses), onOpenRating = { rateOpen = true })
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
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(top = DetailTopBarHeight),
                )
            }
        }
        RecipeTopBar(
            recipe = recipe,
            overPhoto = if (recipe != null) barOverPhoto else state.isLoading,
            titleGone = titleGone,
            isFavorite = state.isFavorite,
            actions = actions,
            // One rework at a time: the next only once the page showed how the last ended.
            onRework = { reworkOpen = true }.takeIf { state.aiAvailable && state.rework == null },
            onRate = { rateOpen = true },
        )
        if (recipe != null) {
            RecipeActionBar(
                canShop = recipe.ingredients.isNotEmpty(),
                canCook = canCook,
                confirmed = state.confirmed,
                actions = actions,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    // Light icons on the photo's shade; the theme's own once the photo has gone beneath the bar.
    StatusBarOverPhoto(overPhoto = if (recipe != null) barOverPhoto else state.isLoading)
    if (rateOpen && recipe != null) {
        RatingSheet(rating = state.userRating, onRate = actions.onRate, onDismiss = { rateOpen = false })
    }
    if (reworkOpen && recipe != null) {
        ReworkSheet(translate = state.translate, onRework = actions.onRework, onDismiss = { reworkOpen = false })
    }
}

private fun LazyListState.bottomOf(key: Any): Int? = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { it.offset + it.size }

/**
 * Plan and shop as round buttons, the two things people do with a recipe besides cooking it, and
 * cooking itself across the rest of the bar, the page's one main action. Shop is there where
 * there is something to buy; cooking, where there are steps to cook by.
 */
@Composable
private fun RecipeActionBar(
    canShop: Boolean,
    canCook: Boolean,
    confirmed: QuickAction?,
    actions: RecipeActions,
    modifier: Modifier = Modifier,
) {
    BottomActionBar(modifier) {
        BarIconButton(
            icon = R.drawable.ic_calendar_add,
            contentDescription = stringResource(R.string.recipe_action_plan),
            onClick = actions.onAddToMealPlan,
            confirmed = confirmed == QuickAction.Plan,
        )
        if (canShop) {
            BarIconButton(
                icon = R.drawable.ic_basket,
                contentDescription = stringResource(R.string.recipe_action_shop),
                onClick = actions.onAddToShoppingList,
                confirmed = confirmed == QuickAction.Shop,
            )
        }
        PrimaryButton(
            text = stringResource(R.string.recipe_start_cooking),
            onClick = actions.onStartCooking,
            leadingIcon = R.drawable.ic_cooking,
            enabled = canCook,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Hero(recipe: Recipe) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f),
    ) {
        RecipeImage(
            image = recipe.image,
            size = ImageSize.Original,
            placeholder = ImageSize.Medium,
            fallbackIconSize = HeroPlateSize,
            modifier = Modifier.fillMaxSize(),
        )
        val scrim = MaterialTheme.colorScheme.scrim
        Box(
            Modifier
                .fillMaxWidth()
                .height(PhotoShadeHeight)
                .background(Brush.verticalGradient(listOf(scrim.copy(alpha = PHOTO_SHADE), scrim.copy(alpha = 0f)))),
        )
    }
}

/**
 * Over the photo, back, favourite and more rest on paper circles. Once the photo has gone, they
 * become the plain bar every pushed screen has, which takes up the title when that has scrolled away.
 * The menu offers a rework with Mealie's AI where there is [onRework].
 */
@Composable
private fun RecipeTopBar(
    recipe: Recipe?,
    overPhoto: Boolean,
    titleGone: Boolean,
    isFavorite: Boolean,
    actions: RecipeActions,
    onRework: (() -> Unit)?,
    onRate: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Box {
        AnimatedVisibility(visible = !overPhoto, enter = fadeIn(tween(Motion.STATE)), exit = fadeOut(tween(Motion.STATE))) {
            DetailTopBar(onBack = actions.onBack, title = recipe?.name, collapsed = titleGone) {
                if (recipe != null) {
                    FavoriteButton(isFavorite, actions.onToggleFavorite, onPhoto = false)
                    MoreMenu(recipe, actions, onRework, onRate, onDelete = { confirmDelete = true }, onPhoto = false)
                }
            }
        }
        AnimatedVisibility(visible = overPhoto, enter = fadeIn(tween(Motion.STATE)), exit = fadeOut(tween(Motion.STATE))) {
            // The circles sit 16 dp from the edges and 8 dp apart; their touch areas reach 4 dp further.
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Space.s, vertical = Space.xxs),
            ) {
                OnPhotoButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), actions.onBack)
                Spacer(Modifier.weight(1f))
                if (recipe != null) {
                    FavoriteButton(isFavorite, actions.onToggleFavorite, onPhoto = true)
                    MoreMenu(recipe, actions, onRework, onRate, onDelete = { confirmDelete = true }, onPhoto = true)
                }
            }
        }
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

@Composable
private fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit, onPhoto: Boolean) {
    val icon = if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite
    val label = stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite)
    val tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    if (onPhoto) {
        OnPhotoButton(icon, label, onClick, tint = tint)
    } else {
        HeaderAction(icon, label, onClick, tint = tint)
    }
}

@Composable
private fun MoreMenu(
    recipe: Recipe,
    actions: RecipeActions,
    onRework: (() -> Unit)?,
    onRate: () -> Unit,
    onDelete: () -> Unit,
    onPhoto: Boolean,
) {
    var open by remember { mutableStateOf(false) }
    val choose: (() -> Unit) -> Unit = { action ->
        open = false
        action()
    }
    Box {
        val label = stringResource(R.string.action_more)
        if (onPhoto) {
            OnPhotoButton(R.drawable.ic_more_horiz, label, onClick = { open = true })
        } else {
            HeaderAction(R.drawable.ic_more_horiz, label, onClick = { open = true })
        }
        AromaDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AromaMenuItem(stringResource(R.string.action_edit), R.drawable.ic_edit, { choose(actions.onEdit) })
            AromaMenuItem(stringResource(R.string.recipe_rate), R.drawable.ic_star, { choose(onRate) })
            onRework?.let { AromaMenuItem(stringResource(R.string.recipe_rework), R.drawable.ic_sparkle, { choose(it) }) }
            AromaMenuItem(stringResource(R.string.action_share), R.drawable.ic_share, { choose(actions.onShare) })
            recipe.sourceUrl?.let { url ->
                AromaMenuItem(stringResource(R.string.recipe_open_source), R.drawable.ic_open_in_new, { choose { actions.onOpenUrl(url) } })
            }
            AromaMenuItem(stringResource(R.string.action_delete), R.drawable.ic_delete, { choose(onDelete) }, destructive = true)
        }
    }
}

/** The steps' [texts] as they read, the [amounts] written into them and the ingredients each [uses], as the list shows them. */
private class Steps(val texts: List<StyledText>, val amounts: List<List<StepAmount>>, val uses: List<List<String>>)

private fun LazyListScope.recipeBody(
    recipe: Recipe,
    state: RecipeUiState,
    actions: RecipeActions,
    steps: Steps,
    onOpenRating: () -> Unit,
) {
    item(key = TITLE_KEY) { TitleBlock(recipe, state.isOffline) }
    item(key = "byline") {
        Byline(
            rating = state.userRating ?: recipe.rating,
            sourceUrl = recipe.sourceUrl,
            onOpenRating = onOpenRating,
            onOpenUrl = actions.onOpenUrl,
        )
    }
    state.rework?.takeIf { it.outcome == null }?.let { job ->
        item(key = "rework") {
            Column(Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.l)) {
                WorkProgress(job.steps, job.startedAt, stringResource(R.string.recipe_rework_running), Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.recipe_rework_progress_hint),
                    style = AromaType.Small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = CardPadding, end = CardPadding, top = Space.s),
                )
            }
        }
    }
    if (recipe.description.isNotBlank()) {
        item(key = "description") {
            Text(
                text = rememberRichText(recipe.description),
                style = AromaType.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.l),
            )
        }
    }
    item(key = "facts") { Facts(recipe) }
    item(key = "organizers") { Organizers(recipe, actions.onOpenOrganizer, actions.onOrganize) }
    if (recipe.ingredients.isNotEmpty()) {
        item(key = "ingredients") { Ingredients(recipe, state, actions) }
    }
    if (recipe.steps.isNotEmpty()) {
        item(key = "stepsHeader") { SectionHeader(stringResource(R.string.recipe_steps)) }
        itemsIndexed(recipe.steps, key = { index, step -> step.id ?: "step$index" }) { index, step ->
            StepItem(
                index = index,
                step = step,
                text = steps.texts.getOrNull(index) ?: StyledText(step.text, emptyList()),
                amounts = steps.amounts.getOrNull(index).orEmpty(),
                uses = if (state.ingredientsInSteps == IngredientsInSteps.None) emptyList() else steps.uses.getOrNull(index).orEmpty(),
                modifier = Modifier.padding(top = if (index == 0) Space.xs else Space.l),
            )
        }
    }
    if (recipe.notes.isNotEmpty()) {
        item(key = "notes") { Notes(recipe.notes) }
    }
    if (recipe.nutrition.isNotEmpty()) {
        item(key = "nutrition") { Nutrition(recipe.nutrition) }
    }
}

@Composable
private fun TitleBlock(recipe: Recipe, isOffline: Boolean) {
    Column(Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.l)) {
        if (isOffline) OfflineNotice(Modifier.padding(bottom = Space.m))
        Text(recipe.name, style = AromaType.PageTitle, modifier = Modifier.semantics { heading() })
    }
}

/**
 * Categories and tags as soft chips; each opens the recipes it names. After them, a pencil to change
 * them, or, while nothing says what kind of dish it is, a link to choose that.
 */
@Composable
private fun Organizers(recipe: Recipe, onOpen: (OrganizerKind, Organizer) -> Unit, onOrganize: () -> Unit) {
    val organizers =
        remember(recipe.categories, recipe.tags) {
            recipe.categories.map { OrganizerKind.Category to it } + recipe.tags.map { OrganizerKind.Tag to it }
        }
    val hasMeal = remember(recipe.categories, recipe.tags) { Filing(recipe.categories, recipe.tags).meals.isNotEmpty() }
    FlowRow(
        modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        organizers.forEach { (kind, organizer) ->
            AromaFilterChip(selected = false, label = organizer.name, onClick = { onOpen(kind, organizer) })
        }
        if (hasMeal) {
            IconAction(R.drawable.ic_edit, stringResource(R.string.organizers_change), onOrganize, glyphSize = Glyph.s)
        } else {
            QuietLink(stringResource(R.string.organizers_choose_meal), onOrganize, leadingIcon = R.drawable.ic_add)
        }
    }
}

/**
 * Stars and the source on one quiet line: the stars open the rating sheet, the host the original
 * page. Stars show only once there is a rating; rating a recipe for the first time is in the menu.
 */
@Composable
private fun Byline(rating: Double?, sourceUrl: String?, onOpenRating: () -> Unit, onOpenUrl: (String) -> Unit) {
    val locale = currentLocale()
    val host = remember(sourceUrl) { sourceUrl?.let { runCatching { URI(it).host?.removePrefix("www.") }.getOrNull() } }
    val meta = AromaType.Small
    val description = ratingDescription(rating)
    if (rating == null && host == null) return
    Row(
        Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rating != null) {
            QuietTarget(
                onClick = onOpenRating,
                onClickLabel = stringResource(R.string.recipe_rate),
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                RatingStars(rating, Modifier.clearAndSetSemantics {})
                Text(
                    text = remember(rating, locale) { "%.1f".format(locale, rating) },
                    style = meta.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(start = Space.xs)
                            .clearAndSetSemantics {},
                )
            }
        }
        if (host != null && sourceUrl != null) {
            if (rating != null) Dot(meta)
            QuietTarget(onClick = { onOpenUrl(sourceUrl) }, modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = host,
                    style = meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    painterResource(R.drawable.ic_open_in_new),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(start = Space.xxs)
                            .size(SourceGlyph),
                )
            }
        }
    }
}

/** A target made of text: no ripple, it dims while pressed, as a quiet link does. */
@Composable
private fun QuietTarget(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier.clickable(
                interactionSource = null,
                indication = QuietPress,
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The middle dot between items on a line, a shade lighter than the items. */
@Composable
private fun Dot(style: TextStyle) {
    Text("·", style = style, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = Space.xs))
}

/** Prep, cook and total time on a soft card, each a value over its name. */
@Composable
private fun Facts(recipe: Recipe) {
    val facts =
        listOfNotNull(
            recipeTimeText(recipe.prepTime)?.let { stringResource(R.string.recipe_prep_time) to it },
            recipeTimeText(recipe.cookTime)?.let { stringResource(R.string.recipe_cook_time) to it },
            recipeTimeText(totalTime(recipe.totalTime, recipe.prepTime, recipe.cookTime))?.let {
                stringResource(R.string.recipe_total_time) to it
            },
        )
    if (facts.isEmpty()) return
    SoftCard(Modifier.padding(top = Space.l)) {
        Row(Modifier.padding(horizontal = CardPadding, vertical = Space.s)) {
            facts.forEach { (label, value) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = value, style = AromaType.BodyStrong.tabular(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = label,
                        style = AromaType.Small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Ingredients(recipe: Recipe, state: RecipeUiState, actions: RecipeActions) {
    val locale = currentLocale()
    val rows =
        remember(recipe.ingredients, state.scale, state.measures, locale) {
            recipe.ingredients.map {
                IngredientRow(it.section, IngredientFormatter.format(it, state.scale, locale, state.measures))
            }
        }
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = ScreenMargin, end = ScreenMargin, top = SectionSpace),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recipe_ingredients),
                style = AromaType.SectionTitle,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { heading() },
            )
            Stepper(
                label = servingsLabel(recipe.servings, state.scale, locale),
                onDecrease = { actions.onScale(Servings.step(recipe.servings, state.scale, -1)) },
                onIncrease = { actions.onScale(Servings.step(recipe.servings, state.scale, +1)) },
                canDecrease = Servings.canDecrease(recipe.servings, state.scale),
            )
        }
        MeasuresLink(state.measures, actions.onMeasures, Modifier.padding(start = ScreenMargin))
        IngredientList(
            rows = rows,
            checked = state.checkedIngredients,
            onToggle = actions.onToggleIngredient,
            onCards = true,
            modifier = Modifier.padding(top = Space.m),
        )
        if (recipe.tools.isNotEmpty()) {
            CardGroup(stringResource(R.string.recipe_tools), Modifier.padding(top = Space.l)) {
                Text(
                    text = recipe.tools.joinToString(", ") { it.name },
                    style = AromaType.Body,
                    modifier = Modifier.padding(horizontal = CardPadding, vertical = Space.s),
                )
            }
        }
    }
}

/** Which measures the amounts show, "Units: as written", as a link that opens the three choices on a sheet. */
@Composable
private fun MeasuresLink(system: MeasureSystem, onSelect: (MeasureSystem) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    QuietLink(
        text = stringResource(R.string.measures_link, stringResource(system.shortLabelRes())),
        onClick = { open = true },
        modifier = modifier,
    )
    if (open) MeasuresSheet(system, onSelect, onDismiss = { open = false })
}

/** The three choices of measures; choosing one applies it and lets the sheet slide away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasuresSheet(current: MeasureSystem, onSelect: (MeasureSystem) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberFullSheetState()
    val scope = rememberCoroutineScope()
    AromaBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.measures_title), sheetState = sheetState) {
        Column(
            Modifier
                .selectableGroup()
                .padding(bottom = Space.m),
        ) {
            MeasureSystem.entries.forEach { option ->
                ChoiceRow(
                    label = stringResource(option.labelRes()),
                    chosen = option == current,
                    modifier =
                        Modifier.selectable(selected = option == current, role = Role.RadioButton) {
                            onSelect(option)
                            scope.hideThen(sheetState, onDismiss)
                        },
                )
            }
        }
    }
}

/** The choice of measures within a sentence: "as written", "metric", "US". */
private fun MeasureSystem.shortLabelRes(): Int =
    when (this) {
        MeasureSystem.AsWritten -> R.string.measures_short_as_written
        MeasureSystem.Metric -> R.string.measures_short_metric
        MeasureSystem.US -> R.string.measures_short_us
    }

/** The name of a choice of measures, as the chip and the setting show it. */
fun MeasureSystem.labelRes(): Int =
    when (this) {
        MeasureSystem.AsWritten -> R.string.measures_as_written
        MeasureSystem.Metric -> R.string.measures_metric
        MeasureSystem.US -> R.string.measures_us
    }

@Composable
@ReadOnlyComposable
fun servingsLabel(baseServings: Double, scale: Double, locale: Locale): String {
    if (baseServings <= 0) {
        val text = Quantities.display(scale, preferFraction = true, locale = locale)?.toPlainText() ?: "1"
        return stringResource(R.string.recipe_multiplier, text)
    }
    val target = baseServings * scale
    val count = Math.round(target).toInt()
    val text = Quantities.display(target, preferFraction = false, locale = locale)?.toPlainText() ?: count.toString()
    return pluralStringResource(R.plurals.servings, count, text)
}

/** A step with its number in sage in the gutter, level with the first line. */
@Composable
private fun StepItem(
    index: Int,
    step: Step,
    text: StyledText,
    amounts: List<StepAmount>,
    uses: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(horizontal = ScreenMargin)) {
        Text(
            text = (index + 1).toString(),
            style = AromaType.SectionTitle.tabular(),
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .width(StepNumberWidth)
                    .alignByBaseline(),
        )
        Column(
            Modifier
                .weight(1f)
                .alignByBaseline(),
        ) {
            if (!step.title.isNullOrBlank()) {
                Text(step.title, style = AromaType.BodyStrong, modifier = Modifier.padding(bottom = Space.xxs))
            }
            Text(text = rememberStepText(text, amounts), style = AromaType.Body)
            // The ingredients the step uses, as the recipe's list shows them, so the step reads on its own.
            if (uses.isNotEmpty()) {
                Text(
                    text = uses.joinToString(" · "),
                    style = AromaType.Small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        }
    }
}

/** The gutter a step's number stands in, wide enough for two digits. */
private val StepNumberWidth = 40.dp

@Composable
private fun Notes(notes: List<RecipeNote>) {
    Column {
        SectionHeader(stringResource(R.string.recipe_notes))
        notes.forEachIndexed { index, note ->
            SoftCard(Modifier.padding(top = if (index == 0) Space.xs else Space.s)) {
                Column(Modifier.padding(horizontal = CardPadding, vertical = Space.s)) {
                    if (note.title.isNotBlank()) {
                        Text(note.title, style = AromaType.BodyStrong, modifier = Modifier.padding(bottom = Space.xxs))
                    }
                    Text(text = rememberRichText(note.text), style = AromaType.Body)
                }
            }
        }
    }
}

@Composable
private fun Nutrition(values: List<NutritionValue>) {
    Column {
        SectionHeader(stringResource(R.string.recipe_nutrition))
        SoftCard(Modifier.padding(top = Space.xs)) {
            values.forEachIndexed { index, value ->
                if (index > 0) Hairline(Modifier.padding(horizontal = CardPadding))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget)
                        .padding(horizontal = CardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = nutritionLabel(value.kind),
                        style = AromaType.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    val amount = value.value.trim()
                    Text(
                        text = if (amount.toDoubleOrNull() != null) "$amount ${value.kind.defaultUnit}" else amount,
                        style = AromaType.BodyStrong.tabular(),
                    )
                }
            }
        }
    }
}

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

/** How the outline's lines measure: the title, the byline and an action. */
private val TitleHeight = 32.dp
private val BylineWidth = 140.dp
private val ActionWidth = 96.dp

/** The page's outline while the recipe loads: photo, title, byline, actions and the description. */
@Composable
private fun RecipeLoading() {
    Column {
        Placeholder(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
            shape = RectangleShape,
        )
        Column(Modifier.padding(horizontal = ScreenMargin)) {
            Placeholder(
                Modifier
                    .padding(top = Space.l)
                    .fillMaxWidth(0.8f)
                    .height(TitleHeight),
                shape = MaterialTheme.shapes.extraSmall,
            )
            PlaceholderLine(
                Modifier
                    .padding(top = Space.m)
                    .width(BylineWidth),
            )
            Row(Modifier.padding(top = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                repeat(2) { Placeholder(Modifier.size(ActionWidth, ChipHeight), shape = CircleShape) }
            }
            // The description: two full lines and a short one, on the body's line pitch.
            Column(Modifier.padding(top = Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                listOf(1f, 1f, 0.6f).forEach { width -> PlaceholderLine(Modifier.fillMaxWidth(width)) }
            }
        }
    }
}
