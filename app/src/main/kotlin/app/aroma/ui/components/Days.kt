package app.aroma.ui.components

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** "Monday", in the reader's language. */
fun weekday(date: LocalDate, locale: Locale): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
