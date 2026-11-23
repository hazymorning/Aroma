package app.aroma.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hintText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.aroma.ui.theme.extras

/** Fields have 12 dp corners: rounder than the page, squarer than a button. */
val FieldShape = RoundedCornerShape(12.dp)

/** Transparent fields with a hairline that turns sage on focus, at the same width. */
@Composable
fun aromaFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = extras.hairlineStrong,
        disabledBorderColor = scheme.outlineVariant,
        errorBorderColor = scheme.error,
        cursorColor = scheme.primary,
        focusedPlaceholderColor = scheme.onSurfaceVariant,
        unfocusedPlaceholderColor = scheme.onSurfaceVariant,
        focusedTrailingIconColor = scheme.onSurfaceVariant,
        unfocusedTrailingIconColor = scheme.onSurfaceVariant,
        focusedSupportingTextColor = scheme.onSurfaceVariant,
        unfocusedSupportingTextColor = scheme.onSurfaceVariant,
    )
}

/** The label above a field: never a notched label inside its border. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // The field itself carries the label for TalkBack, so it is not read twice.
        modifier = modifier.padding(bottom = 8.dp).clearAndSetSemantics {},
    )
}

/** Help or error text under a field, on the same edge as the label above it. */
@Composable
private fun FieldNote(text: String, isError: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * A text field with its label above it. Single-line fields are 52 dp tall, multi-line ones start
 * at 112 dp. [fieldModifier] reaches the field itself, for focus and autofill semantics.
 */
@Composable
fun LabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = aromaFieldColors()
    Column(modifier) {
        if (label != null) FieldLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                fieldModifier
                    .fillMaxWidth()
                    .heightIn(min = if (singleLine) 52.dp else 112.dp)
                    .semantics { if (label != null) hintText = label },
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = inner,
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    interactionSource = interaction,
                    isError = isError,
                    placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
                    trailingIcon = trailingIcon,
                    colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled,
                            isError = isError,
                            interactionSource = interaction,
                            colors = colors,
                            shape = FieldShape,
                            focusedBorderThickness = 1.dp,
                            unfocusedBorderThickness = 1.dp,
                        )
                    },
                )
            },
        )
        if (supportingText != null) FieldNote(supportingText, isError)
    }
}

/** [LabeledTextField] for passwords and tokens. */
@Composable
fun LabeledSecureTextField(
    state: TextFieldState,
    label: String?,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    textObfuscationMode: TextObfuscationMode = TextObfuscationMode.RevealLastTyped,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = aromaFieldColors()
    Column(modifier) {
        if (label != null) FieldLabel(label)
        BasicSecureTextField(
            state = state,
            modifier =
                fieldModifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .semantics { if (label != null) hintText = label },
            onKeyboardAction = onKeyboardAction,
            interactionSource = interaction,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            textObfuscationMode = textObfuscationMode,
            keyboardOptions = keyboardOptions,
            decorator =
                OutlinedTextFieldDefaults.decorator(
                    state = state,
                    enabled = true,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    outputTransformation = null,
                    interactionSource = interaction,
                    labelPosition = TextFieldLabelPosition.Attached(),
                    trailingIcon = trailingIcon,
                    isError = isError,
                    colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = isError,
                            interactionSource = interaction,
                            colors = colors,
                            shape = FieldShape,
                            focusedBorderThickness = 1.dp,
                            unfocusedBorderThickness = 1.dp,
                        )
                    },
                ),
        )
        if (supportingText != null) FieldNote(supportingText, isError)
    }
}
