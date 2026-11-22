package app.kochi.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth

/**
 * Lets a horizontally scrolling row reach the screen edges while its parent keeps a
 * [margin] on both sides, so chips scroll out of view at the edge instead of mid-screen.
 */
fun Modifier.fullBleed(margin: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = (margin * 2).roundToPx()
        val wider = constraints.copy(maxWidth = constraints.maxWidth + extra, minWidth = constraints.minWidth + extra)
        val placeable = measurable.measure(wider)
        layout(constraints.constrainWidth(placeable.width - extra), placeable.height) {
            placeable.place(-extra / 2, 0)
        }
    }
