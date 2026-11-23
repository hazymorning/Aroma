package app.aroma.ui.navigation

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.aroma.MainActivity
import app.aroma.R
import app.aroma.feature.editor.ImportInput
import java.util.UUID

/** What Aroma was opened for: something shared to it, one of its launcher shortcuts, a notification or a widget. */
sealed interface LaunchRequest {
    /** A link shared to Aroma, imported at once. */
    data class Import(val url: String) : LaunchRequest

    /** A recipe's text or photos shared to Aroma, which wait on the add screen for a look before they are imported. */
    data class Shared(val text: String = "", val photos: List<Uri> = emptyList()) : LaunchRequest

    /** A recipe an import added, or a planned one tapped in a widget. */
    data class Recipe(val slug: String) : LaunchRequest {
        fun intent(context: Context): Intent = Intent(OPEN_RECIPE, null, context, MainActivity::class.java).putExtra(EXTRA, slug)
    }

    /** An import as its screen shows it, running or ended. */
    data class ShowImport(val job: UUID) : LaunchRequest {
        fun intent(context: Context): Intent = Intent(SHOW_IMPORT, null, context, MainActivity::class.java).putExtra(EXTRA, job.toString())
    }

    data object Search : LaunchRequest

    data object AddRecipe : LaunchRequest

    data object MealPlan : LaunchRequest {
        fun intent(context: Context): Intent = Shortcut.MealPlan.intent(context)
    }

    /** The shopping tab; from a widget with the widget's list, and with [add] ready to add to it. */
    data class Shopping(val listId: String? = null, val add: Boolean = false) : LaunchRequest {
        fun intent(context: Context): Intent =
            Shortcut.Shopping.intent(context).apply {
                listId?.let { putExtra(EXTRA, it) }
                if (add) putExtra(ADD, true)
            }
    }

    companion object {
        /** The request [intent] carries, if any. */
        fun from(intent: Intent?): LaunchRequest? =
            when (intent?.action) {
                Intent.ACTION_SEND -> {
                    shared(intent, listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)))
                }

                Intent.ACTION_SEND_MULTIPLE -> {
                    shared(intent, IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty())
                }

                OPEN_RECIPE -> {
                    intent.getStringExtra(EXTRA)?.let(::Recipe)
                }

                SHOW_IMPORT -> {
                    importId(intent.getStringExtra(EXTRA))?.let(::ShowImport)
                }

                Shortcut.Shopping.action -> {
                    Shopping(intent.getStringExtra(EXTRA), intent.getBooleanExtra(ADD, false))
                }

                else -> {
                    Shortcut.entries.firstOrNull { it.action == intent?.action }?.request
                }
            }

        private const val OPEN_RECIPE = "app.aroma.action.OPEN_RECIPE"
        private const val SHOW_IMPORT = "app.aroma.action.SHOW_IMPORT"
        private const val EXTRA = "id"
        private const val ADD = "add"

        /**
         * What another app shared: a link alone is imported at once, as sharing it to Aroma already
         * said what to do with it; a recipe's text or photos open the add screen for a look first.
         * Photos come as content uris only, since a file would be one of Aroma's own.
         */
        private fun shared(intent: Intent, streams: List<Uri>): LaunchRequest? {
            // Some apps share styled text, which a plain string extra would not return.
            val text =
                intent
                    .getCharSequenceExtra(Intent.EXTRA_TEXT)
                    ?.toString()
                    .orEmpty()
                    .trim()
            val photos = streams.filter { it.scheme == ContentResolver.SCHEME_CONTENT && intent.type?.startsWith("image/") == true }
            val input = ImportInput.of(text)
            return when {
                photos.isNotEmpty() -> Shared(text, photos)
                input.link != null && input.text.isEmpty() -> Import(input.link)
                text.isNotEmpty() -> Shared(text)
                else -> null
            }
        }

        /** Any app can send these actions, so an id that is none opens nothing. */
        private fun importId(text: String?): UUID? =
            try {
                text?.let(UUID::fromString)
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}

/** The actions a long press on Aroma's launcher icon offers. */
enum class Shortcut(
    val request: LaunchRequest,
    @StringRes private val label: Int,
    @StringRes private val longLabel: Int,
    @DrawableRes private val icon: Int,
) {
    Search(LaunchRequest.Search, R.string.shortcut_search, R.string.shortcut_search_long, R.drawable.shortcut_search),
    AddRecipe(LaunchRequest.AddRecipe, R.string.shortcut_add, R.string.shortcut_add_long, R.drawable.shortcut_add),
    MealPlan(LaunchRequest.MealPlan, R.string.nav_meal_plan, R.string.shortcut_plan_long, R.drawable.shortcut_plan),
    Shopping(LaunchRequest.Shopping(), R.string.nav_shopping, R.string.shortcut_shopping_long, R.drawable.shortcut_shopping),
    ;

    val action = "app.aroma.action.${name.uppercase()}"

    fun intent(context: Context): Intent = Intent(action, null, context, MainActivity::class.java)

    companion object {
        /** Publishes the shortcuts in the app's current language; launchers keep them until the next call. */
        fun publish(context: Context) {
            val shortcuts =
                entries.map { shortcut ->
                    ShortcutInfoCompat
                        .Builder(context, shortcut.name)
                        .setShortLabel(context.getString(shortcut.label))
                        .setLongLabel(context.getString(shortcut.longLabel))
                        .setIcon(IconCompat.createWithResource(context, shortcut.icon))
                        .setIntent(shortcut.intent(context))
                        .build()
                }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }
}
