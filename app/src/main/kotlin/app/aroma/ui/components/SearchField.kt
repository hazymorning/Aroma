package app.aroma.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.ui.theme.Lines
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.extras

/**
 * The stadium field for search and quick entry: transparent with a hairline that turns sage while
 * focused, at the same width.
 */
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
    trailing: @Composable (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else extras.hairlineStrong,
        tween(Motion.STATE),
        label = "fieldBorder",
    )
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(Lines.control, border),
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 0.dp)) {
            Icon(
                painterResource(leadingIcon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    state = rememberSyncedTextFieldState(value, onValueChange),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = imeAction),
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
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                else -> {
                    Spacer(Modifier.width(16.dp))
                }
            }
        }
    }
}
