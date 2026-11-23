package app.aroma.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aroma.R
import app.aroma.core.model.ImageSize
import app.aroma.core.model.RecipeSummary
import app.aroma.ui.theme.CardTitle
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.tabular

/**
 * A recipe as a photo with its title set underneath, flush with the photo's edge. Pressing dims the
 * photo a little instead of drawing a ripple box around the whole card.
 */
@Composable
fun RecipeCard(recipe: RecipeSummary, onClick: () -> Unit, modifier: Modifier = Modifier, imageAspectRatio: Float = 1f) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val photoAlpha by animateFloatAsState(if (pressed) 0.88f else 1f, tween(if (pressed) Motion.PRESS else Motion.STATE), label = "press")
    Column(
        modifier =
            modifier
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {},
    ) {
        RecipeImage(
            image = recipe.image,
            size = ImageSize.Medium,
            shape = MaterialTheme.shapes.medium,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspectRatio)
                    .graphicsLayer { alpha = photoAlpha },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = recipe.name,
            style = CardTitle,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        RecipeMeta(recipe, Modifier.padding(top = 4.dp))
    }
}

/** A row with a small photo, for search results and pickers; the text starts on the 96 dp keyline. */
@Composable
fun RecipeListItem(recipe: RecipeSummary, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = ScreenMargin, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RecipeImage(
            image = recipe.image,
            size = ImageSize.Thumbnail,
            shape = MaterialTheme.shapes.small,
            fallbackIconSize = 20.dp,
            modifier = Modifier.size(56.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(text = recipe.name, style = CardTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            RecipeMeta(recipe, Modifier.padding(top = 2.dp))
        }
        trailing?.invoke()
    }
}

/** Time and rating as one quiet line, "25 Min. · ★ 4,5"; keeps its height when there is nothing to say. */
@Composable
fun RecipeMeta(recipe: RecipeSummary, modifier: Modifier = Modifier) {
    val time = recipeTimeText(recipe.totalTime) ?: recipeTimeText(recipe.cookTime) ?: recipeTimeText(recipe.prepTime)
    val rating = recipe.rating?.takeIf { it > 0 }
    val style = MaterialTheme.typography.bodySmall.tabular()
    if (time == null && rating == null) {
        Spacer(modifier.height(with(androidx.compose.ui.platform.LocalDensity.current) { style.lineHeight.toDp() }))
        return
    }
    val locale = currentLocale()
    val text =
        buildAnnotatedString {
            if (time != null) append(time)
            if (time != null && rating != null) append("  ·  ")
            if (rating != null) {
                appendInlineContent(STAR, "★")
                append(" ")
                append(String.format(locale, "%.1f", rating))
            }
        }
    val star =
        mapOf(
            STAR to
                InlineTextContent(Placeholder(12.sp, 12.sp, PlaceholderVerticalAlign.TextCenter)) {
                    Icon(
                        painterResource(R.drawable.ic_star_filled),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
        )
    Text(text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, inlineContent = star, maxLines = 1, modifier = modifier)
}

private const val STAR = "star"
