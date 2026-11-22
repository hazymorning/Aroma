package app.aroma.data.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.aroma.core.model.Recipe
import app.aroma.data.remote.MealieJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.days

/** A recipe cooked with Aroma that has no rating yet. */
@Serializable
data class CookedRecipe(
    val userId: String,
    val recipeId: String,
    val slug: String,
    val name: String,
    val imageVersion: String? = null,
    val cookedAt: Long,
)

/**
 * Remembers the last recipe cooked without rating it, so Home can ask how it was once it has been
 * eaten. Only one at a time: the reminder is meant as a gentle nudge, not a to-do list.
 */
class RatingReminder(private val dataStore: DataStore<Preferences>, private val now: () -> Long = System::currentTimeMillis) {
    private val key = stringPreferencesKey("rating_reminder")

    val pending: Flow<CookedRecipe?> =
        dataStore.data.map { prefs ->
            prefs[key]
                ?.let { runCatching { MealieJson.decodeFromString(CookedRecipe.serializer(), it) }.getOrNull() }
                ?.takeIf { now() - it.cookedAt < KEEP.inWholeMilliseconds }
        }

    suspend fun cooked(recipe: Recipe, userId: String) {
        val cooked = CookedRecipe(userId, recipe.id, recipe.slug, recipe.name, recipe.image?.version, now())
        dataStore.edit { it[key] = MealieJson.encodeToString(CookedRecipe.serializer(), cooked) }
    }

    /** Forgets the reminder, for [recipeId] only when given, so rating one recipe leaves another's alone. */
    suspend fun clear(recipeId: String? = null) {
        dataStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { MealieJson.decodeFromString(CookedRecipe.serializer(), it) }.getOrNull() }
            if (recipeId == null || current == null || current.recipeId == recipeId) prefs.remove(key)
        }
    }

    private companion object {
        /** After a few days nobody remembers how it tasted. */
        val KEEP = 3.days
    }
}
