package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.BlockChairInfo
import com.vultisig.wallet.data.api.models.BlockChairInfoJson
import com.vultisig.wallet.data.api.models.SuggestedTransactionFeeDataJson
import com.vultisig.wallet.data.api.models.TransactionHashDataJson
import com.vultisig.wallet.data.api.models.TransactionHashRequestBodyJson
import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

interface BlockChairApi {
    suspend fun getAddressInfo(
        chain: Chain,
        address: String,
    ): BlockChairInfo?

    suspend fun getBlockChairStats(chain: Chain): BigInteger
    suspend fun broadcastTransaction(chain: Chain, signedTransaction: String): String
}

internal class BlockChairApiImp @Inject constructor(
    private val json: Json,
    private val httpClient: HttpClient,
) : BlockChairApi {

    private fun getChainName(chain: Chain): String = when (chain) {
        Chain.Bitcoin -> "bitcoin"
        Chain.BitcoinCash -> "bitcoin-cash"
        Chain.Litecoin -> "litecoin"
        Chain.Dogecoin -> "dogecoin"
        Chain.Dash -> "dash"
        else -> throw IllegalArgumentException("Unsupported chain $chain")
    }

    override suspend fun getAddressInfo(
        chain: Chain,
        address: String,
    ): BlockChairInfo? {
        try {
            val response =
                httpClient.get("https://api.vultisig.com/blockchair/${getChainName(chain)}/dashboards/address/${address}") {
                    header("Content-Type", "application/json")
                }
            val responseData = response.body<BlockChairInfoJson>()
            Timber.d("response data: $responseData")
            return responseData.data[address]
        } catch (e: Exception) {
            Timber.e("fail to get address info from blockchair: ${e.message}")
        }
        return null
    }

    override suspend fun getBlockChairStats(chain: Chain): BigInteger {
        val response =
            httpClient.get("https://api.vultisig.com/blockchair/${getChainName(chain)}/stats") {
                header("Content-Type", "application/json")
            }
        return response.body<SuggestedTransactionFeeDataJson>().data.value
    }

    override suspend fun broadcastTransaction(chain: Chain, signedTransaction: String): String {
        val bodyContent = json.encodeToString(
            TransactionHashRequestBodyJson(signedTransaction)
        )
        Timber.d("bodyContent:$bodyContent")
        val response =
            httpClient.post("https://api.vultisig.com/blockchair/${getChainName(chain)}/push/transaction") {
                header("Content-Type", "application/json")
                setBody(bodyContent)
            }
        if (response.status != HttpStatusCode.OK) {
            Timber.d("fail to broadcast transaction: ${response.bodyAsText()}")
            throw Exception("fail to broadcast transaction")
        }

        return response.body<TransactionHashDataJson>().data.value
    }
}