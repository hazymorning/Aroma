package app.kochi

import android.app.Application
import app.kochi.timer.TimerNotifications
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.launch

class KochiApplication :
    Application(),
    SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch { container.sessions.restore() }
        TimerNotifications.createChannel(this)
    }

    /** Images share the API's HTTP client and get a disk cache large enough for offline use. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.clients.httpClient })) }
            .diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve("images"))
                    .maxSizeBytes(300L * 1024 * 1024)
                    .build()
            }.build()
}
