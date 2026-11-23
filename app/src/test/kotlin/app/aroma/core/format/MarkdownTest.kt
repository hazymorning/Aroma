package app.aroma.core.format

import app.aroma.core.format.StyledText.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test
    fun `plain text passes through unchanged`() {
        val text = "Schneide den Lauch in dünne Ringe und wasche diese gründlich."
        assertEquals(StyledText(text, emptyList()), Markdown.parse(text))
    }

    @Test
    fun `bold and italic are removed from the text and become spans`() {
        val parsed = Markdown.parse("Den **Teig** kurz _ruhen_ lassen")
        assertEquals("Den Teig kurz ruhen lassen", parsed.text)
        assertEquals(listOf(Span.Bold(4, 8), Span.Italic(14, 19)), parsed.spans)
    }

    @Test
    fun `underscores inside words are not emphasis`() {
        val parsed = Markdown.parse("Use the snake_case_name as is")
        assertEquals("Use the snake_case_name as is", parsed.text)
        assertTrue(parsed.spans.isEmpty())
    }

    @Test
    fun `links keep their label and url`() {
        val parsed = Markdown.parse("Siehe [Grundteig](https://example.com/teig).")
        assertEquals("Siehe Grundteig.", parsed.text)
        assertEquals(listOf(Span.Link(6, 15, "https://example.com/teig")), parsed.spans)
    }

    @Test
    fun `links to anything but web pages stay plain text`() {
        val parsed = Markdown.parse("[Video](file:///sdcard/x.mp4) und [App](intent://scan#Intent;end)")
        assertEquals("Video und App", parsed.text)
        assertTrue(parsed.spans.isEmpty())
    }

    @Test
    fun `bare urls become links`() {
        val parsed = Markdown.parse("Video: https://example.com/v")
        assertEquals(listOf(Span.Link(7, 28, "https://example.com/v")), parsed.spans)
    }

    @Test
    fun `a scheme without an address stays text and the link after it keeps its place`() {
        val parsed = Markdown.parse("http:// und https://example.com")
        assertEquals("http:// und https://example.com", parsed.text)
        assertEquals(listOf(Span.Link(12, 31, "https://example.com")), parsed.spans)
    }

    @Test
    fun `headings turn bold and bullets become dots`() {
        val parsed = Markdown.parse("## Sauce\n- 2 EL Butter\n* Mehl")
        assertEquals("Sauce\n• 2 EL Butter\n• Mehl", parsed.text)
        assertEquals(listOf(Span.Bold(0, 5)), parsed.spans)
    }

    @Test
    fun `an unmatched marker stays visible`() {
        assertEquals("5 * 3 Portionen", Markdown.parse("5 * 3 Portionen").text)
    }
}
