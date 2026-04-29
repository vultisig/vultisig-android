package com.vultisig.wallet.data.api.chains.ton

import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import java.math.BigInteger
import java.util.Base64
import javax.inject.Inject

interface TonApi {

    suspend fun getBalance(address: String): BigInteger

    suspend fun getJettonBalance(address: String, contract: String): BigInteger

    suspend fun broadcastTransaction(transaction: String): String?

    suspend fun getSeqno(address: String): BigInteger

    suspend fun getWalletState(address: String): String

    suspend fun getJettonWallet(address: String, contract: String): JettonWalletsJson

    suspend fun estimateFee(address: String, serializedBoc: String): BigInteger

    suspend fun getTsStatus(txHash: String): TonStatusResult
}

internal class TonApiImpl @Inject constructor(private val http: HttpClient) : TonApi {

    override suspend fun getBalance(address: String): BigInteger =
        getAddressInformation(address).balance

    override suspend fun getJettonBalance(address: String, contract: String): BigInteger {
        val wallet = getJettonWallet(address, contract).jettonWallets.firstOrNull()
        return wallet?.balance?.toBigIntegerOrNull() ?: BigInteger.ZERO
    }

    private suspend fun getAddressInformation(address: String): TonAddressInfoResponseJson =
        http
            .get("$BASE_URL/v3/addressInformation") {
                parameter("address", address)
                parameter("use_v2", false)
            }
            .bodyOrThrow<TonAddressInfoResponseJson>()

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun broadcastTransaction(transaction: String): String? {
        val response =
            http
                .post("$BASE_URL/v2/sendBocReturnHash") {
                    setBody(TonBroadcastTransactionRequestJson(transaction))
                }
                .bodyOrThrow<TonBroadcastTransactionResponseJson>()
        if (response.error != null) {
            // Returning null for duplicate-message lets the caller fall back to the
            // already-known hash via orKnownHash; treating it as an error would surface
            // a spurious failure on retried broadcasts.
            if (response.error.lowercase().contains(DUPLICATE_MESSAGE_MARKER)) {
                return null
            }
            error("Error broadcasting transaction: ${response.error}")
        }
        val hash = response.result?.hash ?: return null
        // The API returns a Base64-encoded hash that needs to be converted to hex format
        return Base64.getDecoder().decode(hash).toHexString()
    }

    override suspend fun getSeqno(address: String): BigInteger =
        http
            .get("$BASE_URL/v2/getExtendedAddressInformation") { parameter("address", address) }
            .bodyOrThrow<TonSpecificTransactionInfoResponseJson>()
            .result
            .accountState
            .seqno
            ?.content
            ?.toBigIntegerOrNull() ?: BigInteger.ZERO

    override suspend fun getWalletState(address: String): String =
        getAddressInformation(address).status

    override suspend fun getJettonWallet(address: String, contract: String): JettonWalletsJson {
        return http
            .get("$BASE_URL/v3/jetton/wallets") {
                parameter("owner_address", address)
                parameter("jetton_master_address", contract)
            }
            .bodyOrThrow<JettonWalletsJson>()
    }

    override suspend fun estimateFee(address: String, serializedBoc: String): BigInteger {
        val feeResponse =
            http
                .get("$BASE_URL/v3/estimateFee") {
                    parameter("address", address)
                    parameter("body", serializedBoc)
                    parameter("ignore_chksig", true)
                }
                .bodyOrThrow<TonEstimateFeeJson>()

        if (!feeResponse.ok || feeResponse.error != null) {
            error("Can't calculate Fees: ${feeResponse.error ?: "code=${feeResponse.code}"}")
        }
        return feeResponse.result?.sourceFees?.totalFee()?.toBigInteger()
            ?: error("Can't calculate Fees: empty result")
    }

    override suspend fun getTsStatus(txHash: String): TonStatusResult =
        http
            .get("$BASE_URL/v3/transactionsByMessage") { parameter("msg_hash", txHash) }
            .bodyOrThrow<TonStatusResult>()

    private companion object {
        const val BASE_URL = "https://api.vultisig.com/ton"
        const val DUPLICATE_MESSAGE_MARKER = "duplicate message"
    }
}
