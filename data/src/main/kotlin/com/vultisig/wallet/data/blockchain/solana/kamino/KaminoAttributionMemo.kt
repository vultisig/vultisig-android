package com.vultisig.wallet.data.blockchain.solana.kamino

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import wallet.core.jni.Base58
import wallet.core.jni.SolanaTransaction

/**
 * Appends Vultisig's attribution memo to a Kamino transaction.
 *
 * Kamino's kVault endpoints take no referrer or partner parameter, so attribution is client-side: a
 * single SPL Memo instruction carrying the literal bytes `8k2mz` rides along with every deposit and
 * withdraw. That tag is the filter every downstream measurement of Vultisig-originated deposits
 * keys on — Kamino's own attribution and the public Dune queries alike — so it must be
 * byte-identical here, on iOS and on Windows: the exact five bytes, and nothing else, in the data
 * field.
 *
 * The memo takes no accounts and adds no signer. Only its program has to enter the message's
 * account table, which WalletCore appends to the static keys while bumping
 * `numReadonlyUnsignedAccounts` and shifting every instruction index that addresses a
 * lookup-table-loaded account. Those indices sit immediately after the static keys, so inserting
 * one static key moves all of them — getting that wrong would silently point existing instructions
 * at the wrong accounts, which is why this delegates rather than re-encoding the message here.
 */
object KaminoAttributionMemo {

    /** SPL Memo v2. */
    const val MEMO_PROGRAM_ID = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"

    /**
     * The attribution tag itself. Five ASCII bytes, case-sensitive, never localised, never padded:
     * it is compared and filtered as bytes, so `8K2MZ` is a different memo that nothing downstream
     * would count.
     */
    const val TAG = "8k2mz"

    /**
     * Appends the memo to [base64Transaction] and returns the updated base64 transaction.
     *
     * @throws IllegalStateException if WalletCore cannot produce the updated transaction. Its FFI
     *   collapses every failure — malformed input, index overflow, a message grown too large — into
     *   a single null, so there is no error to distinguish. Failing here is deliberate: signing the
     *   unmodified transaction would silently drop the attribution the deposit exists to record.
     */
    fun append(base64Transaction: String): String =
        checkNotNull(
            SolanaTransaction.insertInstruction(base64Transaction, APPEND, instructionJson)
        ) {
            "WalletCore could not append the Kamino attribution memo"
        }

    /**
     * `-1` appends. Kamino builds no compute-budget instructions of its own and WalletCore requires
     * any the app injects to stay first, so appending keeps that ordering intact regardless of what
     * else has already been inserted.
     */
    private const val APPEND = -1

    /**
     * The instruction is a constant, so it is built once. `data` is base58 — the encoding
     * WalletCore's JSON instruction format expects, not base64.
     */
    private val instructionJson: String by lazy {
        buildJsonObject {
                put("programId", MEMO_PROGRAM_ID)
                put("accounts", JsonArray(emptyList()))
                put("data", Base58.encodeNoCheck(TAG.toByteArray(Charsets.US_ASCII)))
            }
            .toString()
    }
}
