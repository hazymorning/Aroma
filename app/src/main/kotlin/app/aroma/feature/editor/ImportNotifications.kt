package app.aroma.feature.editor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import app.aroma.R
import app.aroma.data.AromaError
import app.aroma.data.settings.inAppLanguage
import app.aroma.ui.components.messageRes
import app.aroma.ui.components.titleRes
import app.aroma.ui.navigation.LaunchRequest
import java.util.UUID

/**
 * The notifications of recipe imports: one while an import runs, which also keeps it running
 * when Aroma is left, and one that tells how an import ended if its screen was not open to show it.
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

    fun foreground(context: Context, id: UUID, startedAt: Long): ForegroundInfo {
        val notification = running(context.inAppLanguage(), id, startedAt, step = null)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }
    }

    /** Shows the step [job] is at, for when its screen is not open. */
    fun running(context: Context, id: UUID, job: ImportJob) {
        val localized = context.inAppLanguage()
        notify(context, null, id.hashCode(), running(localized, id, job.startedAt, job.steps.lastOrNull()))
    }

    /**
     * Takes the running notification away and, unless the import's screen [seen] how it ended,
     * tells it in a notification of its own.
     */
    fun ended(context: Context, id: UUID, job: ImportJob, seen: Boolean) {
        NotificationManagerCompat.from(context).cancel(id.hashCode())
        if (seen) return
        val localized = context.inAppLanguage()
        val builder =
            NotificationCompat
                .Builder(localized, CHANNEL)
                .setSmallIcon(R.drawable.ic_recipes)
                .setAutoCancel(true)
        when (val outcome = job.outcome) {
            is ImportOutcome.Added -> {
                builder
                    .setContentTitle(localized.getString(R.string.import_added))
                    .setContentText(outcome.name)
                    .setContentIntent(open(context, id, LaunchRequest.Recipe(outcome.slug).intent(context)))
            }

            is ImportOutcome.Failed -> {
                val reason = outcome.error.let { (it as? AromaError.ImportFailed)?.detail ?: localized.getString(it.messageRes()) }
                builder
                    .setContentTitle(localized.getString(outcome.error.titleRes()))
                    .setContentText(reason)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
                    .setContentIntent(open(context, id, LaunchRequest.ShowImport(id).intent(context)))
            }

            null -> {
                return
            }
        }
        notify(context, id.toString(), ENDED, builder.build())
    }

    /** The screen showed how the import ended. */
    fun dismiss(context: Context, id: UUID) {
        NotificationManagerCompat.from(context).cancel(id.toString(), ENDED)
    }

    private fun running(context: Context, id: UUID, startedAt: Long, step: ImportStep?): Notification =
        NotificationCompat
            .Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_recipes)
            .setContentTitle(context.getString(R.string.import_running))
            .setContentText(step?.label(context.resources))
            .setProgress(0, 0, true)
            .setWhen(startedAt)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(open(context, id, LaunchRequest.ShowImport(id).intent(context)))
            .build()

    private fun open(context: Context, id: UUID, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun notify(context: Context, tag: String?, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(tag, id, notification)
    }
}
