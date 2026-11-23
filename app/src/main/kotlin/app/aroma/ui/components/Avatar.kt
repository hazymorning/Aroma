package app.aroma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.extras
import coil3.compose.AsyncImage

/**
 * A person as Mealie knows them: their photo, or the first letter of their name on the soft fill
 * while there is no photo. The letter grows with the circle, not with the text size.
 */
@Composable
fun Avatar(name: String, photo: String?, size: Dp, modifier: Modifier = Modifier) {
    val letterSize = with(LocalDensity.current) { (size * 0.45f).toSp() }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(extras.selected),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(currentLocale()),
            style = AromaType.SectionTitle.copy(fontSize = letterSize, lineHeight = letterSize * 1.3f),
            color = extras.onSelected,
        )
        if (photo != null) {
            AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}
