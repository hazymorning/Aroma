package app.aroma.core.model

import java.time.Instant
import java.time.LocalDate

enum class ImageSize(val fileName: String) {
    Original("original.webp"),
    Medium("min-original.webp"),
    Thumbnail("tiny-original.webp"),
}

/** Mealie serves three renditions per recipe; [version] busts caches after an image change. */
data class RecipeImage(val serverUrl: String, val recipeId: String, val version: String) {
    fun url(size: ImageSize): String = "$serverUrl/api/media/recipes/$recipeId/images/${size.fileName}?version=$version"
}

/** Mealie 3 sends durations in seconds; older servers only have free text such as "1 hour". */
data class RecipeTime(val seconds: Int?, val text: String?) {
    val isEmpty: Boolean get() = (seconds ?: 0) <= 0 && text.isNullOrBlank()
}

data class Organizer(val id: String, val name: String, val slug: String)

data class RecipeSummary(
    val id: String,
    val slug: String,
    val name: String,
    val description: String,
    val image: RecipeImage?,
    val totalTime: RecipeTime?,
    val prepTime: RecipeTime?,
    val cookTime: RecipeTime?,
    val servings: Double,
    val rating: Double?,
    val categories: List<Organizer>,
    val tags: List<Organizer>,
)

data class Recipe(
    val id: String,
    val slug: String,
    val name: String,
    val description: String,
    val image: RecipeImage?,
    val totalTime: RecipeTime?,
    val prepTime: RecipeTime?,
    val cookTime: RecipeTime?,
    val servings: Double,
    val yieldQuantity: Double,
    val yieldText: String,
    val rating: Double?,
    val categories: List<Organizer>,
    val tags: List<Organizer>,
    val tools: List<Organizer>,
    val sourceUrl: String?,
    val ingredients: List<Ingredient>,
    val steps: List<Step>,
    val notes: List<RecipeNote>,
    val nutrition: List<NutritionValue>,
    val dateAdded: LocalDate?,
    val lastMade: Instant?,
)

data class IngredientUnit(
    val id: String?,
    val name: String,
    val pluralName: String?,
    val abbreviation: String,
    val pluralAbbreviation: String?,
    val useAbbreviation: Boolean,
    val useFraction: Boolean,
) {
    fun label(plural: Boolean): String {
        if (useAbbreviation && abbreviation.isNotBlank()) {
            return if (plural && !pluralAbbreviation.isNullOrBlank()) pluralAbbreviation else abbreviation
        }
        return if (plural && !pluralName.isNullOrBlank()) pluralName else name
    }
}

data class IngredientFood(val id: String?, val name: String, val pluralName: String?) {
    fun label(plural: Boolean): String = if (plural && !pluralName.isNullOrBlank()) pluralName else name
}

data class LinkedRecipe(val slug: String, val name: String)

data class Ingredient(
    val referenceId: String?,
    val section: String?,
    val quantity: Double,
    val unit: IngredientUnit?,
    val food: IngredientFood?,
    val note: String,
    val display: String,
    val linkedRecipe: LinkedRecipe?,
) {
    val isParsed: Boolean get() = unit != null || food != null
}

data class Step(
    val id: String?,
    val title: String?,
    val text: String,
    val ingredientReferences: List<String>,
    val durationSeconds: Int?,
)

data class RecipeNote(val title: String, val text: String)

enum class NutritionKind(val defaultUnit: String) {
    Calories("kcal"),
    Protein("g"),
    Fat("g"),
    SaturatedFat("g"),
    Carbohydrates("g"),
    Sugar("g"),
    Fiber("g"),
    Sodium("mg"),
    Cholesterol("mg"),
}

data class NutritionValue(val kind: NutritionKind, val value: String)
