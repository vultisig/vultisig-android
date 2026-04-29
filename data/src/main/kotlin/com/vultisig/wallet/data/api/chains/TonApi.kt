package com.vultisig.wallet.data.api.chains

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

    suspend fun getSpecificTransactionInfo(address: String): BigInteger

    suspend fun getWalletState(address: String): String

    suspend fun getJettonWallet(address: String, contract: String): JettonWalletsJson

    suspend fun getEstimateFee(address: String, serializedBoc: String): BigInteger

    suspend fun getTsStatus(txHash: String): TonStatusResult
}

internal class TonApiImpl @Inject constructor(private val http: HttpClient) : TonApi {

    private val baseUrl: String = "https://api.vultisig.com/ton"

    override suspend fun getBalance(address: String): BigInteger =
        getAddressInformation(address).balance

    override suspend fun getJettonBalance(address: String, contract: String): BigInteger {
        val wallet = getJettonWallet(address, contract).jettonWallets.firstOrNull()
        return wallet?.balance?.toBigIntegerOrNull() ?: BigInteger.ZERO
    }

    private suspend fun getAddressInformation(address: String): TonAddressInfoResponseJson =
        http
            .get("$baseUrl/v3/addressInformation") {
                parameter("address", address)
                parameter("use_v2", false)
            }
            .bodyOrThrow<TonAddressInfoResponseJson>()

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun broadcastTransaction(transaction: String): String? {
        val response =
            http
                .post("$baseUrl/v2/sendBocReturnHash") {
                    setBody(TonBroadcastTransactionRequestJson(transaction))
                }
                .bodyOrThrow<TonBroadcastTransactionResponseJson>()
        if (response.error != null) {
            if (response.error.contains("duplicate message")) {
                return null
            }
            error("Error broadcasting transaction: ${response.error}")
        }
        if (response.result == null) {
            return null
        }
        // The API returns a Base64-encoded hash that needs to be converted to hex format
        val decodedBytes = Base64.getDecoder().decode(response.result.hash)
        return decodedBytes.toHexString()
    }

    override suspend fun getSpecificTransactionInfo(address: String): BigInteger =
        http
            .get("$baseUrl/v2/getExtendedAddressInformation") { parameter("address", address) }
            .bodyOrThrow<TonSpecificTransactionInfoResponseJson>()
            .result
            .accountState
            .seqno
            ?.content
            ?.let { BigInteger(it) } ?: BigInteger.ZERO

    override suspend fun getWalletState(address: String): String =
        getAddressInformation(address).status

    override suspend fun getJettonWallet(address: String, contract: String): JettonWalletsJson {
        return http
            .get("$baseUrl/v3/jetton/wallets") {
                parameter("owner_address", address)
                parameter("jetton_master_address", contract)
            }
            .bodyOrThrow<JettonWalletsJson>()
    }

    override suspend fun getEstimateFee(address: String, serializedBoc: String): BigInteger {
        val feeResponse =
            http
                .get("$baseUrl/v3/estimateFee") {
                    parameter("address", address)
                    parameter("body", serializedBoc)
                    parameter("ignore_chksig", true)
                }
                .bodyOrThrow<TonEstimateFeeJson>()

        return feeResponse.result?.sourceFees?.totalFee()?.toBigInteger()
            ?: error("Can't calculate Fees")
    }

    override suspend fun getTsStatus(txHash: String): TonStatusResult =
        http
            .get("$baseUrl/v3/transactionsByMessage") { parameter("msg_hash", txHash) }
            .bodyOrThrow<TonStatusResult>()
}
