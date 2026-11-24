package app.aroma.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.ui.theme.AromaColors
import app.aroma.ui.theme.BoxGap
import app.aroma.ui.theme.BoxPadding
import app.aroma.ui.theme.DarkColors
import app.aroma.ui.theme.EndSpace
import app.aroma.ui.theme.GridColumnGap
import app.aroma.ui.theme.GridRowGap
import app.aroma.ui.theme.HeaderGap
import app.aroma.ui.theme.HeaderTop
import app.aroma.ui.theme.HighlightReach
import app.aroma.ui.theme.LightColors
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.SectionGap
import app.aroma.ui.theme.SectionTitleGap
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.SubtitleGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The rules of docs/design/AUFTRAG.md that can be read from the code: paper, ink and sage from the
 * theme and nothing else; text in Aroma's roles, never in capitals or tracked; spacing on the scale;
 * no outlines, no dividers but the separator, no floating buttons; controls from Aroma's components;
 * motion from Motion; and every pairing of words and ground legible.
 */
class DesignRulesTest {
    private val root = File("src/main/kotlin/app/aroma")
    private val resources = File("src/main/res")

    private fun files(vararg dirs: String) = dirs.flatMap { File(root, it).walk().filter { file -> file.extension == "kt" }.toList() }

    /** Every line in [dirs] that matches [pattern], except declarations of a named constant. */
    private fun violations(pattern: Regex, vararg dirs: String): List<String> =
        files(*dirs).flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//")
                val named = NamedConstant.containsMatchIn(code)
                if (!named && pattern.containsMatchIn(code)) "${file.relativeTo(root)}:${index + 1}: ${line.trim()}" else null
            }
        }

    private fun assertNone(rule: String, found: List<String>) = assertTrue("$rule\n" + found.joinToString("\n"), found.isEmpty())

    @Test
    fun screensSizeWithTokensOrNamedConstants() =
        assertNone(
            "Use Space, a token of Tokens.kt or a named constant instead of a bare size:",
            violations(Regex("""\b(?!0\.dp)\d+(\.\d+)?\.dp\b"""), "feature", "ui/navigation"),
        )

    @Test
    fun spacingKeepsToTheScale() {
        val scale = setOf(4, 8, 12, 16, 20, 24, 32, 40, 48, 64).map { it.dp }.toSet()
        val steps =
            Space::class.java.declaredMethods
                .filter { it.name.startsWith("getS") }
                .map { Dp(it.invoke(Space) as Float) }
        assertEquals("Space offers every step of the scale and no other", scale, steps.toSet())
        // The rows of the recipe grid are 28 dp apart, as the brief sets them; every other distance is on the scale.
        val tokens =
            listOf(
                PageMargin,
                BoxPadding,
                BoxGap,
                SectionGap,
                SectionTitleGap,
                HeaderTop,
                SubtitleGap,
                HeaderGap,
                HighlightReach,
                EndSpace,
                GridColumnGap,
            )
        assertNone("A spacing token off the scale:", tokens.filter { it !in scale }.map { it.toString() })
        assertEquals(28.dp, GridRowGap)
    }

    @Test
    fun coloursComeFromTheTheme() =
        assertNone(
            "Use a colour of AromaTheme.colors:",
            violations(Regex("""Color\(0x|Color\.(White|Black|Gray|LightGray|DarkGray)\b"""), "feature", "ui/navigation") +
                violations(Regex("""Color\(0x"""), "ui/components"),
        )

    @Test
    fun noWhiteGreyOrMintSurfaces() {
        val banned = setOf(0xFFFFFFFF, 0xFFEFEEEB, 0xFFD6E7DA).map { it.toInt() }
        listOf("light" to LightColors, "dark" to DarkColors).forEach { (scheme, colors) ->
            val found = colors.roles().filter { (_, color) -> color.toArgb() in banned }.map { it.first }
            assertNone("The $scheme palette has a banned surface:", found)
        }
        val hex = Regex("""#(FF)?(FFFFFF|EFEEEB|D6E7DA)\b""", RegexOption.IGNORE_CASE)
        val found =
            resources.walk().filter { it.extension == "xml" && it.parentFile.name.startsWith("values") }.flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (hex.containsMatchIn(line)) "${file.relativeTo(resources)}:${index + 1}: ${line.trim()}" else null
                }
            }
        assertNone("A banned colour in the resources:", found.toList())
    }

    @Test
    fun shapesComeFromTheTheme() =
        assertNone("Use AromaTheme.shapes or CircleShape:", violations(Regex("""RoundedCornerShape\("""), "feature", "ui/navigation"))

    @Test
    fun typeComesFromTypeKt() =
        assertNone(
            "Name the role in Type.kt instead of sizing text here:",
            violations(Regex("""fontSize\s*=|\b\d+(\.\d+)?\.(sp|em)\b"""), "feature", "ui/components", "ui/navigation"),
        )

    @Test
    fun screensSetTextInAromasRoles() =
        assertNone(
            "Use AromaTheme.type and AromaTheme.colors instead of Material's slots:",
            violations(Regex("""MaterialTheme\.(typography|colorScheme|shapes)"""), "feature", "ui/components", "ui/navigation"),
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

    @Test
    fun textIsNeverSetInCapitalsOrTracked() =
        assertNone(
            "Set text in sentence case, without letter spacing:",
            // An initial is a name's own capital, and an intent's action is no text; screens set none in capitals.
            violations(Regex("""\.uppercase\(|\.toUpperCase\("""), "feature") +
                violations(Regex("""letterSpacing\s*="""), "feature", "ui/components", "ui/navigation"),
        )

    @Test
    fun nothingIsOutlined() =
        assertNone(
            "Give a control a tonal fill instead of an outline:",
            violations(Regex("""\.border\(|BorderStroke\("""), "feature", "ui/components", "ui/navigation"),
        )

    @Test
    fun theSeparatorIsTheOnlyLine() =
        assertNone(
            "Lines stand only between the rows of a grouped list in a box, as Separator draws them:",
            violations(Regex("""\b(Horizontal|Vertical)?Divider\b"""), "feature", "ui", "widget"),
        )

    @Test
    fun nothingFloats() =
        assertNone("Aroma has no floating buttons:", violations(Regex("""FloatingActionButton"""), "feature", "ui", "widget"))

    @Test
    fun controlsComeFromAromasComponents() =
        assertNone(
            "Use Aroma's component instead of Material's:",
            violations(Regex("""^import androidx\.compose\.material3\.($MATERIAL_PRIMITIVES)$"""), "feature", "ui/navigation"),
        )

    @Test
    fun motionComesFromMotion() =
        assertNone("Use a duration of Motion:", violations(Regex("""tween\(\s*\d"""), "feature", "ui/components", "ui/navigation"))

    @Test
    fun listRowsMoveAlike() =
        assertNone("Move rows of a list with animateRow:", violations(Regex("""\.animateItem\("""), "feature", "ui/navigation"))

    @Test
    fun listsGlideInsteadOfGrowingInSteps() {
        val list = Regex("""\bLazy(Column|Row|ListScope)\b""")
        val growing = Regex("""\b(expandVertically|shrinkVertically|animateContentSize)\b""")
        val found =
            files("feature", "ui/components", "ui/navigation")
                .filter { file -> file.readText().let { list.containsMatchIn(it) && growing.containsMatchIn(it) } }
                .map { it.relativeTo(root).path }
        assertNone("Change a row's size at once and let the list glide around it, instead of growing it in steps:", found)
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

    /** The contrast of two colours as WCAG has it, from 1 to 21. */
    private fun contrast(a: Color, b: Color): Float {
        val (lighter, darker) = listOf(a.compositeOver(b).luminance(), b.luminance()).sortedDescending()
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /** Every colour role of a palette, by name. */
    private fun AromaColors.roles(): List<Pair<String, Color>> =
        listOf(
            "background" to background,
            "surface" to surface,
            "surfaceInBox" to surfaceInBox,
            "surfacePressed" to surfacePressed,
            "bar" to bar,
            "ink" to ink,
            "inkSecondary" to inkSecondary,
            "inkTertiary" to inkTertiary,
            "accent" to accent,
            "accentSoft" to accentSoft,
            "onInk" to onInk,
        )

    private companion object {
        /** WCAG's least contrast for text of a normal size. */
        const val MIN_CONTRAST = 4.5f

        /** `val Name = 56.dp`: a size with a name, which is what the rules ask for. */
        val NamedConstant = Regex("""^\s*(private |internal )?(const )?val [A-Z][A-Za-z0-9]*\s*(:\s*\w+\s*)?=""")

        const val MATERIAL_PRIMITIVES =
            "Button|OutlinedButton|TextButton|FilledTonalButton|ElevatedButton|IconButton|FilledIconButton|" +
                "Card|ElevatedCard|OutlinedCard|" +
                "AlertDialog|ModalBottomSheet|DropdownMenu|DropdownMenuItem|Switch|Checkbox|RadioButton|FilterChip|AssistChip|" +
                "SuggestionChip|InputChip|Snackbar|CircularProgressIndicator|LinearProgressIndicator|TextField|OutlinedTextField|" +
                "NavigationBar|NavigationBarItem|TopAppBar|LargeTopAppBar|CenterAlignedTopAppBar|Scaffold|Surface|Divider"
    }
}
