package app.aroma.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.TextUnit
import app.aroma.R
import app.aroma.ui.theme.AromaTypography
import app.aroma.ui.theme.LightColors
import app.aroma.ui.theme.TABULAR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The type roles are the brief's table (4.2) and nothing else: serif only where it names things,
 * the sans in two weights, nothing smaller than 12 sp, nothing tracked out, and every role hyphenates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TypeRolesTest {
    private enum class Face { Serif, Sans }

    private data class Role(
        val face: Face,
        val size: Float,
        val lineHeight: Float,
        val medium: Boolean = false,
        val italic: Boolean = false,
        val tabular: Boolean = false,
    )

    private val table =
        mapOf(
            "display" to Role(Face.Serif, 34f, 40f),
            "recipeTitle" to Role(Face.Serif, 30f, 36f),
            "section" to Role(Face.Serif, 22f, 28f),
            "cardTitle" to Role(Face.Serif, 17f, 22f),
            "headnote" to Role(Face.Serif, 17f, 26f, italic = true),
            "groupLabel" to Role(Face.Serif, 17f, 24f, italic = true),
            "numeralLarge" to Role(Face.Serif, 64f, 64f, tabular = true),
            "numeral" to Role(Face.Serif, 28f, 32f, tabular = true),
            "barTitle" to Role(Face.Serif, 20f, 24f),
            "subtitle" to Role(Face.Sans, 15f, 20f),
            "body" to Role(Face.Sans, 16f, 24f),
            "bodySmall" to Role(Face.Sans, 14f, 20f),
            "labelLarge" to Role(Face.Sans, 17f, 22f, medium = true),
            "label" to Role(Face.Sans, 15f, 20f, medium = true),
            "meta" to Role(Face.Sans, 13f, 18f, tabular = true),
            "navSelected" to Role(Face.Sans, 12f, 16f, medium = true),
            "nav" to Role(Face.Sans, 12f, 16f),
            "cookStep" to Role(Face.Sans, 24f, 34f),
            // The initial in the profile's circle, as 6.7 sets it.
            "initial" to Role(Face.Sans, 22f, 28f, medium = true),
        )

    private val serif = setOf(R.font.newsreader_display, R.font.newsreader_subhead, R.font.newsreader_text, R.font.newsreader_text_italic)
    private val sans = setOf(R.font.instrument_sans_regular, R.font.instrument_sans_medium)

    /** Every role of [AromaTypography], found by reflection so that a new role is checked too. */
    private fun roles(): Map<String, TextStyle> {
        val type = AromaTypography(LightColors, LocaleList("de-DE"))
        return AromaTypography::class.java.declaredFields
            .filter { it.type == TextStyle::class.java }
            .associate { field ->
                field.isAccessible = true
                field.name to field.get(type) as TextStyle
            }
    }

    private fun FontFamily?.face(): Face? {
        val fonts = (this as? FontListFontFamily)?.fonts?.map { (it as ResourceFont).resId }?.toSet() ?: return null
        return when {
            fonts.all { it in serif } -> Face.Serif
            fonts.all { it in sans } -> Face.Sans
            else -> null
        }
    }

    @Test
    fun theRolesAreTheTable() {
        val roles = roles()
        assertEquals("Roles beyond or missing from the table", table.keys, roles.keys)
        roles.forEach { (name, style) ->
            val role = table.getValue(name)
            assertEquals("$name: face", role.face, style.fontFamily.face())
            assertEquals("$name: size", role.size, style.fontSize.value)
            assertEquals("$name: line height", role.lineHeight, style.lineHeight.value)
            assertEquals("$name: weight", if (role.medium) FontWeight.Medium else FontWeight.Normal, style.fontWeight)
            assertEquals("$name: italic", if (role.italic) FontStyle.Italic else FontStyle.Normal, style.fontStyle ?: FontStyle.Normal)
            assertEquals("$name: tabular figures", role.tabular, style.fontFeatureSettings?.contains(TABULAR) == true)
        }
    }

    @Test
    fun nothingIsTrackedOutOrTooSmall() {
        roles().forEach { (name, style) ->
            val tracking: TextUnit = style.letterSpacing
            assertTrue("$name is tracked out", tracking == TextUnit.Unspecified || tracking.value <= 0f)
            assertTrue("$name is smaller than 12 sp", style.fontSize.value >= MIN_SIZE)
        }
    }

    @Test
    fun everyRoleHyphenatesInTheAppsLanguage() {
        roles().forEach { (name, style) ->
            assertEquals("$name does not hyphenate", Hyphens.Auto, style.hyphens)
            assertEquals("$name has no language", LocaleList("de-DE"), style.localeList)
        }
    }

    private companion object {
        const val MIN_SIZE = 12f
    }
}
