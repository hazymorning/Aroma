package app.aroma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.LocalTonalFill
import app.aroma.ui.theme.Space

/** The height of the search field and of a quick entry like it. */
private val SearchFieldHeight: Dp = 48.dp

/**
 * The search field, and quick entry like it: a 48 dp pill on the tonal fill where it stands, a
 * 20 dp glyph in second ink and a placeholder. While there is text, a clear button takes the end.
 */
@Composable
fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int = R.drawable.ic_search,
    imeAction: ImeAction = ImeAction.Search,
    onSubmit: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = AromaTheme.colors
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(SearchFieldHeight)
                .clip(AromaTheme.shapes.round)
                .background(AromaTheme.tonalFill)
                .padding(start = Space.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(leadingIcon), contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(Glyph.control))
        Spacer(Modifier.width(Space.s12))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = AromaTheme.type.body, color = colors.inkSecondary, maxLines = 1)
            BasicTextField(
                state = rememberSyncedTextFieldState(value, onValueChange),
                lineLimits = TextFieldLineLimits.SingleLine,
                textStyle = AromaTheme.type.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = imeAction),
                interactionSource = interaction,
                onKeyboardAction = {
                    onSubmit?.invoke()
                    if (imeAction == ImeAction.Search) focusManager.clearFocus()
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
            )
        }
        // Inside the field, a button stands on the box's paper, a step off the field's fill.
        CompositionLocalProvider(LocalTonalFill provides colors.surfaceInBox) {
            when {
                trailing != null -> {
                    trailing()
                }

                value.isNotEmpty() -> {
                    IconButton(
                        R.drawable.ic_close,
                        stringResource(R.string.action_clear),
                        onClick = { onValueChange("") },
                    )
                }

                else -> {
                    Spacer(Modifier.width(Space.s16))
                }
            }
        }
    }
}
