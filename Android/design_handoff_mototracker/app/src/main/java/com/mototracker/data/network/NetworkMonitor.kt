package com.mototracker.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes whether the device has an active internet-capable network connection.
 *
 * Implementations must emit the current state immediately on collection, then emit
 * on every subsequent change. The flow must never complete while the subscriber is active.
 */
interface NetworkMonitor {
    /**
     * `true` when at least one network with [NetworkCapabilities.NET_CAPABILITY_INTERNET]
     * is available; `false` otherwise.
     */
    val isOnline: Flow<Boolean>
}

/**
 * Android implementation of [NetworkMonitor] backed by [ConnectivityManager.NetworkCallback].
 *
 * Real connectivity behaviour is on-device only (🔬); use a fake [NetworkMonitor] in unit tests.
 *
 * @param context Application context used to obtain the [ConnectivityManager].
 */
@Singleton
class AndroidNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkMonitor {

    override val isOnline: Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)

        fun hasInternet(): Boolean =
            cm.activeNetwork?.let { n ->
                cm.getNetworkCapabilities(n)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } ?: false

        trySend(hasInternet())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(hasInternet())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities,
            ) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
