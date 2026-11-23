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

/** What Aroma was opened for: a link shared to it, or one of its launcher shortcuts. */
sealed interface LaunchRequest {
    data class Import(val url: String) : LaunchRequest

    data object Search : LaunchRequest

    data object AddRecipe : LaunchRequest

    data object MealPlan : LaunchRequest

    data object Shopping : LaunchRequest

    companion object {
        /** The request [intent] carries, if any. */
        fun from(intent: Intent?): LaunchRequest? =
            when (intent?.action) {
                Intent.ACTION_SEND -> {
                    // Some apps share styled text, which a plain string extra would not return.
                    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    text?.let { link.find(it)?.value?.trimEnd('.', ',', ')', '!', '?', '"', '\u201D') }?.let(::Import)
                }

                else -> {
                    Shortcut.entries.firstOrNull { it.action == intent?.action }?.request
                }
            }

        private val link = Regex("""https?://\S+""")
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
    Shopping(LaunchRequest.Shopping, R.string.nav_shopping, R.string.shortcut_shopping_long, R.drawable.shortcut_shopping),
    ;

    val action = "app.aroma.action.${name.uppercase()}"

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
                        .setIntent(Intent(shortcut.action, null, context, MainActivity::class.java))
                        .build()
                }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }
}
