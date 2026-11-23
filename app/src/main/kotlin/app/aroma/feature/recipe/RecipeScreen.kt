package app.aroma.feature.recipe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.format.IngredientFormatter
import app.aroma.core.format.Quantities
import app.aroma.core.model.ImageSize
import app.aroma.core.model.NutritionKind
import app.aroma.core.model.NutritionValue
import app.aroma.core.model.Organizer
import app.aroma.core.model.OrganizerKind
import app.aroma.core.model.Recipe
import app.aroma.core.model.RecipeNote
import app.aroma.core.model.Step
import app.aroma.data.AromaError
import app.aroma.ui.components.AromaAlertDialog
import app.aroma.ui.components.AromaDropdownMenu
import app.aroma.ui.components.AromaMenuItem
import app.aroma.ui.components.DetailTopBar
import app.aroma.ui.components.DetailTopBarHeight
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.HeaderAction
import app.aroma.ui.components.IngredientList
import app.aroma.ui.components.IngredientRow
import app.aroma.ui.components.Kicker
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.OnPhotoButton
import app.aroma.ui.components.Placeholder
import app.aroma.ui.components.PlaceholderLine
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.PrimaryButtonHeight
import app.aroma.ui.components.QuietPress
import app.aroma.ui.components.RatingStars
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.SecondaryButton
import app.aroma.ui.components.SectionHeader
import app.aroma.ui.components.SoftCard
import app.aroma.ui.components.Stepper
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.recipeTimeText
import app.aroma.ui.components.rememberRichText
import app.aroma.ui.theme.IconKeyline
import app.aroma.ui.theme.KickerStyle
import app.aroma.ui.theme.Lines
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Motion.Settle
import app.aroma.ui.theme.Reading
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.Standfirst
import app.aroma.ui.theme.StepNumber
import app.aroma.ui.theme.TouchTarget
import app.aroma.ui.theme.extras
import app.aroma.ui.theme.floatingShadow
import app.aroma.ui.theme.tabular
import java.net.URI
import java.util.Locale

data class RecipeUiState(
    val recipe: Recipe? = null,
    val scale: Double = 1.0,
    val checkedIngredients: Set<Int> = emptySet(),
    val isFavorite: Boolean = false,
    val userRating: Double? = null,
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val error: AromaError? = null,
    val confirmed: QuickAction? = null,
)

/** The two actions under the title; [RecipeUiState.confirmed] marks one that just went through. */
enum class QuickAction { Plan, Shop }

data class RecipeActions(
    val onBack: () -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
    val onRate: (Int) -> Unit = {},
    val onScale: (Double) -> Unit = {},
    val onToggleIngredient: (Int) -> Unit = {},
    val onAddToShoppingList: () -> Unit = {},
    val onAddToMealPlan: () -> Unit = {},
    val onStartCooking: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onOpenUrl: (String) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onOpenRecipe: (String) -> Unit = {},
    val onOpenOrganizer: (OrganizerKind, Organizer) -> Unit = { _, _ -> },
    val onRetry: () -> Unit = {},
)

private const val HERO_KEY = "hero"
private const val TITLE_KEY = "title"

/** The plate that stands in for a missing photo at the head of the page. */
private val HeroPlateSize = 48.dp

/** Just enough shade at the top of the photo for the status bar and the buttons to read on it. */
private val PhotoShadeHeight = 96.dp

/** The glyph after the source, as small as the stars before it. */
private val SourceGlyph = 14.dp

/** Nutrition is a small table, set closer than rows that take a tap. */
private val NutritionRowHeight = 44.dp

@Composable
fun RecipeScreen(state: RecipeUiState, actions: RecipeActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val recipe = state.recipe
    var rateOpen by rememberSaveable { mutableStateOf(false) }
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
    // The cook button rises once the photo is gone, so on opening it never sits on the servings;
    // a recipe too short to scroll that far shows it right away.
    val cookShown by remember(listState) { derivedStateOf { !barOverPhoto || !listState.canScrollForward } }
    Box(modifier.fillMaxSize()) {
        when {
            recipe != null -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = endSpace(canCook)),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = HERO_KEY) { Hero(recipe) }
                    recipeBody(recipe, state, actions, onOpenRating = { rateOpen = true })
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
        )
        CookButton(visible = canCook && cookShown, onClick = actions.onStartCooking)
    }
    if (rateOpen && recipe != null) {
        RatingSheet(rating = state.userRating, onRate = actions.onRate, onDismiss = { rateOpen = false })
    }
}

private fun LazyListState.bottomOf(key: Any): Int? = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { it.offset + it.size }

/** Room below the last section: clear of the cook button when there is one. */
@Composable
private fun endSpace(canCook: Boolean) =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        if (canCook) PrimaryButtonHeight + Space.m + Space.xl else Space.xl

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
                .background(Brush.verticalGradient(listOf(scrim.copy(alpha = 0.28f), scrim.copy(alpha = 0f)))),
        )
    }
}

/**
 * Over the photo, back, favourite and more rest on paper circles. Once the photo has gone, they
 * become the plain bar every pushed screen has, which takes up the title when that has scrolled away.
 */
@Composable
private fun RecipeTopBar(
    recipe: Recipe?,
    overPhoto: Boolean,
    titleGone: Boolean,
    isFavorite: Boolean,
    actions: RecipeActions,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Box {
        AnimatedVisibility(visible = !overPhoto, enter = fadeIn(tween(Motion.STATE)), exit = fadeOut(tween(Motion.STATE))) {
            DetailTopBar(onBack = actions.onBack, title = recipe?.name, collapsed = titleGone) {
                if (recipe != null) {
                    FavoriteButton(isFavorite, actions.onToggleFavorite, onPhoto = false)
                    MoreMenu(recipe, actions, onDelete = { confirmDelete = true }, onPhoto = false)
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
                    MoreMenu(recipe, actions, onDelete = { confirmDelete = true }, onPhoto = true)
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
private fun MoreMenu(recipe: Recipe, actions: RecipeActions, onDelete: () -> Unit, onPhoto: Boolean) {
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
            AromaMenuItem(stringResource(R.string.recipe_add_to_plan), R.drawable.ic_calendar_add, { choose(actions.onAddToMealPlan) })
            if (recipe.ingredients.isNotEmpty()) {
                AromaMenuItem(
                    stringResource(R.string.recipe_add_to_list),
                    R.drawable.ic_basket_add,
                    { choose(actions.onAddToShoppingList) },
                )
            }
            Hairline(Modifier.padding(vertical = Space.xxs))
            AromaMenuItem(stringResource(R.string.action_edit), R.drawable.ic_edit, { choose(actions.onEdit) })
            AromaMenuItem(stringResource(R.string.action_share), R.drawable.ic_share, { choose(actions.onShare) })
            recipe.sourceUrl?.let { url ->
                AromaMenuItem(stringResource(R.string.recipe_open_source), R.drawable.ic_open_in_new, { choose { actions.onOpenUrl(url) } })
            }
            AromaMenuItem(stringResource(R.string.action_delete), R.drawable.ic_delete, { choose(onDelete) }, destructive = true)
        }
    }
}

/**
 * The cook button floats over the page like the tab bar does on the tab pages, and rises a moment
 * after the recipe is there. The page fades into paper behind it, so text never runs into its edge.
 */
@Composable
private fun BoxScope.CookButton(visible: Boolean, onClick: () -> Unit) {
    // Starts hidden even when the recipe is already there, so the button always rises into place.
    val shown = remember { MutableTransitionState(false) }
    SideEffect { shown.targetState = visible }
    AnimatedVisibility(
        visibleState = shown,
        enter = fadeIn(tween(Motion.EXPAND, delayMillis = Motion.STATE)),
        exit = fadeOut(tween(Motion.STATE)),
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            val page = MaterialTheme.colorScheme.background
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0f to page.copy(alpha = 0f), 0.45f to page.copy(alpha = 0.85f), 1f to page))
                    .navigationBarsPadding()
                    .height(PrimaryButtonHeight + Space.m + Space.l),
            )
            PrimaryButton(
                text = stringResource(R.string.recipe_start_cooking),
                onClick = onClick,
                leadingIcon = R.drawable.ic_cooking,
                modifier =
                    Modifier
                        .animateEnterExit(
                            enter = slideInVertically(tween(Motion.EXPAND, delayMillis = Motion.STATE, easing = Settle)) { it / 3 },
                            exit = ExitTransition.None,
                        ).navigationBarsPadding()
                        .padding(bottom = Space.m)
                        .floatingShadow(CircleShape),
            )
        }
    }
}

private fun LazyListScope.recipeBody(recipe: Recipe, state: RecipeUiState, actions: RecipeActions, onOpenRating: () -> Unit) {
    item(key = TITLE_KEY) { TitleBlock(recipe, state.isOffline, actions.onOpenOrganizer) }
    item(key = "byline") {
        Byline(
            rating = state.userRating ?: recipe.rating,
            sourceUrl = recipe.sourceUrl,
            onOpenRating = onOpenRating,
            onOpenUrl = actions.onOpenUrl,
        )
    }
    item(key = "actions") {
        RecipeQuickActions(
            canShop = recipe.ingredients.isNotEmpty(),
            confirmed = state.confirmed,
            onPlan = actions.onAddToMealPlan,
            onShop = actions.onAddToShoppingList,
            modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.l),
        )
    }
    if (recipe.description.isNotBlank()) {
        item(key = "description") {
            Text(
                text = rememberRichText(recipe.description),
                style = Standfirst,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.l),
            )
        }
    }
    item(key = "facts") { Facts(recipe) }
    if (recipe.ingredients.isNotEmpty()) {
        item(key = "ingredients") { Ingredients(recipe, state, actions) }
    }
    if (recipe.steps.isNotEmpty()) {
        item(key = "stepsHeader") { SectionHeader(stringResource(R.string.recipe_steps), Modifier.padding(top = Space.xxl)) }
        itemsIndexed(recipe.steps, key = { index, step -> step.id ?: "step$index" }) { index, step ->
            StepItem(index, step, Modifier.padding(top = if (index == 0) Space.m else Space.l))
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
private fun TitleBlock(recipe: Recipe, isOffline: Boolean, onOpenOrganizer: (OrganizerKind, Organizer) -> Unit) {
    val organizers =
        remember(recipe.categories, recipe.tags) {
            recipe.categories.map { OrganizerKind.Category to it } + recipe.tags.map { OrganizerKind.Tag to it }
        }
    Column(Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.l)) {
        if (isOffline) OfflineNotice(Modifier.padding(bottom = Space.m))
        if (organizers.isNotEmpty()) OrganizerLine(organizers, onOpenOrganizer, Modifier.padding(bottom = Space.xs))
        Text(recipe.name, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    }
}

/**
 * Categories and tags as one line of small capitals, where the chips used to be; each opens the
 * recipes it names. Compose widens these short targets to the 48 dp minimum, so the line keeps
 * its tight place above the title.
 */
@Composable
private fun OrganizerLine(
    organizers: List<Pair<OrganizerKind, Organizer>>,
    onOpen: (OrganizerKind, Organizer) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier) {
        organizers.forEachIndexed { index, (kind, organizer) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuietTarget(onClick = { onOpen(kind, organizer) }) { Kicker(organizer.name) }
                if (index < organizers.lastIndex) Dot(KickerStyle)
            }
        }
    }
}

/** Stars and the source on one quiet line: the stars open the rating sheet, the host the original page. */
@Composable
private fun Byline(rating: Double?, sourceUrl: String?, onOpenRating: () -> Unit, onOpenUrl: (String) -> Unit) {
    val locale = currentLocale()
    val host = remember(sourceUrl) { sourceUrl?.let { runCatching { URI(it).host?.removePrefix("www.") }.getOrNull() } }
    val meta = MaterialTheme.typography.bodySmall
    val description = stringResource(R.string.rating_description, rating ?: 0.0)
    Row(
        Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuietTarget(
            onClick = onOpenRating,
            onClickLabel = stringResource(R.string.recipe_rate),
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            RatingStars(rating, Modifier.clearAndSetSemantics {})
            if (rating != null) {
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
            Dot(meta)
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

/** Plan and shop, the two things people do with a recipe besides cooking it, right under its name. */
@Composable
private fun RecipeQuickActions(
    canShop: Boolean,
    confirmed: QuickAction?,
    onPlan: () -> Unit,
    onShop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        SecondaryButton(
            icon = R.drawable.ic_calendar_add,
            text = stringResource(R.string.recipe_action_plan),
            onClick = onPlan,
            confirmed = confirmed == QuickAction.Plan,
            modifier = Modifier.weight(1f),
        )
        if (canShop) {
            SecondaryButton(
                icon = R.drawable.ic_basket_add,
                text = stringResource(R.string.recipe_action_shop),
                onClick = onShop,
                confirmed = confirmed == QuickAction.Shop,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Prep, cook and total time on a soft card, in columns that start on the margin. */
@Composable
private fun Facts(recipe: Recipe) {
    val facts =
        listOfNotNull(
            recipeTimeText(recipe.prepTime)?.let { stringResource(R.string.recipe_prep_time) to it },
            recipeTimeText(recipe.cookTime)?.let { stringResource(R.string.recipe_cook_time) to it },
            recipeTimeText(recipe.totalTime)?.let { stringResource(R.string.recipe_total_time) to it },
        )
    if (facts.isEmpty()) return
    SoftCard(Modifier.padding(top = Space.xl)) {
        Row(Modifier.padding(horizontal = ScreenMargin, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            facts.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    VerticalDivider(Modifier.height(Space.xl), thickness = Lines.hairline, color = MaterialTheme.colorScheme.outlineVariant)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .then(if (index > 0) Modifier.padding(start = Space.m) else Modifier),
                ) {
                    Kicker(label)
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Space.xxs),
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
        remember(recipe.ingredients, state.scale, locale) {
            recipe.ingredients.map {
                IngredientRow(it.section, IngredientFormatter.format(it, state.scale, locale), it.linkedRecipe?.slug)
            }
        }
    Column(Modifier.padding(top = Space.xxl)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recipe_ingredients),
                style = MaterialTheme.typography.headlineSmall,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { heading() },
            )
            // The plus circle, not its touch area, ends on the margin.
            Stepper(
                label = servingsLabel(recipe.servings, state.scale, locale),
                onDecrease = { actions.onScale(Servings.step(recipe.servings, state.scale, -1)) },
                onIncrease = { actions.onScale(Servings.step(recipe.servings, state.scale, +1)) },
                canDecrease = Servings.canDecrease(recipe.servings, state.scale),
                modifier = Modifier.offset(x = Space.xxs),
            )
        }
        IngredientList(
            rows = rows,
            checked = state.checkedIngredients,
            onToggle = actions.onToggleIngredient,
            onCards = true,
            modifier = Modifier.padding(top = Space.m),
        )
        if (recipe.tools.isNotEmpty()) {
            Kicker(stringResource(R.string.recipe_tools), Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.l))
            Text(
                text = recipe.tools.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xs),
            )
        }
    }
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

/** A step as in a printed cookbook: a quiet serif numeral in the gutter, level with the first line. */
@Composable
private fun StepItem(index: Int, step: Step, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = ScreenMargin)) {
        Text(
            text = (index + 1).toString(),
            style = StepNumber,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    // Step text starts on the 64 dp keyline, as in the editor.
                    .width(IconKeyline - ScreenMargin)
                    .alignByBaseline(),
        )
        Column(
            Modifier
                .weight(1f)
                .alignByBaseline(),
        ) {
            if (!step.title.isNullOrBlank()) {
                Text(step.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Space.xxs))
            }
            Text(text = rememberRichText(step.text), style = Reading)
        }
    }
}

@Composable
private fun Notes(notes: List<RecipeNote>) {
    Column(Modifier.padding(top = Space.xxl)) {
        SectionHeader(stringResource(R.string.recipe_notes))
        notes.forEachIndexed { index, note ->
            Note(note, Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = if (index == 0) Space.m else Space.l))
        }
    }
}

/** A note set off by a fine rule in the margin, as a cookbook prints an aside. */
@Composable
private fun Note(note: RecipeNote, modifier: Modifier = Modifier) {
    Row(modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(Lines.rule)
                .fillMaxHeight()
                .background(extras.hairlineStrong),
        )
        Column(Modifier.padding(start = Space.m)) {
            if (note.title.isNotBlank()) {
                Text(note.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = Space.xxs))
            }
            Text(text = rememberRichText(note.text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Nutrition(values: List<NutritionValue>) {
    Column(Modifier.padding(top = Space.xxl)) {
        SectionHeader(stringResource(R.string.recipe_nutrition))
        SoftCard(Modifier.padding(top = Space.s)) {
            values.forEachIndexed { index, value ->
                if (index > 0) Hairline(Modifier.padding(horizontal = ScreenMargin))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = NutritionRowHeight)
                        .padding(horizontal = ScreenMargin),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = nutritionLabel(value.kind),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    val amount = value.value.trim()
                    Text(
                        text = if (amount.toDoubleOrNull() != null) "$amount ${value.kind.defaultUnit}" else amount,
                        style =
                            MaterialTheme.typography.bodyMedium
                                .copy(fontWeight = FontWeight.Medium)
                                .tabular(),
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

/** How the outline's lines measure: the categories over the title, the title, and the byline. */
private val CategoriesWidth = 120.dp
private val TitleHeight = 32.dp
private val BylineWidth = 140.dp

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
            PlaceholderLine(
                Modifier
                    .padding(top = Space.l)
                    .width(CategoriesWidth),
            )
            Placeholder(
                Modifier
                    .padding(top = Space.s)
                    .fillMaxWidth(0.8f)
                    .height(TitleHeight),
                shape = MaterialTheme.shapes.extraSmall,
            )
            PlaceholderLine(
                Modifier
                    .padding(top = Space.m)
                    .width(BylineWidth),
            )
            Row(Modifier.padding(top = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                repeat(2) {
                    Placeholder(
                        Modifier
                            .weight(1f)
                            .height(TouchTarget),
                        shape = CircleShape,
                    )
                }
            }
            // The description: two full lines and a short one, on the standfirst's line pitch.
            Column(Modifier.padding(top = Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                listOf(1f, 1f, 0.6f).forEach { width -> PlaceholderLine(Modifier.fillMaxWidth(width)) }
            }
        }
    }
}
