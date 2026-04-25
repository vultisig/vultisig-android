package com.vultisig.wallet.data.repositories

import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

interface RequestResultRepository {

    suspend fun <T> request(requestId: String): T?

    suspend fun respond(requestId: String, result: Any?)
}

internal class RequestResultRepositoryImpl @Inject constructor() : RequestResultRepository {

    private data class Response(val requestId: String, val result: Any?)

    private val results = MutableSharedFlow<Response>(replay = 1)

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> request(requestId: String): T? =
        results.first { it.requestId == requestId }.result as? T

    override suspend fun respond(requestId: String, result: Any?) {
        results.emit(Response(requestId, result))
    }
}
