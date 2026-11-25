package app.aroma.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import app.aroma.ui.theme.AromaTypography
import app.aroma.ui.theme.DarkColors
import app.aroma.ui.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The rules of docs/DESIGN.md that can be read from the code: colours from the theme, text set with
 * Aroma's Text in roles that hyphenate, none of the pieces the design does without, and words and
 * meaningful shapes legible on every ground.
 */
class DesignRulesTest {
    private val root = File("src/main/kotlin/app/aroma")

    private fun files(vararg dirs: String) = dirs.flatMap { File(root, it).walk().filter { file -> file.extension == "kt" }.toList() }

    /** Every line of code in [dirs] that matches [pattern]. */
    private fun violations(pattern: Regex, vararg dirs: String): List<String> =
        files(*dirs).flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (pattern.containsMatchIn(line.substringBefore("//"))) "${file.relativeTo(root)}:${index + 1}: ${line.trim()}" else null
            }
        }

    private fun assertNone(rule: String, found: List<String>) = assertTrue("$rule\n" + found.joinToString("\n"), found.isEmpty())

    @Test
    fun coloursComeFromTheTheme() =
        assertNone(
            "Use a colour of AromaTheme.colors:",
            violations(Regex("""Color\(0x|Color\.(White|Black|Gray|LightGray|DarkGray)\b"""), "feature", "ui/navigation") +
                violations(Regex("""Color\(0x"""), "ui/components"),
        )

    @Test
    fun textBreaksThroughAromasText() =
        assertNone(
            "Set text with ui.components.Text, which breaks German words after their hyphens:",
            violations(Regex("""^import androidx\.compose\.(material3\.Text|foundation\.text\.BasicText)$"""), "feature", "ui/navigation") +
                files("ui/components").filter { it.name != "Text.kt" }.flatMap { file ->
                    file.readLines().filter { it == "import androidx.compose.material3.Text" }.map { "${file.name}: $it" }
                },
        )

    /** Every role of [AromaTypography], found by reflection so that a new role is checked too. */
    @Test
    fun everyRoleHyphenatesInTheAppsLanguage() {
        val type = AromaTypography(LightColors, LocaleList("de-DE"))
        AromaTypography::class.java.declaredFields.filter { it.type == TextStyle::class.java }.forEach { field ->
            field.isAccessible = true
            val style = field.get(type) as TextStyle
            assertEquals("${field.name} does not hyphenate", Hyphens.Auto, style.hyphens)
            assertEquals("${field.name} has no language", LocaleList("de-DE"), style.localeList)
        }
    }

    @Test
    fun noneOfThePiecesTheDesignDoesWithout() {
        val found =
            Bans.mapNotNull { ban ->
                violations(ban.pattern, *ban.dirs).takeIf { it.isNotEmpty() }?.joinToString("\n", "${ban.rule}\n")
            }
        assertTrue(found.joinToString("\n\n"), found.isEmpty())
    }

    @Test
    fun wordsReadOnEveryGround() {
        listOf("light" to LightColors, "dark" to DarkColors).forEach { (scheme, c) ->
            val pairs =
                listOf(
                    "ink on background" to (c.ink to c.background),
                    "ink on surface" to (c.ink to c.surface),
                    "ink on surfaceInBox" to (c.ink to c.surfaceInBox),
                    "ink on surfacePressed" to (c.ink to c.surfacePressed),
                    "ink on bar" to (c.ink to c.bar),
                    "ink on accentSoft" to (c.ink to c.accentSoft),
                    "inkSecondary on background" to (c.inkSecondary to c.background),
                    "inkSecondary on surface" to (c.inkSecondary to c.surface),
                    "inkSecondary on surfaceInBox" to (c.inkSecondary to c.surfaceInBox),
                    "inkSecondary on bar" to (c.inkSecondary to c.bar),
                    "accent on background" to (c.accent to c.background),
                    "accent on surface" to (c.accent to c.surface),
                    "accent on surfaceInBox" to (c.accent to c.surfaceInBox),
                    "accent on bar" to (c.accent to c.bar),
                    "onInk on ink" to (c.onInk to c.ink),
                    "onInk on accent" to (c.onInk to c.accent),
                    "error on background" to (c.error to c.background),
                    "error on surface" to (c.error to c.surface),
                    "error on bar" to (c.error to c.bar),
                )
            val weak =
                pairs.mapNotNull { (name, colors) ->
                    val ratio = contrast(colors.first, colors.second)
                    if (ratio < MIN_CONTRAST) "$scheme $name: ${"%.2f".format(ratio)}:1" else null
                }
            assertNone("Words must stand at least $MIN_CONTRAST:1 against their ground:", weak)
        }
    }

    @Test
    fun shapesThatMeanSomethingStandOut() {
        listOf("light" to LightColors, "dark" to DarkColors).forEach { (scheme, c) ->
            val pairs =
                listOf(
                    "open tick's ring on surface" to (c.inkTertiary to c.surface),
                    "open tick's ring on background" to (c.inkTertiary to c.background),
                    "open tick's ring on bar" to (c.inkTertiary to c.bar),
                    "done tick on surface" to (c.accent to c.surface),
                    "done tick on bar" to (c.accent to c.bar),
                    "switch's knob, off, on its track" to (c.inkTertiary to c.surfacePressed),
                    "switch's track, on, on surface" to (c.accent to c.surface),
                    "steps done on background" to (c.accent to c.background),
                    "glyphs in second ink on surface" to (c.inkSecondary to c.surface),
                    "glyphs in second ink on bar" to (c.inkSecondary to c.bar),
                    "today's circle on surface" to (c.ink to c.surface),
                    "ribbon on a light photo" to (c.accent to c.background),
                )
            val weak =
                pairs.mapNotNull { (name, colors) ->
                    val ratio = contrast(colors.first, colors.second)
                    if (ratio < MIN_SHAPE_CONTRAST) "$scheme $name: ${"%.2f".format(ratio)}:1" else null
                }
            assertNone("Icons and shapes that mean something must stand at least $MIN_SHAPE_CONTRAST:1 against their ground:", weak)
        }
    }

    /** The contrast of two colours as WCAG has it, from 1 to 21. */
    private fun contrast(a: Color, b: Color): Float {
        val (lighter, darker) = listOf(a.compositeOver(b).luminance(), b.luminance()).sortedDescending()
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /** A piece the design does without, as a line of code shows it, with what to use instead, and where the rule holds. */
    private class Ban(val rule: String, val pattern: Regex, vararg val dirs: String)

    private companion object {
        /** WCAG's least contrast for text of a normal size. */
        const val MIN_CONTRAST = 4.5f

        /** WCAG's least contrast for icons and shapes that carry meaning. */
        const val MIN_SHAPE_CONTRAST = 3f

        const val MATERIAL_PRIMITIVES =
            "Button|OutlinedButton|TextButton|FilledTonalButton|ElevatedButton|IconButton|FilledIconButton|" +
                "Card|ElevatedCard|OutlinedCard|" +
                "AlertDialog|ModalBottomSheet|DropdownMenu|DropdownMenuItem|Switch|Checkbox|RadioButton|FilterChip|AssistChip|" +
                "SuggestionChip|InputChip|Snackbar|CircularProgressIndicator|LinearProgressIndicator|TextField|OutlinedTextField|" +
                "NavigationBar|NavigationBarItem|TopAppBar|LargeTopAppBar|CenterAlignedTopAppBar|Scaffold|Surface|Divider"

        val Bans =
            listOf(
                Ban("Aroma has no floating buttons:", Regex("""FloatingActionButton"""), "feature", "ui", "widget"),
                Ban(
                    "Lines stand only between the rows of a grouped list in a box, as Separator draws them:",
                    Regex("""\b(Horizontal|Vertical)?Divider\b"""),
                    "feature",
                    "ui",
                    "widget",
                ),
                Ban(
                    "Give a control a tonal fill instead of an outline:",
                    Regex("""\.border\(|BorderStroke\("""),
                    "feature",
                    "ui/components",
                    "ui/navigation",
                ),
                // An initial is a name's own capital, and an intent's action is no text; screens set none in capitals.
                Ban("Set text in sentence case:", Regex("""\.uppercase\(|\.toUpperCase\("""), "feature"),
                Ban("Set text without letter spacing:", Regex("""letterSpacing\s*="""), "feature", "ui/components", "ui/navigation"),
                Ban("Use AromaTheme.shapes or CircleShape:", Regex("""RoundedCornerShape\("""), "feature", "ui/navigation"),
                Ban(
                    "Use Aroma's component instead of Material's:",
                    Regex("""^import androidx\.compose\.material3\.($MATERIAL_PRIMITIVES)$"""),
                    "feature",
                    "ui/navigation",
                ),
                Ban("Use a duration of Motion:", Regex("""tween\(\s*\d"""), "feature", "ui/components", "ui/navigation"),
                Ban("Move rows of a list with animateRow:", Regex("""\.animateItem\("""), "feature", "ui/navigation"),
            )
    }
}
