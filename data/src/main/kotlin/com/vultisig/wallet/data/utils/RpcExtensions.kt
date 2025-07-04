package com.vultisig.wallet.data.utils

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.WebsocketContentConvertException
import io.ktor.serialization.WebsocketDeserializeException

suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
    return if (status.isSuccess()) {
        try {
            body()
        } catch (t: JsonConvertException) {
            throw NetworkException(status.value, bodyAsText(), t)
        } catch (t: ContentConvertException) {
            throw NetworkException(status.value, bodyAsText(), t)
        } catch (t: WebsocketDeserializeException) {
            throw NetworkException(status.value, bodyAsText(), t)
        } catch (t: WebsocketContentConvertException) {
            throw NetworkException(status.value, bodyAsText(), t)
        }
    } else {
        throw NetworkException(status.value, bodyAsText())
    }
}

class NetworkException(
    val httpStatusCode: Int,
    override val message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)