package app.aroma.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.aroma.ui.components.AromaSwitch
import app.aroma.ui.components.SettingsRow
import app.aroma.ui.theme.BoxPadding
import app.aroma.ui.theme.PageMargin

/**
 * A setting that is on or off. The switch stands beside the title and the [description], centred
 * on them, so a description of several lines wraps before the switch instead of running under it.
 * The whole row takes the tap. [onSheet], the words start on the page's margin instead of a box's
 * padding, and the press still reaches across the sheet.
 */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    description: String? = null,
    onSheet: Boolean = false,
) {
    SettingsRow(
        title = title,
        icon = icon,
        description = description,
        modifier =
            modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = if (onSheet) PageMargin - BoxPadding else 0.dp),
        trailing = { AromaSwitch(checked = checked, onCheckedChange = null) },
    )
}
