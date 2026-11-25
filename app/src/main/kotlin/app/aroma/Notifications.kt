package app.aroma

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Whether Aroma may post notifications: from Android 13 on, only once the user allowed it. Lint
 * takes a call named check…Permission before a notification is posted as the check it asks for.
 */
fun Context.checkNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
