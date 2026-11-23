package app.aroma.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/** The add screen's one field, read as a link, the text of a recipe, or both. */
class ImportInputTest {
    @Test
    fun `a link alone is the link`() {
        assertEquals(ImportInput(LINK, ""), ImportInput.of("  $LINK\n"))
    }

    @Test
    fun `a title or a few words around a link are no recipe`() {
        assertEquals(ImportInput(LINK, ""), ImportInput.of("Leek fritters | Good Food $LINK"))
        assertEquals(ImportInput(LINK, ""), ImportInput.of("Look at this ($LINK)."))
    }

    @Test
    fun `a caption below its link is the recipe's text`() {
        assertEquals(ImportInput(LINK, CAPTION), ImportInput.of("$LINK\n\n$CAPTION"))
    }

    @Test
    fun `a caption told in one long paragraph is the recipe's text too`() {
        val caption =
            "Leek fritters: slice two leeks finely, stir them into two eggs, three spoons of flour and some salt, " +
                "and fry spoonfuls of it in hot oil until golden."

        assertEquals(ImportInput(LINK, caption), ImportInput.of("$LINK $caption"))
    }

    @Test
    fun `a text without a link is all text`() {
        assertEquals(ImportInput(null, CAPTION), ImportInput.of("\n$CAPTION\n"))
    }

    @Test
    fun `the first link is the link, and later ones stay in the text`() {
        val text = "$CAPTION\nMore at https://example.com/blog"

        assertEquals(ImportInput(LINK, text), ImportInput.of("$LINK\n$text"))
    }

    private companion object {
        const val LINK = "https://www.bbcgoodfood.com/recipes/leek-fritters"
        const val CAPTION = "Leek fritters\nIngredients:\n2 leeks\n2 eggs"
    }
}
