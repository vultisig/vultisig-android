package com.vultisig.wallet.ui.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject


class NetworkUtils @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun observeConnectivityAsFlow(): Flow<Boolean> = context.observeConnectivityAsFlow()

    fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }


    fun Context.observeConnectivityAsFlow(): Flow<Boolean> = callbackFlow {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Thread-safe set for concurrent access from ConnectivityManager callbacks
        val validatedNetworks = ConcurrentHashMap.newKeySet<Network>()

        fun updateAndEmit() {
            trySend(validatedNetworks.isNotEmpty())
        }

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                try {
                    val caps = connectivityManager.getNetworkCapabilities(network)
                    if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) {
                        validatedNetworks.add(network)
                        updateAndEmit()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error in onAvailable")
                }
            }

            override fun onLost(network: Network) {
                try {
                    validatedNetworks.remove(network)
                    updateAndEmit()
                } catch (e: Exception) {
                    Timber.e(e, "Error in onLost")
                }
            }

            override fun onUnavailable() {
                // Network request could not be fulfilled
                // Don't modify state, just emit current state
                try {
                    updateAndEmit()
                } catch (t: Throwable) {
                    Timber.e(t,"Error on onUnavailable")
                }
            }

            override fun onCapabilitiesChanged(
                network: Network, networkCapabilities: NetworkCapabilities
            ) {
                try {
                    val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (isValidated) {
                        validatedNetworks.add(network)
                    } else {
                        validatedNetworks.remove(network)
                    }
                    updateAndEmit()
                } catch (e: Exception) {
                    Timber.e(e, "Error in onCapabilitiesChanged")
                }
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(
            networkRequest,
            networkCallback
        )

        // Send initial connectivity state by checking current active network
        try {
            val currentNetwork = connectivityManager.activeNetwork
            if (currentNetwork != null) {
                val capabilities = connectivityManager.getNetworkCapabilities(currentNetwork)
                if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) {
                    validatedNetworks.add(currentNetwork)
                }
            }
            updateAndEmit()
        } catch (e: Exception) {
            Timber.e(e, "Error checking initial connectivity")
            trySend(false)
        }

        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
}





