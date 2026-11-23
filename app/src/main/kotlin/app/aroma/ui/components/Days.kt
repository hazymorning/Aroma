package app.aroma.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.aroma.R
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.RowHeight
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.extras
import app.aroma.ui.theme.tabular
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** "Monday", in the reader's language. */
fun weekday(date: LocalDate, locale: Locale): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

/**
 * The head of a day's card, in the plan and in the timeline alike: the weekday as a section title,
 * its date beside it ("Nov 23", with the year once it is not this one) and a sage "Today" on
 * [today]. The glyph of an [action] at its end ends on the card's content edge.
 */
@Composable
fun DayHeader(date: LocalDate, today: LocalDate, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val locale = currentLocale()
    val name = remember(date, locale) { weekday(date, locale) }
    val shortDate =
        remember(date, today.year, locale) {
            val skeleton = if (date.year == today.year) "MMMd" else "MMMdy"
            date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(RowHeight.oneLine)
                .padding(start = CardPadding, end = if (action != null) CardActionPadding else CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = AromaType.SectionTitle)
            Spacer(Modifier.width(Space.xs))
            Text(text = shortDate, style = AromaType.Small.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (date == today) {
                Spacer(Modifier.width(Space.s))
                TodayBadge()
            }
        }
        action?.invoke()
    }
}

/** "Today" on the sage wash, the one mark a day can carry. */
@Composable
private fun TodayBadge() {
    Text(
        text = stringResource(R.string.home_today),
        style = AromaType.Caption,
        color = extras.onSelected,
        modifier =
            Modifier
                .background(extras.selected, CircleShape)
                .padding(horizontal = Space.xs, vertical = Space.xxs / 2),
    )
}
