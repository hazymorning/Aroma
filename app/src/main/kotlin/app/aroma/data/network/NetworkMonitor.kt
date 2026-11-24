package app.aroma.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Whether the phone has a network that may lead to the Mealie server. */
interface NetworkMonitor {
    /**
     * Whether the phone has a network that may lead to the internet. It says true again each time
     * another such network joins or Android finds that one reaches the internet: a server out of
     * reach before may answer through it, as at home or once a VPN is up.
     */
    val isOnline: Flow<Boolean>
}

/**
 * [NetworkMonitor] on what Android knows of the phone's networks, which it tells as they change:
 * nothing is polled. A network counts once it may lead to the internet, whether or not Android has
 * found that it does, as a Mealie server at home also answers on a network without the internet.
 */
class ConnectivityNetworkMonitor(private val context: Context) : NetworkMonitor {
    override val isOnline: Flow<Boolean> =
        callbackFlow {
            val manager = context.getSystemService<ConnectivityManager>()
            if (manager == null) {
                // Nothing to ask: Aroma tries the server and goes by its answers alone.
                trySend(true)
                close()
                return@callbackFlow
            }
            // Android calls back for every network that matches, on one thread, one call at a time.
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    private val networks = mutableSetOf<Network>()
                    private val validated = mutableSetOf<Network>()

                    override fun onAvailable(network: Network) {
                        networks += network
                        trySend(true)
                    }

                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && validated.add(network)) {
                            trySend(true)
                        }
                    }

                    override fun onLost(network: Network) {
                        networks -= network
                        validated -= network
                        trySend(networks.isNotEmpty())
                    }
                }
            val internet = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            manager.registerNetworkCallback(internet, callback)
            trySend(manager.hasInternet())
            awaitClose { manager.unregisterNetworkCallback(callback) }
        }.conflate()

    private fun ConnectivityManager.hasInternet(): Boolean =
        activeNetwork?.let(::getNetworkCapabilities)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
