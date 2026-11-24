package app.aroma.ui.components

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** "Monday", in the reader's language. */
fun weekday(date: LocalDate, locale: Locale): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

/** "Mo", "Di": a weekday's short name as calendars print it, without a full stop. */
fun weekdayShort(date: LocalDate, locale: Locale): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale).trimEnd('.')
