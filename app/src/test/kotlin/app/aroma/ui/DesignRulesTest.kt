package app.aroma.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The rules of docs/DESIGN.md that can be read from the code: screens take their sizes, colours,
 * shapes, type and motion from the theme, and their controls from Aroma's components.
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
    fun screensUseAromasComponents() =
        assertNone(
            "Use the Aroma component instead of Material's:",
            violations(Regex("""import androidx\.compose\.material3\.($MATERIAL_PRIMITIVES)$"""), "feature", "ui/navigation"),
        )

    @Test
    fun motionComesFromMotion() =
        assertNone("Use a duration of Motion:", violations(Regex("""tween\(\s*\d"""), "feature", "ui/components", "ui/navigation"))

    private companion object {
        /** `val Name = 56.dp`: a size with a name, which is what the rules ask for. */
        val NamedConstant = Regex("""^\s*(private |internal )?(const )?val [A-Z][A-Za-z0-9]*\s*(:\s*\w+\s*)?=""")

        const val MATERIAL_PRIMITIVES =
            "Button|OutlinedButton|TextButton|IconButton|Card|AlertDialog|ModalBottomSheet|DropdownMenu|DropdownMenuItem|" +
                "Switch|FilterChip|Snackbar|CircularProgressIndicator|LinearProgressIndicator|TextField|OutlinedTextField"
    }
}
