package app.aroma.feature.editor

import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.data.AromaError
import app.aroma.ui.components.DetailTopBar
import app.aroma.ui.components.DetailTopBarHeight
import app.aroma.ui.components.Hairline
import app.aroma.ui.components.IconAction
import app.aroma.ui.components.InlineProgress
import app.aroma.ui.components.LabeledTextField
import app.aroma.ui.components.LargeTitle
import app.aroma.ui.components.Placeholder
import app.aroma.ui.components.PrimaryButton
import app.aroma.ui.components.QuietLink
import app.aroma.ui.components.SecondaryButton
import app.aroma.ui.components.SoftCard
import app.aroma.ui.components.SwitchRow
import app.aroma.ui.components.messageRes
import app.aroma.ui.components.titleRes
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.ContentMaxWidth
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Motion.Settle
import app.aroma.ui.theme.ScreenMargin
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import app.aroma.ui.theme.extras
import app.aroma.ui.theme.photoButtonShadow
import app.aroma.ui.theme.tabular
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The add screen's state. [starting] covers the moment before an import exists; from then on
 * [import] is the import the screen follows, running or ended. [photos] of the recipe can be
 * added where Mealie's AI reads them ([photosAvailable]).
 */
data class AddRecipeUiState(
    val url: String = "",
    val text: String = "",
    val name: String = "",
    val aiAvailable: Boolean = false,
    val photosAvailable: Boolean = false,
    val photos: List<ImportPhoto> = emptyList(),
    val useAi: Boolean = true,
    val translate: Boolean = false,
    val starting: Boolean = false,
    val creating: Boolean = false,
    val import: ImportJob? = null,
    val createError: AromaError? = null,
) {
    /** AI is only offered when the server has a provider set up for the group; photos are always read with it. */
    val usesAi: Boolean get() = (aiAvailable && useAi) || photos.isNotEmpty()

    val isImporting: Boolean get() = starting || (import != null && import.outcome == null)

    val canImport: Boolean
        get() =
            !isImporting && !creating && photos.all { it.file != null } &&
                (url.isNotBlank() || text.isNotBlank() || photos.isNotEmpty())

    val importError: AromaError? get() = (import?.outcome as? ImportOutcome.Failed)?.error
}

/** A photo for an import, in the order it was added; [file] is null while Aroma scales it down. */
data class ImportPhoto(val file: File? = null, val id: String = UUID.randomUUID().toString())

/** As many photos as a recipe needs: a page or two, a card's front and back. */
const val MAX_IMPORT_PHOTOS = 4

data class AddRecipeActions(
    val onBack: () -> Unit = {},
    val onUrlChange: (String) -> Unit = {},
    val onPaste: () -> Unit = {},
    val onTextChange: (String) -> Unit = {},
    val onAddPhotos: (List<Uri>) -> Unit = {},
    val onRemovePhoto: (ImportPhoto) -> Unit = {},
    val onUseAiChange: (Boolean) -> Unit = {},
    val onTranslateChange: (Boolean) -> Unit = {},
    val onImport: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onCreate: () -> Unit = {},
)

/** The tick or the spinner in front of an import step, as large as [InlineProgress]. */
private val StepMark = 20.dp

/** A chosen photo, smaller only where four do not fit beside each other, and the round button on its corner that takes it out again. */
private val PhotoSize = 72.dp
private val RemoveMark = 24.dp

/** How far the remove button's touch area reaches past the photo's corner, so that the mark sits [Space.xxs] inside it. */
private val RemoveReach = (TouchTarget - RemoveMark) / 2 - Space.xxs

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
                FromWebsite(state, actions)
                Spacer(Modifier.height(Space.xxl))
                WriteYourself(state, actions)
            }
        }
        DetailTopBar(onBack = actions.onBack, title = title, collapsed = collapsed)
    }
}

@Composable
private fun FromWebsite(state: AddRecipeUiState, actions: AddRecipeActions) {
    val importing = state.isImporting
    val editable = !importing && !state.creating
    val inputAlpha by animateFloatAsState(if (importing) 0.6f else 1f, tween(Motion.STATE, easing = LinearEasing), label = "importInputs")
    Column {
        SectionIntro(stringResource(R.string.add_from_web), stringResource(R.string.add_from_web_hint))
        Spacer(Modifier.height(Space.l))
        Column(Modifier.alpha(inputAlpha)) {
            LabeledTextField(
                value = state.url,
                onValueChange = actions.onUrlChange,
                label = stringResource(R.string.add_url_label),
                enabled = editable,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { actions.onImport() }),
                trailingIcon = {
                    IconAction(R.drawable.ic_content_paste, stringResource(R.string.add_paste), actions.onPaste, enabled = editable)
                },
                modifier = Modifier.padding(horizontal = ScreenMargin),
            )
            Spacer(Modifier.height(Space.m))
            LabeledTextField(
                value = state.text,
                onValueChange = actions.onTextChange,
                label = stringResource(R.string.add_text_label),
                placeholder = stringResource(R.string.add_text_hint),
                singleLine = false,
                minLines = 3,
                enabled = editable,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.padding(horizontal = ScreenMargin),
            )
            if (state.photosAvailable) {
                Photos(state.photos, editable, actions)
            } else if (state.aiAvailable) {
                Spacer(Modifier.height(Space.m))
            }
            if (state.aiAvailable) AiOptions(state, actions)
        }
        Spacer(Modifier.height(Space.l))
        // While an import runs, the button rests with its label; the card below is the one place that shows progress.
        PrimaryButton(
            text = stringResource(R.string.add_import),
            onClick = actions.onImport,
            enabled = state.canImport,
            modifier =
                Modifier
                    .padding(horizontal = ScreenMargin)
                    .fillMaxWidth(),
        )
        state.import?.takeIf { importing }?.let { job ->
            val usesAi = state.usesAi || job.request.photos.isNotEmpty()
            ImportProgress(
                title = stringResource(R.string.import_running),
                startedAt = job.startedAt,
                steps = job.steps,
                hint = stringResource(if (usesAi) R.string.add_progress_ai_hint else R.string.add_progress_hint),
                modifier = Modifier.padding(top = Space.m),
            )
        }
        state.importError?.let {
            InlineError(it, Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m))
        }
    }
}

/**
 * Photos of the recipe for Mealie's AI: the chosen ones as thumbnails, each with a button that
 * takes it out again, and two links that add more, from the gallery or the camera. They stand as
 * far from the field above and the card below as the fields from each other; the links' touch
 * area brings most of that room along.
 */
@Composable
private fun Photos(photos: List<ImportPhoto>, editable: Boolean, actions: AddRecipeActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val room = MAX_IMPORT_PHOTOS - photos.size
    val pickOne = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let { actions.onAddPhotos(listOf(it)) } }
    // Offers as many as there is room for; for the last one alone the single picker, as this one asks for two at least.
    val pickSome =
        rememberLauncherForActivityResult(remember(room) { PickMultipleVisualMedia(room.coerceAtLeast(2)) }) { uris ->
            if (uris.isNotEmpty()) actions.onAddPhotos(uris)
        }
    var taking by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera =
        rememberLauncherForActivityResult(TakePicture()) { taken ->
            val uri = taking ?: return@rememberLauncherForActivityResult
            taking = null
            if (taken) actions.onAddPhotos(listOf(uri)) else scope.launch(Dispatchers.IO) { forgetCameraPhoto(context, uri) }
        }
    val hasCamera = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    Column(Modifier.padding(top = if (photos.isEmpty()) Space.xxs else Space.m, bottom = if (room > 0) Space.xxs else Space.m)) {
        if (photos.isNotEmpty()) {
            Row(Modifier.padding(horizontal = ScreenMargin), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                photos.forEachIndexed { index, photo ->
                    key(photo.id) {
                        // On a narrow screen, four share the row rather than run off it.
                        PhotoThumbnail(photo, index + 1, editable, { actions.onRemovePhoto(photo) }, Modifier.weight(1f, fill = false))
                    }
                }
            }
        }
        if (room > 0) {
            Row(Modifier.padding(horizontal = ScreenMargin), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                QuietLink(
                    text = stringResource(R.string.add_photos_choose),
                    onClick = {
                        val request = PickVisualMediaRequest(PickVisualMedia.ImageOnly)
                        if (room > 1) pickSome.launch(request) else pickOne.launch(request)
                    },
                    leadingIcon = R.drawable.ic_add_photo_alternate,
                    enabled = editable,
                )
                if (hasCamera) {
                    QuietLink(
                        text = stringResource(R.string.add_photos_take),
                        onClick = {
                            scope.launch {
                                val uri = withContext(Dispatchers.IO) { newCameraPhoto(context) }
                                taking = uri
                                try {
                                    camera.launch(uri)
                                } catch (_: ActivityNotFoundException) {
                                    taking = null
                                }
                            }
                        },
                        leadingIcon = R.drawable.ic_photo_camera,
                        enabled = editable,
                    )
                }
            }
        }
    }
}

/** A chosen photo, a soft pulse while Aroma scales it down; while it may be taken out, with the round button for that. */
@Composable
private fun PhotoThumbnail(
    photo: ImportPhoto,
    number: Int,
    removable: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .widthIn(max = PhotoSize)
            .aspectRatio(1f),
    ) {
        if (photo.file == null) {
            Placeholder(Modifier.matchParentSize(), shape = MaterialTheme.shapes.small)
        } else {
            AsyncImage(
                model = photo.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .matchParentSize()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            if (removable) {
                RemovePhotoButton(
                    number,
                    onRemove,
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = RemoveReach, y = -RemoveReach),
                )
            }
        }
    }
}

/** A small paper disc with a cross, resting on the photo's corner like the larger buttons on photos; its target reaches past the photo. */
@Composable
private fun RemovePhotoButton(number: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.add_photos_remove, number)
    Box(
        modifier
            .size(TouchTarget)
            .clickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = RemoveMark / 2),
                role = Role.Button,
                onClick = onClick,
            ).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(RemoveMark)
                .photoButtonShadow(CircleShape)
                .background(extras.raised, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(Glyph.s),
            )
        }
    }
}

/**
 * Mealie's smarter reading, on a card of its own; translating unfolds below the switch. Photos are
 * always read with AI, so while there are some, the switch, which stands for a link or a text, steps aside.
 */
@Composable
private fun AiOptions(state: AddRecipeUiState, actions: AddRecipeActions) {
    SoftCard {
        AnimatedVisibility(visible = state.photos.isEmpty(), enter = Unfold, exit = Fold) {
            SwitchRow(
                title = stringResource(R.string.add_ai_title),
                subtitle = stringResource(R.string.add_ai_hint),
                checked = state.useAi,
                onChange = actions.onUseAiChange,
            )
        }
        AnimatedVisibility(visible = state.usesAi, enter = Unfold, exit = Fold) {
            Column {
                if (state.photos.isEmpty()) Hairline(Modifier.padding(horizontal = CardPadding))
                SwitchRow(
                    title = stringResource(R.string.add_ai_translate),
                    subtitle = null,
                    checked = state.translate,
                    onChange = actions.onTranslateChange,
                )
            }
        }
    }
}

private val Unfold = expandVertically(tween(Motion.EXPAND, easing = Settle)) + fadeIn(tween(Motion.EXPAND, easing = LinearEasing))
private val Fold = shrinkVertically(tween(Motion.EXPAND, easing = Settle)) + fadeOut(tween(Motion.STATE, easing = LinearEasing))

/**
 * How far an import or a rework has come, as a checklist on a card with the time it has taken under
 * its [title], so a slow one never looks like a frozen screen and never ends without a word.
 */
@Composable
internal fun ImportProgress(
    title: String,
    startedAt: Long,
    steps: List<ImportStep>,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val shown = steps.ifEmpty { listOf(ImportStep.Waiting) }
    Column(modifier) {
        SoftCard {
            Column(
                Modifier
                    .padding(vertical = Space.m)
                    .animateContentSize(tween(Motion.EXPAND, easing = Settle)),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Row(Modifier.padding(horizontal = CardPadding), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = AromaType.Label, modifier = Modifier.weight(1f))
                    Elapsed(startedAt)
                }
                shown.forEachIndexed { index, step ->
                    StepRow(step.label(resources), done = index < shown.lastIndex)
                }
            }
        }
        Text(
            text = hint,
            style = AromaType.Small,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.s),
        )
    }
}

@Composable
private fun StepRow(label: String, done: Boolean) {
    Row(Modifier.padding(horizontal = CardPadding), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(StepMark), contentAlignment = Alignment.Center) {
            if (done) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else {
                InlineProgress(color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(Space.s))
        Text(
            text = label,
            style = AromaType.Small,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            // The step under way is announced as it changes; finished ones and the clock are not.
            modifier = if (done) Modifier else Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** The time since [startedAt], in the format of a notification's clock. */
@Composable
private fun Elapsed(startedAt: Long) {
    val seconds by produceState(secondsSince(startedAt), startedAt) {
        while (true) {
            delay(1000 - (System.currentTimeMillis() - startedAt).mod(1000L))
            value = secondsSince(startedAt)
        }
    }
    Text(
        text = DateUtils.formatElapsedTime(seconds),
        style = AromaType.Label.tabular(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun secondsSince(startedAt: Long): Long = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)

/** What the checklist and the notification call a step; Mealie's own words come without its trailing dots. */
internal fun ImportStep.label(resources: Resources): String =
    when (this) {
        is ImportStep.Server -> message.trimEnd('.', '…', ' ')
        ImportStep.ReadingPost -> resources.getString(R.string.add_progress_reading_post)
        ImportStep.Waiting -> resources.getString(R.string.add_progress_waiting)
        ImportStep.Saving -> resources.getString(R.string.add_progress_saving)
        ImportStep.StillWorking -> resources.getString(R.string.add_progress_still_working)
        ImportStep.AddingPhoto -> resources.getString(R.string.add_progress_photo)
    }

@Composable
private fun WriteYourself(state: AddRecipeUiState, actions: AddRecipeActions) {
    val creating = state.creating
    Column {
        SectionIntro(stringResource(R.string.add_manual), stringResource(R.string.add_manual_hint))
        Spacer(Modifier.height(Space.l))
        LabeledTextField(
            value = state.name,
            onValueChange = actions.onNameChange,
            label = stringResource(R.string.editor_name),
            enabled = !creating && !state.isImporting,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { actions.onCreate() }),
            modifier = Modifier.padding(horizontal = ScreenMargin),
        )
        Spacer(Modifier.height(Space.m))
        Box(
            Modifier
                .padding(horizontal = ScreenMargin)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // While the recipe is created, a spinner takes the label's place inside the same outline.
            SecondaryButton(
                text = if (creating) "" else stringResource(R.string.add_create),
                onClick = { if (!creating) actions.onCreate() },
                enabled = state.name.isNotBlank() && !state.isImporting,
                modifier = Modifier.fillMaxWidth(),
            )
            if (creating) InlineProgress(color = MaterialTheme.colorScheme.onSurface)
        }
        state.createError?.let {
            InlineError(it, Modifier.padding(start = ScreenMargin, end = ScreenMargin, top = Space.m))
        }
    }
}

@Composable
private fun SectionIntro(title: String, body: String) {
    Column(Modifier.padding(horizontal = ScreenMargin)) {
        Text(title, style = AromaType.SectionTitle, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.xs))
        Text(body, style = AromaType.Small, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An error set as text where it happened: a small glyph and title in the error colour, the reason below. */
@Composable
internal fun InlineError(error: AromaError, modifier: Modifier = Modifier) {
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
            Text(
                text = (error as? AromaError.ImportFailed)?.detail ?: stringResource(error.messageRes()),
                style = AromaType.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val ErrorGlyph = 16.dp
private val ErrorGlyphNudge = 1.dp
