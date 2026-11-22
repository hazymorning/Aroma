package app.kochi.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kochi.KochiApplication
import app.kochi.data.session.SessionState
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

/** Sends queued shopping list changes once the device is online again. */
class ShoppingSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as KochiApplication).container
        if (container.sessions.state.first { it != SessionState.Loading } !is SessionState.SignedIn) return Result.success()
        return if (container.shopping.sync()) Result.success() else Result.retry()
    }

    companion object {
        fun enqueue(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<ShoppingSyncWorker>()
                    .setConstraints(online)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork("shopping-sync", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** Keeps a copy of every recipe on the device while offline mode is switched on. */
class OfflineRecipesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as KochiApplication).container
        if (container.sessions.state.first { it != SessionState.Loading } !is SessionState.SignedIn) return Result.success()
        return runCatching { container.recipes.downloadAll() }.fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        private const val NAME = "offline-recipes"

        fun schedule(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<OfflineRecipesWorker>(1, TimeUnit.DAYS).setConstraints(online).build()
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
