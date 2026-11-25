package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramEmbedTest {
    /** The parts of an embed page as Instagram serves it to a client that is not a browser. */
    private val page =
        """
        <html><body><div class="Embed">
        <img class="EmbeddedMediaImage" alt="Instagram post shared by &#064;kochmitmir" src="https://scontent.example/v/g.jpg?stp=dst-jpg&amp;_nc_cat=1">
        <div class="Caption"><a class="CaptionUsername" href="https://www.instagram.com/kochmitmir/" target="_blank">kochmitmir</a><br /><br />Tomaten-Gnocchi &amp; Basilikum<br /><br />Zutaten:<br />- 500 g Gnocchi <br />- 250 g Kirschtomaten<br /><br /><br /><br />Zubereitung:<br />1. Gnocchi braten.<br />2. Tomaten dazu, Rezept von <a href="/lena/?utm_source=ig_embed">&#064;lena</a> <a href="/explore/tags/gnocchi/">#gnocchi</a><div class="CaptionComments"><a class="CaptionCommentsExpand" href="#">View all 16 comments</a></div></div>
        </div></body></html>
        """.trimIndent()

    @Test
    fun `the caption keeps its lines and words, without its author`() =
        assertEquals(
            "Tomaten-Gnocchi & Basilikum\n\nZutaten:\n- 500 g Gnocchi\n- 250 g Kirschtomaten\n\n" +
                "Zubereitung:\n1. Gnocchi braten.\n2. Tomaten dazu, Rezept von @lena #gnocchi",
            InstagramEmbed.caption(page),
        )

    @Test
    fun `a caption without comments ends with its block, and one of only its author is none`() {
        assertEquals(
            "Linsensuppe",
            InstagramEmbed.caption(
                """<div class="Caption"><a class="CaptionUsername" href="/a/">a</a><br />Linsensuppe</div><div>Footer</div>""",
            ),
        )
        assertNull(InstagramEmbed.caption("""<div class="Caption"><a class="CaptionUsername" href="/a/">a</a></div>"""))
    }

    @Test
    fun `posts, reels and links with the author's name all carry the code`() {
        assertEquals("C-TErV8t1pz", InstagramEmbed.code("/p/C-TErV8t1pz/"))
        assertEquals("C-TErV8t1pz", InstagramEmbed.code("/reel/C-TErV8t1pz/"))
        assertEquals("C-TErV8t1pz", InstagramEmbed.code("/reels/C-TErV8t1pz/"))
        assertEquals("C-TErV8t1pz", InstagramEmbed.code("/kochmitmir/p/C-TErV8t1pz/"))
        assertEquals("C-TErV8t1pz", InstagramEmbed.code("/tv/C-TErV8t1pz"))
        assertNull(InstagramEmbed.code("/kochmitmir/"))
        assertNull(InstagramEmbed.code("/share/reel/BAxyz/"))
    }
}
