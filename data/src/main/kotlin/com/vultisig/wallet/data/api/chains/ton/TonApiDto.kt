package com.vultisig.wallet.data.api.chains.ton

import java.math.BigInteger
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class TonAddressInfoResponseJson(
    @SerialName("balance") @Contextual val balance: BigInteger,
    @SerialName("status") val status: String,
)

@Serializable
internal data class TonBroadcastTransactionRequestJson(@SerialName("boc") val boc: String)

@Serializable
internal data class TonBroadcastTransactionResponseJson(
    @SerialName("result") val result: TonBroadcastTransactionResponseResultJson?,
    @SerialName("error") val error: String?,
)

@Serializable
internal data class TonBroadcastTransactionResponseResultJson(@SerialName("hash") val hash: String)

@Serializable
internal data class TonSpecificTransactionInfoResponseJson(
    @SerialName("result") val result: TonSpecificTransactionInfoResponseResultJson
)

@Serializable
internal data class TonSpecificTransactionInfoResponseResultJson(
    @SerialName("account_state")
    val accountState: TonSpecificTransactionInfoResponseAccountStateJson
)

@Serializable
internal data class TonSpecificTransactionInfoResponseAccountStateJson(
    @SerialName("seqno") val seqno: JsonPrimitive?
)

@Serializable
data class JettonWalletsJson(
    @SerialName("jetton_wallets") val jettonWallets: List<JettonWalletJson> = emptyList(),
    @SerialName("address_book") val addressBook: Map<String, AddressEntryJson> = emptyMap(),
) {
    /**
     * Find the jetton wallet whose master matches [master]. The indexer's `jetton_master_address`
     * filter is not honored, so the response may contain wallets for other masters; matching
     * compares both the raw `jetton` field and its user-friendly form from the address book.
     * Returns `null` when no wallet matches.
     *
     * TON addresses have multiple equal-but-non-identical encodings (bounceable `EQ…` vs
     * non-bounceable `UQ…`, URL-safe base64, raw `0:hex`), so both sides are routed through
     * [toUserFriendly] before comparing — consistent with the codebase's canonicalize-then-compare
     * pattern. The default identity keeps the raw semantics for tests.
     *
     * @param master requested jetton master, in raw (`0:…`) or user-friendly (`EQ…`) form.
     * @param toUserFriendly canonicalizes an address to its user-friendly form for comparison;
     *   returns `null` for a non-address value.
     */
    fun matchingWallet(
        master: String,
        toUserFriendly: (String) -> String? = { it },
    ): JettonWalletJson? {
        val canonicalMaster = toUserFriendly(master) ?: master
        return jettonWallets.firstOrNull { wallet ->
            (toUserFriendly(wallet.jetton) ?: wallet.jetton) == canonicalMaster ||
                addressBook[wallet.jetton]?.userFriendly?.let { toUserFriendly(it) ?: it } ==
                    canonicalMaster
        }
    }

    /**
     * Resolve the user-friendly jetton wallet address for the wallet holding [master]. Returns
     * `null` when no returned wallet matches the requested master (never an arbitrary fallback).
     *
     * @param master requested jetton master, in raw (`0:…`) or user-friendly (`EQ…`) form.
     * @param toUserFriendly canonicalizes an address to its user-friendly form for comparison.
     */
    fun getJettonsAddress(master: String, toUserFriendly: (String) -> String? = { it }): String? {
        val jettonAddress = matchingWallet(master, toUserFriendly)?.address ?: return null
        return addressBook[jettonAddress]?.userFriendly
    }

    /**
     * Resolve the jetton master address (in user-friendly form when the address book provides it)
     * for the wallet whose `address` matches [walletAddress]. Used to map a jetton wallet back to
     * the token it holds when decoding a dApp transfer. Returns `null` when no wallet matches.
     *
     * Both sides are canonicalized through [toUserFriendly] so encodings that differ only by
     * bounceable flag / URL-safe base64 / raw form still match.
     *
     * @param walletAddress the jetton wallet address, in raw (`0:…`) or user-friendly (`EQ…`) form.
     * @param toUserFriendly canonicalizes an address to its user-friendly form for comparison.
     */
    fun getMasterAddress(
        walletAddress: String,
        toUserFriendly: (String) -> String? = { it },
    ): String? {
        val canonicalWallet = toUserFriendly(walletAddress) ?: walletAddress
        val master =
            jettonWallets
                .firstOrNull { wallet ->
                    (toUserFriendly(wallet.address) ?: wallet.address) == canonicalWallet ||
                        addressBook[wallet.address]?.userFriendly?.let {
                            toUserFriendly(it) ?: it
                        } == canonicalWallet
                }
                ?.jetton ?: return null
        return addressBook[master]?.userFriendly ?: master
    }
}

@Serializable
data class JettonWalletJson(
    @SerialName("address") val address: String,
    @SerialName("jetton") val jetton: String,
    @SerialName("balance") val balance: String,
)

@Serializable data class AddressEntryJson(@SerialName("user_friendly") val userFriendly: String)

@Serializable
data class JettonMastersJson(
    @SerialName("jetton_masters") val jettonMasters: List<JettonMasterJson> = emptyList()
)

@Serializable
data class JettonMasterJson(
    @SerialName("address") val address: String? = null,
    @SerialName("jetton_content") val jettonContent: JettonContentJson? = null,
)

@Serializable
data class JettonContentJson(
    @SerialName("symbol") val symbol: String? = null,
    // toncenter encodes decimals as a string (e.g. "6").
    @SerialName("decimals") val decimals: String? = null,
    @SerialName("image") val image: String? = null,
)

@Serializable
internal data class RunGetMethodRequest(
    @SerialName("address") val address: String,
    @SerialName("method") val method: String,
    @SerialName("stack") val stack: List<String> = emptyList(),
)

@Serializable
internal data class RunGetMethodResponse(
    @SerialName("exit_code") val exitCode: Int = -1,
    @SerialName("stack") val stack: List<RunGetMethodStackEntry> = emptyList(),
)

@Serializable
internal data class RunGetMethodStackEntry(
    @SerialName("type") val type: String,
    @SerialName("value") val value: String? = null,
)

@Serializable
data class TonEstimateFeeJson(
    @SerialName("ok") val ok: Boolean,
    @SerialName("result") val result: TonFeeResult? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("code") val code: Int? = null,
)

@Serializable
data class TonFeeResult(
    @SerialName("@type") val type: String,
    @SerialName("source_fees") val sourceFees: TonFees,
    @SerialName("destination_fees") val destinationFees: List<TonFees> = emptyList(),
    @SerialName("@extra") val extra: String? = null,
)

@Serializable
data class TonFees(
    @SerialName("@type") val type: String,
    @SerialName("in_fwd_fee") val inFwdFee: Long,
    @SerialName("storage_fee") val storageFee: Long,
    @SerialName("gas_fee") val gasFee: Long,
    @SerialName("fwd_fee") val fwdFee: Long,
) {
    fun totalFee(): Long = inFwdFee + storageFee + gasFee + fwdFee
}

@Serializable
data class TonStatusResult(
    @SerialName("transactions") val transactions: List<TransactionJson> = emptyList()
)

@Serializable
data class TransactionJson(
    @SerialName("description") val description: TonTransactionDescriptionJson? = null
)

@Serializable
data class TonTransactionDescriptionJson(
    @SerialName("aborted") val aborted: Boolean? = null,
    @SerialName("destroyed") val destroyed: Boolean? = null,
    @SerialName("compute_ph") val computePhase: TonComputePhaseJson? = null,
    @SerialName("action") val actionPhase: TonActionPhaseJson? = null,
)

/**
 * TVM compute phase of a TON transaction. A `null` [exitCode] means the message carried no compute
 * phase (a plain transfer to a wallet), which is not a failure. Exit codes 0 and 1 are the TVM
 * success conventions; any other code is a revert.
 */
@Serializable data class TonComputePhaseJson(@SerialName("exit_code") val exitCode: Int? = null)

/**
 * Action phase of a TON transaction — where the wallet contract actually emits its outgoing
 * transfer message(s). Because every Vultisig TON send is signed with `IGNORE_ACTION_PHASE_ERRORS`,
 * an action that can't be carried out (e.g. insufficient balance for value+fees) is silently
 * skipped instead of aborting the transaction, so these fields — not `aborted` — are what reveal a
 * transfer that never moved any funds.
 */
@Serializable
data class TonActionPhaseJson(
    @SerialName("success") val success: Boolean? = null,
    @SerialName("no_funds") val noFunds: Boolean? = null,
    @SerialName("result_code") val resultCode: Int? = null,
    @SerialName("skipped_actions") val skippedActions: Int? = null,
)
