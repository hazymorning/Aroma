package app.aroma

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider

/**
 * Text as it reads: without the zero-width spaces and word joiners Aroma's text adds for the line
 * breaker (see `breakable`), which show nothing and which TalkBack does not read.
 */
fun String.asRead(): String = filterNot { it == '​' || it == '⁠' }

/** Like `hasText`, for text as it reads. */
fun hasWords(text: String, substring: Boolean = false): SemanticsMatcher =
    SemanticsMatcher("has words '$text'") { node ->
        val shown =
            node.config.getOrNull(SemanticsProperties.Text).orEmpty() +
                listOfNotNull(node.config.getOrNull(SemanticsProperties.EditableText))
        shown.any { if (substring) it.text.asRead().contains(text) else it.text.asRead() == text }
    }

/** Like `onNodeWithText`, for text as it reads. */
fun SemanticsNodeInteractionsProvider.onNodeWithWords(
    text: String,
    substring: Boolean = false,
    useUnmergedTree: Boolean = false,
): SemanticsNodeInteraction = onNode(hasWords(text, substring), useUnmergedTree)

/** Like `onAllNodesWithText`, for text as it reads. */
fun SemanticsNodeInteractionsProvider.onAllNodesWithWords(
    text: String,
    substring: Boolean = false,
    useUnmergedTree: Boolean = false,
): SemanticsNodeInteractionCollection = onAllNodes(hasWords(text, substring), useUnmergedTree)
