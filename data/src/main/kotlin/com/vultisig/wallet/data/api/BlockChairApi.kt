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

        // A standard transaction tops out near ~1400 P2WPKH inputs at Bitcoin's 100kvB
        // standardness limit, so an address with more UTXOs than this cap covers has nothing
        // further worth fetching for a single send. This bounds worst-case pagination latency
        // (20 sequential requests) rather than expressing any real chain limit.
        private const val MAX_UTXO_PAGES = 20
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
        val seenOutpoints = mutableSetOf<Pair<String, Int>>()
        var firstPage: BlockChairInfo? = null
        var expectedUtxoCount: Int? = null

        for (pageIndex in 0 until MAX_UTXO_PAGES) {
            val offset = pageIndex * UTXO_PAGE_SIZE
            val response =
                httpClient.get(
                    "$BASE_URL/$chainName/dashboards/address/$address" +
                        // limit/offset take a "transactions,utxo" pair; zeroing the transactions
                        // side skips fetching hashes this method never reads.
                        "?state=latest&limit=0,$UTXO_PAGE_SIZE&offset=0,$offset"
                ) {
                    header("Content-Type", "application/json")
                }
            val body = response.bodyOrThrow<BlockChairInfoJson>()
            val page =
                body.data[address]?.copy(currentBlockHeight = body.context?.state?.toLong())
                    ?: error("Blockchair returned no address data for $chain:$address")

            if (firstPage == null) firstPage = page

            // Offset paging only holds up over a list that stays still: removing one entry
            // slides everything after it past an offset already walked, opening a gap no
            // amount of merging can see. The count moves with the list, so a change in it
            // between pages is that signal — every page must keep reporting the same total.
            val pageUtxoCount = page.address.unspentOutputCount
            check(expectedUtxoCount == null || expectedUtxoCount == pageUtxoCount) {
                "Blockchair UTXO count for $chain:$address moved from $expectedUtxoCount to " +
                    "$pageUtxoCount mid-walk"
            }
            expectedUtxoCount = pageUtxoCount

            // A newest-first list that shifts between two page requests can hand back the same
            // outpoint twice; deduplicating keeps a shifted entry from being counted, or later
            // selected as a transaction input, twice.
            for (utxo in page.utxos) {
                if (seenOutpoints.add(utxo.transactionHash to utxo.index)) utxos += utxo
            }

            // A page shorter than the page size is the only reliable "no more data" signal — a
            // full page that happens to match unspent_output_count could still be followed by
            // more UTXOs if that count itself is a hair stale, which is the exact inconsistency
            // this method exists to catch.
            if (page.utxos.size < UTXO_PAGE_SIZE) {
                check(utxos.size >= pageUtxoCount) {
                    "Blockchair returned ${utxos.size} UTXOs for $chain:$address, " +
                        "expected $pageUtxoCount"
                }
                return checkNotNull(firstPage).copy(utxos = utxos)
            }
        }

        error(
            "Blockchair pagination exceeded $MAX_UTXO_PAGES pages for $chain:$address with " +
                "${utxos.size} UTXOs retrieved"
        )
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
