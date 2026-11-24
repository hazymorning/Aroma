package app.aroma.feature.editor

import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.aroma.R
import app.aroma.ui.components.Skeleton
import app.aroma.ui.theme.AromaTheme
import app.aroma.ui.theme.Glyph
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.TouchTarget
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where photos of a recipe come from: the gallery, and the camera where the phone has one. */
internal class PhotoSources(val pick: () -> Unit, val take: (() -> Unit)?)

/** Opens the gallery or the camera for as many photos as there is [room] for, and hands them to [onAdd]. */
@Composable
internal fun rememberPhotoSources(room: Int, onAdd: (List<Uri>) -> Unit): PhotoSources {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val add by rememberUpdatedState(onAdd)
    val pickOne = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let { add(listOf(it)) } }
    // Offers as many as there is room for; for the last one alone the single picker, as this one asks for two at least.
    val pickSome =
        rememberLauncherForActivityResult(remember(room) { PickMultipleVisualMedia(room.coerceAtLeast(2)) }) { uris ->
            if (uris.isNotEmpty()) add(uris)
        }
    var taking by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera =
        rememberLauncherForActivityResult(TakePicture()) { taken ->
            val uri = taking ?: return@rememberLauncherForActivityResult
            taking = null
            if (taken) add(listOf(uri)) else scope.launch(Dispatchers.IO) { forgetCameraPhoto(context, uri) }
        }
    val hasCamera = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    return remember(room, hasCamera) {
        PhotoSources(
            pick = {
                val request = PickVisualMediaRequest(PickVisualMedia.ImageOnly)
                if (room > 1) pickSome.launch(request) else pickOne.launch(request)
            },
            take =
                if (hasCamera) {
                    {
                        scope.launch {
                            val uri = withContext(Dispatchers.IO) { newCameraPhoto(context) }
                            taking = uri
                            try {
                                camera.launch(uri)
                            } catch (_: ActivityNotFoundException) {
                                taking = null
                            }
                        }
                    }
                } else {
                    null
                },
        )
    }
}

/**
 * The photos chosen for Mealie's AI, as thumbnails on the page's edge, each with a button that
 * takes it out again; while there is room for more, tiles of the same size add them from the
 * gallery or the camera.
 */
@Composable
internal fun PhotoStrip(
    photos: List<ImportPhoto>,
    editable: Boolean,
    sources: PhotoSources,
    onRemove: (ImportPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
        // On a narrow screen, photos and tiles share the row rather than run off it.
        photos.forEachIndexed { index, photo ->
            key(photo.id) {
                PhotoThumbnail(photo, index + 1, editable, { onRemove(photo) }, Modifier.weight(1f, fill = false))
            }
        }
        if (editable && photos.size < MAX_IMPORT_PHOTOS) {
            AddPhotoTile(
                R.drawable.ic_add_photo_alternate,
                stringResource(R.string.add_photos_choose),
                sources.pick,
                Modifier.weight(1f, fill = false),
            )
            sources.take?.let { take ->
                AddPhotoTile(R.drawable.ic_photo_camera, stringResource(R.string.add_photos_take), take, Modifier.weight(1f, fill = false))
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
            Skeleton(Modifier.matchParentSize(), shape = AromaTheme.shapes.thumbnail)
        } else {
            AsyncImage(
                model = photo.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .matchParentSize()
                        .clip(AromaTheme.shapes.thumbnail)
                        .background(AromaTheme.colors.surfacePressed),
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
                .background(AromaTheme.colors.bar, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = null,
                tint = AromaTheme.colors.ink,
                modifier = Modifier.size(Glyph.meta),
            )
        }
    }
}

/** A tile as large as a photo, on the soft fill of every control, that adds another one. */
@Composable
private fun AddPhotoTile(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .widthIn(max = PhotoSize)
            .aspectRatio(1f)
            .clip(AromaTheme.shapes.thumbnail)
            .background(AromaTheme.tonalFill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = AromaTheme.colors.inkSecondary,
            modifier = Modifier.size(Glyph.nav),
        )
    }
}

/** A chosen photo, smaller only where the row has no room for it, and the round button on its corner that takes it out again. */
private val PhotoSize = 72.dp
private val RemoveMark = 24.dp

/** How far the remove button's touch area reaches past the photo's corner, so that the mark sits [Space.s4] inside it. */
private val RemoveReach = (TouchTarget - RemoveMark) / 2 - Space.s4
