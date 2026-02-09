package com.vultisig.wallet.data.networkutils

import io.ktor.http.HttpStatusCode
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import timber.log.Timber
import javax.inject.Inject

class NetworkStateInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        return try {
            chain.proceed(request)
        } catch (e: Exception) {
            Timber.e(e, "NetworkStateInterceptor: Caught exception for $url")

            // Create a Synthetic Error Response
            // This prevents the exception from bubbling up and crashing the app.
            // We use HTTP 503 (Service Unavailable) as a generic container.
            val mediaType = "application/json".toMediaTypeOrNull()
            val errorMessage = "{\"error\": \"Network failure: ${e.localizedMessage}\"}"
            val responseBody = errorMessage.toResponseBody(mediaType)

            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(HttpStatusCode.ServiceUnavailable.value)
                .message("Client Side Network Error: ${e.message}")
                .body(responseBody)
                .build()
        }
    }
}