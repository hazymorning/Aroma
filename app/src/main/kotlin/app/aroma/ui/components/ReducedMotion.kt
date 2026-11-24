package app.aroma.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Whether the system asks for no motion: its animations turned off, as "Remove animations" does. */
@Composable
fun reducedMotion(): Boolean = systemAnimationScale() == 0f

/**
 * How fast the system runs animations, 0 with them turned off. Compose's own animations follow it by
 * themselves; Coil times its crossfade on its own clock, so the photo is told.
 */
@Composable
internal fun systemAnimationScale(): Float {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
}
