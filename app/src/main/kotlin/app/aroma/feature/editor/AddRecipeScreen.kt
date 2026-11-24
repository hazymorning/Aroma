package app.aroma.feature.editor

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.data.AromaError
import app.aroma.ui.components.AromaAlertDialog
import app.aroma.ui.components.AromaFilterChip
import app.aroma.ui.components.CardActionPadding
import app.aroma.ui.components.CardGroup
import app.aroma.ui.components.DetailTopBar
import app.aroma.ui.components.DetailTopBarHeight
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.IconAction
import app.aroma.ui.components.InlineProgress
import app.aroma.ui.components.LabeledTextField
import app.aroma.ui.components.LargeTitle
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.QuietLink
import app.aroma.ui.components.Text
import app.aroma.ui.components.messageRes
import app.aroma.ui.components.titleRes
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.ContentEdge
import app.aroma.ui.theme.ContentMaxWidth
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Motion.Glide
import app.aroma.ui.theme.RowHeight
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.SectionSpace
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import java.io.File
import java.util.UUID

/**
 * The add screen's state. [input] is the one field: a link, the text of a recipe, or both.
 * [starting] covers the moment before an import exists; from then on [import] is the import the
 * screen follows, running or ended. [photos] of the recipe can be added where Mealie's AI reads
 * them ([photosAvailable]). [copied] is what the clipboard offers to paste.
 */
data class AddRecipeUiState(
    val input: String = "",
    val name: String = "",
    val aiAvailable: Boolean = false,
    val photosAvailable: Boolean = false,
    val photos: List<ImportPhoto> = emptyList(),
    val photosNote: PhotosNote? = null,
    val useAi: Boolean = true,
    val translate: Boolean = false,
    val copied: PasteOffer? = null,
    val starting: Boolean = false,
    val creating: Boolean = false,
    val import: ImportJob? = null,
    val createError: AromaError? = null,
) {
    /** AI is only offered when the server has a provider set up for the group; photos are always read with it. */
    val usesAi: Boolean get() = (aiAvailable && useAi) || photos.isNotEmpty()

    val isImporting: Boolean get() = starting || (import != null && import.outcome == null)

    val canImport: Boolean
        get() = !isImporting && !creating && photos.all { it.file != null } && (input.isNotBlank() || photos.isNotEmpty())

    val importError: AromaError? get() = (import?.outcome as? ImportOutcome.Failed)?.error

    /** What was copied, offered where it fills a gap in the field: a link where there is none, a text where there is none. */
    val pasteOffer: PasteOffer?
        get() {
            if (isImporting) return null
            val held = ImportInput.of(input)
            return copied?.takeIf { if (it == PasteOffer.Link) held.link == null else held.text.isEmpty() }
        }
}

/** A photo for an import, in the order it was added; [file] is null while Aroma scales it down. */
data class ImportPhoto(val file: File? = null, val id: String = UUID.randomUUID().toString())

/** As many photos as a recipe needs: a page or two, a card's front and back. */
const val MAX_IMPORT_PHOTOS = 4

enum class PasteOffer { Link, Text }

/** Why photos shared to Aroma are not all there: Mealie cannot read photos, or an import takes fewer. */
enum class PhotosNote { Unreadable, FirstOnly }

data class AddRecipeActions(
    val onBack: () -> Unit = {},
    val onInputChange: (String) -> Unit = {},
    val onPaste: () -> Unit = {},
    val onAddPhotos: (List<Uri>) -> Unit = {},
    val onRemovePhoto: (ImportPhoto) -> Unit = {},
    val onUseAiChange: (Boolean) -> Unit = {},
    val onTranslateChange: (Boolean) -> Unit = {},
    val onImport: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onCreate: () -> Unit = {},
)

/**
 * One field for a link or a recipe's text, the options of Mealie's AI as two chips, and the
 * import: everything else, photos and writing a recipe by hand, waits quietly below. While an
 * import runs, the button turns into its progress and the rest steps aside.
 */
@Composable
fun AddRecipeScreen(state: AddRecipeUiState, actions: AddRecipeActions, modifier: Modifier = Modifier) {
    // While the screen is watched, a sleeping phone would only hide the progress; Aroma keeps it lit.
    val view = LocalView.current
    val busy = state.isImporting || state.creating
    DisposableEffect(view, busy) {
        view.keepScreenOn = busy
        onDispose { view.keepScreenOn = false }
    }
    val title = stringResource(R.string.recipes_add)
    val scroll = rememberScrollState()
    var titleEnd by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val collapsed by remember { derivedStateOf { scroll.value > titleEnd } }
    val photoSources = rememberPhotoSources(MAX_IMPORT_PHOTOS - state.photos.size, actions.onAddPhotos)
    var naming by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scroll)
                .statusBarsPadding()
                .padding(top = DetailTopBarHeight)
                .navigationBarsPadding()
                .padding(bottom = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth(),
            ) {
                LargeTitle(title, Modifier.onSizeChanged { titleEnd = it.height })
                Spacer(Modifier.height(Space.l))
                ImportForm(state, actions, photoSources)
                AnimatedVisibility(visible = !state.isImporting, enter = Unfold, exit = Fold) {
                    OtherWays(state, photoSources, onWrite = { naming = true }, modifier = Modifier.padding(top = SectionSpace))
                }
            }
        }
        DetailTopBar(onBack = actions.onBack, title = title, collapsed = collapsed)
    }
    if (naming) {
        NameDialog(
            name = state.name,
            onNameChange = actions.onNameChange,
            onCreate = {
                naming = false
                actions.onCreate()
            },
            onDismiss = { naming = false },
        )
    }
}

@Composable
private fun ImportForm(state: AddRecipeUiState, actions: AddRecipeActions, photoSources: PhotoSources) {
    val importing = state.isImporting
    val job = state.import
    val editable = !importing && !state.creating
    val inputAlpha by animateFloatAsState(if (importing) 0.6f else 1f, tween(Motion.STATE, easing = LinearEasing), label = "importInput")
    Column {
        Column(Modifier.alpha(inputAlpha)) {
            LabeledTextField(
                value = state.input,
                onValueChange = actions.onInputChange,
                label = stringResource(R.string.add_input_label),
                placeholder = stringResource(R.string.add_input_hint),
                singleLine = false,
                enabled = editable,
                // Links are pasted more often than typed, and corrections would only break them.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.padding(horizontal = ScreenMargin),
            )
            PasteLink(state.pasteOffer, actions.onPaste)
            if (state.photos.isNotEmpty()) {
                PhotoStrip(
                    photos = state.photos,
                    editable = editable,
                    sources = photoSources,
                    onRemove = actions.onRemovePhoto,
                    modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m),
                )
            }
            state.photosNote?.takeIf { !importing }?.let { PhotosNoteText(it) }
            if (state.aiAvailable) {
                AnimatedVisibility(visible = !importing, enter = Unfold, exit = Fold) { AiOptions(state, actions) }
            }
        }
        Spacer(Modifier.height(Space.l))
        // The button turns into the progress of the import it started, in the same place and as large.
        Crossfade(importing, Modifier.padding(horizontal = ScreenMargin), tween(Motion.STATE), label = "importAction") { running ->
            if (running) {
                ImportProgress(job, Modifier.fillMaxWidth())
            } else {
                PrimaryButton(
                    text = stringResource(R.string.add_import),
                    onClick = actions.onImport,
                    enabled = state.canImport,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        val error = state.importError
        when {
            importing -> {
                // Photos are read with AI whatever the chip says, also in an import opened from its notification.
                val withAi = state.usesAi || job?.request?.photos?.isNotEmpty() == true
                Text(
                    text = stringResource(if (withAi) R.string.add_progress_ai_hint else R.string.add_progress_hint),
                    style = AromaType.Small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = ContentEdge, end = ContentEdge, top = Space.s),
                )
            }

            error != null -> {
                InlineError(
                    error,
                    Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m),
                    next = state.nextStep(error)?.let { stringResource(it) },
                )
            }
        }
    }
}

/**
 * The link that pastes what was copied, standing under the field it fills as the field's note
 * would, apart from the options below; it fades away once it is done.
 */
@Composable
private fun PasteLink(offer: PasteOffer?, onPaste: () -> Unit) {
    AnimatedContent(
        targetState = offer,
        transitionSpec = {
            fadeIn(tween(Motion.STATE)) togetherWith fadeOut(tween(Motion.PRESS)) using
                SizeTransform { _, _ -> tween(Motion.EXPAND, easing = Glide) }
        },
        contentAlignment = Alignment.TopStart,
        label = "pasteOffer",
    ) { shown ->
        if (shown != null) {
            QuietLink(
                text = stringResource(if (shown == PasteOffer.Link) R.string.add_paste_link else R.string.add_paste_text),
                onClick = onPaste,
                leadingIcon = R.drawable.ic_content_paste,
                modifier = Modifier.padding(horizontal = ContentEdge),
            )
        }
    }
}

/** Why photos shared to Aroma are not all there, set as a field's note is. */
@Composable
private fun PhotosNoteText(note: PhotosNote) {
    Text(
        text =
            when (note) {
                PhotosNote.Unreadable -> stringResource(R.string.add_photos_unreadable)
                PhotosNote.FirstOnly -> pluralStringResource(R.plurals.add_photos_first, MAX_IMPORT_PHOTOS, MAX_IMPORT_PHOTOS)
            },
        style = AromaType.Small,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .padding(start = ContentEdge, end = ContentEdge, top = Space.xs)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * Mealie's smarter reading and its translation, as chips that show the remembered choice and change
 * it with a tap. Photos are always read with AI, so while there are some, the chip for a link or a
 * text steps aside; translating only comes with AI.
 */
@Composable
private fun AiOptions(state: AddRecipeUiState, actions: AddRecipeActions) {
    FlowRow(
        Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        if (state.photos.isEmpty()) {
            AromaFilterChip(
                selected = state.useAi,
                label = stringResource(R.string.add_ai_title),
                onClick = { actions.onUseAiChange(!state.useAi) },
            )
        }
        AnimatedVisibility(visible = state.usesAi, enter = fadeIn(tween(Motion.STATE)), exit = fadeOut(tween(Motion.PRESS))) {
            AromaFilterChip(
                selected = state.translate,
                label = stringResource(R.string.add_ai_translate),
                onClick = { actions.onTranslateChange(!state.translate) },
            )
        }
    }
}

/** What to do about an import that found no recipe, where Mealie gave no reason of its own: read it with AI, or add its text. */
@StringRes
private fun AddRecipeUiState.nextStep(error: AromaError): Int? {
    if (error !is AromaError.ImportFailed || error.reason != AromaError.ImportFailed.Reason.NoRecipe || error.detail != null) return null
    val held = ImportInput.of(input)
    return when {
        aiAvailable && !useAi -> R.string.add_retry_with_ai
        held.link != null && held.text.isEmpty() -> R.string.add_retry_with_text
        else -> null
    }
}

/** The ways in without a link, quiet on a card below the import: photos for Mealie's AI, and writing the recipe by hand. */
@Composable
private fun OtherWays(state: AddRecipeUiState, photoSources: PhotoSources, onWrite: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        CardGroup(stringResource(R.string.add_other_ways)) {
            if (state.photosAvailable && state.photos.isEmpty()) {
                WayRow(
                    R.drawable.ic_add_photo_alternate,
                    stringResource(R.string.add_photos),
                    stringResource(R.string.add_photos_hint),
                    photoSources.pick,
                ) {
                    photoSources.take?.let { IconAction(R.drawable.ic_photo_camera, stringResource(R.string.add_photos_take), it) }
                }
                Hairline(Modifier.padding(start = WayTextEdge, end = CardPadding))
            }
            WayRow(
                R.drawable.ic_edit,
                stringResource(R.string.add_manual),
                stringResource(R.string.add_manual_hint),
                onWrite,
                enabled = !state.creating,
            ) {
                // While the recipe is created, a spinner stands at the row's end, where an action would.
                if (state.creating) Box(Modifier.size(TouchTarget), contentAlignment = Alignment.Center) { InlineProgress() }
            }
        }
        state.createError?.let {
            InlineError(it, Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m))
        }
    }
}

/** A row of the card of other ways: a glyph, what it does and a second line on what for, and an action at its end. */
@Composable
private fun WayRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight.twoLines)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = CardPadding, end = CardActionPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Glyph.m),
        )
        Spacer(Modifier.width(Space.m))
        Column(
            Modifier
                .weight(1f)
                .padding(end = WayTextEnd, top = Space.s, bottom = Space.s),
        ) {
            Text(title, style = AromaType.Body)
            Text(subtitle, style = AromaType.Small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** Where the text of a row of other ways starts in its card, after the glyph: where the hairline between them starts too. */
private val WayTextEdge = CardPadding + Glyph.m + Space.m

/** How far before the action at its end the text of such a row stops, so that without one it keeps the card's padding. */
private val WayTextEnd = CardPadding - CardActionPadding

/** A recipe written by hand starts with its name, asked for in a dialog; ingredients and steps follow in the editor. */
@Composable
private fun NameDialog(name: String, onNameChange: (String) -> Unit, onCreate: () -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    AromaAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.add_manual),
        confirmLabel = stringResource(R.string.add_create),
        onConfirm = onCreate,
        dismissLabel = stringResource(R.string.action_cancel),
        confirmEnabled = name.isNotBlank(),
        content = {
            LabeledTextField(
                value = name,
                onValueChange = onNameChange,
                label = stringResource(R.string.editor_name),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onCreate() }),
                fieldModifier = Modifier.focusRequester(focus),
            )
            LaunchedEffect(focus) { focus.requestFocus() }
        },
    )
}

private val Unfold = expandVertically(tween(Motion.EXPAND, easing = Glide)) + fadeIn(tween(Motion.EXPAND, easing = LinearEasing))
private val Fold = shrinkVertically(tween(Motion.EXPAND, easing = Glide)) + fadeOut(tween(Motion.STATE, easing = LinearEasing))

/**
 * An error set as text where it happened: a small glyph and title in the error colour, the reason
 * below, and [next], what to do about it, where the reason does not say.
 */
@Composable
internal fun InlineError(error: AromaError, modifier: Modifier = Modifier, next: String? = null) {
    Row(
        modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Icon(
            painterResource(R.drawable.ic_error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            // Centres the glyph on the title's 18 sp line.
            modifier =
                Modifier
                    .padding(top = ErrorGlyphNudge)
                    .size(ErrorGlyph),
        )
        Column {
            Text(stringResource(error.titleRes()), style = AromaType.Label, color = MaterialTheme.colorScheme.error)
            val reason = (error as? AromaError.ImportFailed)?.detail ?: stringResource(error.messageRes())
            Text(
                text = listOfNotNull(reason, next).joinToString(" "),
                style = AromaType.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val ErrorGlyph = 16.dp
private val ErrorGlyphNudge = 1.dp
