package app.aroma.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialCaptionTest {
    private val gnocchi =
        """
        Cremige Tomaten-Gnocchi 🍅 in nur 15 Minuten! Perfekt für den Feierabend 🙌

        Zutaten (2 Portionen):
        - 500 g Gnocchi
        - 250 g Kirschtomaten
        - 1 Knoblauchzehe
        - 100 ml Sahne
        - 30 g Parmesan
        - Basilikum, Salz, Pfeffer

        Zubereitung:
        1. Gnocchi in einer Pfanne mit etwas Öl goldbraun braten.
        2. Tomaten halbieren, Knoblauch hacken und 3 Minuten mitbraten.
        3. Sahne und Parmesan unterrühren, 2 Minuten köcheln lassen.
        4. Mit Salz, Pfeffer und Basilikum abschmecken.

        Speichert euch das Rezept! 💛
        #gnocchi #feierabendküche #pasta #rezept
        """.trimIndent()

    @Test
    fun `the caption is taken out of Instagram's link preview`() {
        val title = "Koch mit mir on Instagram: \"$gnocchi\""
        val description = "1,234 likes, 56 comments - kochmitmir on November 20, 2026: \"${gnocchi.take(80)}\". "
        assertEquals(gnocchi, SocialCaption.extract(title, description))
    }

    @Test
    fun `a post imported a second time, which Mealie numbers, still yields its caption`() {
        assertEquals(gnocchi, SocialCaption.extract("Koch mit mir on Instagram: \"$gnocchi\" (1)", ""))
        assertEquals("Kekse (2)", SocialCaption.extract("Lena auf Instagram: „Kekse (2)“", ""))
    }

    @Test
    fun `German quotes and site names are removed, plain text stays as it is`() {
        assertEquals("Ofenkürbis mit Feta", SocialCaption.extract("Lena auf Instagram: „Ofenkürbis mit Feta“", ""))
        assertEquals("Ofenkürbis mit Feta", SocialCaption.extract("Ofenkürbis mit Feta | TikTok", ""))
        assertEquals("Linsensuppe", SocialCaption.extract("Linsensuppe", "Linsensuppe"))
    }

    @Test
    fun `a sign-in page is told apart from a post`() {
        val desc = "Create an account or log in to Instagram - Share what you're into with the people who get you."
        assertTrue(SocialCaption.isSignInPage("Instagram", desc))
        assertTrue(SocialCaption.isSignInPage("Instagram (1)", ""))
        assertTrue(SocialCaption.isSignInPage("Login • Instagram", desc))
        assertTrue(SocialCaption.isSignInPage("TikTok - Make Your Day", ""))
        assertTrue(SocialCaption.isSignInPage("Log in to Facebook", ""))
        assertFalse(SocialCaption.isSignInPage("Koch mit mir on Instagram: \"$gnocchi\"", desc))
        assertFalse(SocialCaption.isSignInPage("Ofenkürbis mit Feta | TikTok", ""))
        assertFalse(SocialCaption.isSignInPage("Lena (@lena) on Threads", "Linsensuppe mit Zitrone"))
        assertFalse(SocialCaption.isSignInPage("TikTok · Lena", "Ofenkürbis mit Feta #rezept"))
    }

    @Test
    fun `a structured German caption becomes a complete recipe`() {
        val recipe = SocialCaption.parse(gnocchi)
        assertEquals("Cremige Tomaten-Gnocchi in nur 15 Minuten", recipe.name)
        assertEquals("Perfekt für den Feierabend", recipe.description)
        assertEquals(
            listOf("500 g Gnocchi", "250 g Kirschtomaten", "1 Knoblauchzehe", "100 ml Sahne", "30 g Parmesan", "Basilikum, Salz, Pfeffer"),
            recipe.ingredients,
        )
        assertEquals(4, recipe.steps.size)
        assertEquals("Gnocchi in einer Pfanne mit etwas Öl goldbraun braten.", recipe.steps.first())
        assertEquals(2.0, recipe.servings)
        assertTrue(recipe.notes.isEmpty())
    }

    @Test
    fun `keycap numbers, emoji bullets and inline tips are understood`() {
        val recipe =
            SocialCaption.parse(
                """
                Garlic butter shrimp 🍤✨

                Ingredients
                🍤 400 g shrimp
                🧄 3 cloves garlic, minced
                🧈 2 tbsp butter

                Method
                1️⃣ Melt the butter over medium heat.
                2️⃣ Add garlic and cook for 1 minute.
                3️⃣ Add the shrimp and cook until pink,
                about 3 minutes.

                💡 Tip: Serve with crusty bread.
                Follow @shrimplover for more!
                """.trimIndent(),
            )
        assertEquals("Garlic butter shrimp", recipe.name)
        assertEquals(listOf("400 g shrimp", "3 cloves garlic, minced", "2 tbsp butter"), recipe.ingredients)
        assertEquals(
            listOf(
                "Melt the butter over medium heat.",
                "Add garlic and cook for 1 minute.",
                "Add the shrimp and cook until pink, about 3 minutes.",
            ),
            recipe.steps,
        )
        assertEquals(listOf("Serve with crusty bread."), recipe.notes)
        assertEquals("", recipe.description)
    }

    @Test
    fun `sections inside the ingredient list are kept`() {
        val recipe =
            SocialCaption.parse(
                """
                Zimtschnecken

                Zutaten für 12 Stück
                Für den Teig:
                500 g Mehl
                1 Würfel Hefe
                Für die Füllung:
                80 g Butter
                2 EL Zimt

                So geht's
                1) Teig kneten und 1 Stunde gehen lassen.
                2) Ausrollen, füllen, aufrollen und backen.
                """.trimIndent(),
            )
        assertEquals(
            listOf("# Für den Teig", "500 g Mehl", "1 Würfel Hefe", "# Für die Füllung", "80 g Butter", "2 EL Zimt"),
            recipe.ingredients,
        )
        assertEquals(2, recipe.steps.size)
        assertNull(recipe.servings)
    }

    @Test
    fun `lists without headers are still found`() {
        val recipe =
            SocialCaption.parse(
                """
                Schnelles Pesto
                - 1 Bund Basilikum
                - 50 g Pinienkerne
                - 100 ml Olivenöl
                1. Pinienkerne rösten.
                2. Alles fein mixen.
                """.trimIndent(),
            )
        assertEquals("Schnelles Pesto", recipe.name)
        assertEquals(listOf("1 Bund Basilikum", "50 g Pinienkerne", "100 ml Olivenöl"), recipe.ingredients)
        assertEquals(listOf("Pinienkerne rösten.", "Alles fein mixen."), recipe.steps)
    }

    @Test
    fun `prose without lists keeps its text in the description`() {
        val recipe =
            SocialCaption.parse(
                "Heute gibt es meine liebste Linsensuppe für kalte Tage. Rote Linsen mit Möhren und Kokosmilch, " +
                    "dazu frisches Brot. Ein Rezept, das jeder schafft! #suppe #herbst",
            )
        assertEquals("Heute gibt es meine liebste Linsensuppe für kalte Tage", recipe.name)
        assertEquals("Rote Linsen mit Möhren und Kokosmilch, dazu frisches Brot. Ein Rezept, das jeder schafft!", recipe.description)
        assertTrue(recipe.ingredients.isEmpty())
        assertTrue(recipe.steps.isEmpty())
    }

    @Test
    fun `a caption that starts with the ingredients has no name`() {
        val recipe = SocialCaption.parse("Zutaten:\n- 2 Eier\n- 1 Prise Salz\nZubereitung:\n1. Verquirlen.")
        assertEquals("", recipe.name)
        assertEquals(listOf("2 Eier", "1 Prise Salz"), recipe.ingredients)
        assertEquals(listOf("Verquirlen."), recipe.steps)
    }
}
