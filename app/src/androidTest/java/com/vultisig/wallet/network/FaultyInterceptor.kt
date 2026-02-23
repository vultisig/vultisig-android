package com.vultisig.wallet.network

import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * OkHttp interceptor for instrumented tests that simulates transport-level failures.
 *
 * Call [setFailure] before making a request to make the interceptor throw that exception
 * instead of proceeding with the chain. When no failure is set, returns a 200 OK response.
 */
class FaultyInterceptor : Interceptor {
    private var exceptionToThrow: Exception? = null

    fun setFailure(e: Exception) {
        this.exceptionToThrow = e
    }

    override fun intercept(chain: Chain): Response {
        exceptionToThrow?.let { throw it }

        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody())
            .build()
    }
}
