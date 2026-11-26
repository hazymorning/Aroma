package app.aroma.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hintText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.LocalTonalFill
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget

/**
 * Fields on the tonal fill where they stand, with no edge: the field with the cursor takes the
 * pressed fill, a step darker, and the cursor is sage.
 */
@Composable
fun aromaFieldColors(): TextFieldColors {
    val colors = AromaTheme.colors
    val fill = AromaTheme.tonalFill
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.surfacePressed,
        unfocusedContainerColor = fill,
        disabledContainerColor = fill,
        errorContainerColor = fill,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        errorBorderColor = Color.Transparent,
        cursorColor = colors.accent,
        errorCursorColor = colors.error,
        focusedTextColor = colors.ink,
        unfocusedTextColor = colors.ink,
        focusedPlaceholderColor = colors.inkSecondary,
        unfocusedPlaceholderColor = colors.inkSecondary,
        focusedTrailingIconColor = colors.inkSecondary,
        unfocusedTrailingIconColor = colors.inkSecondary,
        focusedSupportingTextColor = colors.inkSecondary,
        unfocusedSupportingTextColor = colors.inkSecondary,
    )
}

/**
 * The label above a field, level with the text inside it, as a group's label stands over its card:
 * never a notched label inside its border.
 */
@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AromaTheme.type.label,
        color = AromaTheme.colors.inkSecondary,
        // The field itself carries the label for TalkBack, so it is not read twice.
        modifier = modifier.padding(start = FieldInset, end = FieldInset, bottom = Space.s8).clearAndSetSemantics {},
    )
}

/** Help or error text under a field, on the same edge as the label above it. */
@Composable
private fun FieldNote(text: String, isError: Boolean) {
    Text(
        text = text,
        style = AromaTheme.type.bodySmall,
        color = if (isError) AromaTheme.colors.error else AromaTheme.colors.inkSecondary,
        modifier = Modifier.padding(start = FieldInset, end = FieldInset, top = Space.s8),
    )
}

/** Where a field's text starts, and its label and note above and below it: notes set under a field outside it stand there too. */
val FieldInset = Space.s16

/** The room around the text of a field for several lines: [FieldInset] at the sides, 12 dp above and below. */
private val FieldPadding = PaddingValues(horizontal = FieldInset, vertical = Space.s12)

/**
 * The least room above and below the text of a field of one line. The field is a button's 48 dp,
 * like the button beside it and the search field, with its line in the middle, and grows past that
 * only where the reader's text is so large that the line and this room no longer fit.
 */
private val SingleLineRoom = Space.s4

/** The room around a field's text: [FieldInset] at the sides, and above and below as a field of one line or several keeps it. */
private fun fieldPadding(singleLine: Boolean): PaddingValues =
    if (singleLine) PaddingValues(horizontal = FieldInset, vertical = SingleLineRoom) else FieldPadding

/**
 * How tall a field of one line stands: a button's 48 dp, or its line and the least room around it
 * where the reader's text is larger. The line is measured as the field sets it, since Android
 * scales large text less than its line height would say.
 */
@Composable
private fun singleLineFieldHeight(): Dp {
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val style = AromaTheme.type.body
    val density = LocalDensity.current
    val line =
        remember(measurer, style, density) {
            with(density) {
                measurer
                    .measure("H", style)
                    .size.height
                    .toDp()
            }
        }
    return maxOf(ButtonHeight, line + SingleLineRoom * 2)
}

/**
 * A field's button at its end: a 40 dp circle on the box's paper in a 48 dp target, in the middle of
 * the field's round end. Where the largest text makes the field taller than its 48 dp, the button
 * moves in from the end by half as much as the field grew, so its circle keeps as much air at the
 * end as above and below.
 */
@Composable
private fun FieldButton(icon: @Composable () -> Unit) {
    val grown = (singleLineFieldHeight() - TouchTarget) / 2
    Box(Modifier.padding(end = grown.coerceAtLeast(0.dp))) {
        CompositionLocalProvider(LocalTonalFill provides AromaTheme.colors.surfaceInBox) { icon() }
    }
}

/** Where a field for several lines starts. */
private val MultiLineFieldHeight = 112.dp

/**
 * A text field with its label above it. Single-line fields are as tall as a button; fields for
 * several lines start at [MultiLineFieldHeight]. [fieldModifier] reaches the field itself, for focus and autofill semantics.
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
                    .heightIn(min = if (singleLine) ButtonHeight else MultiLineFieldHeight)
                    .semantics { if (label != null) hintText = label },
            enabled = enabled,
            textStyle = AromaTheme.type.body.copy(color = AromaTheme.colors.ink),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            interactionSource = interaction,
            cursorBrush = SolidColor(AromaTheme.colors.accent),
            decorationBox = { inner ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = inner,
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interaction,
                    isError = isError,
                    placeholder = placeholder?.let { { Text(it, style = AromaTheme.type.body) } },
                    trailingIcon =
                        trailingIcon?.let { icon ->
                            {
                                if (singleLine) {
                                    FieldButton(icon)
                                } else {
                                    CompositionLocalProvider(LocalTonalFill provides AromaTheme.colors.surfaceInBox) { icon() }
                                }
                            }
                        },
                    colors = colors,
                    contentPadding = fieldPadding(singleLine),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled,
                            isError = isError,
                            interactionSource = interaction,
                            colors = colors,
                            shape = if (singleLine) AromaTheme.shapes.round else AromaTheme.shapes.box,
                            focusedBorderThickness = 0.dp,
                            unfocusedBorderThickness = 0.dp,
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
                    .heightIn(min = ButtonHeight)
                    .semantics { if (label != null) hintText = label },
            onKeyboardAction = onKeyboardAction,
            interactionSource = interaction,
            cursorBrush = SolidColor(AromaTheme.colors.accent),
            textStyle = AromaTheme.type.body.copy(color = AromaTheme.colors.ink),
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
                    trailingIcon = trailingIcon?.let { icon -> { FieldButton(icon) } },
                    isError = isError,
                    colors = colors,
                    contentPadding = fieldPadding(singleLine = true),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = isError,
                            interactionSource = interaction,
                            colors = colors,
                            shape = AromaTheme.shapes.round,
                            focusedBorderThickness = 0.dp,
                            unfocusedBorderThickness = 0.dp,
                        )
                    },
                ),
        )
        if (supportingText != null) FieldNote(supportingText, isError)
    }
}
