package app.aroma.ui

import androidx.compose.ui.text.TextStyle
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.AromaTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The rules of docs/DESIGN.md that can be read from the code: one type scale of seven sizes, set
 * without capitals or tracking; nothing outlined on a page; sizes, colours, shapes, type and motion
 * from the theme; controls from Aroma's components.
 */
class DesignRulesTest {
    private val root = File("src/main/kotlin/app/aroma")

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
            "Use Space, ScreenMargin or a named constant instead of a bare size:",
            violations(Regex("""\b\d+(\.\d+)?\.dp\b"""), "feature"),
        )

    @Test
    fun coloursComeFromTheTheme() =
        assertNone("Use a colour of the theme:", violations(Regex("""Color\(0x"""), "feature", "ui/components", "ui/navigation"))

    @Test
    fun shapesComeFromTheTheme() =
        assertNone("Use MaterialTheme.shapes or CircleShape:", violations(Regex("""RoundedCornerShape\("""), "feature", "ui/navigation"))

    @Test
    fun typeComesFromTypeKt() =
        assertNone(
            "Name the style in Type.kt instead of sizing text here:",
            violations(Regex("""fontSize\s*="""), "feature", "ui/navigation"),
        )

    @Test
    fun screensSetTextInAromasRoles() =
        assertNone(
            "Use a role of AromaType instead of Material's type slots:",
            violations(Regex("""MaterialTheme\.typography"""), "feature", "ui/navigation"),
        )

    @Test
    fun textIsNeverSetInCapitalsOrTracked() =
        assertNone(
            "Set text in sentence case, without letter spacing:",
            violations(Regex("""\.uppercase\(|letterSpacing\s*="""), "feature") +
                violations(Regex("""letterSpacing\s*="""), "ui/components", "ui/navigation"),
        )

    @Test
    fun nothingOnAPageIsOutlined() =
        assertNone(
            "Group on a SoftCard or give a control the soft fill instead of an outline:",
            violations(Regex("""\.border\(|BorderStroke\("""), "feature"),
        )

    @Test
    fun theScaleHasSevenSizes() {
        // Every role in AromaType, found by reflection so a new role is checked too.
        val roles =
            AromaType::class.java.declaredFields
                .filter { it.type == TextStyle::class.java }
                .map { field ->
                    field.isAccessible = true
                    field.get(AromaType) as TextStyle
                }
        val slots =
            with(AromaTypography) {
                listOf(
                    displayLarge,
                    displayMedium,
                    displaySmall,
                    headlineLarge,
                    headlineMedium,
                    headlineSmall,
                    titleLarge,
                    titleMedium,
                    titleSmall,
                    bodyLarge,
                    bodyMedium,
                    bodySmall,
                    labelLarge,
                    labelMedium,
                    labelSmall,
                )
            }
        assertTrue("AromaType has no roles", roles.isNotEmpty())
        val sizes = (roles + slots).map { it.fontSize.value }.toSortedSet()
        assertEquals(sortedSetOf(12f, 14f, 16f, 18f, 22f, 24f, 28f), sizes)
    }

    private companion object {
        /** `val Name = 56.dp`: a size with a name, which is what the rules ask for. */
        val NamedConstant = Regex("""^\s*(private |internal )?(const )?val [A-Z][A-Za-z0-9]*\s*(:\s*\w+\s*)?=""")

        const val MATERIAL_PRIMITIVES =
            "Button|OutlinedButton|TextButton|IconButton|Card|AlertDialog|ModalBottomSheet|DropdownMenu|DropdownMenuItem|" +
                "Switch|FilterChip|Snackbar|CircularProgressIndicator|LinearProgressIndicator|TextField|OutlinedTextField"
    }
}
