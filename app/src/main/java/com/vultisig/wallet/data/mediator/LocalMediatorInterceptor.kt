package com.vultisig.wallet.data.mediator

import java.io.IOException
import java.net.Proxy
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.ForwardingSink
import okio.buffer

/**
 * Prove that the discovered host knows the QR secret before sending a ceremony request.
 * The network interceptor authenticates every exchange, including transport retries.
 */
internal class LocalMediatorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.isHttps || url.port != LocalMediatorAuth.PORT) return chain.proceed(request)

        val origin = "http://" + url.host + ":" + url.port
        val binding = LocalMediatorCredentials.get(origin)
            ?: throw IOException("Local mediator session is unavailable")
        val auth = binding.auth
        if (!binding.hostVerified) {
            val probe = Request.Builder().url("$origin/auth/" + auth.sessionId).get().build()
            chain.proceed(probe.authenticated(auth)).use { response ->
                if (!response.isSuccessful) throw IOException("Local mediator did not verify")
            }
            binding.hostVerified = true
        }
        return chain.proceed(request.authenticated(auth))
    }

    private fun Request.authenticated(auth: LocalMediatorAuth): Request =
        newBuilder()
            .tag(LocalMediatorAuth::class.java, auth)
            .header("Accept-Encoding", "identity")
            .header("Cache-Control", "no-cache, no-store")
            .build()
}

/** Runs before OkHttp can follow a redirect or return unverified bytes to Ktor. */
internal class LocalMediatorNetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val auth = request.tag(LocalMediatorAuth::class.java) ?: return chain.proceed(request)
        if (chain.connection()?.route()?.proxy?.type() != Proxy.Type.DIRECT) {
            throw IOException("Local mediator connections must be direct")
        }
        val timestamp = System.currentTimeMillis()
        val nonce = auth.newNonce()
        val digest = LocalMediatorAuth.digest(request.bodyBytes())
        val target = request.url.encodedPath
        if (request.url.encodedQuery != null) throw IOException("Unexpected local mediator target")
        val tag =
            auth.requestTag(
                request.method,
                target,
                request.header("message_id"),
                timestamp,
                nonce,
                digest,
            )
        val response =
            chain.proceed(
                request.newBuilder()
                    .header(LocalMediatorAuth.TIMESTAMP_HEADER, timestamp.toString())
                    .header(LocalMediatorAuth.NONCE_HEADER, nonce)
                    .header(LocalMediatorAuth.DIGEST_HEADER, digest)
                    .header(LocalMediatorAuth.AUTH_HEADER, tag)
                    .build()
            )

        try {
            val contentEncoding = response.header("Content-Encoding")
            if (response.isRedirect ||
                (contentEncoding != null && !contentEncoding.equals("identity", ignoreCase = true))
            ) {
                throw IOException("Unexpected local mediator response")
            }
            val limit = LocalMediatorAuth.MAX_RESPONSE_BYTES.toLong()
            val data = response.peekBody(limit + 1).bytes()
            if (data.size > limit) throw IOException("Local mediator response exceeds its size limit")
            if (!auth.matches(
                    auth.responseTag(tag, response.code, data),
                    response.header(LocalMediatorAuth.RESPONSE_HEADER),
                )
            ) {
                throw IOException("Local mediator response failed authentication")
            }
            return response
        } catch (error: Exception) {
            response.close()
            throw error
        }
    }

    private fun Request.bodyBytes(): ByteArray {
        val body = body ?: return byteArrayOf()
        if (body.isDuplex() || body.isOneShot()) {
            throw IOException("Unexpected local mediator request body")
        }
        val limit = LocalMediatorAuth.MAX_REQUEST_BYTES.toLong()
        if (body.contentLength() > limit) {
            throw IOException("Local mediator request exceeds its size limit")
        }
        val buffer = Buffer()
        val boundedSink =
            object : ForwardingSink(buffer) {
                private var size = 0L

                override fun write(source: Buffer, byteCount: Long) {
                    if (byteCount > limit - size) {
                        throw IOException("Local mediator request exceeds its size limit")
                    }
                    size += byteCount
                    super.write(source, byteCount)
                }
            }.buffer()
        boundedSink.use { body.writeTo(it) }
        return buffer.readByteArray()
    }
}
