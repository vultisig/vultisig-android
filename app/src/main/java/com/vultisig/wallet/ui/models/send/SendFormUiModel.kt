package com.vultisig.wallet.ui.models.send

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.vultisig.wallet.R.string
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.maxMemoCharacters
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.ui.models.send.AmountFraction.F100
import com.vultisig.wallet.ui.models.send.AmountFraction.F25
import com.vultisig.wallet.ui.models.send.AmountFraction.F50
import com.vultisig.wallet.ui.models.send.AmountFraction.F75
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import java.math.BigInteger

@Immutable
internal data class TokenBalanceUiModel(
    val model: SendSrc,
    val title: String,
    val balance: String?,
    val fiatValue: String?,
    val isNativeToken: Boolean,
    val isLayer2: Boolean,
    val tokenStandard: String?,
    val tokenLogo: ImageModel,
    @param:DrawableRes val chainLogo: Int,
)

sealed class AmountFraction(val title: UiText, val value: Float) {
    data object F25 : AmountFraction(title = "25%".asUiText(), value = 0.25f)

    data object F50 : AmountFraction(title = "50%".asUiText(), value = 0.5f)

    data object F75 : AmountFraction(title = "75%".asUiText(), value = 0.75f)

    data object F100 : AmountFraction(title = string.send_screen_max.asUiText(), value = 1f)
}

@Immutable
internal data class SendFormUiModel(
    val selectedCoin: TokenBalanceUiModel? = null,
    val fiatCurrency: String = "",

    // src data
    val srcAddress: String = "",
    val srcVaultName: String = "",

    // dst data
    val isDstAddressComplete: Boolean = false,

    // fees
    val totalGas: UiText = UiText.Empty,
    val gasTokenBalance: UiText? = null,
    val estimatedFee: UiText = UiText.Empty,
    val isGasFeeLoading: Boolean = true,

    // type
    val defiType: DeFiNavActions? = null,
    val slippage: String = "1.0",
    val isAutocompound: Boolean = false,

    // errors
    val errorText: UiText? = null,
    val dstAddressError: UiText? = null,
    val tokenAmountError: UiText? = null,
    val memoError: UiText? = null,
    val reapingError: UiText? = null,
    val hasMemo: Boolean = false,
    // XRP: show a dedicated destination-tag field instead of the free-text memo field.
    val isDestinationTag: Boolean = false,
    val destinationTagLocked: Boolean = false,
    val showGasFee: Boolean = true,
    val hasGasSettings: Boolean = false,
    val showGasSettings: Boolean = false,
    val specific: BlockChainSpecificAndUtxo? = null,
    val expandedSection: SendSections = SendSections.Asset,
    val usingTokenAmountInput: Boolean = true,
    // Whether the entered token amount is present and valid. Used to gate Continue on the TRON
    // freeze/unfreeze screens, where the network fee is amount-independent and resolves on entry.
    val isAmountValid: Boolean = false,
    // Whether the amount field holds any text. Separates a fee estimate that is genuinely in
    // flight from the pre-entry state, where isGasFeeLoading still sits at its initial true and
    // no estimate has been armed yet.
    val hasAmountInput: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isAmountSelectionLoading: Boolean = false,
    val selectedAmountFraction: AmountFraction? = null,
    val amountFractionEntries: List<AmountFraction> = listOf(F25, F50, F75, F100),

    // Tron freeze/unfreeze
    val tronResourceType: TronResourceType? = null,
    val tronBalanceAvailableOverride: String? = null,
    val isTronFrozenBalancesLoading: Boolean = false,
    val hasTronFrozenBalancesError: Boolean = false,
)

/** Whether an invalid recipient should block the form. */
internal val SendFormUiModel.isDstAddressBlocking: Boolean
    get() = dstAddressError != null

/**
 * Whether an over-long memo should block the form.
 *
 * Only the free-text memo field the form actually renders blocks: DeFi flows build their own memos,
 * and a token without a memo field ([hasMemo] false) offers the user no way to shorten one, so
 * blocking there would disable Continue with no visible reason.
 */
internal val SendFormUiModel.isMemoBlocking: Boolean
    get() = memoError != null && hasMemo && defiType == null

/**
 * Whether the Continue action should be disabled for the current form state.
 *
 * The gas-fee-loading gate applies to every flow: while the network fee is (re)computing —
 * including the recompute triggered by tapping a percentage/MAX button — Continue must stay
 * disabled so it can't submit against a fee still shown as loading.
 *
 * The percentage/MAX calculation itself ([isAmountSelectionLoading]) gates for the same reason: it
 * is about to overwrite the amount field, so submitting mid-calculation would sign the amount the
 * user just replaced.
 *
 * TRON freeze/unfreeze pays a fixed, amount-independent fee that the orchestrator resolves on entry
 * with a zero-amount placeholder (see GasFeeOrchestrator), so [isGasFeeLoading] clears before any
 * amount is entered and can no longer stand in for "an amount is present". These flows are
 * therefore additionally gated on [isAmountValid], and Unfreeze on [isTronFrozenBalancesLoading] so
 * Continue cannot enable before the frozen balance loads — otherwise submit coerces the still-null
 * balance to zero and falsely rejects a fundable unfreeze.
 *
 * An invalid recipient the user can correct ([isDstAddressBlocking]) also disables Continue so the
 * flow can't proceed from an address that failed chain validation — the inline error is the visible
 * reason, and correcting the field clears it and re-enables Continue.
 *
 * A memo over the chain's ceiling ([isMemoBlocking]) disables Continue for the same reason: the
 * node rejects it at broadcast with "memo too large", so letting the flow reach keysign would burn
 * a full multi-device signing ceremony on a transaction that can never be accepted (issue #5435).
 *
 * @return true when Continue must stay disabled.
 */
internal fun SendFormUiModel.isContinueDisabled(): Boolean =
    isLoading ||
        isAmountSelectionLoading ||
        isDstAddressBlocking ||
        isMemoBlocking ||
        (showGasFee && isGasFeeLoading) ||
        (tronResourceType != null && (!isAmountValid || isTronFrozenBalancesLoading))

/**
 * Whether Continue is disabled because work is in flight rather than because the form holds
 * something the user has to fix — the button then shows its loading indicator instead of greying
 * out with no stated reason.
 *
 * Covers the submit itself, the percentage/MAX calculation, and the fee recompute that selection
 * triggers: tapping a percentage is the case the user actually waits on, and on the flows that hide
 * the fee row (Circle withdraw) the shimmering fee is not even on screen to explain the wait.
 *
 * The fee window is additionally gated on [hasAmountInput]: [isGasFeeLoading] also holds its
 * initial true before any amount exists, and no estimate is armed there — nothing is loading, so
 * Continue stays plainly disabled until the user enters an amount.
 *
 * @return true when Continue should render as loading.
 */
internal fun SendFormUiModel.isContinueLoading(): Boolean =
    isLoading || isAmountSelectionLoading || (hasAmountInput && showGasFee && isGasFeeLoading)

/**
 * Validates a memo against the chain's published ceiling ([Chain.maxMemoCharacters]).
 *
 * Shared by the form (inline error + Continue gate) and the submit strategies, so a memo the node
 * would reject can neither pass the form nor slip through a submit that races the form's
 * validation.
 *
 * Measured in UTF-8 bytes, matching what the node compares against the limit (Cosmos SDK
 * `ValidateMemo` measures the encoded length, so a multi-byte memo can sit within the limit as a
 * character count and still be rejected). The error reports that same byte length, so it never
 * names a number that looks like it fits.
 *
 * A blank memo never errors, matching the submit path dropping a whitespace-only memo to null: the
 * signed transaction carries no memo at all, so blocking on its length would disable Continue over
 * something the node never sees.
 *
 * @param chain the chain the memo will be signed for; `null` (no chain selected yet) never errors.
 * @return a [UiText] error naming the current length and the limit, or null when the memo fits.
 */
internal fun memoLengthErrorOrNull(chain: Chain?, memo: String): UiText? {
    if (memo.isBlank()) return null
    val limit = chain?.maxMemoCharacters ?: return null
    val bytes = memo.toByteArray(Charsets.UTF_8).size
    if (bytes <= limit) return null
    return UiText.FormattedText(string.send_error_memo_too_long, listOf(bytes, limit))
}

internal data class SendSrc(val address: Address, val account: Account)

internal enum class SendSections {
    Asset,
    Address,
    Amount,
}

internal enum class SendFocusField {
    ADDRESS,
    AMOUNT,
}

enum class AddressBookType {
    OUTPUT
}

internal sealed class GasSettings {
    data class Eth(val baseFee: BigInteger, val priorityFee: BigInteger, val gasLimit: BigInteger) :
        GasSettings() {
        init {
            require(baseFee >= BigInteger.ZERO) { "baseFee must be non-negative" }
            require(priorityFee >= BigInteger.ZERO) { "priorityFee must be non-negative" }
        }

        // Single source of truth for the EIP-1559 fee cap: both the signed transaction
        // (DefaultSendStrategy.applyGasSettings) and the displayed estimate
        // (selectGasFeeForFeeEstimation) must derive maxFeePerGas from this, or the two can
        // silently disagree (issue #5397).
        val maxFeePerGasWei: BigInteger
            get() = baseFee + priorityFee
    }

    data class UTXO(val byteFee: BigInteger) : GasSettings()
}

internal data class InvalidTransactionDataException(val text: UiText) : Exception()
