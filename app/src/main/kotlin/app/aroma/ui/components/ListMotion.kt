package app.aroma.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier
import app.aroma.ui.theme.Motion

/**
 * How the rows of a lazy list come, go and make room; the rows need stable keys. A new row fades in
 * where it lands while the rows after it glide aside. A row that leaves holds still while the rows
 * after it glide over it, and fades only once it is nearly covered, so a card made of several rows
 * closes without a gap.
 */
fun Modifier.animateRow(scope: LazyItemScope): Modifier =
    with(scope) {
        animateItem(
            fadeInSpec = tween(Motion.STATE, easing = Motion.Decelerate),
            placementSpec = tween(Motion.STATE, easing = Motion.Decelerate),
            fadeOutSpec = tween(Motion.EDGE, delayMillis = Motion.EDGE),
        )
    }
