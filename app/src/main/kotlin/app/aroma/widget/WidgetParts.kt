package app.aroma.widget

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
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

/**
 * A widget's paper, or [color] in its place, with the system's corners, and the view Android opens
 * the app from. From Android 12 the corners also clip what it holds, such as a photo filling it.
 */
@Composable
internal fun WidgetSurface(
    modifier: GlanceModifier = GlanceModifier,
    color: ColorProvider = WidgetColors.background,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize().appWidgetBackground().then(paper(color)), content = content)
}

private fun paper(color: ColorProvider): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceModifier.background(color).cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        GlanceModifier.background(ImageProvider(R.drawable.widget_paper), colorFilter = ColorFilter.tint(color))
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

/** A soft fill with the app's card corners, which works before Android 12 too. */
internal fun GlanceModifier.fill(
    color: ColorProvider,
    @DrawableRes shape: Int = R.drawable.widget_card,
): GlanceModifier = background(ImageProvider(shape), colorFilter = ColorFilter.tint(color))

/** The one filled button a widget shows at most: a compact sage pill, as the app's main action. */
@Composable
internal fun FilledButton(text: String, onClick: Action) {
    Box(GlanceModifier.height(TargetSize).clickable(onClick), contentAlignment = Alignment.Center) {
        Box(
            GlanceModifier.height(CompactHeight).fill(WidgetColors.cta, R.drawable.widget_pill).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = WidgetType.label.copy(color = WidgetColors.onCta), maxLines = 1)
        }
    }
}

/**
 * A widget's title over its list, which opens the app there, with a quiet [subtitle] such as how
 * many items are left, and at its end the round [action] the widget offers, if any.
 */
@Composable
internal fun WidgetHeader(
    title: String,
    onClick: Action,
    modifier: GlanceModifier = GlanceModifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = WidgetPadding, end = if (action != null) ActionEdge else WidgetPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.defaultWeight().height(TargetSize).clickable(onClick), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = WidgetType.title, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = WidgetType.small, maxLines = 1)
        }
        action?.invoke()
    }
}

/** The space above a header that stands over a list; with the header's 48 dp, as tall as one of the app's rows. */
internal val HeaderTop = 8.dp

/** The round "+" in the corner of the shopping widget, the widget's main action. */
@Composable
internal fun AddButton(description: String, onClick: Action) {
    Box(
        GlanceModifier
            .size(TargetSize)
            .clickable(onClick, rippleOverride = R.drawable.widget_ripple_circle)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(GlanceModifier.size(CompactHeight).fill(WidgetColors.cta, R.drawable.widget_circle), contentAlignment = Alignment.Center) {
            Glyph(R.drawable.ic_add, WidgetColors.onCta, size = 20.dp)
        }
    }
}

/** The app's shopping check: a fine ring, or a sage disc with its stroke. */
@Composable
internal fun Check(checked: Boolean) {
    if (checked) {
        Box(GlanceModifier.size(GlyphSize).fill(WidgetColors.primary, R.drawable.widget_circle), contentAlignment = Alignment.Center) {
            Glyph(R.drawable.widget_check_mark, WidgetColors.onPrimary)
        }
    } else {
        Glyph(R.drawable.widget_check_off, WidgetColors.outline)
    }
}

/**
 * What a widget says in place of its content, as the app's message states do: that nobody is
 * signed in, that nothing is there yet or that the server is out of reach. The whole widget takes
 * the tap for [onClick], which a [button] offers where there is room for one. One cell shows the
 * glyph only, a low widget a single line, a larger one the glyph, the title and the button, and a
 * tall one the [body] as well.
 */
@Composable
internal fun WidgetMessage(
    @DrawableRes icon: Int,
    title: String,
    onClick: Action,
    body: String? = null,
    button: String? = null,
) {
    val size = LocalSize.current
    WidgetSurface(GlanceModifier.clickable(onClick)) {
        when {
            size.width < WidgetSizes.Narrow -> {
                Box(GlanceModifier.fillMaxSize().semantics { contentDescription = title }, contentAlignment = Alignment.Center) {
                    Glyph(icon, WidgetColors.onSurfaceVariant)
                }
            }

            size.height < MessageHeight -> {
                MessageLine(icon, title, GlanceModifier.fillMaxSize())
            }

            else -> {
                Column(
                    GlanceModifier.fillMaxSize().padding(WidgetPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(icon, WidgetColors.onSurfaceVariant)
                    Spacer(GlanceModifier.height(8.dp))
                    Text(title, style = WidgetType.name.copy(textAlign = TextAlign.Center), maxLines = 2)
                    if (body != null && size.height >= MessageWithBodyHeight) {
                        Spacer(GlanceModifier.height(4.dp))
                        Text(body, style = WidgetType.small.copy(textAlign = TextAlign.Center), maxLines = 3)
                    }
                    if (button != null) {
                        Spacer(GlanceModifier.height(8.dp))
                        FilledButton(button, onClick)
                    }
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
        Glyph(icon, WidgetColors.onSurfaceVariant)
        Spacer(GlanceModifier.width(12.dp))
        Text(text, style = WidgetType.body, maxLines = 2)
    }
}

/** The outline of content still on its way, drawn as the app's placeholder lines. */
@Composable
internal fun LoadingOutline() {
    Column(GlanceModifier.fillMaxSize().padding(WidgetPadding), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.height(OutlineLine).width(96.dp).fill(WidgetColors.card, R.drawable.widget_line)) {}
        Spacer(GlanceModifier.height(8.dp))
        Box(GlanceModifier.height(OutlineLine).fillMaxWidth().fill(WidgetColors.card, R.drawable.widget_line)) {}
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

/** The height of the app's compact buttons and chips. */
private val CompactHeight = 36.dp

/** The height of the app's placeholder lines. */
private val OutlineLine = 12.dp

/** From this height a message has room for its glyph, a title of two lines and its button. */
private val MessageHeight = 170.dp

/** From this height a message has room for its body as well. */
private val MessageWithBodyHeight = 240.dp

/** The padding after a round button at a widget's edge, so its circle, not its target, ends on the edge. */
private val ActionEdge: Dp = WidgetPadding - (TargetSize - CompactHeight) / 2
