package app.aroma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.TouchTarget

/** The first letter of [name] as one character, in the reader's capitals, or null while there is no name. */
@Composable
private fun initialOf(name: String?): String? =
    name?.trim()?.takeIf { it.isNotEmpty() }?.let { it.substring(0, it.offsetByCodePoints(0, 1)).uppercase(currentLocale()) }

/**
 * A person as Mealie knows them: the first letter of their [name] on a circle of the tonal fill
 * where it stands, set in [style], or a person glyph while there is no name.
 */
@Composable
fun Avatar(name: String?, size: Dp, modifier: Modifier = Modifier, style: TextStyle = AromaTheme.type.initial) {
    val initial = initialOf(name)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(AromaTheme.tonalFill),
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            Text(text = initial, style = style, color = AromaTheme.colors.ink)
        } else {
            Icon(
                painterResource(R.drawable.ic_person),
                contentDescription = null,
                tint = AromaTheme.colors.inkSecondary,
                modifier = Modifier.size(Glyph.control),
            )
        }
    }
}

/** The profile as an icon button: the user's initial in the label role on a 40 dp circle, in a 48 dp target. */
@Composable
fun ProfileButton(name: String?, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .size(TouchTarget)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Avatar(
            name,
            ProfileCircle,
            Modifier
                .clip(CircleShape)
                .indication(interaction, PressTint),
            style = AromaTheme.type.label,
        )
    }
}

private val ProfileCircle = IconCircle

/** The circle of the profile box in the settings. */
val ProfileAvatarSize: Dp = 56.dp
