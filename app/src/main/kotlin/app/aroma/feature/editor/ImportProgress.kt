package app.aroma.feature.editor

import android.content.res.Resources
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.aroma.R
import app.aroma.ui.components.InlineProgress
import app.aroma.ui.components.Text
import app.aroma.ui.theme.AromaType
import app.aroma.ui.theme.CardPadding
import app.aroma.ui.theme.ControlHeight
import app.aroma.ui.theme.Motion
import app.aroma.ui.theme.Space
import app.aroma.ui.theme.extras
import app.aroma.ui.theme.tabular
import kotlinx.coroutines.delay

/**
 * How far an import has come, in the place of the button that started it and as large: the step
 * under way beside a spinner, and the time it has taken, so a slow import never looks like a
 * frozen screen. [job] is null for the moment before the import exists.
 */
@Composable
internal fun ImportProgress(job: ImportJob?, modifier: Modifier = Modifier) =
    WorkProgress(job?.steps.orEmpty(), job?.startedAt, stringResource(R.string.import_running), modifier)

/**
 * The same for any work of Mealie's AI on the way, such as reworking a recipe: the step under way,
 * [waiting] until the first one is reported, and the time since [startedAt].
 */
@Composable
internal fun WorkProgress(steps: List<ImportStep>, startedAt: Long?, waiting: String, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    Row(
        modifier
            .height(ControlHeight)
            .clip(CircleShape)
            .background(extras.card)
            .padding(horizontal = CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InlineProgress(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Space.s))
        AnimatedContent(
            targetState = steps.lastOrNull()?.label(resources) ?: waiting,
            transitionSpec = { fadeIn(tween(Motion.STATE)) togetherWith fadeOut(tween(Motion.STATE)) },
            label = "importStep",
            modifier = Modifier.weight(1f),
        ) { step ->
            Text(
                text = step,
                style = AromaType.Label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // The step under way is announced as it changes; the clock is not.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (startedAt != null) {
            Spacer(Modifier.width(Space.s))
            Elapsed(startedAt)
        }
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

/** What the progress and the notification call a step; Mealie's own words come without its trailing dots. */
internal fun ImportStep.label(resources: Resources): String =
    when (this) {
        is ImportStep.Server -> message.trimEnd('.', '…', ' ')
        ImportStep.ReadingPost -> resources.getString(R.string.add_progress_reading_post)
        ImportStep.Waiting -> resources.getString(R.string.add_progress_waiting)
        ImportStep.Saving -> resources.getString(R.string.add_progress_saving)
        ImportStep.StillWorking -> resources.getString(R.string.add_progress_still_working)
        ImportStep.AddingPhoto -> resources.getString(R.string.add_progress_photo)
    }
