package app.aroma.data.remote

import app.aroma.data.remote.dto.RecipeDto
import app.aroma.data.remote.dto.UnitDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitStandardTest {
    private val tablespoon =
        """{"id":"u1","name":"tablespoon","abbreviation":"tbsp","standardQuantity":0.5,"standardUnit":"fluid_ounce"}"""

    @Test
    fun `units of Mealie 3_13 and newer carry their standard`() {
        val unit = MealieJson.decodeFromString(UnitDto.serializer(), tablespoon).toModel()
        assertEquals(0.5, unit.standardQuantity!!, 0.0)
        assertEquals("fluid_ounce", unit.standardUnit)
    }

    @Test
    fun `units of older servers have none`() {
        val unit = MealieJson.decodeFromString(UnitDto.serializer(), """{"id":"u2","name":"Prise","standardUnit":null}""").toModel()
        assertNull(unit.standardQuantity)
        assertNull(unit.standardUnit)
    }

    @Test
    fun `the offline copy keeps the standard`() {
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
