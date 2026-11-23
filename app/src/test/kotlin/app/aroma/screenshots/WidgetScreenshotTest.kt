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
import app.aroma.widget.ShoppingState
import app.aroma.widget.TodayContent
import app.aroma.widget.TodayState
import app.aroma.widget.WidgetLook
import app.aroma.widget.WidgetTheme
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

/** The widgets as a launcher draws them, through RemoteViews, at sizes phones give them in portrait and landscape. */
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
        TODAY_SIZES.forEach { (name, size) -> capture("today_$name", size) { TodayContent(ready, onRetry = {}) } }
        capture("today_no_photo", SQUARE) { TodayContent(ready.copy(hero = null), onRetry = {}) }
        capture("today_tomorrow", WIDE) {
            TodayContent(ready.copy(plan = todayPlan(entries, sample.today.atTime(21, 30)), hero = photo("pancakes", HERO)), onRetry = {})
        }
        val nothingToday = ready.copy(plan = todayPlan(entries.filter { it.date != sample.today }, sample.today.atTime(9, 0)))
        capture("today_empty", WIDE) { TodayContent(nothingToday, onRetry = {}) }
        capture("today_week_empty", TALL) { TodayContent(nothingToday, onRetry = {}) }
        capture("today_signed_out", SQUARE) { TodayContent(TodayState.SignedOut, onRetry = {}) }
        capture("today_unreachable", WIDE) { TodayContent(TodayState.Unreachable, onRetry = {}) }
        capture("today_unreachable_row", ROW) { TodayContent(TodayState.Unreachable, onRetry = {}) }
        capture("today_loading", WIDE) { TodayContent(TodayState.Loading, onRetry = {}) }
    }

    @Test
    fun shopping() {
        val ready = ShoppingState.Ready(sample.shoppingList)
        SHOPPING_SIZES.forEach { (name, size) ->
            capture("shopping_$name", size) { ShoppingContent(ready, emptySet(), onToggle = {}, onRetry = {}) }
        }
        val justChecked = sample.shoppingList.items[1].id
        val items = sample.shoppingList.items.map { it.copy(checked = it.id == justChecked) }
        val lingering = ShoppingState.Ready(sample.shoppingList.copy(items = items))
        capture("shopping_lingering", TALL) { ShoppingContent(lingering, setOf(justChecked), onToggle = {}, onRetry = {}) }
        val done = ShoppingState.Ready(sample.shoppingList.copy(items = sample.shoppingList.items.map { it.copy(checked = true) }))
        capture("shopping_done", SQUARE) { ShoppingContent(done, emptySet(), onToggle = {}, onRetry = {}) }
        val empty = ShoppingState.Ready(sample.shoppingList.copy(items = emptyList()))
        capture("shopping_empty", SQUARE) { ShoppingContent(empty, emptySet(), onToggle = {}, onRetry = {}) }
        capture("shopping_signed_out", SQUARE) { ShoppingContent(ShoppingState.SignedOut, emptySet(), onToggle = {}, onRetry = {}) }
        capture("shopping_signed_out_tall", TALL) { ShoppingContent(ShoppingState.SignedOut, emptySet(), onToggle = {}, onRetry = {}) }
        capture("shopping_no_lists", BAR) { ShoppingContent(ShoppingState.NoLists, emptySet(), onToggle = {}, onRetry = {}) }
    }

    /** Renders [content] at [size] on a plain wallpaper, with room around it to show the widget's corners. */
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun capture(name: String, size: DpSize, content: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val views =
            runBlocking {
                GlanceRemoteViews()
                    .compose(activity, size) { WidgetTheme(WidgetLook(ThemeMode.System, wallpaperColors = false), content) }
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
        private val WIDE = DpSize(300.dp, 190.dp)
        private val BAR = DpSize(260.dp, 60.dp)
        private val TALL = DpSize(300.dp, 400.dp)
        private val ROW = DpSize(290.dp, 102.dp)

        /** Sizes from Android's guide to widget sizes, for a phone in portrait and in landscape. */
        private val TODAY_SIZES =
            listOf(
                "line" to DpSize(150.dp, 60.dp),
                "line_wide" to DpSize(290.dp, 60.dp),
                "small" to DpSize(130.dp, 102.dp),
                "row" to ROW,
                "square" to SQUARE,
                "wide" to WIDE,
                "landscape" to DpSize(520.dp, 110.dp),
                "week" to TALL,
                "week_narrow" to DpSize(150.dp, 400.dp),
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
