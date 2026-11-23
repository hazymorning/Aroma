package app.aroma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import app.aroma.R
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.extras

/**
 * A person as Mealie knows them: the first letter of their [name] in ink on a soft circle, or a
 * person glyph while there is no name. The letter grows with the circle, not with the text size. On
 * a card, which has the soft fill itself, [onCard] sets the circle on paper.
 */
@Composable
fun Avatar(name: String?, size: Dp, modifier: Modifier = Modifier, onCard: Boolean = false) {
    val initial = name?.trim()?.takeIf { it.isNotEmpty() }?.let { it.substring(0, it.offsetByCodePoints(0, 1)) }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (onCard) extras.raised else extras.card),
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            val letterSize = with(LocalDensity.current) { (size * LETTER).toSp() }
            Text(
                text = initial.uppercase(currentLocale()),
                style = AromaType.SectionTitle.copy(fontSize = letterSize, lineHeight = letterSize * 1.3f),
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_person),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * GLYPH),
            )
        }
    }
}

/** How large the letter and the glyph stand in their circle. */
private const val LETTER = 0.45f
private const val GLYPH = 0.5f
