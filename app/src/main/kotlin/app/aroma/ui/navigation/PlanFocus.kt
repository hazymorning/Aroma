package app.aroma.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** A day the plan should open at, set where the plan is opened from, such as the start page's week, and cleared once it shows. */
@Stable
class PlanFocus {
    var day by mutableStateOf<LocalDate?>(null)
}
