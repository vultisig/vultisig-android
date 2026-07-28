package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.BlockChainStatusDeserialized
import com.vultisig.wallet.data.api.models.BlockChairInfo
import com.vultisig.wallet.data.api.models.BlockChairInfoJson
import com.vultisig.wallet.data.api.models.BlockChairUtxoInfo
import com.vultisig.wallet.data.api.models.SuggestedTransactionFeeDataJson
import com.vultisig.wallet.data.api.models.TransactionHashDataJson
import com.vultisig.wallet.data.api.models.TransactionHashRequestBodyJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.utils.UTXOStatusResponseSerializer
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.serialization.json.Json
import timber.log.Timber

interface BlockChairApi {
    suspend fun getAddressInfo(chain: Chain, address: String): BlockChairInfo?

    /**
     * Unlike [getAddressInfo], paginates past Blockchair's ~100-entry default page until every UTXO
     * in `unspent_output_count` is retrieved, and throws rather than returning a silently-truncated
     * view when a page falls short — see #5433.
     */
    suspend fun getAllUtxos(chain: Chain, address: String): BlockChairInfo

    suspend fun getBlockChairStats(chain: Chain): BigInteger

    suspend fun broadcastTransaction(chain: Chain, signedTransaction: String): String

    suspend fun getTsStatus(chain: Chain, txHash: String): BlockChainStatusDeserialized?
}

internal class BlockChairApiImp
@Inject
constructor(
    private val json: Json,
    private val httpClient: HttpClient,
    private val utxoStatusResponseSerializer: UTXOStatusResponseSerializer,
) : BlockChairApi {
    companion object {
        private const val BASE_URL = "https://api.vultisig.com/blockchair"
        private const val UTXO_PAGE_SIZE = 1000
    }

    private fun getChainName(chain: Chain): String =
        when (chain) {
            Chain.Bitcoin -> "bitcoin"
            Chain.BitcoinCash -> "bitcoin-cash"
            Chain.Litecoin -> "litecoin"
            Chain.Dogecoin -> "dogecoin"
            Chain.Dash -> "dash"
            Chain.Zcash -> "zcash"
            Chain.Cardano -> "cardano"
            else -> throw IllegalArgumentException("Unsupported chain $chain")
        }

    override suspend fun getAddressInfo(chain: Chain, address: String): BlockChairInfo? {
        try {
            val response =
                httpClient.get(
                    "$BASE_URL/${getChainName(chain)}/dashboards/address/${address}?state=latest"
                ) {
                    header("Content-Type", "application/json")
                }
            val responseData = response.bodyOrThrow<BlockChairInfoJson>()
            Timber.d("response data: $responseData")
            return responseData.data[address]?.copy(
                currentBlockHeight = responseData.context?.state?.toLong()
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e("fail to get address info from blockchair: ${e.message}")
        }
        return null
    }

    override suspend fun getAllUtxos(chain: Chain, address: String): BlockChairInfo {
        val chainName = getChainName(chain)
        val utxos = mutableListOf<BlockChairUtxoInfo>()
        var firstPage: BlockChairInfo? = null
        var expectedUtxoCount = 0
        var offset = 0

        while (true) {
            val response =
                httpClient.get(
                    "$BASE_URL/$chainName/dashboards/address/$address" +
                        "?state=latest&limit=$UTXO_PAGE_SIZE&offset=$offset"
                ) {
                    header("Content-Type", "application/json")
                }
            val body = response.bodyOrThrow<BlockChairInfoJson>()
            val page =
                body.data[address]?.copy(currentBlockHeight = body.context?.state?.toLong())
                    ?: error("Blockchair returned no address data for $chain:$address")

            if (firstPage == null) {
                firstPage = page
                expectedUtxoCount = page.address.unspentOutputCount
            }
            utxos += page.utxos

            // A page shorter than the page size is the only reliable "no more data" signal — a
            // full page that happens to match unspent_output_count could still be followed by
            // more UTXOs if that count itself is a hair stale, which is the exact inconsistency
            // this method exists to catch.
            if (page.utxos.size < UTXO_PAGE_SIZE) break
            offset += UTXO_PAGE_SIZE
        }

        check(utxos.size >= expectedUtxoCount) {
            "Blockchair returned ${utxos.size} UTXOs for $chain:$address, expected $expectedUtxoCount"
        }
        return checkNotNull(firstPage).copy(utxos = utxos)
    }

    override suspend fun getBlockChairStats(chain: Chain): BigInteger {
        val response =
            httpClient.get("$BASE_URL/${getChainName(chain)}/stats") {
                header("Content-Type", "application/json")
            }
        return response.bodyOrThrow<SuggestedTransactionFeeDataJson>().data.value
    }

    suspend fun broadcastTransactionMempool(signedTransaction: String): String {
        try {
            val response =
                httpClient.post("https://api.vultisig.com/bitcoin/") {
                    header("Content-Type", "text/plain")
                    setBody(signedTransaction)
                }
            if (response.status != HttpStatusCode.OK) {
                Timber.e("Failed to broadcast transaction: ${response.bodyAsText()}")
                throw Exception("Failed to broadcast transaction")
            }
            return response.bodyAsText() // Returns the transaction ID
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e("Error broadcasting transaction: ${e.message}")
            throw e
        }
    }

    override suspend fun broadcastTransaction(chain: Chain, signedTransaction: String): String {
        when (chain) {
            Chain.Bitcoin -> {
                return broadcastTransactionMempool(signedTransaction)
            }
            else -> {
                val bodyContent =
                    json.encodeToString(TransactionHashRequestBodyJson(signedTransaction))
                Timber.d("bodyContent:$bodyContent")
                val response =
                    httpClient.post("$BASE_URL/${getChainName(chain)}/push/transaction") {
                        header("Content-Type", "application/json")
                        setBody(bodyContent)
                    }
                if (response.status != HttpStatusCode.OK) {
                    val errorBody = response.bodyAsText()
                    Timber.e("fail to broadcast transaction: $errorBody")
                    error("fail to broadcast transaction: $errorBody")
                }

                return response.bodyOrThrow<TransactionHashDataJson>().data.value
            }
        }
    }

    override suspend fun getTsStatus(chain: Chain, txHash: String): BlockChainStatusDeserialized? {
        val response =
            httpClient.get("$BASE_URL/${getChainName(chain)}/dashboards/transaction/${txHash}")

        if (response.status == HttpStatusCode.Forbidden) {
            Timber.tag("BlockChairApiImp").d("Forbidden (403) when checking tx status: $txHash")
            return null
        }
        if (!response.status.isSuccess()) {
            Timber.tag("BlockChairApiImp")
                .e("Failed to get tx status: ${response.status} for $txHash")
            return null
        }
        return json.decodeFromString(utxoStatusResponseSerializer, response.bodyAsText())
    }
}
