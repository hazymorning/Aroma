package app.aroma.feature.editor

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.aroma.R
import app.aroma.data.AromaError
import app.aroma.ui.components.AromaAlertDialog
import app.aroma.ui.components.DetailPage
import app.aroma.ui.components.ErrorNote
import app.aroma.ui.components.FieldInset
import app.aroma.ui.components.GroupHead
import app.aroma.ui.components.IconButton
import app.aroma.ui.components.LabeledTextField
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.Segment
import app.aroma.ui.components.SegmentGap
import app.aroma.ui.components.SegmentInset
import app.aroma.ui.components.Separator
import app.aroma.ui.components.SettingsRow
import app.aroma.ui.components.SettingsTextEdge
import app.aroma.ui.components.Text
import app.aroma.ui.components.TextAction
import app.aroma.ui.components.TonalBox
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.PageMargin
import app.aroma.ui.theme.SectionGap
import app.aroma.ui.theme.SectionTitleGap
import app.aroma.ui.theme.Space
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
    val photoSources = rememberPhotoSources(MAX_IMPORT_PHOTOS - state.photos.size, actions.onAddPhotos)
    var naming by rememberSaveable { mutableStateOf(false) }
    DetailPage(title = stringResource(R.string.recipes_add), onBack = actions.onBack, modifier = modifier) {
        ImportForm(state, actions, photoSources)
        AnimatedVisibility(visible = !state.isImporting, enter = Unfold, exit = Fold) {
            OtherWays(state, photoSources, onWrite = { naming = true }, modifier = Modifier.padding(top = SectionGap))
        }
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
    val inputAlpha by animateFloatAsState(if (importing) RESTING else 1f, Motion.state(), label = "importInput")
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
                modifier = Modifier.padding(horizontal = PageMargin),
            )
            PasteLink(state.pasteOffer, actions.onPaste)
            if (state.photos.isNotEmpty()) {
                PhotoStrip(
                    photos = state.photos,
                    editable = editable,
                    sources = photoSources,
                    onRemove = actions.onRemovePhoto,
                    modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s16),
                )
            }
            state.photosNote?.takeIf { !importing }?.let { PhotosNoteText(it) }
            if (state.aiAvailable) {
                AnimatedVisibility(visible = !importing, enter = Unfold, exit = Fold) { AiOptions(state, actions) }
            }
        }
        Spacer(Modifier.height(Space.s24))
        // The button turns into the progress of the import it started, in the same place and as large.
        Crossfade(importing, Modifier.padding(horizontal = PageMargin), Motion.state(), label = "importAction") { running ->
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
                    style = AromaTheme.type.bodySmall,
                    color = AromaTheme.colors.inkSecondary,
                    modifier = Modifier.padding(start = NoteEdge, end = NoteEdge, top = Space.s12),
                )
            }

            error != null -> {
                ErrorNote(
                    error,
                    Modifier.padding(start = PageMargin, end = PageMargin, top = Space.s16),
                    hint = state.nextStep(error)?.let { stringResource(it) },
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
            fadeIn(Motion.state()) togetherWith fadeOut(Motion.edge()) using SizeTransform { _, _ -> Motion.state() }
        },
        contentAlignment = Alignment.TopStart,
        label = "pasteOffer",
    ) { shown ->
        if (shown != null) {
            TextAction(
                text = stringResource(if (shown == PasteOffer.Link) R.string.add_paste_link else R.string.add_paste_text),
                onClick = onPaste,
                icon = R.drawable.ic_content_paste,
                modifier = Modifier.padding(horizontal = NoteEdge),
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
        style = AromaTheme.type.bodySmall,
        color = AromaTheme.colors.inkSecondary,
        modifier =
            Modifier
                .padding(start = NoteEdge, end = NoteEdge, top = Space.s8)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * Mealie's smarter reading and its translation, as pills that show the remembered choice and change
 * it with a tap. Photos are always read with AI, so while there are some, the pill for a link or a
 * text steps aside; translating only comes with AI.
 */
@Composable
private fun AiOptions(state: AddRecipeUiState, actions: AddRecipeActions) {
    FlowRow(
        // The pills' words stand on the field's text edge; their tint reaches past it.
        Modifier.padding(start = PageMargin + FieldInset - SegmentInset, end = PageMargin, top = Space.s12),
        horizontalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        if (state.photos.isEmpty()) {
            Segment(
                text = stringResource(R.string.add_ai_title),
                selected = state.useAi,
                onClick = { actions.onUseAiChange(!state.useAi) },
            )
        }
        AnimatedVisibility(visible = state.usesAi, enter = fadeIn(Motion.state()), exit = fadeOut(Motion.edge())) {
            Segment(
                text = stringResource(R.string.add_ai_translate),
                selected = state.translate,
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

/** The ways in without a link, in a box below the import: photos for Mealie's AI, and writing the recipe by hand. */
@Composable
private fun OtherWays(state: AddRecipeUiState, photoSources: PhotoSources, onWrite: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = PageMargin)) {
        GroupHead(stringResource(R.string.add_other_ways), Modifier.padding(bottom = SectionTitleGap))
        TonalBox(contentPadding = PaddingValues()) {
            if (state.photosAvailable && state.photos.isEmpty()) {
                val take = photoSources.take
                SettingsRow(
                    title = stringResource(R.string.add_photos),
                    icon = R.drawable.ic_add_photo_alternate,
                    description = stringResource(R.string.add_photos_hint),
                    onClick = photoSources.pick,
                    trailing = take?.let { { IconButton(R.drawable.ic_photo_camera, stringResource(R.string.add_photos_take), it) } },
                )
                Separator(start = SettingsTextEdge)
            }
            // While the recipe is created, the row says so in place of its hint and takes no second tap.
            SettingsRow(
                title = stringResource(R.string.add_manual),
                icon = R.drawable.ic_edit,
                description = stringResource(if (state.creating) R.string.add_creating else R.string.add_manual_hint),
                onClick = if (state.creating) null else onWrite,
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            )
        }
        state.createError?.let { ErrorNote(it, Modifier.padding(top = Space.s16)) }
    }
}

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

private val Unfold = expandVertically(Motion.state()) + fadeIn(Motion.state())
private val Fold = shrinkVertically(Motion.state()) + fadeOut(Motion.state())

/** How far the field steps back while its import runs: still legible, no longer for typing. */
private const val RESTING = 0.6f

/** Where notes under the field start: on its text, not its edge. */
private val NoteEdge = PageMargin + FieldInset
