package app.aroma.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.aroma.R
import app.aroma.data.update.UpdateMessage
import app.aroma.data.update.Updates
import kotlinx.coroutines.launch
import java.io.File

/**
 * Says what became of an update, and opens Android's installer on a download. Messages wait while
 * Aroma is in the background, so the installer never opens behind another app.
 */
@Composable
fun UpdateMessages(updates: Updates, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(updates, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            updates.messages.collect { message ->
                when (message) {
                    is UpdateMessage.Found -> {
                        launch {
                            val result =
                                snackbar.showSnackbar(
                                    message = resources.getString(R.string.update_found, message.update.version),
                                    actionLabel = resources.getString(R.string.settings_updates_install),
                                    duration = SnackbarDuration.Long,
                                )
                            if (result == SnackbarResult.ActionPerformed) updates.install(message.update)
                        }
                    }

                    is UpdateMessage.Install -> {
                        if (!openInstaller(context, message.apk)) snackbar.showSnackbar(resources.getString(R.string.update_no_installer))
                    }

                    is UpdateMessage.NotDownloaded -> {
                        launch {
                            val result =
                                snackbar.showSnackbar(
                                    message = resources.getString(R.string.update_not_downloaded, message.update.version),
                                    actionLabel = resources.getString(R.string.action_retry),
                                )
                            if (result == SnackbarResult.ActionPerformed) updates.install(message.update)
                        }
                    }

                    is UpdateMessage.NotAroma -> {
                        snackbar.showSnackbar(resources.getString(R.string.update_not_aroma))
                    }
                }
            }
        }
    }
}

/** Android's installer asks before it replaces the app, the first time also whether Aroma may install apps. */
private fun openInstaller(context: Context, apk: File): Boolean =
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
