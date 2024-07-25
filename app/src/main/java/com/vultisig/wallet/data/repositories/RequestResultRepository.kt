package com.vultisig.wallet.data.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal interface RequestResultRepository {

    suspend fun <T> request(requestId: String): T?

    fun <T> requestAsFlow(requestId: String): Flow<T?>

    suspend fun respond(requestId: String, result: Any?)
}

internal class RequestResultRepositoryImpl @Inject constructor() : RequestResultRepository {

    private data class Response(
        val requestId: String,
        val result: Any?
    )

    private val results = MutableSharedFlow<Response>(REPLY_CACHE)

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> request(requestId: String): T? =
        results.first { it.requestId == requestId }.result as T?

    @Suppress("UNCHECKED_CAST")
    override fun <T> requestAsFlow(requestId: String): Flow<T?> =
        results.filter { it.requestId == requestId }.map { it.result } as Flow<T?>

    override suspend fun respond(requestId: String, result: Any?) {
        results.emit(Response(requestId, result))
    }


    companion object {
        private const val REPLY_CACHE = 2
    }
}