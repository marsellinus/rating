package com.ratig.app.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connectivity signal for the offline-first core. `true` means "a network
 * with validated INTERNET capability is available" - the conservative gate
 * used before any Supabase roundtrip and by the sync scheduler.
 */
interface NetworkMonitor {

    /** Cold-flow-safe current state; updates without polling. */
    val isOnline: StateFlow<Boolean>
}

/**
 * [ConnectivityManager]-backed implementation. Tracks each INTERNET-capable
 * network individually: losing one network while another remains does not
 * flip the state to offline. A network only counts as online once it reports
 * [NetworkCapabilities.NET_CAPABILITY_VALIDATED] (captive portals excluded).
 */
@Singleton
class ConnectivityManagerNetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) : NetworkMonitor {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isOnline = MutableStateFlow(initialOnline())

    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    /** Networks currently tracked with their latest validated capability. */
    private val validatedByNetwork = HashMap<Network, Boolean>()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(validatedByNetwork) {
                validatedByNetwork[network] = false
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            synchronized(validatedByNetwork) {
                validatedByNetwork[network] = validated
            }
            publish()
        }

        override fun onLost(network: Network) {
            synchronized(validatedByNetwork) {
                validatedByNetwork.remove(network)
            }
            publish()
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            connectivityManager.registerNetworkCallback(request, callback)
        }
    }

    private fun publish() {
        val anyValidated = synchronized(validatedByNetwork) { validatedByNetwork.values.any { it } }
        _isOnline.value = anyValidated
    }

    private fun initialOnline(): Boolean = runCatching {
        val network = connectivityManager.activeNetwork ?: return@runCatching false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@runCatching false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)
}
