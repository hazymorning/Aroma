package app.aroma.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import app.aroma.asRead
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Hyphenated German compounds are handed to the line breaker so each part can hyphenate on its own,
 * and short words are kept whole.
 */
class BreakableTest {
    @Test
    fun `the text reads as before, and running it again changes nothing`() =
        listOf(
            "Zitronen-Baiser-Plätzchen vom Blech",
            "Koreanische BBQ-Hähnchen-Wraps",
            "rasch zu einem glatten Teig verkneten",
            "Rhabarber-Erdbeer-Streuselkuchenschnitten",
            "10–15 Min.",
            "Mürbeteig- und Quarkfüllung",
            "-5 °C",
            "Ei",
        ).forEach { text ->
            val once = text.breakable()
            assertEquals(text, once.asRead())
            assertEquals(text, once, once.breakable())
        }

    @Test
    fun `a link stays one link`() {
        val timer = LinkAnnotation.Clickable("timer") {}
        val text =
            buildAnnotatedString {
                append("Knoblauch ")
                withLink(timer) { append("eine Minute") }
                append(" mitbraten")
            }
        val broken = text.breakable()
        val links = broken.getLinkAnnotations(0, broken.length)
        assertEquals(1, links.size)
        assertEquals("e${J}ine M${J}inute", broken.text.substring(links[0].start, links[0].end))
    }

    @Test
    fun `styled text keeps its styles on their characters`() {
        val sage = SpanStyle(color = Color.Green)
        val text =
            buildAnnotatedString {
                append("250 g ")
                withStyle(sage) { append("Kokos-Joghurt") }
                append(" unterrühren")
            }
        val broken = text.breakable()
        assertEquals("250 g K${J}okos-${Z}J${J}oghurt unterrühren", broken.text)
        val sageCharacters =
            broken.text.indices.filter { index -> broken.spanStyles.any { it.item == sage && index >= it.start && index < it.end } }
        assertEquals("K${J}okos-${Z}J${J}oghurt", sageCharacters.map { broken.text[it] }.joinToString(""))
    }

    private companion object {
        /** The zero-width space that follows a hyphen. */
        const val Z = "\u200B"

        /** The word joiner that keeps a short word whole. */
        const val J = "\u2060"
    }
}
