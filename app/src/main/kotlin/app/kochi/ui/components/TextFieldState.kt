package app.kochi.ui.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

/**
 * A [TextFieldState] that mirrors a value owned by a ViewModel: typing reports changes
 * through [onValueChange], and outside changes (such as clearing) are written back.
 */
@Composable
fun rememberSyncedTextFieldState(value: String, onValueChange: (String) -> Unit): TextFieldState {
    val state = rememberTextFieldState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestValue by rememberUpdatedState(value)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text -> if (text != latestValue) latestOnChange(text) }
    }
    LaunchedEffect(value) {
        if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
    }
    return state
}
