package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.OPERATION_MINT
import com.vultisig.wallet.data.models.OPERATION_WITHDRAW

/**
 * Result of parsing a THORChain/MayaChain memo into deposit-screen fields.
 *
 * @property operation Display label for the action (e.g. "Mint", "Withdraw", "Bond").
 * @property pool Pool identifier from the memo, when present.
 * @property thorAddress Address embedded in the memo when it is a thor1/maya1 address.
 * @property pairedAddress Address embedded in the memo when it is on a non-thor chain (e.g. EVM,
 *   BTC).
 */
data class ParsedThorchainMemo(
    val operation: String,
    val pool: String = "",
    val thorAddress: String = "",
    val pairedAddress: String = "",
)

/**
 * Parses THORChain/MayaChain memo strings into deposit-screen fields so the joined-device verify
 * screen can render LP/bond/loan layouts on par with the initiator.
 *
 * Returns `null` for swaps and unrecognised memos so the caller can fall back to the default
 * deposit layout without inventing fields.
 */
object ThorchainMemoParser {

    /**
     * Parses [memo] and returns the extracted deposit metadata, or `null` for memos that are not
     * deposit-shaped (swaps, blank, unknown prefixes).
     */
    fun parse(memo: String): ParsedThorchainMemo? {
        val normalized = memo.trim()
        if (normalized.isEmpty()) return null
        val parts = normalized.split(":")
        val tag = parts[0].uppercase()

        return when (tag) {
            "+",
            "ADD" -> {
                val addr = parts.getOrNull(2).orEmpty()
                ParsedThorchainMemo(
                    operation = OPERATION_MINT,
                    pool = parts.getOrNull(1).orEmpty(),
                    thorAddress = addr.takeIfThorAddress(),
                    pairedAddress = addr.takeIfNonThorAddress(),
                )
            }
            "-",
            "WITHDRAW",
            "WD" ->
                ParsedThorchainMemo(
                    operation = OPERATION_WITHDRAW,
                    pool = parts.getOrNull(1).orEmpty(),
                )
            "BOND" -> {
                val addr = parts.getOrNull(1).orEmpty()
                ParsedThorchainMemo(
                    operation = "Bond",
                    thorAddress = addr.takeIfThorAddress(),
                    pairedAddress = addr.takeIfNonThorAddress(),
                )
            }
            "UNBOND" -> {
                val addr = parts.getOrNull(1).orEmpty()
                ParsedThorchainMemo(
                    operation = "Unbond",
                    thorAddress = addr.takeIfThorAddress(),
                    pairedAddress = addr.takeIfNonThorAddress(),
                )
            }
            "LEAVE" -> {
                val addr = parts.getOrNull(1).orEmpty()
                ParsedThorchainMemo(
                    operation = "Leave",
                    thorAddress = addr.takeIfThorAddress(),
                    pairedAddress = addr.takeIfNonThorAddress(),
                )
            }
            "LOAN+",
            "\$+" ->
                ParsedThorchainMemo(operation = "Loan Open", pool = parts.getOrNull(1).orEmpty())
            "LOAN-",
            "\$-" ->
                ParsedThorchainMemo(operation = "Loan Close", pool = parts.getOrNull(1).orEmpty())
            else -> null
        }
    }

    private fun String.isThorAddress(): Boolean =
        startsWith("thor1", ignoreCase = true) || startsWith("maya1", ignoreCase = true)

    private fun String.takeIfThorAddress(): String = if (isThorAddress()) this else ""

    private fun String.takeIfNonThorAddress(): String =
        if (isNotEmpty() && !isThorAddress()) this else ""
}
