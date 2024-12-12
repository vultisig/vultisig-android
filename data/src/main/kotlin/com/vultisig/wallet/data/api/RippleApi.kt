package com.vultisig.wallet.data.api

import ReultJosn
import com.vultisig.wallet.data.api.models.RpcPayload
import com.vultisig.wallet.data.models.Coin
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject


interface RippleApi {
    suspend fun broadcastTransaction(tx: String): String?
    suspend fun getBalance(coin: Coin): BigInteger
    suspend fun fetchAccountsInfo(walletAddress: String): RippleAccountResponse?
}

internal class RippleApiImp @Inject constructor(
    private val http: HttpClient,
) : RippleApi {
    private val rpcUrl: String = "https://api.vultisig.com/ripple"
    private val rpcURL2: String = "https://xrplcluster.com"


    override suspend fun broadcastTransaction(hex: String): String {
        try {
            val payload = RpcPayload(
                method = "submit",
                params = buildJsonArray {
                    addJsonObject {
                        put(
                            "tx_blob",
                            hex
                        )
                    }
                }
            )
            val response = http.post(rpcURL2) {
                setBody(payload)
            }

            val rpcResp = response.body<ReultJosn>()

            if (rpcResp.engineResult != "tesSUCCESS") {

                if (rpcResp.engineResultMessage?.toString()
                        ?.lowercase() == "This sequence number has already passed.".lowercase()
                ) {

                    if (rpcResp.tx_json?.hash != null) {
                        return rpcResp.tx_json.hash ?: ""
                    }
                }
                return rpcResp.engineResultMessage?.description ?: ""
            }
            if (rpcResp.tx_json?.hash?.isNotEmpty() == true) {
                return rpcResp.engineResultMessage?.description ?: ""
            }
            return ""
        } catch (e: Exception) {
            Timber.e(
                "Error in Broadcast XRP Transaction",
                e.message
            )
            throw e
        }
    }


    override suspend fun getBalance(coin: Coin): BigInteger {
        try {
            val accountInfo = fetchAccountsInfo(coin.address)
            return accountInfo?.accountData?.balance?.toBigInteger() ?: BigInteger.ZERO
        } catch (e: Exception) {
            Timber.e("Error in getBalance: ${e.message}")
            return BigInteger.ZERO
        }
    }

    override suspend fun fetchAccountsInfo(walletAddress: String): RippleAccountResponse? {
        return try {
            val payload = RpcPayload(
                method = "account_info",
                params = buildJsonArray {
                    addJsonObject {
                        put(
                            "account",
                            walletAddress
                        )
                        put(
                            "ledger_index",
                            "current"
                        )
                        put(
                            "queue",
                            true
                        )
                    }
                }
            )
            val response = http.post(rpcURL2) {
                setBody(payload)
            }
            response.body<RippleAccountResponse>()
        } catch (e: Exception) {
            Timber.e("Error in fetchTokenAccountsByOwner: ${e.message}")
            throw e
        }
    }
}


@Serializable
data class RippleAccountResponse(
    @SerialName("account_data")
    val accountData: AccountData? = null,
    @SerialName("ledger_current_index")
    val ledgerCurrentIndex: Int? = null,
    @SerialName("queue_data")
    val queueData: QueueData? = null,
    val status: String? = null,
    val validated: Boolean? = null
)

@Serializable
data class AccountData(
    @SerialName("Account")
    val account: String? = null,
    @SerialName("Balance")
    val balance: String? = null,
    @SerialName("Flags")
    val flags: Int? = null,
    @SerialName("LedgerEntryType")
    val ledgerEntryType: String? = null,
    @SerialName("OwnerCount")
    val ownerCount: Int? = null,
    @SerialName("PreviousTxnID")
    val previousTxnID: String? = null,
    @SerialName("PreviousTxnLgrSeq")
    val previousTxnLgrSeq: Int? = null,
    @SerialName("Sequence")
    val sequence: Int? = null,
    val index: String? = null
)

@Serializable
data class QueueData(
    @SerialName("auth_change_queued")
    val authChangeQueued: Boolean? = null,
    @SerialName("highest_sequence")
    val highestSequence: Int? = null,
    @SerialName("lowest_sequence")
    val lowestSequence: Int? = null,
    @SerialName("max_spend_drops_total")
    val maxSpendDropsTotal: String? = null,
    val transactions: List<Transaction>? = null,
    @SerialName("txn_count") val txnCount: Int? = null
)

@Serializable
data class Transaction(
    @SerialName("auth_change")
    val authChange: Boolean? = null,
    val fee: String? = null,
    @SerialName(
        "fee_level"
    ) val feeLevel: String? = null,
    @SerialName("max_spend_drops")
    val maxSpendDrops: String? = null,
    val seq: Int? = null,
    @SerialName("LastLedgerSequence")
    val lastLedgerSequence: Int? = null
)
