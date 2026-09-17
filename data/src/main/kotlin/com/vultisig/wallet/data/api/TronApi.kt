package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronAccountRequestJson
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.api.models.TronBalanceResponseJson
import com.vultisig.wallet.data.api.models.TronBroadcastTxResponseJson
import com.vultisig.wallet.data.api.models.TronChainParametersJson
import com.vultisig.wallet.data.api.models.TronSpecificBlockJson
import com.vultisig.wallet.data.api.models.TronTransactionStatusResponse
import com.vultisig.wallet.data.api.models.TronTriggerConstantContractJson
import com.vultisig.wallet.data.chains.helpers.TronFunctions.buildTrc20TransferParameters
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.utils.NetworkException
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.serialization.ContentConvertException
import java.io.IOException
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

interface TronApi {

    suspend fun getBalance(coin: Coin): BigInteger

    suspend fun broadcastTransaction(tx: String): String?

    suspend fun getSpecific(): TronSpecificBlockJson

    /**
     * Simulates a TRC-20 transfer to size its energy cost. Throws when the node answers with a
     * failed or reverted simulation, so a caller never turns one into a `fee_limit`.
     */
    suspend fun getTriggerConstantContractFee(
        ownerAddressBase58: String,
        contractAddressBase58: String,
        recipientAddressHex: String,
        functionSelector: String,
        amount: BigInteger,
    ): TronTriggerConstantContractJson

    suspend fun getChainParameters(): TronChainParametersJson

    suspend fun getAccountResource(address: String): TronAccountResourceJson

    suspend fun getAccount(address: String): TronAccountJson

    suspend fun getTsStatus(chain: Chain, txHash: String): TronTransactionStatusResponse?

    /**
     * Reads a no-argument view function's ABI-encoded return via `triggerconstantcontract`,
     * self-addressed as owner since the call touches no state and needs no funded account. Returns
     * `null` on a failed/reverted simulation or network error, so a custom-token lookup fails
     * closed rather than guessing a symbol or decimals.
     */
    suspend fun readContractConstant(
        contractAddressBase58: String,
        functionSelector: String,
    ): String?
}

internal class TronApiImpl @Inject constructor(private val httpClient: HttpClient) : TronApi {

    private val tronGrid = "https://api.vultisig.com/tron"

    /**
     * The proxy occasionally answers a 200 with an ack body (`{"message":"ok"}`) instead of the
     * TronGrid payload. `bodyOrThrow` would surface that body's own `message` — the useless error
     * `ok` — so a 2xx that is the ack, or that fails to deserialize, is thrown as an explicit
     * invalid response.
     */
    private suspend inline fun <reified T> HttpResponse.tronBodyOrThrow(): T {
        if (status.isSuccess() && isProxyAckBody()) {
            throw NetworkException(status.value, "Invalid Tron response from $tronGrid")
        }
        return try {
            bodyOrThrow<T>()
        } catch (e: NetworkException) {
            if (status.isSuccess() && e.cause is ContentConvertException) {
                throw NetworkException(status.value, "Invalid Tron response from $tronGrid", e)
            }
            throw e
        }
    }

    /**
     * True when a 2xx body is the proxy's bare acknowledgement (`{"message":"ok"}`) rather than a
     * TronGrid payload. Failing to deserialize is not a reliable signal on its own: every field of
     * [TronAccountJson] and [TronAccountResourceJson] has a default, so the ack parses cleanly into
     * zeroed data that would price a fee off zero resources or report an activated account as new.
     */
    private suspend fun HttpResponse.isProxyAckBody(): Boolean {
        val root =
            try {
                Json.parseToJsonElement(bodyAsText())
            } catch (e: SerializationException) {
                Timber.d(e, "Tron response is not JSON")
                return false
            }
        return root is JsonObject && root.keys == setOf("message")
    }

    override suspend fun broadcastTransaction(tx: String): String {
        repeat(MAX_BROADCAST_RETRIES) { attempt ->
            val httpResponse =
                httpClient.post(tronGrid) {
                    url { path("tron", "wallet", "broadcasttransaction") }
                    contentType(ContentType.Application.Json)
                    setBody(tx)
                }
            val response = httpResponse.tronBodyOrThrow<TronBroadcastTxResponseJson>()
            if (response.code == NOT_ENOUGH_EFFECTIVE_CONNECTION_ERROR_CODE) {
                Timber.d("Tron broadcast NOT_ENOUGH_EFFECTIVE_CONNECTION, attempt %d", attempt + 1)
                if (attempt < MAX_BROADCAST_RETRIES - 1) {
                    delay(1000L shl attempt)
                }
                return@repeat
            }
            return response.txId.takeIf {
                response.code in listOf(null, DUP_TRANSACTION_ERROR_CODE)
            } ?: throw Exception("Error broadcasting transaction: ${response.code}")
        }
        throw Exception("Tron network is unstable. Please try again shortly.")
    }

    override suspend fun getSpecific() =
        httpClient
            .post(tronGrid) { url { path("tron", "wallet", "getnowblock") } }
            .tronBodyOrThrow<TronSpecificBlockJson>()

    override suspend fun getTriggerConstantContractFee(
        ownerAddressBase58: String,
        contractAddressBase58: String,
        recipientAddressHex: String,
        functionSelector: String,
        amount: BigInteger,
    ): TronTriggerConstantContractJson {
        val parameter =
            buildTrc20TransferParameters(recipientBaseHex = recipientAddressHex, amount = amount)
        val body = buildJsonObject {
            put("owner_address", ownerAddressBase58)
            put("contract_address", contractAddressBase58)
            put("function_selector", functionSelector)
            put("parameter", parameter)
            put("visible", true)
        }

        val response =
            httpClient
                .post(tronGrid) {
                    // Full node (`wallet`), not `walletsolidity`: the solidified node lags the head
                    // by ~60s, so a just-funded or just-approved account is simulated against stale
                    // state. iOS and the SDK both simulate on the full node.
                    url { path("tron", "wallet", "triggerconstantcontract") }
                    setBody(body)
                    accept(ContentType.Application.Json)
                }
                .tronBodyOrThrow<TronTriggerConstantContractJson>()

        // A 200 does not mean the transfer would land. A revert is returned as an ordinary response
        // whose energy figures are far below the real cost, and consuming one produces a signed
        // fee_limit that guarantees OUT_OF_ENERGY at broadcast.
        check(response.isSuccessfulSimulation()) {
            "Tron fee simulation failed: ${response.simulationFailureReason()}"
        }
        check(response.energyUsed + response.energyPenalty > 0L) {
            "Tron fee simulation returned a non-positive energy estimate"
        }

        return response
    }

    override suspend fun getChainParameters(): TronChainParametersJson {
        return httpClient
            .post(tronGrid) { url { path("tron", "wallet", "getchainparameters") } }
            .tronBodyOrThrow<TronChainParametersJson>()
    }

    /**
     * A failed read throws instead of reading as zero. This balance feeds both the portfolio and
     * the TRC-20 fee simulation, so swallowing an RPC failure would show real funds as empty and
     * simulate the transfer at amount 0 — a different shape from the send that actually gets
     * signed. Only an account the node does not know reads zero.
     */
    override suspend fun getBalance(coin: Coin): BigInteger {
        val content =
            httpClient
                .get("$tronGrid/v1/accounts/${coin.address}")
                .tronBodyOrThrow<TronBalanceResponseJson>()
        val account = content.tronBalanceResponseData.firstOrNull() ?: return BigInteger.ZERO

        return if (coin.isNativeToken) {
            account.balance
        } else {
            account.trc20
                .asSequence()
                .mapNotNull { it[coin.contractAddress]?.toBigIntegerOrNull() }
                .firstOrNull() ?: BigInteger.ZERO
        }
    }

    override suspend fun getAccountResource(address: String): TronAccountResourceJson {
        return httpClient
            .post(tronGrid) {
                url { path("tron", "wallet", "getaccountresource") }
                contentType(ContentType.Application.Json)
                setBody(TronAccountRequestJson(address, true))
            }
            .tronBodyOrThrow<TronAccountResourceJson>()
    }

    override suspend fun getAccount(address: String): TronAccountJson {
        return httpClient
            .post(tronGrid) {
                url { appendPathSegments("/wallet/getaccount") }
                contentType(ContentType.Application.Json)
                setBody(TronAccountRequestJson(address, true))
            }
            .tronBodyOrThrow<TronAccountJson>()
    }

    override suspend fun readContractConstant(
        contractAddressBase58: String,
        functionSelector: String,
    ): String? {
        val body = buildJsonObject {
            put("owner_address", contractAddressBase58)
            put("contract_address", contractAddressBase58)
            put("function_selector", functionSelector)
            put("visible", true)
        }
        return try {
            httpClient
                .post(tronGrid) {
                    url { path("tron", "wallet", "triggerconstantcontract") }
                    setBody(body)
                    accept(ContentType.Application.Json)
                }
                .tronBodyOrThrow<TronTriggerConstantContractJson>()
                .takeIf { it.isSuccessfulSimulation() }
                ?.constantResult
                ?.firstOrNull()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: NetworkException) {
            Timber.d(
                e,
                "Tron constant call failed for %s %s",
                contractAddressBase58,
                functionSelector,
            )
            null
        } catch (e: IOException) {
            Timber.d(
                e,
                "Tron constant call failed for %s %s",
                contractAddressBase58,
                functionSelector,
            )
            null
        } catch (e: SerializationException) {
            Timber.d(
                e,
                "Tron constant call failed for %s %s",
                contractAddressBase58,
                functionSelector,
            )
            null
        }
    }

    override suspend fun getTsStatus(chain: Chain, txHash: String): TronTransactionStatusResponse? {

        return try {
            httpClient
                .post(tronGrid) {
                    // Full node (`wallet`), not `walletsolidity`: the solidified node only returns
                    // a
                    // tx after ~60s of confirmations, far longer than the recovery verify window,
                    // so
                    // it would report a just-broadcast tx as a failure. The full node returns it as
                    // soon as it is accepted.
                    url { path("tron", "wallet", "gettransactionbyid") }
                    setBody(mapOf("value" to txHash))
                }
                .tronBodyOrThrow<TronTransactionStatusResponse?>()
                ?.takeIf { it.txId != null }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    companion object {
        const val TRANSFER_FUNCTION_SELECTOR = "transfer(address,uint256)"
        private const val DUP_TRANSACTION_ERROR_CODE = "DUP_TRANSACTION_ERROR"
        private const val NOT_ENOUGH_EFFECTIVE_CONNECTION_ERROR_CODE =
            "NOT_ENOUGH_EFFECTIVE_CONNECTION"
        private const val MAX_BROADCAST_RETRIES = 3
    }
}
