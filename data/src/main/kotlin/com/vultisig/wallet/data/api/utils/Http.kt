package com.vultisig.wallet.data.api.utils

import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

internal fun HttpResponse.throwIfUnsuccessful(): HttpResponse {
    if (!status.isSuccess()) {
        error("Request failed with status $status")
    } else {
        return this
    }
}