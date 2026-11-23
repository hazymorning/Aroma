package app.aroma.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.aroma.R
import app.aroma.core.model.ShoppingListInfo
import app.aroma.data.session.SessionState
import app.aroma.data.settings.ThemeMode
import app.aroma.ui.components.DetailPage
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.SoftCard
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.RowHeight
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Chooses the list a shopping widget shows: when it is reconfigured from the launcher, and when it
 * is added before Android 12, which always asks. Without a choice it shows the list open in the app.
 */
class ShoppingWidgetConfigActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        // Leaving without a choice keeps the widget as it was; before Android 12 it is then not added.
        setResult(RESULT_CANCELED, result)
        // Any app can start this screen; it only ever sets up one of Aroma's own shopping widgets.
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider
        if (provider != ComponentName(this, ShoppingWidgetReceiver::class.java)) {
            finish()
            return
        }
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(widgetId)
        val container = appContainer
        // The widget's choice is read first, so the screen opens with it already checked.
        lifecycleScope.launch {
            val chosen = getAppWidgetState(this@ShoppingWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)[ChosenList]
            setContent {
                val settings = container.appSettings.collectAsStateWithLifecycle().value ?: return@setContent
                val session by container.sessions.state.collectAsStateWithLifecycle()
                val lists by remember {
                    container.shopping
                        .lists()
                        .map { it.data }
                        .catch { emit(emptyList()) }
                }.collectAsStateWithLifecycle(emptyList())
                val dark =
                    when (settings.themeMode) {
                        ThemeMode.System -> isSystemInDarkTheme()
                        ThemeMode.Light -> false
                        ThemeMode.Dark -> true
                    }
                DisposableEffect(dark) {
                    val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose { }
                }
                AromaTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                    ListChoice(
                        lists = lists,
                        chosen = chosen,
                        signedIn = session is SessionState.SignedIn,
                        onChoose = { choose(glanceId, it, result) },
                        onBack = ::finish,
                    )
                }
            }
        }
    }

    private fun choose(glanceId: GlanceId, listId: String?, result: Intent) {
        lifecycleScope.launch {
            updateAppWidgetState(this@ShoppingWidgetConfigActivity, glanceId) { state ->
                if (listId == null) state.remove(ChosenList) else state[ChosenList] = listId
            }
            ShoppingWidget().update(this@ShoppingWidgetConfigActivity, glanceId)
            setResult(RESULT_OK, result)
            finish()
        }
    }
}

/** The list open in the app first, as it is the default, then every list of the household, on one card. */
@Composable
private fun ListChoice(
    lists: List<ShoppingListInfo>,
    chosen: String?,
    signedIn: Boolean,
    onChoose: (String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailPage(
        title = stringResource(R.string.widget_config_title),
        onBack = onBack,
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        Text(
            text = stringResource(if (signedIn) R.string.widget_config_hint else R.string.widget_config_signed_out),
            style = AromaType.Body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.xs),
        )
        SoftCard(
            Modifier
                .padding(top = Space.l)
                .selectableGroup(),
        ) {
            Choice(
                name = stringResource(R.string.widget_config_follow),
                hint = stringResource(R.string.widget_config_follow_hint),
                selected = chosen == null,
                onClick = { onChoose(null) },
            )
            lists.forEach { list ->
                Hairline(Modifier.padding(horizontal = CardPadding))
                Choice(name = list.name, hint = null, selected = chosen == list.id, onClick = { onChoose(list.id) })
            }
        }
    }
}

/** One choice on the card: its name, a quiet line about it, and the sage check once it is the widget's. */
@Composable
private fun Choice(name: String, hint: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (hint == null) RowHeight.oneLine else RowHeight.twoLines)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = CardPadding, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = AromaType.Body)
            if (hint != null) Text(hint, style = AromaType.Small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Spacer(Modifier.width(Space.m))
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Glyph.m),
            )
        }
    }
}
