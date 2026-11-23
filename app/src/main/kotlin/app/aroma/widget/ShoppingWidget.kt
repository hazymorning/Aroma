package app.aroma.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import app.aroma.AppContainer
import app.aroma.R
import app.aroma.core.model.ShoppingItem
import app.aroma.core.model.ShoppingLabel
import app.aroma.core.model.ShoppingList
import app.aroma.data.attempt
import app.aroma.data.session.SessionState
import app.aroma.ui.navigation.LaunchRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.time.Duration.Companion.seconds

/** What the shopping widget shows. */
internal sealed interface ShoppingState {
    data object Loading : ShoppingState

    data object SignedOut : ShoppingState

    data object NoLists : ShoppingState

    /** Nothing is cached yet and the server could not be reached. */
    data object Unreachable : ShoppingState

    data class Ready(val list: ShoppingList) : ShoppingState
}

/** The list chosen in a widget's settings; without one, the widget shows the list open in the app. */
internal val ChosenList = stringPreferencesKey("list")

/** The shopping widget: a list's items by aisle, checked off where they are. */
class ShoppingWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_error) {
    override val sizeMode = SizeMode.Responsive(ShoppingLayout.sizes)
    override val previewSizeMode = SizeMode.Responsive(ShoppingLayout.sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val chosen = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[ChosenList]
        val firstLook = container.widgetLooks.first()
        val firstState = shoppingStates(container, chosen).firstShown(ShoppingState.Loading)
        provideContent {
            val look by container.widgetLooks.collectAsState(firstLook)
            val chosenNow = currentState(ChosenList)
            var retries by remember { mutableIntStateOf(0) }
            val state by remember(chosenNow, retries) { shoppingStates(container, chosenNow) }.collectAsState(firstState)
            // Items checked a moment ago stay where they were, struck through, until the checking stops.
            var lingering by remember { mutableStateOf(emptySet<String>()) }
            LaunchedEffect(lingering) {
                if (lingering.isEmpty()) return@LaunchedEffect
                delay(LINGER)
                lingering = emptySet()
            }
            WidgetTheme(look) {
                ShoppingContent(
                    state = state,
                    lingering = lingering,
                    onToggle = { item ->
                        lingering = if (item.checked) lingering - item.id else lingering + item.id
                        // In the app's scope: the change is saved and queued for Mealie even if this widget stops drawing.
                        container.scope.launch { attempt { container.shopping.toggle(item.listId, item.id) } }
                    },
                    onRetry = { retries++ },
                )
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val look = context.appContainer.widgetLooks.first()
        provideContent {
            WidgetTheme(look) {
                ShoppingContent(
                    ShoppingState.Ready(previewShoppingList(LocalContext.current)),
                    lingering = emptySet(),
                    onToggle = {},
                    onRetry = {},
                )
            }
        }
    }
}

class ShoppingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShoppingWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Widgets.keepFresh(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Widgets.keepFresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Widgets.removed(context)
    }
}

/** The widget's state as the session, the list it shows and that list change. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun shoppingStates(container: AppContainer, chosen: String?): Flow<ShoppingState> =
    container.sessions.state.flatMapLatest { session ->
        when (session) {
            SessionState.Loading -> {
                flowOf(ShoppingState.Loading)
            }

            SessionState.SignedOut -> {
                flowOf(ShoppingState.SignedOut)
            }

            is SessionState.SignedIn -> {
                combine(container.shopping.lists(), container.settings.settings) { lists, settings ->
                    pickList(lists.data, chosen, settings.lastShoppingList)?.id
                }.distinctUntilChanged()
                    .flatMapLatest { listId -> if (listId == null) flowOf(ShoppingState.NoLists) else listStates(container, listId) }
                    .catch { emit(ShoppingState.Unreachable) }
            }
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
private fun listStates(container: AppContainer, listId: String): Flow<ShoppingState> =
    container.shopping
        .observe(listId)
        .distinctUntilChanged()
        .transformLatest { list ->
            if (list != null) {
                emit(ShoppingState.Ready(list))
            } else {
                // Not opened on this phone yet: fetch it once; the cached copy then comes through above.
                emit(ShoppingState.Loading)
                attempt { container.shopping.refresh(listId) }.onFailure { emit(ShoppingState.Unreachable) }
            }
        }

/** The lists the shopping widgets on home screens show, for the refresh to fetch. */
internal suspend fun shownShoppingLists(context: Context, container: AppContainer): Set<String> {
    val widgets = GlanceAppWidgetManager(context).getGlanceIds(ShoppingWidget::class.java)
    if (widgets.isEmpty()) return emptySet()
    val lists =
        attempt {
            container.shopping
                .lists()
                .last()
                .data
        }.getOrElse { return emptySet() }
    val openInApp =
        container.settings.settings
            .first()
            .lastShoppingList
    return widgets.mapNotNullTo(mutableSetOf()) { id ->
        pickList(lists, getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[ChosenList], openInApp)?.id
    }
}

@Composable
internal fun ShoppingContent(state: ShoppingState, lingering: Set<String>, onToggle: (ShoppingItem) -> Unit, onRetry: () -> Unit) {
    val context = LocalContext.current
    val openList = actionStartActivity(LaunchRequest.Shopping.intent(context))
    when (state) {
        ShoppingState.Loading -> {
            WidgetSurface { LoadingOutline() }
        }

        ShoppingState.SignedOut -> {
            WidgetSurface(GlanceModifier.clickable(openList)) {
                WidgetMessage(
                    icon = R.drawable.ic_basket,
                    title = context.getString(R.string.widget_signed_out),
                    modifier = GlanceModifier.fillMaxSize(),
                    body = context.getString(R.string.widget_shopping_signed_out),
                    button = { FilledButton(context.getString(R.string.widget_sign_in), openList) },
                )
            }
        }

        ShoppingState.NoLists -> {
            WidgetSurface(GlanceModifier.clickable(openList)) {
                WidgetMessage(
                    icon = R.drawable.ic_basket,
                    title = context.getString(R.string.widget_no_lists_title),
                    modifier = GlanceModifier.fillMaxSize(),
                    body = context.getString(R.string.widget_no_lists_message),
                )
            }
        }

        ShoppingState.Unreachable -> {
            WidgetSurface {
                WidgetMessage(
                    icon = R.drawable.ic_cloud_off,
                    title = context.getString(R.string.widget_unreachable_title),
                    modifier = GlanceModifier.fillMaxSize(),
                    body = context.getString(R.string.widget_unreachable_message),
                    button = { FilledButton(context.getString(R.string.widget_retry), action(block = onRetry)) },
                )
            }
        }

        is ShoppingState.Ready -> {
            when (ShoppingLayout.at(LocalSize.current)) {
                ShoppingLayout.Count -> Count(state.list, openList)
                ShoppingLayout.Bar -> WidgetSurface { Header(state.list, openList, GlanceModifier.fillMaxSize()) }
                ShoppingLayout.List -> Items(state.list, lingering, aisles = false, openList, onToggle)
                ShoppingLayout.Aisles -> Items(state.list, lingering, aisles = true, openList, onToggle)
            }
        }
    }
}

/** One cell: how many items are left, or a check once there are none. */
@Composable
private fun Count(list: ShoppingList, openList: Action) {
    val context = LocalContext.current
    val left = list.items.count { !it.checked }
    val description = context.resources.getQuantityString(R.plurals.widget_items_left_on, left, left, list.name)
    WidgetSurface(GlanceModifier.clickable(openList).semantics { contentDescription = description }) {
        Column(
            GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (left == 0) {
                Glyph(R.drawable.ic_done_all, WidgetColors.accent)
            } else {
                Glyph(R.drawable.ic_basket, WidgetColors.accent)
                Text(
                    NumberFormat.getInstance(context.resources.configuration.locales[0]).format(left),
                    style = WidgetType.title.copy(fontSize = 20.sp),
                )
            }
        }
    }
}

/** The items to check off under a header; with [aisles], under the names of their aisles too. */
@Composable
private fun Items(
    list: ShoppingList,
    lingering: Set<String>,
    aisles: Boolean,
    openList: Action,
    onToggle: (ShoppingItem) -> Unit,
) {
    val context = LocalContext.current
    val rows = shoppingRows(list, lingering).filter { aisles || it is ShoppingRow.Item }
    WidgetSurface {
        Column(GlanceModifier.fillMaxSize()) {
            Header(list, openList)
            when {
                rows.isNotEmpty() -> {
                    LazyColumn(GlanceModifier.defaultWeight().padding(horizontal = WidgetPadding - ROW_INSET)) {
                        items(rows, itemId = { it.id }) { row ->
                            when (row) {
                                is ShoppingRow.Aisle -> AisleName(row.label)
                                is ShoppingRow.Item -> ItemRow(row.item, onToggle)
                            }
                        }
                    }
                }

                list.items.isEmpty() -> {
                    MessageLine(R.drawable.ic_basket, context.getString(R.string.widget_list_empty), GlanceModifier.defaultWeight())
                }

                else -> {
                    MessageLine(R.drawable.ic_done_all, context.getString(R.string.widget_list_done), GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

/** The list's name and how many items are left, which open the list in the app, and "+", which opens it to add. */
@Composable
private fun Header(list: ShoppingList, openList: Action, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val left = list.items.count { !it.checked }
    Row(modifier.fillMaxWidth().padding(start = WidgetPadding, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight().height(TargetSize).clickable(openList), verticalAlignment = Alignment.CenterVertically) {
            Text(list.name, style = WidgetType.title, maxLines = 1)
            if (left > 0) {
                Text(context.resources.getQuantityString(R.plurals.widget_items_left, left, left), style = WidgetType.caption, maxLines = 1)
            }
        }
        AddButton(context.getString(R.string.widget_add_item), openList)
    }
}

@Composable
private fun AisleName(label: ShoppingLabel?) {
    Text(
        text = label?.name ?: LocalContext.current.getString(R.string.widget_aisle_other),
        style = WidgetType.label.copy(color = WidgetColors.accent),
        maxLines = 1,
        modifier = GlanceModifier.fillMaxWidth().padding(start = ROW_INSET, end = ROW_INSET, top = 12.dp, bottom = 2.dp),
    )
}

/** An item and its check; the whole row takes the tap. A checked item is struck through while it lingers. */
@Composable
private fun ItemRow(item: ShoppingItem, onToggle: (ShoppingItem) -> Unit) {
    val context = LocalContext.current
    val text = item.text(context.resources.configuration.locales[0])
    val description = context.getString(if (item.checked) R.string.widget_item_checked else R.string.widget_item_open, text)
    Row(
        GlanceModifier
            .fillMaxWidth()
            .clickable { onToggle(item) }
            .semantics { contentDescription = description }
            .padding(horizontal = ROW_INSET, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Check(item.checked)
        Spacer(GlanceModifier.width(WidgetPadding))
        Text(
            text = text,
            style =
                if (item.checked) {
                    WidgetType.body.copy(
                        color = WidgetColors.softInk,
                        textDecoration = TextDecoration.LineThrough,
                    )
                } else {
                    WidgetType.body
                },
            maxLines = 2,
        )
    }
}

/** How long checked items stay in place after the last check, so a slip can be undone where it happened. */
private val LINGER = 3.seconds

/** How far a row's press reaches past the keyline its check and words stand on. */
private val ROW_INSET = 4.dp
