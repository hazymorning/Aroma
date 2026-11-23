package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPreviewTest {
    @Test
    fun `reads an Instagram post's caption with its lines, its photo and that it is a video`() {
        val html =
            """
            <html><head>
            <meta property="og:type" content="video.other" />
            <meta property="og:title"
              content="Koch mit mir on Instagram: &quot;Gnocchi &#x1f345;&#10;- 500 g Gnocchi&#10;1. Braten, bis sie &gt; goldbraun sind&quot;" />
            <meta property="og:image" content="https://scontent.cdninstagram.com/a.jpg?stp=1&amp;oh=2" />
            </head><body></body></html>
            """.trimIndent()

        val preview = LinkPreview.parse(html)

        assertEquals("Koch mit mir on Instagram: \"Gnocchi 🍅\n- 500 g Gnocchi\n1. Braten, bis sie > goldbraun sind\"", preview.title)
        assertEquals("https://scontent.cdninstagram.com/a.jpg?stp=1&oh=2", preview.image)
        assertTrue(preview.isVideo)
    }

    @Test
    fun `a quoted greater-than sign does not end a tag, and attributes come in any order`() {
        val html = """<meta content='Ofen auf > 180 °C' name="description"><meta content="Pizza" property="og:title">"""

        val preview = LinkPreview.parse(html)

        assertEquals("Pizza", preview.title)
        assertEquals("Ofen auf > 180 °C", preview.description)
        assertFalse(preview.isVideo)
        assertNull(preview.image)
    }

    @Test
    fun `twitter tags stand in for missing og tags`() {
        val html =
            """
            <meta name="twitter:title" content="Linsensuppe">
            <meta name="twitter:description" content="Wärmt von innen">
            <meta name="twitter:image" content="https://example.com/suppe.jpg">
            """.trimIndent()

        assertEquals(
            LinkPreview("Linsensuppe", "Wärmt von innen", "https://example.com/suppe.jpg", isVideo = false),
            LinkPreview.parse(html),
        )
    }

    @Test
    fun `references Aroma does not know stay as they are`() {
        assertEquals(
            "Salz &amp Pfeffer &hellip;",
            LinkPreview.parse("""<meta property="og:title" content="Salz &amp Pfeffer &hellip;">""").title,
        )
    }
}
