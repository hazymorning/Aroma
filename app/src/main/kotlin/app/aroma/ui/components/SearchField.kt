package app.aroma.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.ControlHeight
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.extras

/** The stadium field for search and quick entry, on the soft fill every control shares. */
@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: Int = R.drawable.ic_search,
    imeAction: ImeAction = ImeAction.Search,
    onSubmit: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = CircleShape,
        color = extras.card,
        modifier =
            modifier
                .fillMaxWidth()
                .height(ControlHeight),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Space.m)) {
            Icon(
                painterResource(leadingIcon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Glyph.m),
            )
            Spacer(Modifier.width(Space.s))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = AromaType.Body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    state = rememberSyncedTextFieldState(value, onValueChange),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = AromaType.Body.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
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
            when {
                trailing != null -> {
                    trailing()
                }

                value.isNotEmpty() -> {
                    IconAction(R.drawable.ic_close, stringResource(R.string.action_clear), onClick = { onValueChange("") })
                }

                else -> {
                    Spacer(Modifier.width(Space.m))
                }
            }
        }
    }
}

/**
 * A search field that opens search elsewhere, as Home's opens it on the Recipes tab: it looks like
 * [PillTextField] and reads as a button.
 */
@Composable
fun SearchPill(placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = extras.card,
        modifier =
            modifier
                .fillMaxWidth()
                .height(ControlHeight),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Space.m)) {
            Icon(
                painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Glyph.m),
            )
            Spacer(Modifier.width(Space.s))
            Text(placeholder, style = AromaType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
