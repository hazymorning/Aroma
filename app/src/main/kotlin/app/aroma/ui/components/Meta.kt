package app.aroma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Space

/**
 * A quiet line of facts: a 16 dp glyph, then the facts in meta, parted by " · ". Left out where
 * there is nothing to say, rather than shown empty.
 */
@Composable
fun MetaLine(
    items: List<String>,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    color: Color = AromaTheme.colors.inkSecondary,
    maxLines: Int = 1,
) {
    val text = items.filter { it.isNotBlank() }.joinToString(SEPARATOR)
    if (text.isEmpty()) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(Glyph.meta))
            Spacer(Modifier.width(Space.s4))
        }
        Text(text, style = AromaTheme.type.meta, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * What parts the facts of a meta line, and the parts of a subtitle: a middle dot with a no-break
 * space before it, so a line may end after the dot but never start with one.
 */
const val SEPARATOR = "\u00A0· "
