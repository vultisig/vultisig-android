package com.vultisig.wallet.network

import okhttp3.Interceptor.Chain
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class FaultyInterceptor {
    private var exceptionToThrow: Exception? = null

    fun setFailure(e: Exception) {
        this.exceptionToThrow = e
    }

    fun intercept(chain: Chain): Response {
        // If the test set an exception, throw it immediately
        exceptionToThrow?.let { throw it }

        // Fallback: Return 200 OK if no exception set
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody())
            .build()
    }
}
