package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.OPERATION_BOND
import com.vultisig.wallet.data.models.OPERATION_LEAVE
import com.vultisig.wallet.data.models.OPERATION_LOAN_CLOSE
import com.vultisig.wallet.data.models.OPERATION_LOAN_OPEN
import com.vultisig.wallet.data.models.OPERATION_MINT
import com.vultisig.wallet.data.models.OPERATION_UNBOND
import com.vultisig.wallet.data.models.OPERATION_WITHDRAW
import java.util.Locale
import javax.inject.Inject

/**
 * Result of parsing a THORChain/MayaChain memo into deposit-screen fields.
 *
 * @property operation Display label for the action (e.g. "Mint", "Withdraw", "Bond").
 * @property pool Pool identifier from the memo, when present.
 * @property nodeAddress Validator node thor1/maya1 address from BOND/UNBOND/LEAVE memos.
 * @property pairedAddress Asset-side address from LP ADD memos (any chain).
 * @property thorAddress User thor1/maya1 address from SECURE+ mint memos.
 */
data class ParsedThorchainMemo(
    val operation: String,
    val pool: String = "",
    val nodeAddress: String = "",
    val pairedAddress: String = "",
    val thorAddress: String = "",
)

/**
 * Parses THORChain/MayaChain memo strings into deposit-screen fields so the joined-device verify
 * screen can render LP/bond/loan layouts on par with the initiator.
 */
interface ThorchainMemoParser {

    /**
     * Parses [memo] and returns the extracted deposit metadata, or `null` for memos that are not
     * deposit-shaped (swaps, blank, unknown prefixes).
     */
    fun parse(memo: String): ParsedThorchainMemo?
}

internal class ThorchainMemoParserImpl @Inject constructor() : ThorchainMemoParser {

    override fun parse(memo: String): ParsedThorchainMemo? {
        val normalized = memo.trim()
        if (normalized.isEmpty()) return null
        val parts = normalized.split(":")
        val tag = parts[0].uppercase(Locale.ROOT)

        return when (tag) {
            "+",
            "ADD" ->
                ParsedThorchainMemo(
                    operation = OPERATION_MINT,
                    pool = parts.getOrNull(1).orEmpty(),
                    pairedAddress = parts.getOrNull(2).orEmpty(),
                )
            "SECURE+" ->
                ParsedThorchainMemo(
                    operation = OPERATION_MINT,
                    thorAddress =
                        parts.getOrNull(1).orEmpty().takeIf { it.isThorAddress() }.orEmpty(),
                )
            "-",
            "WITHDRAW",
            "WD" ->
                ParsedThorchainMemo(
                    operation = OPERATION_WITHDRAW,
                    pool = parts.getOrNull(1).orEmpty(),
                )
            "BOND" ->
                ParsedThorchainMemo(
                    operation = OPERATION_BOND,
                    nodeAddress = parts.extractNodeAddress(),
                )
            "UNBOND" ->
                ParsedThorchainMemo(
                    operation = OPERATION_UNBOND,
                    nodeAddress = parts.extractNodeAddress(),
                )
            "LEAVE" ->
                ParsedThorchainMemo(
                    operation = OPERATION_LEAVE,
                    nodeAddress = parts.extractNodeAddress(),
                )
            "LOAN+",
            "\$+" ->
                ParsedThorchainMemo(
                    operation = OPERATION_LOAN_OPEN,
                    pool = parts.getOrNull(1).orEmpty(),
                )
            "LOAN-",
            "\$-" ->
                ParsedThorchainMemo(
                    operation = OPERATION_LOAN_CLOSE,
                    pool = parts.getOrNull(1).orEmpty(),
                )
            else -> null
        }
    }

    private fun List<String>.extractNodeAddress(): String =
        drop(1).firstOrNull { it.isThorAddress() }.orEmpty()

    private fun String.isThorAddress(): Boolean =
        startsWith("thor1", ignoreCase = true) || startsWith("maya1", ignoreCase = true)
}
