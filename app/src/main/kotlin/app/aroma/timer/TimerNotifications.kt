package app.aroma.timer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.aroma.MainActivity
import app.aroma.R

object TimerNotifications {
    private const val CHANNEL = "timers"

    fun createChannel(context: Context) {
        val channel =
            NotificationChannel(CHANNEL, context.getString(R.string.timer_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.timer_channel_description)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
                enableVibration(true)
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showFinished(context: Context, id: Long, recipeName: String, label: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(context.getString(R.string.timer_finished_title))
                .setContentText(listOf(recipeName, label).filter { it.isNotBlank() }.joinToString(" · "))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
    }
}

/** Fires when a timer ends while Aroma is in the background. */
class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimerNotifications.showFinished(
            context,
            intent.getLongExtra(EXTRA_ID, 0),
            intent.getStringExtra(EXTRA_RECIPE).orEmpty(),
            intent.getStringExtra(EXTRA_LABEL).orEmpty(),
        )
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_RECIPE = "recipe"
        const val EXTRA_LABEL = "label"
    }
}
