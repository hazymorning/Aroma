package app.aroma.feature.editor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import app.aroma.R
import app.aroma.checkNotificationPermission
import app.aroma.data.AromaError
import app.aroma.data.settings.inAppLanguage
import app.aroma.ui.components.messageRes
import app.aroma.ui.components.titleRes
import app.aroma.ui.navigation.LaunchRequest
import java.util.UUID

/**
 * The notifications of recipe imports, and of reworks, which run the same way: one while it runs,
 * which also keeps it running when Aroma is left, and one that tells how it ended if its screen was
 * not open to show it.
 */
object ImportNotifications {
    private const val CHANNEL = "imports"

    /** Tag and id of an ended import's notification: the tag is the import's id. */
    private const val ENDED = 1

    fun createChannel(context: Context) {
        val channel =
            NotificationChannel(CHANNEL, context.getString(R.string.import_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.import_channel_description)
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun foreground(context: Context, id: UUID, startedAt: Long): ForegroundInfo =
        foreground(id, importing(context.inAppLanguage(), id, startedAt, step = null))

    /** The notification a rework runs under, which opens its recipe. */
    fun foreground(context: Context, job: ReworkJob): ForegroundInfo = foreground(job.id, reworking(context.inAppLanguage(), job))

    /** Shows the step [job] is at, for when its screen is not open. */
    fun running(context: Context, id: UUID, job: ImportJob) {
        notify(context, null, id.hashCode(), importing(context.inAppLanguage(), id, job.startedAt, job.steps.lastOrNull()))
    }

    /** Shows the step [job] is at, for when its recipe's page is not open. */
    fun running(context: Context, job: ReworkJob) {
        notify(context, null, job.id.hashCode(), reworking(context.inAppLanguage(), job))
    }

    /**
     * Takes the running notification away and, unless the import's screen [seen] how it ended,
     * tells it in a notification of its own.
     */
    fun ended(context: Context, id: UUID, job: ImportJob, seen: Boolean) {
        NotificationManagerCompat.from(context).cancel(id.hashCode())
        if (seen) return
        val localized = context.inAppLanguage()
        val notification =
            when (val outcome = job.outcome) {
                is ImportOutcome.Added -> {
                    val recipe = LaunchRequest.Recipe(outcome.slug).intent(context)
                    ended(localized, id, localized.getString(R.string.import_added), outcome.name, recipe)
                }

                is ImportOutcome.Failed -> {
                    val reason = outcome.error.let { (it as? AromaError.ImportFailed)?.detail ?: localized.getString(it.messageRes()) }
                    ended(
                        localized,
                        id,
                        localized.getString(outcome.error.titleRes()),
                        reason,
                        LaunchRequest.ShowImport(id).intent(context),
                    )
                }

                null -> {
                    return
                }
            }
        notify(context, id.toString(), ENDED, notification)
    }

    /** As for an import: tells how the rework ended unless its recipe's page [seen] it, and opens the recipe. */
    fun ended(context: Context, job: ReworkJob, seen: Boolean) {
        NotificationManagerCompat.from(context).cancel(job.id.hashCode())
        if (seen) return
        val localized = context.inAppLanguage()
        val notification =
            when (val outcome = job.outcome) {
                is ReworkOutcome.Reworked -> {
                    val recipe = LaunchRequest.Recipe(outcome.slug).intent(context)
                    ended(localized, job.id, localized.getString(R.string.recipe_reworked), job.request.name, recipe)
                }

                is ReworkOutcome.Failed -> {
                    val reason = outcome.error.reworkReason(localized.resources)
                    val recipe = LaunchRequest.Recipe(job.request.slug).intent(context)
                    ended(localized, job.id, localized.getString(R.string.recipe_rework_failed), reason, recipe)
                }

                null -> {
                    return
                }
            }
        notify(context, job.id.toString(), ENDED, notification)
    }

    /** The screen showed how the import or rework ended. */
    fun dismiss(context: Context, id: UUID) {
        NotificationManagerCompat.from(context).cancel(id.toString(), ENDED)
    }

    private fun foreground(id: UUID, notification: Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }

    private fun importing(context: Context, id: UUID, startedAt: Long, step: ImportStep?): Notification =
        running(context, id, context.getString(R.string.import_running), startedAt, step, LaunchRequest.ShowImport(id).intent(context))

    private fun reworking(context: Context, job: ReworkJob): Notification =
        running(
            context,
            job.id,
            context.getString(R.string.recipe_rework_running),
            job.startedAt,
            job.steps.lastOrNull(),
            LaunchRequest.Recipe(job.request.slug).intent(context),
        )

    private fun running(
        context: Context,
        id: UUID,
        title: String,
        startedAt: Long,
        step: ImportStep?,
        intent: Intent,
    ): Notification =
        NotificationCompat
            .Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_recipes)
            .setContentTitle(title)
            .setContentText(step?.label(context.resources))
            .setProgress(0, 0, true)
            .setWhen(startedAt)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(open(context, id, intent))
            .build()

    /** How an import or rework ended; a tap opens [intent]. */
    private fun ended(
        context: Context,
        id: UUID,
        title: String,
        text: String,
        intent: Intent,
    ): Notification =
        NotificationCompat
            .Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_recipes)
            .setAutoCancel(true)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(context, id, intent))
            .build()

    private fun open(context: Context, id: UUID, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun notify(context: Context, tag: String?, id: Int, notification: Notification) {
        if (!context.checkNotificationPermission()) return
        NotificationManagerCompat.from(context).notify(tag, id, notification)
    }
}
