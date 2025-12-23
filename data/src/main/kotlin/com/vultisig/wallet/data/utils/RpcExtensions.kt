package com.vultisig.wallet.data.utils

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.WebsocketContentConvertException
import io.ktor.serialization.WebsocketDeserializeException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
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


suspend fun extractError(
    response: HttpResponse,
    errorKey: String
): String {
    val body = response.bodyAsText()

    return try {
        val element = Json.parseToJsonElement(body)

        findValueRecursively(element, errorKey)
            ?: body
    } catch (t: Throwable) {
        Timber.e(t, "Failed to extract error from response")
        body
    }
}



private fun findValueRecursively(
    element: JsonElement,
    key: String
): String? {
    return when (element) {

        is JsonObject -> {
            element[key]
                ?.takeIf { it is JsonPrimitive && it.isString }
                ?.jsonPrimitive
                ?.content
                ?: element.values
                    .asSequence()
                    .mapNotNull { child ->
                        findValueRecursively(child, key)
                    }
                    .firstOrNull()
        }

        is JsonArray -> {
            element.asSequence()
                .mapNotNull { child ->
                    findValueRecursively(child, key)
                }
                .firstOrNull()
        }

        else -> null
    }
}



class NetworkException(
    val httpStatusCode: Int,
    override val message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)