package app.aroma.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Hyphenated German compounds are handed to the line breaker so each part can hyphenate on its own,
 * and short words are kept whole.
 */
class BreakableTest {
    @Test
    fun `a hyphen between letters is followed by a place to break`() {
        assertEquals("Zitronen-${Z}B${J}aiser-${Z}Plätzchen vom B${J}lech", "Zitronen-Baiser-Plätzchen vom Blech".breakable())
        assertEquals("Koreanische BBQ-${Z}Hähnchen-${Z}W${J}raps", "Koreanische BBQ-Hähnchen-Wraps".breakable())
    }

    @Test
    fun `a non-breaking hyphen becomes an ordinary one`() {
        assertEquals("BBQ-${Z}Hähnchen", "BBQ${NON_BREAKING}Hähnchen".breakable())
    }

    @Test
    fun `dashes, spans and hyphens without a letter on both sides stay as they are`() {
        listOf("10–15 Min.", "Mürbeteig- und Quarkfüllung", "Ei", "-5 °C").forEach { assertEquals(it, it.breakable()) }
    }

    @Test
    fun `words of four to seven letters are kept whole, longer ones may hyphenate`() {
        assertEquals("r${J}asch zu e${J}inem g${J}latten T${J}eig verkneten", "rasch zu einem glatten Teig verkneten".breakable())
        assertEquals("Zitronensaft und Stärke", "Zitronensaft und Stärke".breakable().replace(J, ""))
        assertEquals("S${J}tärke", "Stärke".breakable())
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
    fun `running it again changes nothing`() {
        val once = "Rhabarber-Erdbeer-Streuselkuchenschnitten, rasch zu einem Teig".breakable()
        assertEquals(once, once.breakable())
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
        const val NON_BREAKING = "\u2011"
    }
}
