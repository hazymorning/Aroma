package app.aroma.screenshots

import android.appwidget.AppWidgetHostView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import app.aroma.core.model.MealPlanEntry
import app.aroma.core.model.MealType
import app.aroma.data.settings.ThemeMode
import app.aroma.widget.ShoppingContent
import app.aroma.widget.ShoppingLayout
import app.aroma.widget.ShoppingState
import app.aroma.widget.TodayContent
import app.aroma.widget.TodayLayout
import app.aroma.widget.TodayState
import app.aroma.widget.WidgetLook
import app.aroma.widget.WidgetTheme
import app.aroma.widget.cells
import app.aroma.widget.shownAt
import app.aroma.widget.todayPlan
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The widgets as a launcher draws them, through RemoteViews, at sizes phones give them in portrait
 * and landscape: each laid out for the declared size the launcher picks, then stretched to its own.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetScreenshotTest(private val variant: Variant) {
    data class Variant(val locale: String, val dark: Boolean) {
        val suffix = "${if (dark) "dark" else "light"}_$locale"

        override fun toString() = suffix
    }

    private val sample = SampleData(german = variant.locale == "de")

    @Before
    fun applyVariant() {
        RuntimeEnvironment.setQualifiers("${variant.locale}-w600dp-h1000dp-${if (variant.dark) "night" else "notnight"}-xxhdpi")
    }

    @Test
    fun today() {
        val entries =
            sample.mealPlan +
                listOf(
                    MealPlanEntry(10, sample.today, MealType.Lunch, "", "", sample.recipes[2]),
                    MealPlanEntry(11, sample.today, MealType.Dessert, "", "", sample.recipes[0]),
                )
        val plan = todayPlan(entries, sample.today.atTime(18, 0))
        val photos = entries.mapNotNull { it.recipe }.associate { it.id to photo(it.id, THUMBNAIL) }
        val ready =
            TodayState.Ready(
                plan,
                photo(
                    plan.next
                        ?.recipe
                        ?.id
                        .orEmpty(),
                    HERO,
                ),
                photos,
            )
        TODAY_SIZES.forEach { (name, size) -> today(name, size) { TodayContent(ready, onRetry = {}) } }

        // Without a photo; and on a bright one, a white plate on a pale table, where the scrim does the most.
        val noPhoto = ready.copy(hero = null)
        val bright = ready.copy(hero = brightPhoto(HERO))
        today("tile_no_photo", TILE) { TodayContent(noPhoto, onRetry = {}) }
        today("banner_no_photo", BANNER) { TodayContent(noPhoto, onRetry = {}) }
        today("cover_no_photo", COVER) { TodayContent(noPhoto, onRetry = {}) }
        today("banner_bright", BANNER) { TodayContent(bright, onRetry = {}) }
        today("banner_landscape_bright", cells(2, 1, landscape = true)) { TodayContent(bright, onRetry = {}) }
        today("cover_bright", COVER) { TodayContent(bright, onRetry = {}) }

        // After dinner the widget looks at tomorrow, which starts with a long name.
        val tomorrow = ready.copy(plan = todayPlan(entries, sample.today.atTime(21, 30)), hero = photo("pancakes", HERO))
        today("tomorrow", WIDE) { TodayContent(tomorrow, onRetry = {}) }
        today("banner_tomorrow", BANNER) { TodayContent(tomorrow, onRetry = {}) }
        today("cover_tomorrow", COVER) { TodayContent(tomorrow, onRetry = {}) }

        // A note in place of a recipe has no photo.
        val note = ready.copy(plan = todayPlan(entries, sample.today.plusDays(3).atTime(18, 0)), hero = null)
        today("tile_note", TILE) { TodayContent(note, onRetry = {}) }
        today("cover_note", COVER) { TodayContent(note, onRetry = {}) }

        val nothingToday = ready.copy(plan = todayPlan(entries.filter { it.date != sample.today }, sample.today.atTime(9, 0)))
        today("empty_tile", TILE) { TodayContent(nothingToday, onRetry = {}) }
        today("empty_banner", BANNER) { TodayContent(nothingToday, onRetry = {}) }
        today("empty_cover", COVER) { TodayContent(nothingToday, onRetry = {}) }
        today("empty", WIDE) { TodayContent(nothingToday, onRetry = {}) }
        today("week_empty", cells(4, 3)) { TodayContent(nothingToday, onRetry = {}) }
        today("signed_out_tile", TILE) { TodayContent(TodayState.SignedOut, onRetry = {}) }
        today("signed_out_cover", COVER) { TodayContent(TodayState.SignedOut, onRetry = {}) }
        today("signed_out", WIDE) { TodayContent(TodayState.SignedOut, onRetry = {}) }
        today("unreachable_tile", TILE) { TodayContent(TodayState.Unreachable, onRetry = {}) }
        today("unreachable_banner", BANNER) { TodayContent(TodayState.Unreachable, onRetry = {}) }
        today("unreachable", WIDE) { TodayContent(TodayState.Unreachable, onRetry = {}) }
        today("loading_tile", TILE) { TodayContent(TodayState.Loading, onRetry = {}) }
        today("loading", WIDE) { TodayContent(TodayState.Loading, onRetry = {}) }
    }

    @Test
    fun shopping() {
        val ready = ShoppingState.Ready(sample.shoppingList)
        SHOPPING_SIZES.forEach { (name, size) ->
            shopping(name, size) { ShoppingContent(ready, emptySet(), onToggle = {}, onRetry = {}) }
        }
        val justChecked = sample.shoppingList.items[1].id
        val items = sample.shoppingList.items.map { it.copy(checked = it.id == justChecked) }
        val lingering = ShoppingState.Ready(sample.shoppingList.copy(items = items))
        shopping("lingering", TALL) { ShoppingContent(lingering, setOf(justChecked), onToggle = {}, onRetry = {}) }
        val done = ShoppingState.Ready(sample.shoppingList.copy(items = sample.shoppingList.items.map { it.copy(checked = true) }))
        shopping("done", SQUARE) { ShoppingContent(done, emptySet(), onToggle = {}, onRetry = {}) }
        val empty = ShoppingState.Ready(sample.shoppingList.copy(items = emptyList()))
        shopping("empty", SQUARE) { ShoppingContent(empty, emptySet(), onToggle = {}, onRetry = {}) }
        shopping("signed_out", SQUARE) { ShoppingContent(ShoppingState.SignedOut, emptySet(), onToggle = {}, onRetry = {}) }
        shopping("signed_out_tall", TALL) { ShoppingContent(ShoppingState.SignedOut, emptySet(), onToggle = {}, onRetry = {}) }
        shopping("no_lists", BAR) { ShoppingContent(ShoppingState.NoLists, emptySet(), onToggle = {}, onRetry = {}) }
    }

    private fun today(name: String, size: DpSize, content: @Composable () -> Unit) =
        capture("today_$name", size, TodayLayout.sizes, content)

    private fun shopping(name: String, size: DpSize, content: @Composable () -> Unit) =
        capture("shopping_$name", size, ShoppingLayout.sizes, content)

    /**
     * Renders [content] on a plain wallpaper, with room around it to show the widget's corners, as a
     * launcher shows a widget of [size] that declares [sizes].
     */
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun capture(name: String, size: DpSize, sizes: Set<DpSize>, content: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val views =
            runBlocking {
                GlanceRemoteViews()
                    .compose(activity, sizes.shownAt(size)) { WidgetTheme(WidgetLook(ThemeMode.System, wallpaperColors = false), content) }
                    .remoteViews
            }
        val density = activity.resources.displayMetrics.density
        val margin = (MARGIN.value * density).roundToInt()
        // Collections show only inside a widget host, as on a home screen.
        val host = AppWidgetHostView(activity)
        host.addView(views.apply(activity, host))
        val wallpaper = FrameLayout(activity).apply { setBackgroundColor(if (variant.dark) DARK_WALLPAPER else LIGHT_WALLPAPER) }
        wallpaper.setPadding(margin, margin, margin, margin)
        wallpaper.addView(host, (size.width.value * density).roundToInt(), (size.height.value * density).roundToInt())
        activity.setContentView(wallpaper, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        shadowOf(Looper.getMainLooper()).idle()
        wallpaper.captureRoboImage("build/outputs/roborazzi/widget_${name}_${variant.suffix}.png")
    }

    /** A soft gradient in place of a recipe photo, so the images stay free of third-party content. */
    private fun photo(seed: String, size: Int): Bitmap {
        val palettes =
            listOf(
                intArrayOf(0xFFB9A27A.toInt(), 0xFF7E8F62.toInt()),
                intArrayOf(0xFFCFAE82.toInt(), 0xFF9A6B45.toInt()),
                intArrayOf(0xFFA9B78F.toInt(), 0xFF5F7353.toInt()),
                intArrayOf(0xFFD8C3A0.toInt(), 0xFFB07B52.toInt()),
            )
        val colors = palettes[Math.floorMod(seed.hashCode(), palettes.size)]
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val paint =
            Paint().apply {
                shader =
                    LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), colors[0], colors[1], Shader.TileMode.CLAMP)
            }
        Canvas(bitmap).drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        return bitmap
    }

    /** A white plate on a pale table, the brightest a recipe photo gets. */
    private fun brightPhoto(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFF1EDE4.toInt())
        val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        canvas.drawCircle(size * 0.5f, size * 0.55f, size * 0.46f, plate)
        val food = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF3D9A4.toInt() }
        canvas.drawCircle(size * 0.5f, size * 0.42f, size * 0.16f, food)
        return bitmap
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants() = listOf(Variant("en", dark = false), Variant("de", dark = true))

        private val MARGIN = 16.dp
        private const val LIGHT_WALLPAPER = 0xFF9FB3C8.toInt()
        private const val DARK_WALLPAPER = 0xFF2B3440.toInt()
        private const val HERO = 480
        private const val THUMBNAIL = 120

        private val SQUARE = DpSize(150.dp, 190.dp)
        private val BAR = DpSize(260.dp, 60.dp)
        private val TALL = DpSize(300.dp, 400.dp)

        private val TILE = cells(1, 1)
        private val BANNER = cells(2, 1)
        private val COVER = cells(2, 2)
        private val WIDE = cells(4, 2)

        /** Every number of cells with a layout of its own, in portrait and landscape, and the cells of a roomier grid. */
        private val TODAY_SIZES =
            listOf(
                "tile" to TILE,
                "tile_landscape" to cells(1, 1, landscape = true),
                "tile_roomy" to DpSize(90.dp, 120.dp),
                "banner" to BANNER,
                "banner_landscape" to cells(2, 1, landscape = true),
                "banner_three" to cells(3, 1),
                "banner_roomy" to DpSize(196.dp, 120.dp),
                "row" to cells(4, 1),
                "line" to cells(4, 1, landscape = true),
                "cover" to COVER,
                "cover_three" to cells(3, 2),
                "cover_roomy" to DpSize(196.dp, 256.dp),
                "wide" to WIDE,
                "landscape_square" to cells(2, 2, landscape = true),
                "landscape" to cells(4, 2, landscape = true),
                "week" to cells(4, 3),
                "week_narrow" to cells(2, 3),
            )

        private val SHOPPING_SIZES =
            listOf(
                "count" to DpSize(70.dp, 90.dp),
                "bar" to BAR,
                "small" to DpSize(150.dp, 110.dp),
                "square" to SQUARE,
                "wide" to DpSize(300.dp, 220.dp),
                "tall" to DpSize(150.dp, 400.dp),
                "large" to TALL,
            )
    }
}
