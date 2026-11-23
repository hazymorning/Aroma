package app.aroma.feature.mealplan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.ImageSize
import app.aroma.core.model.MealPlanEntry
import app.aroma.data.AromaError
import app.aroma.ui.components.ErrorState
import app.aroma.ui.components.LocalBottomBarInset
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PageEndSpace
import app.aroma.ui.components.Placeholder
import app.aroma.ui.components.RecipeImage
import app.aroma.ui.components.currentLocale
import app.aroma.ui.components.mealTypeLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class MealPlanDay(val date: LocalDate, val entries: List<MealPlanEntry>)

data class MealPlanUiState(
    val today: LocalDate = LocalDate.now(),
    val weekStart: LocalDate = today,
    val days: List<MealPlanDay> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val error: AromaError? = null,
)

data class MealPlanActions(
    val onPreviousWeek: () -> Unit = {},
    val onNextWeek: () -> Unit = {},
    val onToday: () -> Unit = {},
    val onAdd: (LocalDate) -> Unit = {},
    val onOpenEntry: (MealPlanEntry) -> Unit = {},
    val onDeleteEntry: (MealPlanEntry) -> Unit = {},
    val onShopEntry: (MealPlanEntry) -> Unit = {},
    val onRefresh: () -> Unit = {},
)

@Composable
fun MealPlanScreen(state: MealPlanUiState, actions: MealPlanActions, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomBarInset.current + PageEndSpace), modifier = Modifier.fillMaxSize()) {
            item(key = "header") { Header(state, actions, locale) }
            when {
                state.isLoading -> {
                    item(key = "loading") { Loading() }
                }

                state.error != null && state.days.all { it.entries.isEmpty() } -> {
                    item(key = "error") {
                        ErrorState(state.error, onRetry = actions.onRefresh, modifier = Modifier.fillMaxWidth())
                    }
                }

                else -> {
                    items(state.days, key = { it.date.toString() }) { day ->
                        DaySection(day, isToday = day.date == state.today, locale = locale, actions = actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: MealPlanUiState, actions: MealPlanActions, locale: Locale) {
    val weekEnd = state.weekStart.plusDays(6)
    val range = remember(state.weekStart, locale) { formatRange(state.weekStart, weekEnd, locale) }
    Column(
        Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 12.dp, top = 20.dp),
    ) {
        Text(stringResource(R.string.nav_meal_plan), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onPreviousWeek) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.plan_previous_week))
            }
            Text(range, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
            IconButton(onClick = actions.onNextWeek) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.plan_next_week))
            }
            Spacer(Modifier.weight(1f))
            val showsToday = state.today >= state.weekStart && state.today <= weekEnd
            if (!showsToday) {
                TextButton(onClick = actions.onToday) { Text(stringResource(R.string.plan_this_week)) }
            }
        }
        if (state.isOffline) OfflineNotice(Modifier.padding(start = 8.dp, bottom = 4.dp))
    }
}

/** "23. – 29. November" in German, "Nov 23 – 29" in English, with the month repeated across months. */
internal fun formatRange(start: LocalDate, end: LocalDate, locale: Locale): String {
    val german = locale.language == "de"
    val sameMonth = start.month == end.month
    val startPattern =
        when {
            german && sameMonth -> "d."
            german -> "d. MMM"
            else -> "MMM d"
        }
    val endPattern =
        when {
            german && sameMonth -> "d. MMMM"
            german -> "d. MMM"
            sameMonth -> "d"
            else -> "MMM d"
        }
    val from = start.format(DateTimeFormatter.ofPattern(startPattern, locale))
    val to = end.format(DateTimeFormatter.ofPattern(endPattern, locale))
    return "$from – $to"
}

@Composable
private fun DaySection(day: MealPlanDay, isToday: Boolean, locale: Locale, actions: MealPlanActions) {
    val weekday = day.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    val date = day.date.format(DateTimeFormatter.ofPattern(if (locale.language == "de") "d. MMMM" else "MMMM d", locale))
    Column(Modifier.padding(top = 20.dp)) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { heading() },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(weekday, style = MaterialTheme.typography.headlineSmall)
                    if (isToday) {
                        Spacer(Modifier.width(10.dp))
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                            Text(
                                text = stringResource(R.string.home_today),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalIconButton(onClick = { actions.onAdd(day.date) }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.plan_add_for, weekday))
            }
        }
        if (day.entries.isEmpty()) {
            Text(
                text = stringResource(R.string.plan_day_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 20.dp, top = 6.dp),
            )
        } else {
            Column(Modifier.padding(top = 8.dp)) {
                day.entries.forEach { entry -> EntryRow(entry, actions) }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: MealPlanEntry, actions: MealPlanActions) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { actions.onOpenEntry(entry) }
                .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.recipe != null) {
            RecipeImage(
                image = entry.recipe.image,
                size = ImageSize.Thumbnail,
                shape = MaterialTheme.shapes.medium,
                fallbackIconSize = 22.dp,
                modifier = Modifier.size(64.dp),
            )
        } else {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_notes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = mealTypeLabel(entry.type),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = entry.recipe?.name ?: entry.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.recipe == null && entry.note.isNotBlank()) {
                Text(
                    entry.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(painterResource(R.drawable.ic_more_horiz), contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (entry.recipe != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recipe_add_to_list)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_basket), contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            actions.onShopEntry(entry)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.plan_remove)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        actions.onDeleteEntry(entry)
                    },
                )
            }
        }
    }
}

@Composable
private fun Loading() {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(4) {
            Placeholder(
                Modifier
                    .width(160.dp)
                    .height(24.dp),
            )
            Placeholder(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            )
        }
    }
}
