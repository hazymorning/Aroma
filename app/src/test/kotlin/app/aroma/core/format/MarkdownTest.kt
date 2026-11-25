package app.aroma.core.format

import app.aroma.core.format.StyledText.Span
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {
    @Test
    fun `bold and italic become spans, while underscores in words and lone stars stay`() {
        assertEquals(
            StyledText("Den Teig kurz ruhen lassen", listOf(Span.Bold(4, 8), Span.Italic(14, 19))),
            Markdown.parse("Den **Teig** kurz _ruhen_ lassen"),
        )
        assertEquals(StyledText("Use the snake_case_name as is", emptyList()), Markdown.parse("Use the snake_case_name as is"))
        assertEquals(StyledText("5 * 3 Portionen", emptyList()), Markdown.parse("5 * 3 Portionen"))
    }

    @Test
    fun `links keep their label and address, and bare addresses become links`() {
        assertEquals(
            StyledText("Siehe Grundteig.", listOf(Span.Link(6, 15, "https://example.com/teig"))),
            Markdown.parse("Siehe [Grundteig](https://example.com/teig)."),
        )
        assertEquals(
            StyledText("http:// und https://example.com", listOf(Span.Link(12, 31, "https://example.com"))),
            Markdown.parse("http:// und https://example.com"),
        )
    }

    @Test
    fun `links to anything but web pages stay plain text`() =
        assertEquals(
            StyledText("Video und App", emptyList()),
            Markdown.parse("[Video](file:///sdcard/x.mp4) und [App](intent://scan#Intent;end)"),
        )

    @Test
    fun `headings turn bold and bullets become dots`() =
        assertEquals(
            StyledText("Sauce\n• 2 EL Butter\n• Mehl", listOf(Span.Bold(0, 5))),
            Markdown.parse("## Sauce\n- 2 EL Butter\n* Mehl"),
        )
}
