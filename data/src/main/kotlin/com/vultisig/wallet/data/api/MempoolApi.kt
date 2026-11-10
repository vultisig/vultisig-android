package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface MempoolApi {
    /**
     * Median fee rate (sat/vB) of the next projected Bitcoin block, or null when the projection
     * holds no block. A failed read throws.
     */
    suspend fun getNextBlockMedianFee(): Double?
}

internal class MempoolApiImpl @Inject constructor(private val httpClient: HttpClient) : MempoolApi {

    override suspend fun getNextBlockMedianFee(): Double? =
        httpClient
            .get(PROJECTED_BLOCKS_URL)
            .bodyOrThrow<List<MempoolProjectedBlockJson>>()
            .firstOrNull()
            ?.medianFee

    private companion object {
        const val PROJECTED_BLOCKS_URL = "https://api.vultisig.com/mempool/v1/fees/mempool-blocks"
    }
}

@Serializable
internal data class MempoolProjectedBlockJson(@SerialName("medianFee") val medianFee: Double)
