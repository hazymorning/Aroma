package app.aroma.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import app.aroma.core.format.DetectedDuration
import app.aroma.core.format.DurationDetector
import app.aroma.core.format.Markdown
import app.aroma.core.format.StyledText

/**
 * Markdown text where durations such as "10 Minuten" become tappable timer links.
 * [onDuration] is null where timers make no sense, e.g. in descriptions.
 */
@Composable
fun rememberRichText(
    markdown: String,
    onDuration: ((DetectedDuration) -> Unit)? = null,
    linkColor: Color = MaterialTheme.colorScheme.primary,
): AnnotatedString {
    val linkStyles =
        TextLinkStyles(
            style = SpanStyle(color = linkColor, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline),
        )
    return remember(markdown, onDuration, linkStyles) {
        val styled = Markdown.parse(markdown)
        buildAnnotatedString {
            append(styled.text)
            styled.spans.forEach { span ->
                when (span) {
                    is StyledText.Span.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.Medium), span.start, span.end)
                    is StyledText.Span.Italic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), span.start, span.end)
                    is StyledText.Span.Link -> addLink(LinkAnnotation.Url(span.url, linkStyles), span.start, span.end)
                }
            }
            if (onDuration != null) {
                val links = styled.spans.filterIsInstance<StyledText.Span.Link>()
                DurationDetector
                    .detect(styled.text)
                    .filter { duration -> links.none { duration.start < it.end && it.start < duration.end } }
                    .forEach { duration ->
                        addLink(
                            LinkAnnotation.Clickable(tag = "timer", styles = linkStyles) { onDuration(duration) },
                            duration.start,
                            duration.end,
                        )
                    }
            }
        }
    }
}
