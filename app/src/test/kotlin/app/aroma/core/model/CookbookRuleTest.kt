package app.aroma.core.model

import app.aroma.data.remote.MealieJson
import app.aroma.data.remote.dto.CookbookDto
import app.aroma.data.remote.toModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How Aroma reads a cookbook's rule, and files a recipe into a cookbook and out of it. */
class CookbookRuleTest {
    private val soups = Organizer("c-soup", "Suppen", "suppen")
    private val baking = Organizer("c-bake", "Backen", "backen")
    private val quick = Organizer("t-quick", "Schnell", "schnell")
    private val oven = Organizer("t-oven", "Ofen", "ofen")
    private val group = Organizers(categories = listOf(soups, baking), tags = listOf(quick, oven))
    private val nothing = Filing(emptyList(), emptyList())

    private fun rule(vararg parts: RulePart) = CookbookRule.of(parts.toList(), written = "set")

    private fun part(attribute: String, operator: String, vararg values: String, joinedBy: String? = null) =
        RulePart(attribute, operator, values.toList(), joinedBy)

    @Test
    fun `a cookbook without a rule holds every recipe`() {
        assertEquals(CookbookRule.Everything, CookbookRule.of(emptyList(), written = ""))
    }

    @Test
    fun `a rule Mealie could not read is Mealie's alone`() {
        assertEquals(CookbookRule.Own, CookbookRule.of(emptyList(), written = "rating >= 4"))
    }

    @Test
    fun `a rule about anything but categories and tags is Mealie's alone`() {
        assertEquals(CookbookRule.Own, rule(part("rating", ">=", "4")))
        assertEquals(CookbookRule.Own, rule(part("tools.id", "IN", "x")))
        assertEquals(
            CookbookRule.Own,
            rule(part("recipe_category.id", "IN", "c-soup"), part("last_made", ">", "\$NOW-30d", joinedBy = "AND")),
        )
    }

    @Test
    fun `one condition or another is Mealie's alone`() {
        assertEquals(CookbookRule.Own, rule(part("recipe_category.id", "IN", "c-soup"), part("tags.id", "IN", "t-quick", joinedBy = "OR")))
    }

    @Test
    fun `a recipe goes into a cookbook of categories by being filed under one of them`() {
        val soupsBook = rule(part("recipe_category.id", "IN", "c-soup", "c-bake")) as CookbookRule.Filed

        assertFalse(soupsBook.holds(nothing))
        val filed = soupsBook.adding(nothing, group)!!
        assertEquals(listOf(soups), filed.categories)
        assertTrue(soupsBook.holds(filed))
        assertTrue("filed under another of them, it is in already", soupsBook.holds(Filing(listOf(baking), emptyList())))
    }

    @Test
    fun `a cookbook asking for all of its tags gets every one that is missing`() {
        val book = rule(part("tags.id", "CONTAINS ALL", "t-quick", "t-oven")) as CookbookRule.Filed
        val half = Filing(emptyList(), listOf(quick))

        assertFalse(book.holds(half))
        assertEquals(listOf(quick, oven), book.adding(half, group)!!.tags)
    }

    @Test
    fun `categories named by their names or slugs are found among the group's`() {
        val byName = rule(part("recipe_category.name", "IN", "suppen")) as CookbookRule.Filed
        val bySlug = rule(part("recipeCategory.slug", "IS", "backen")) as CookbookRule.Filed

        assertEquals(listOf(soups), byName.adding(nothing, group)!!.categories)
        assertEquals(listOf(baking), bySlug.adding(nothing, group)!!.categories)
    }

    @Test
    fun `taking a recipe out files it under the cookbook's categories no longer, and keeps the rest`() {
        val book =
            rule(
                part("recipe_category.id", "IN", "c-soup"),
                part("tags.id", "IN", "t-quick", joinedBy = "AND"),
            ) as CookbookRule.Filed
        val filed = Filing(listOf(soups, baking), listOf(quick, oven))

        assertTrue(book.holds(filed))
        val out = book.removing(filed)!!
        assertFalse(book.holds(out))
        assertEquals(listOf(baking), out.categories)
        assertEquals("the first condition is enough", listOf(quick, oven), out.tags)
    }

    @Test
    fun `a cookbook that asks for something to be left out cannot take a recipe in by filing`() {
        val book = rule(part("tags.id", "NOT IN", "t-oven")) as CookbookRule.Filed

        assertNull(book.adding(Filing(emptyList(), listOf(oven)), group))
        assertNull("nor out", book.removing(nothing))
    }

    @Test
    fun `a category the group no longer has cannot be filed under`() {
        val book = rule(part("recipe_category.id", "IN", "gone")) as CookbookRule.Filed

        assertNull(book.adding(nothing, group))
    }

    @Test
    fun `Mealie's reading of a cookbook's rule becomes the cookbook's rule`() {
        val book =
            MealieJson
                .decodeFromString(
                    CookbookDto.serializer(),
                    """
                    {"id":"b1","name":"Suppen","slug":"suppen","queryFilterString":"recipe_category.id IN [\"c-soup\"] AND tags.id CONTAINS ALL [\"t-quick\"]",
                     "queryFilter":{"parts":[
                       {"leftParenthesis":null,"rightParenthesis":null,"logicalOperator":null,"attributeName":"recipe_category.id","relationalOperator":"IN","value":["c-soup"]},
                       {"logicalOperator":"AND","attributeName":"tags.id","relationalOperator":"CONTAINS ALL","value":["t-quick"]}
                     ]}}
                    """.trimIndent(),
                ).toModel()

        val expected =
            CookbookRule.Filed(
                listOf(
                    FilingCondition(OrganizerKind.Category, FilingCondition.Key.Id, FilingCondition.Match.AnyOf, listOf("c-soup")),
                    FilingCondition(OrganizerKind.Tag, FilingCondition.Key.Id, FilingCondition.Match.AllOf, listOf("t-quick")),
                ),
            )
        assertEquals(expected, book.rule)
    }

    @Test
    fun `a cookbook kept from before Aroma read rules is Mealie's alone until it loads again`() {
        val book = MealieJson.decodeFromString(CookbookDto.serializer(), """{"id":"b1","name":"Suppen"}""").toModel()

        assertEquals(CookbookRule.Own, book.rule)
    }
}
