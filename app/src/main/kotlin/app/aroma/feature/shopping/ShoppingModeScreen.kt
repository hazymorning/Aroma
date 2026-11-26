package app.aroma.feature.shopping

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.core.model.ShoppingItem
import app.aroma.ui.components.AromaSnackbarHost
import app.aroma.ui.components.BottomActionBar
import app.aroma.ui.components.BoxPart
import app.aroma.ui.components.CheckCircle
import app.aroma.ui.components.EmptyState
import app.aroma.ui.components.GroupHead
import app.aroma.ui.components.IconButton
import app.aroma.ui.components.IconButtonReach
import app.aroma.ui.components.KeepScreenOn
import app.aroma.ui.components.OfflineNotice
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.RollingText
import app.aroma.ui.components.Separator
import app.aroma.ui.components.Text
import app.aroma.ui.components.TonalBoxPart
import app.aroma.ui.components.TopBarHeight
import app.aroma.ui.components.actionBarInset
import app.aroma.ui.components.currentLocale
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.BoxPadding
import app.aroma.ui.theme.EndSpace
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.Space

/**
 * Shopping mode: the list made for the shop, on a page of its own, read at arm's length with a
 * trolley in the other hand. The head stays: the way out, the list's name, and how much of it is in
 * the basket, in words and in a fine sage bar. Below it the aisles in the shop's order, each aisle's
 * name staying at the top while its rows go by. What there was to buy as shopping began stays where
 * it is: a row ticked off is struck through in its place, so nothing moves under the thumb. Rows are
 * large and the whole row is the target; nothing here removes or edits, and the screen stays on.
 * Once everything is in the basket, "Done" stands at the foot.
 */
@Composable
fun ShoppingModeScreen(
    state: ShoppingUiState,
    onToggle: (ShoppingItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    KeepScreenOn(true)
    val list = state.list
    // What there was to buy as shopping began, kept while it goes on, through rotation too.
    var session by rememberSaveable { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(list == null) {
        if (list != null && session == null) session = list.items.filterNot { it.checked }.map { it.id }
    }
    val aisles = remember(list, session) { session?.let { list?.shoppingAisles(it.toSet()) } }
    val shown = aisles?.flatMap { it.items }.orEmpty()
    val total = shown.size
    val inBasket = shown.count(state::isChecked)
    val allDone = aisles != null && inBasket == total
    val navigation = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val finished = allDone && total > 0
    Box(
        modifier
            .fillMaxSize()
            .background(AromaTheme.colors.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            ModeHead(name = list?.name.orEmpty(), inBasket = inBasket, total = total, ready = aisles != null, onClose = onClose)
            if (state.isOffline) OfflineNotice(Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s12))
            LazyColumn(
                state = rememberLazyListState(),
                contentPadding = PaddingValues(bottom = (if (finished) actionBarInset() else navigation) + EndSpace),
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                when {
                    aisles == null -> {}

                    total == 0 -> {
                        item(key = "empty") {
                            // Nothing was open as shopping began: an empty list, or one already in the basket.
                            val words = if (list?.items.isNullOrEmpty()) R.string.shopping_empty_title else R.string.shopping_all_in_basket
                            EmptyState(text = stringResource(words), modifier = Modifier.padding(top = Space.s24))
                        }
                    }

                    else -> {
                        modeAisles(aisles, state, onToggle)
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = finished,
            enter = fadeIn(Motion.edge()),
            exit = fadeOut(Motion.edge()),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomActionBar {
                PrimaryButton(stringResource(R.string.action_done), onClick = onClose, modifier = Modifier.weight(1f))
            }
        }
        // A message stands 8 dp above "Done" once it is there, as above any bar, else 16 dp above the system's navigation.
        AromaSnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (finished) actionBarInset() + Space.s8 else navigation + Space.s16),
        )
    }
}

/**
 * The aisles of shopping mode: each one's name stays at the top on the page's paper while its rows
 * scroll by, over a box of its rows.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.modeAisles(aisles: List<ShoppingSection>, state: ShoppingUiState, onToggle: (ShoppingItem) -> Unit) {
    aisles.forEach { aisle ->
        stickyHeader(key = "aisle-${aisle.label?.id ?: "none"}", contentType = "aisle") {
            GroupHead(
                text = aisle.label?.name ?: stringResource(R.string.shopping_other),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(AromaTheme.colors.background)
                        .padding(start = PageMargin, end = PageMargin, top = Space.s24, bottom = Space.s12),
            )
        }
        itemsIndexed(aisle.items, key = { _, item -> item.id }, contentType = { _, _ -> "item" }) { index, item ->
            TonalBoxPart(part = BoxPart.of(index, aisle.items.size), modifier = Modifier.padding(horizontal = PageMargin)) {
                if (index > 0) Separator(start = ModeTextEdge)
                ModeRow(item = item, checked = state.isChecked(item), onToggle = { onToggle(item) })
            }
        }
    }
}

/**
 * The head of shopping mode, which stays while the aisles scroll: the way out and the list's name in
 * the row of a collapsed head, then how much is in the basket, as words that roll as they count and
 * as a fine bar that fills with sage. Once everything is in, the words say so.
 */
@Composable
private fun ModeHead(
    name: String,
    inBasket: Int,
    total: Int,
    ready: Boolean,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(TopBarHeight)
                .padding(start = PageMargin - IconButtonReach, end = PageMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(R.drawable.ic_close, stringResource(R.string.shopping_mode_off), onClose)
            Spacer(Modifier.width(Space.s4))
            Text(name, style = AromaTheme.type.barTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        if (ready && total > 0) {
            val done = inBasket == total
            AnimatedContent(
                targetState = done,
                transitionSpec = { fadeIn(Motion.edge()) togetherWith fadeOut(Motion.edge()) },
                label = "basket",
                modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s4),
            ) { all ->
                if (all) {
                    Text(stringResource(R.string.shopping_all_in_basket), style = AromaTheme.type.subtitle)
                } else {
                    RollingText(stringResource(R.string.shopping_in_basket, inBasket, total), style = AromaTheme.type.subtitle)
                }
            }
            BasketBar(share = inBasket / total.toFloat())
        }
    }
}

/** How far the shopping has come: a fine round bar on the page's edges that fills with sage. */
@Composable
private fun BasketBar(share: Float) {
    val filled by animateFloatAsState(share, Motion.state(), label = "basketBar")
    val colors = AromaTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = PageMargin, end = PageMargin, top = Space.s12)
            .height(BasketBarHeight)
            .clip(CircleShape)
            .background(colors.surfacePressed)
            .drawBehind { drawRect(colors.accent, size = size.copy(width = size.width * filled)) },
    )
}

private val BasketBarHeight = 3.dp

/**
 * One item in shopping mode: a large tick and the item in cook mode's large type, the recipes it is
 * for under it. The whole row ticks it off and back; it strikes through where it stands.
 */
@Composable
private fun ModeRow(item: ShoppingItem, checked: Boolean, onToggle: () -> Unit) {
    val colors = AromaTheme.colors
    val locale = currentLocale()
    val line = remember(item, locale) { itemLine(item, locale) }
    val amount by animateColorAsState(if (checked) colors.inkTertiary else colors.accent, Motion.state(), label = "modeAmount")
    val ink by animateColorAsState(if (checked) colors.inkTertiary else colors.ink, Motion.state(), label = "modeInk")
    val text = remember(line, amount) { itemText(line, amount) }
    val strike = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) { strike.animateTo(if (checked) 1f else 0f, Motion.state()) }
    val haptics = LocalHapticFeedback.current
    val stateLabel = stringResource(if (checked) R.string.shopping_checked else R.string.shopping_unchecked)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Checkbox) {
                    haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onToggle()
                }.semantics(mergeDescendants = true) {
                    toggleableState = ToggleableState(checked)
                    stateDescription = stateLabel
                }.heightIn(min = ModeRowMin)
                .padding(horizontal = BoxPadding, vertical = Space.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(checked, size = ModeCheckSize)
        Spacer(Modifier.width(Space.s12))
        Column(Modifier.weight(1f)) {
            StruckText(text = text, style = AromaTheme.type.cookStep, color = ink, progress = { strike.value })
            if (item.recipeNames.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.shopping_for_recipe, item.recipeNames.joinToString(", ")),
                    style = AromaTheme.type.bodySmall,
                    color = if (checked) colors.inkTertiary else colors.inkSecondary,
                )
            }
        }
    }
}

/** The tick of shopping mode, large enough to find at a glance. */
private val ModeCheckSize: Dp = 32.dp

/** A row of shopping mode at least as tall as a large target. */
private val ModeRowMin: Dp = 64.dp

/** Where an item's text starts in shopping mode, and the separators with it. */
private val ModeTextEdge: Dp = BoxPadding + ModeCheckSize + Space.s12
