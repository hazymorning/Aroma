package app.aroma.widget

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import app.aroma.AppContainer
import app.aroma.AromaApplication
import app.aroma.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

internal val Context.appContainer: AppContainer get() = (applicationContext as AromaApplication).container

/**
 * What a widget first shows: its first state past [loading], read before the first frame so that
 * frame is complete. A first fetch that takes longer than [FIRST_FRAME] shows [loading] meanwhile.
 */
internal suspend fun <T> Flow<T>.firstShown(loading: T): T =
    withContext(Dispatchers.IO) { withTimeoutOrNull(FIRST_FRAME) { first { it != loading } } ?: loading }

private val FIRST_FRAME = 3.seconds

/** A widget's paper, with the system's corners, and the view Android opens the app from. */
@Composable
internal fun WidgetSurface(modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().appWidgetBackground().then(paper()), content = content)
}

@Composable
@ReadOnlyComposable
private fun paper(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceModifier.background(WidgetColors.page).cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        GlanceModifier.background(ImageProvider(R.drawable.widget_paper), colorFilter = ColorFilter.tint(WidgetColors.page))
    }

/** One of the app's line glyphs in [color]. */
@Composable
internal fun Glyph(
    @DrawableRes icon: Int,
    color: ColorProvider,
    size: Dp = GlyphSize,
) {
    Image(ImageProvider(icon), contentDescription = null, colorFilter = ColorFilter.tint(color), modifier = GlanceModifier.size(size))
}

/** A recipe photo, cropped to fill; its corners follow [modifier] from Android 12 on, which can clip them. */
@Composable
internal fun Photo(bitmap: Bitmap, modifier: GlanceModifier = GlanceModifier) {
    Image(ImageProvider(bitmap), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

/** A soft fill with rounded corners, which works before Android 12 too. */
internal fun GlanceModifier.fill(
    color: ColorProvider,
    @DrawableRes shape: Int = R.drawable.widget_card,
): GlanceModifier = background(ImageProvider(shape), colorFilter = ColorFilter.tint(color))

/** The one filled button a widget shows at most. */
@Composable
internal fun FilledButton(text: String, onClick: Action) {
    Box(GlanceModifier.height(TargetSize).clickable(onClick), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = WidgetType.label.copy(color = WidgetColors.onButton),
            maxLines = 1,
            modifier = GlanceModifier.fill(WidgetColors.button, R.drawable.widget_pill).padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** The round "+" in the corner of the shopping widget. */
@Composable
internal fun AddButton(description: String, onClick: Action) {
    Box(
        GlanceModifier
            .size(TargetSize)
            .clickable(onClick, rippleOverride = R.drawable.widget_ripple_circle)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(GlanceModifier.size(AddButtonSize).fill(WidgetColors.button, R.drawable.widget_circle), contentAlignment = Alignment.Center) {
            Glyph(R.drawable.ic_add, WidgetColors.onButton)
        }
    }
}

/** The app's shopping check: a fine ring, or a sage disc with its stroke. */
@Composable
internal fun Check(checked: Boolean) {
    if (checked) {
        Box(GlanceModifier.size(GlyphSize).fill(WidgetColors.accent, R.drawable.widget_circle), contentAlignment = Alignment.Center) {
            Glyph(R.drawable.widget_check_mark, WidgetColors.page)
        }
    } else {
        Glyph(R.drawable.widget_check_off, WidgetColors.ring)
    }
}

/**
 * What a widget says in place of its content: that nobody is signed in, that nothing is there yet
 * or that the server is out of reach. A widget one row high gets a single line, one cell only the
 * glyph; a larger one a glyph, a title, a line of help and at most one button.
 */
@Composable
internal fun WidgetMessage(
    @DrawableRes icon: Int,
    title: String,
    modifier: GlanceModifier = GlanceModifier,
    body: String? = null,
    button: (@Composable () -> Unit)? = null,
) {
    val size = LocalSize.current
    when {
        size.width < WidgetSizes.Narrow -> {
            Box(modifier.semantics { contentDescription = title }, contentAlignment = Alignment.Center) {
                Glyph(icon, WidgetColors.accent)
            }
        }

        size.height < WidgetSizes.Low -> {
            MessageLine(icon, title, modifier)
        }

        else -> {
            Column(
                modifier.padding(WidgetPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(icon, WidgetColors.accent)
                Spacer(GlanceModifier.height(8.dp))
                Text(title, style = WidgetType.title.copy(textAlign = TextAlign.Center), maxLines = 2)
                if (body != null) {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(body, style = WidgetType.caption.copy(textAlign = TextAlign.Center), maxLines = 3)
                }
                if (button != null) {
                    Spacer(GlanceModifier.height(4.dp))
                    button()
                }
            }
        }
    }
}

/** A glyph and a line of text, for a message where there is room for no more. */
@Composable
internal fun MessageLine(
    @DrawableRes icon: Int,
    text: String,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = WidgetPadding), verticalAlignment = Alignment.CenterVertically) {
        Glyph(icon, WidgetColors.accent)
        Spacer(GlanceModifier.width(WidgetPadding))
        Text(text, style = WidgetType.body, maxLines = 2)
    }
}

/** The outline of content still on its way, as the app's screens show it. */
@Composable
internal fun LoadingOutline() {
    Column(GlanceModifier.fillMaxSize().padding(WidgetPadding), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.height(OutlineLine).width(72.dp).fill(WidgetColors.card, R.drawable.widget_pill)) {}
        Spacer(GlanceModifier.height(8.dp))
        Box(GlanceModifier.height(OutlineLine).fillMaxWidth().fill(WidgetColors.card, R.drawable.widget_pill)) {}
    }
}

/**
 * Where the widgets change their layout, set by what fits rather than by cells, which launchers size
 * differently: a cell is about 73 dp wide, and about 118 dp high in portrait but half that in landscape.
 */
internal object WidgetSizes {
    /** The least a widget can be: one cell in landscape. */
    val Smallest = 40.dp

    /** Narrower is a single cell, with room for a glyph and a number only. */
    val Narrow = 110.dp

    /** From here a photo fits beside a list. */
    val Wide = 240.dp

    /** Lower is a row in landscape, with room for a line or two of words only. */
    val Low = 100.dp

    /** From here a header fits over a week of meals: three rows in portrait. */
    val Tall = 300.dp
}

private val GlyphSize = 24.dp
private val AddButtonSize = 40.dp
private val OutlineLine = 12.dp
