package com.vultisig.wallet.data.api.utils

import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

/** Thrown when an HTTP response carries a non-2xx status code. */
class HttpException(val statusCode: Int) : Exception("Request failed with status $statusCode")

internal fun HttpResponse.throwIfUnsuccessful(): HttpResponse {
    if (!status.isSuccess()) {
        throw HttpException(status.value)
    } else {
        return this
    }
}
