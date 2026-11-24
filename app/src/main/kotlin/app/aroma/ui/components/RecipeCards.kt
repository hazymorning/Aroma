package app.aroma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.ImageSize
import app.aroma.core.model.RecipeImage
import app.aroma.core.model.RecipeSummary
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.ThumbnailSize

/** A recipe card's photo is a little taller than wide, as a page of a cookbook is. */
private const val CARD_PHOTO = 4f / 5f

/** A card's name keeps room for this many lines, and runs to at most [MAX_TITLE_LINES]. */
const val MIN_TITLE_LINES = 2
private const val MAX_TITLE_LINES = 3

/**
 * A recipe as a card: its photo, 4:5 with 14 dp corners, its name under it and a meta line, all
 * flush with the photo's edge. A saved recipe wears the bookmark ribbon on its photo. The name keeps
 * room for [titleLines] lines, so the meta lines of cards side by side stand on one line (see
 * [rememberTitleLines]). Pressing shrinks the card a little and tints its photo.
 */
@Composable
fun RecipeCard(
    recipe: RecipeSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    saved: Boolean = false,
    titleLines: Int = MIN_TITLE_LINES,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier =
            modifier
                .pressScale(interaction)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onLongClickLabel = onLongClick?.let { stringResource(R.string.action_more) },
                    onLongClick = onLongClick,
                    onClick = onClick,
                ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_PHOTO),
        ) {
            RecipeImage(
                image = recipe.image,
                size = ImageSize.Medium,
                shape = AromaTheme.shapes.cardImage,
                modifier =
                    Modifier
                        .matchParentSize()
                        .indication(interaction, PressTint),
            )
            Ribbon(saved, Modifier.align(Alignment.TopEnd).padding(end = Space.s12))
        }
        Spacer(Modifier.height(Space.s8))
        Text(
            text = recipe.name,
            style = AromaTheme.type.cardTitle,
            minLines = titleLines.coerceIn(MIN_TITLE_LINES, MAX_TITLE_LINES),
            maxLines = MAX_TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        RecipeMetaLine(recipe, Modifier.padding(top = Space.s4))
    }
}

/**
 * How many lines the longest of [names] takes on a card [width] wide, at least two and at most
 * three: cards side by side keep this many for their names, so their meta lines stand on one line.
 */
@Composable
fun rememberTitleLines(names: List<String>, width: Dp): Int {
    val measurer = rememberTextMeasurer()
    val style = AromaTheme.type.cardTitle
    val widthPx = with(LocalDensity.current) { width.roundToPx() }
    return remember(names, widthPx, style) {
        if (widthPx <= 0) return@remember MIN_TITLE_LINES
        val widest = Constraints(maxWidth = widthPx)
        names
            .maxOfOrNull { name ->
                measurer.measure(name.breakable(), style, maxLines = MAX_TITLE_LINES, constraints = widest).lineCount
            }?.coerceIn(MIN_TITLE_LINES, MAX_TITLE_LINES) ?: MIN_TITLE_LINES
    }
}

/**
 * A recipe as a row: a 56 dp thumbnail with 12 dp corners, the name beside it in at most two lines
 * and a meta line under it. Rows stand 12 dp apart. A [dimmed] row is past: its words in tertiary
 * ink and its photo faded.
 */
@Composable
fun RecipeRow(
    title: String,
    image: RecipeImage?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    meta: List<String> = emptyList(),
    @DrawableRes metaIcon: Int? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    dimmed: Boolean = false,
    @DrawableRes fallbackIcon: Int = R.drawable.ic_plate,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = AromaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val tap =
        if (onClick != null) {
            Modifier.combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onLongClickLabel = onLongClickLabel ?: onLongClick?.let { stringResource(R.string.action_more) },
                onLongClick = onLongClick,
                onClick = onClick,
            )
        } else {
            Modifier
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ThumbnailSize)
                .then(tap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s16),
    ) {
        RecipeImage(
            image = image,
            size = ImageSize.Thumbnail,
            shape = AromaTheme.shapes.thumbnail,
            fallbackIcon = fallbackIcon,
            fallbackIconSize = FallbackGlyph,
            modifier =
                Modifier
                    .size(ThumbnailSize)
                    .alpha(if (dimmed) DIMMED_PHOTO else 1f)
                    .indication(interaction, PressTint),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AromaTheme.type.cardTitle,
                color = if (dimmed) colors.inkTertiary else colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // A row's meta line may take a second line with large text, so nothing of it is lost.
            MetaLine(
                items = meta,
                icon = metaIcon,
                color = if (dimmed) colors.inkTertiary else colors.inkSecondary,
                maxLines = ROW_META_LINES,
                modifier = Modifier.padding(top = Space.s4),
            )
        }
        trailing?.invoke(this)
    }
}

/** A recipe of the library as a [RecipeRow], with its time or its category under its name. */
@Composable
fun RecipeRow(
    recipe: RecipeSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val time = recipeTime(recipe)
    RecipeRow(
        title = recipe.name,
        image = recipe.image,
        onClick = onClick,
        meta = listOfNotNull(time ?: recipe.categories.firstOrNull()?.name),
        metaIcon = if (time != null) R.drawable.ic_clock else null,
        onLongClick = onLongClick,
        modifier = modifier,
        trailing = trailing,
    )
}

/** How long a recipe takes, "25 Min.", or else what kind of dish it is; left out where there is neither. */
@Composable
fun RecipeMetaLine(recipe: RecipeSummary, modifier: Modifier = Modifier) {
    val time = recipeTime(recipe)
    MetaLine(
        items = listOfNotNull(time ?: recipe.categories.firstOrNull()?.name),
        icon = if (time != null) R.drawable.ic_clock else null,
        modifier = modifier,
    )
}

/** A recipe's time as cards and rows show it: the total, or prep and cook added up, or either alone. */
@Composable
fun recipeTime(recipe: RecipeSummary): String? {
    val resources = LocalResources.current
    return remember(recipe, resources) {
        resources.recipeTimeText(totalTime(recipe.totalTime, recipe.prepTime, recipe.cookTime))
            ?: resources.recipeTimeText(recipe.cookTime)
            ?: resources.recipeTimeText(recipe.prepTime)
    }
}

private val FallbackGlyph = 20.dp

/** How much of a past day's photos still shows. */
private const val DIMMED_PHOTO = 0.6f

/** How many lines a row's meta line may take. */
private const val ROW_META_LINES = 2
