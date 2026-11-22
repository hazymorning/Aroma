package app.kochi.feature.recipe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kochi.R
import app.kochi.core.format.IngredientFormatter
import app.kochi.core.format.Quantities
import app.kochi.core.model.ImageSize
import app.kochi.core.model.NutritionKind
import app.kochi.core.model.Organizer
import app.kochi.core.model.OrganizerKind
import app.kochi.core.model.Recipe
import app.kochi.core.model.Step
import app.kochi.data.KochiError
import app.kochi.ui.components.ErrorState
import app.kochi.ui.components.IngredientList
import app.kochi.ui.components.IngredientRow
import app.kochi.ui.components.OfflineNotice
import app.kochi.ui.components.Placeholder
import app.kochi.ui.components.PlaceholderLines
import app.kochi.ui.components.RatingStars
import app.kochi.ui.components.RecipeImage
import app.kochi.ui.components.Stepper
import app.kochi.ui.components.TagChip
import app.kochi.ui.components.currentLocale
import app.kochi.ui.components.recipeTimeText
import app.kochi.ui.components.rememberRichText
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
    val error: KochiError? = null,
)

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

@Composable
fun RecipeScreen(state: RecipeUiState, actions: RecipeActions, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val recipe = state.recipe
    Box(modifier.fillMaxSize()) {
        when {
            recipe != null -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 120.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "hero") { Hero(recipe) }
                    recipeBody(recipe, state, actions)
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
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(top = 64.dp),
                )
            }
        }
        RecipeTopBar(
            title = recipe?.name.orEmpty(),
            collapsed = collapsed || recipe == null,
            isFavorite = state.isFavorite,
            hasRecipe = recipe != null,
            sourceUrl = recipe?.sourceUrl,
            actions = actions,
        )
        if (recipe != null && recipe.steps.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = actions.onStartCooking,
                icon = { Icon(painterResource(R.drawable.ic_cooking), contentDescription = null) },
                text = { Text(stringResource(R.string.recipe_start_cooking)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun Hero(recipe: Recipe) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f),
    ) {
        RecipeImage(
            image = recipe.image,
            size = ImageSize.Original,
            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
            fallbackIconSize = 48.dp,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))),
        )
    }
}

@Composable
private fun RecipeTopBar(
    title: String,
    collapsed: Boolean,
    isFavorite: Boolean,
    hasRecipe: Boolean,
    sourceUrl: String?,
    actions: RecipeActions,
) {
    val barColor by animateColorAsState(
        if (collapsed) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
        label = "barColor",
    )
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(barColor)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), collapsed, actions.onBack)
        AnimatedVisibility(collapsed, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (!collapsed) Spacer(Modifier.weight(1f))
        if (hasRecipe) {
            BarButton(
                icon = if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite,
                description = stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite),
                plain = collapsed,
                onClick = actions.onToggleFavorite,
            )
            Spacer(Modifier.width(8.dp))
            Box {
                BarButton(R.drawable.ic_more_vert, stringResource(R.string.action_more), collapsed) { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuItem(R.string.recipe_add_to_plan, R.drawable.ic_calendar_add_on) {
                        menuOpen = false
                        actions.onAddToMealPlan()
                    }
                    MenuItem(R.string.action_edit, R.drawable.ic_edit) {
                        menuOpen = false
                        actions.onEdit()
                    }
                    MenuItem(R.string.action_share, R.drawable.ic_share) {
                        menuOpen = false
                        actions.onShare()
                    }
                    if (sourceUrl != null) {
                        MenuItem(R.string.recipe_open_source, R.drawable.ic_open_in_new) {
                            menuOpen = false
                            actions.onOpenUrl(sourceUrl)
                        }
                    }
                    MenuItem(R.string.action_delete, R.drawable.ic_delete) {
                        menuOpen = false
                        confirmDelete = true
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.recipe_delete_title)) },
            text = { Text(stringResource(R.string.recipe_delete_message, title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun MenuItem(label: Int, icon: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun BarButton(icon: Int, description: String, plain: Boolean, onClick: () -> Unit) {
    if (plain) {
        IconButton(onClick = onClick) { Icon(painterResource(icon), contentDescription = description) }
    } else {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.recipeBody(recipe: Recipe, state: RecipeUiState, actions: RecipeActions) {
    item(key = "title") {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
            if (state.isOffline) OfflineNotice(Modifier.padding(bottom = 12.dp))
            Text(recipe.name, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RatingStars(rating = state.userRating ?: recipe.rating, starSize = 18, onRate = actions.onRate)
                recipe.sourceUrl?.let { url ->
                    val host = remember(url) { runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() }
                    if (host != null) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { actions.onOpenUrl(url) }) {
                            Text(host, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    if (recipe.description.isNotBlank()) {
        item(key = "description") {
            Text(
                text = rememberRichText(recipe.description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            )
        }
    }
    item(key = "facts") { Facts(recipe) }
    val organizers = recipe.categories.map { OrganizerKind.Category to it } + recipe.tags.map { OrganizerKind.Tag to it }
    if (organizers.isNotEmpty()) {
        item(key = "organizers") {
            FlowRow(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                organizers.forEach { (kind, organizer) -> TagChip(organizer.name, onClick = { actions.onOpenOrganizer(kind, organizer) }) }
            }
        }
    }
    if (recipe.ingredients.isNotEmpty()) {
        item(key = "ingredients") { Ingredients(recipe, state, actions) }
    }
    if (recipe.steps.isNotEmpty()) {
        item(key = "stepsHeader") { SectionTitle(stringResource(R.string.recipe_steps), Modifier.padding(top = 36.dp, bottom = 4.dp)) }
        itemsIndexed(recipe.steps, key = { index, step -> step.id ?: "step$index" }) { index, step ->
            StepItem(index, step)
        }
    }
    if (recipe.notes.isNotEmpty()) {
        item(key = "notes") { Notes(recipe) }
    }
    if (recipe.nutrition.isNotEmpty()) {
        item(key = "nutrition") { Nutrition(recipe) }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        modifier =
            modifier
                .padding(horizontal = 20.dp)
                .semantics { heading() },
    )
}

@Composable
private fun Facts(recipe: Recipe) {
    val facts =
        listOfNotNull(
            recipeTimeText(recipe.prepTime)?.let { it to stringResource(R.string.recipe_prep_time) },
            recipeTimeText(recipe.cookTime)?.let { it to stringResource(R.string.recipe_cook_time) },
            recipeTimeText(recipe.totalTime)?.let { it to stringResource(R.string.recipe_total_time) },
        )
    if (facts.isEmpty()) return
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp),
    ) {
        Row(Modifier.padding(vertical = 16.dp)) {
            facts.forEach { (value, label) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Column(Modifier.padding(top = 36.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recipe_ingredients),
                style = MaterialTheme.typography.headlineMedium,
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
        IngredientList(
            rows = rows,
            checked = state.checkedIngredients,
            onToggle = actions.onToggleIngredient,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
        FilledTonalButton(
            onClick = actions.onAddToShoppingList,
            modifier =
                Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp)
                    .fillMaxWidth()
                    .height(52.dp),
        ) {
            Icon(painterResource(R.drawable.ic_add_shopping_cart), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.recipe_add_to_list))
        }
        if (recipe.tools.isNotEmpty()) {
            Text(
                text = stringResource(R.string.recipe_tools, recipe.tools.joinToString(", ") { it.name }),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
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

@Composable
private fun StepItem(index: Int, step: Step) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(36.dp),
        )
        Column(Modifier.weight(1f)) {
            if (!step.title.isNullOrBlank()) {
                Text(step.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp, top = 6.dp))
            }
            Text(
                text = rememberRichText(step.text),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = if (step.title.isNullOrBlank()) 6.dp else 0.dp),
            )
        }
    }
}

@Composable
private fun Notes(recipe: Recipe) {
    Column(Modifier.padding(top = 36.dp)) {
        SectionTitle(stringResource(R.string.recipe_notes), Modifier.padding(bottom = 12.dp))
        recipe.notes.forEach { note ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
            ) {
                Column(Modifier.padding(18.dp)) {
                    if (note.title.isNotBlank()) {
                        Text(note.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = rememberRichText(note.text, linkColor = MaterialTheme.colorScheme.onTertiaryContainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun Nutrition(recipe: Recipe) {
    Column(Modifier.padding(top = 36.dp)) {
        SectionTitle(stringResource(R.string.recipe_nutrition), Modifier.padding(bottom = 12.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                recipe.nutrition.forEach { value ->
                    Row(Modifier.padding(vertical = 8.dp)) {
                        Text(nutritionLabel(value.kind), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        val numeric = value.value.trim().toDoubleOrNull() != null
                        Text(
                            text = if (numeric) "${value.value.trim()} ${value.kind.defaultUnit}" else value.value.trim(),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
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

@Composable
private fun RecipeLoading() {
    Column {
        Placeholder(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f),
            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
        )
        Placeholder(
            Modifier
                .padding(start = 20.dp, top = 24.dp)
                .width(260.dp)
                .height(32.dp),
        )
        PlaceholderLines(4, Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp))
    }
}
