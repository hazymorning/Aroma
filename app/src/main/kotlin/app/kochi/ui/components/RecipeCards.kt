package app.kochi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kochi.R
import app.kochi.core.model.ImageSize
import app.kochi.core.model.RecipeSummary
import app.kochi.ui.theme.CardTitle

/** Portrait card for carousels and the recipe grid: photo first, title in the serif face. */
@Composable
fun RecipeCard(recipe: RecipeSummary, onClick: () -> Unit, modifier: Modifier = Modifier, imageAspectRatio: Float = 0.82f) {
    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.large)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {},
    ) {
        RecipeImage(
            image = recipe.image,
            size = ImageSize.Medium,
            shape = MaterialTheme.shapes.large,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspectRatio),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = recipe.name,
            style = CardTitle,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        RecipeMeta(recipe, Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp))
    }
}

/** Compact row used in search results and pickers. */
@Composable
fun RecipeListItem(recipe: RecipeSummary, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RecipeImage(
            image = recipe.image,
            size = ImageSize.Thumbnail,
            shape = MaterialTheme.shapes.medium,
            fallbackIconSize = 22.dp,
            modifier = Modifier.size(64.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            RecipeMeta(recipe, Modifier.padding(top = 2.dp))
        }
        trailing?.invoke()
    }
}

@Composable
fun RecipeMeta(recipe: RecipeSummary, modifier: Modifier = Modifier) {
    val time = recipeTimeText(recipe.totalTime) ?: recipeTimeText(recipe.cookTime) ?: recipeTimeText(recipe.prepTime)
    val rating = recipe.rating?.takeIf { it > 0 }
    if (time == null && rating == null) {
        Spacer(modifier.height(18.dp))
        return
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (time != null) {
            MetaLabel(R.drawable.ic_schedule, time)
        }
        if (rating != null) {
            MetaLabel(
                R.drawable.ic_star_filled,
                String.format(currentLocale(), "%.1f", rating),
                iconTint = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun MetaLabel(icon: Int, text: String, iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
