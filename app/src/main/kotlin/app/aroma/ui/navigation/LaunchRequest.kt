package app.aroma.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.aroma.MainActivity
import app.aroma.R
import java.util.UUID

/** What Aroma was opened for: a link shared to it, one of its launcher shortcuts, a notification or a widget. */
sealed interface LaunchRequest {
    data class Import(val url: String) : LaunchRequest

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
                    // Some apps share styled text, which a plain string extra would not return.
                    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    text?.let { link.find(it)?.value?.trimEnd('.', ',', ')', '!', '?', '"', '\u201D') }?.let(::Import)
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

        private val link = Regex("""https?://\S+""")

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
