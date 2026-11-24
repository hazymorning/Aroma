package app.aroma.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/** Hyphenated German compounds are handed to the line breaker so each part can hyphenate on its own. */
class BreakableTest {
    @Test
    fun `a hyphen between letters is followed by a place to break`() {
        assertEquals("Zitronen-${Z}Baiser-${Z}Plätzchen vom Blech", "Zitronen-Baiser-Plätzchen vom Blech".breakable())
        assertEquals("Koreanische BBQ-${Z}Hähnchen-${Z}Wraps", "Koreanische BBQ-Hähnchen-Wraps".breakable())
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
    fun `running it again changes nothing`() {
        val once = "Rhabarber-Erdbeer-Streuselkuchenschnitten".breakable()
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
        assertEquals("250 g Kokos-${Z}Joghurt unterrühren", broken.text)
        val sageCharacters =
            broken.text.indices.filter { index -> broken.spanStyles.any { it.item == sage && index >= it.start && index < it.end } }
        assertEquals("Kokos-Joghurt", sageCharacters.map { broken.text[it] }.joinToString(""))
    }

    private companion object {
        /** The zero-width space that follows a hyphen. */
        const val Z = "\u200B"
        const val NON_BREAKING = "\u2011"
    }
}
