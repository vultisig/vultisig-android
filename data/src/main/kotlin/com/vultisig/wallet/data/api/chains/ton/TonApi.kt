package com.vultisig.wallet.data.api.chains.ton

import com.vultisig.wallet.data.crypto.ton.TonBocParser
import com.vultisig.wallet.data.utils.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import java.math.BigInteger
import java.util.Base64
import javax.inject.Inject
import wallet.core.jni.TONAddressConverter

/** Display metadata for a jetton, resolved from its master contract. */
data class TonJettonMetadata(val ticker: String, val decimals: Int, val logo: String?)

/**
 * Canonicalize a TON address to its user-friendly bounceable form for equality comparison. Raw
 * `0:hex`, URL-safe base64, and non-bounceable `UQ…` variants all normalize to the same `EQ…`
 * string. Returns `null` when [address] is not a valid TON address, so callers can fall back to the
 * raw value.
 */
internal fun tonUserFriendlyAddress(address: String): String? =
    runCatching { TONAddressConverter.toUserFriendly(address, true, false) }.getOrNull()

interface TonApi {

    suspend fun getBalance(address: String): BigInteger

    suspend fun getJettonBalance(address: String, contract: String): BigInteger

    suspend fun broadcastTransaction(transaction: String): String?

    suspend fun getSeqno(address: String): BigInteger

    suspend fun getWalletState(address: String): String

    suspend fun getJettonWallet(address: String, contract: String): JettonWalletsJson

    /**
     * Resolve the jetton master address for a given jetton wallet contract. Returns `null` when the
     * wallet is unknown to the indexer. Used to map a dApp transfer's destination wallet back to a
     * vault token.
     */
    suspend fun getJettonMasterAddress(jettonWalletAddress: String): String?

    /**
     * Resolve a jetton master's display metadata (ticker, decimals, logo) for rendering a swap's
     * output token, which the user does not hold and is not in `vault.coins`. Returns `null` when
     * the master is unknown to the indexer or carries no usable symbol.
     */
    suspend fun getJettonMetadata(masterAddress: String): TonJettonMetadata?

    /**
     * Resolve a DeDust pool's output token. A DeDust swap addresses the **pool**, not the output
     * jetton wallet, so the output asset is read from the pool's `get_assets` (the non-native
     * side). Returns the jetton master in raw `workchain:hex` form, or `null` for a TON-out pool /
     * on failure.
     */
    suspend fun getDedustPoolOutputMaster(poolAddress: String): String?

    suspend fun estimateFee(address: String, serializedBoc: String): BigInteger

    suspend fun getTsStatus(txHash: String): TonStatusResult
}

internal class TonApiImpl @Inject constructor(private val http: HttpClient) : TonApi {

    override suspend fun getBalance(address: String): BigInteger =
        getAddressInformation(address).balance

    override suspend fun getJettonBalance(address: String, contract: String): BigInteger {
        val wallet =
            getJettonWallet(address, contract).matchingWallet(contract, ::tonUserFriendlyAddress)
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

    override suspend fun getJettonWallet(address: String, contract: String): JettonWalletsJson =
        http
            .get("$BASE_URL/v3/jetton/wallets") {
                parameter("owner_address", address)
                parameter("jetton_master_address", contract)
            }
            .bodyOrThrow<JettonWalletsJson>()

    override suspend fun getJettonMasterAddress(jettonWalletAddress: String): String? =
        http
            .get("$BASE_URL/v3/jetton/wallets") {
                parameter("address", jettonWalletAddress)
                parameter("limit", 1)
            }
            .bodyOrThrow<JettonWalletsJson>()
            .getMasterAddress(jettonWalletAddress, ::tonUserFriendlyAddress)

    override suspend fun getJettonMetadata(masterAddress: String): TonJettonMetadata? {
        val content =
            http
                .get("$BASE_URL/v3/jetton/masters") {
                    parameter("address", masterAddress)
                    parameter("limit", 1)
                }
                .bodyOrThrow<JettonMastersJson>()
                .jettonMasters
                .firstOrNull()
                ?.jettonContent ?: return null
        val ticker = content.symbol?.takeIf { it.isNotBlank() } ?: return null
        // toncenter returns decimals as a string; default to 9 (TON's native scale) when absent.
        return TonJettonMetadata(
            ticker = ticker,
            decimals = content.decimals?.trim()?.toIntOrNull() ?: 9,
            logo = content.image?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun getDedustPoolOutputMaster(poolAddress: String): String? {
        val response =
            http
                .post("$BASE_URL/v3/runGetMethod") {
                    setBody(RunGetMethodRequest(address = poolAddress, method = "get_assets"))
                }
                .bodyOrThrow<RunGetMethodResponse>()
        if (response.exitCode != 0) return null
        // get_assets returns the pool's two Asset cells; the non-native one is the output jetton.
        return response.stack
            .asSequence()
            .filter { it.type == "cell" }
            .mapNotNull { it.value?.let(::parseDedustAssetMaster) }
            .firstOrNull()
    }

    /**
     * Parse a DeDust `Asset` cell: `native$0000` or `jetton$0001 workchain:int8 address:uint256`.
     * Returns the jetton master as raw `workchain:hex`, or `null` for the native asset / a
     * malformed cell.
     */
    @OptIn(ExperimentalStdlibApi::class)
    private fun parseDedustAssetMaster(cellBase64: String): String? =
        runCatching {
                val slice = TonBocParser.parse(cellBase64).beginParse()
                if (slice.loadUInt(4) != 1L) {
                    null
                } else {
                    val workchain = slice.loadUInt(8).toByte().toInt()
                    val hash = slice.loadUIntBig(256)
                    "$workchain:${hash.toString(16).padStart(64, '0')}"
                }
            }
            .getOrNull()

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
