package com.vultisig.wallet.data.utils

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.WebsocketContentConvertException
import io.ktor.serialization.WebsocketDeserializeException
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

suspend inline fun <reified T> HttpResponse.bodyOrThrow(errorKey: String = "message"): T {
    return if (status.isSuccess()) {
        try {
            body()
        } catch (t: JsonConvertException) {
            throw NetworkException(status.value, extractError(this, errorKey), t)
        } catch (t: ContentConvertException) {
            throw NetworkException(status.value, extractError(this, errorKey), t)
        } catch (t: WebsocketDeserializeException) {
            throw NetworkException(status.value, extractError(this, errorKey), t)
        } catch (t: WebsocketContentConvertException) {
            throw NetworkException(status.value, extractError(this, errorKey), t)
        }
    } else {
        throw NetworkException(status.value, bodyAsText())
    }
}

suspend fun extractError(response: HttpResponse, errorKey: String): String {
    return try {
        val responseBody = response.bodyAsText()
        val root: Any? = try {
            JSONObject(responseBody)
        } catch (e: Exception) {
            try {
                JSONArray(responseBody)
            } catch (e2: Exception) {
                null
            }
        }

        if (root == null) return responseBody

        fun findValue(node: Any?): String? {
            when (node) {
                is JSONObject -> {
                    if (node.has(errorKey)) {
                        return node.opt(errorKey)?.toString()
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val found = findValue(node.opt(k))
                        if (found != null) return found
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        val found = findValue(node.opt(i))
                        if (found != null) return found
                    }
                }
            }
            return null
        }

        findValue(root) ?: if (root is JSONObject) root.optString(errorKey, responseBody) else responseBody
    } catch (t: Throwable) {
        Timber.e(t, "Failed to extract error from response")
        response.bodyAsText()
    }
}

class NetworkException(
    val httpStatusCode: Int,
    override val message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause){
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
