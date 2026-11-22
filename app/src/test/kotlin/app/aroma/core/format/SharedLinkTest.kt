package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedLinkTest {
    @Test
    fun `tracking parameters of shared links are dropped`() {
        assertEquals(
            "https://www.instagram.com/reel/DAroma01/",
            SharedLink.clean("https://www.instagram.com/reel/DAroma01/?igsh=MWQ1ZGUxMzBkMA=="),
        )
        assertEquals(
            "https://example.com/rezept?id=7",
            SharedLink.clean("https://example.com/rezept?utm_source=x&id=7&utm_medium=y&fbclid=abc"),
        )
        assertEquals("https://youtu.be/abc", SharedLink.clean("https://youtu.be/abc?si=xyz"))
    }

    @Test
    fun `links without tracking stay exactly as they are`() {
        assertEquals("https://example.com/rezept?id=7&portion=2", SharedLink.clean(" https://example.com/rezept?id=7&portion=2 "))
        assertEquals("kein link", SharedLink.clean("kein link"))
    }
}
