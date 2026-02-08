package com.vultisig.wallet.data.networkutils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

class NetworkStateInterceptor @Inject constructor(
    private val networkStateManager: NetworkStateManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        networkStateManager.updateState(NetworkState.Loading)
        return try {
            val response = chain.proceed(request)
            networkStateManager.updateState(NetworkState.Success(url, response.code))
            response
        } catch (e: Exception) {
            networkStateManager.updateState(NetworkState.Error(url, e))
            Timber.e(e)
            throw e
        }
    }
}

@Singleton
class NetworkStateManager @Inject constructor() {
    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Idle)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()
    fun updateState(state: NetworkState) {
        _networkState.value = state
    }
}

sealed class NetworkState {
    object Idle : NetworkState()
    object Loading : NetworkState()
    data class Success(val url: String, val code: Int) : NetworkState()
    data class Error(val url: String, val error: Throwable) : NetworkState()
}
