package app.aroma.data.remote

import app.aroma.data.remote.dto.RecipeDto
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitStandardTest {
    @Test
    fun `a unit's standard survives the offline copy`() {
        val tablespoon = """{"id":"u1","name":"tablespoon","abbreviation":"tbsp","standardQuantity":0.5,"standardUnit":"fluid_ounce"}"""
        val recipe = """{"id":"r1","slug":"pancakes","recipeIngredient":[{"quantity":2,"unit":$tablespoon,"food":{"name":"sugar"}}]}"""
        val cached = MealieJson.encodeToString(RecipeDto.serializer(), MealieJson.decodeFromString(RecipeDto.serializer(), recipe))
        val unit =
            MealieJson
                .decodeFromString(RecipeDto.serializer(), cached)
                .toModel("https://mealie.example")
                .ingredients
                .single()
                .unit!!
        assertEquals(0.5, unit.standardQuantity!!, 0.0)
        assertEquals("fluid_ounce", unit.standardUnit)
    }
}
