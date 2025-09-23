package com.vultisig.wallet.data.utils

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.WebsocketContentConvertException
import io.ktor.serialization.WebsocketDeserializeException
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

// TODO: Enhance and check for key recursively
suspend fun extractError(response: HttpResponse, errorKey: String): String {
    return try {
        val responseBody = response.bodyAsText()
        val json = JSONObject(responseBody)
        return json.optString(errorKey, responseBody)
    } catch (t: Throwable) {
        Timber.e(t, "Failed to extract error from response")
        response.bodyAsText()
    }
}

class NetworkException(
    val httpStatusCode: Int,
    override val message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)