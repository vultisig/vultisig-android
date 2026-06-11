package com.vultisig.wallet.ui.models.deposit

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.utils.TextFieldUtils
import com.vultisig.wallet.ui.utils.UiText
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Pure field-level validation for the deposit form, extracted from `DepositFormViewModel` so each
 * rule is unit-testable in isolation. Mirrors the `DepositMemoAssetsValidatorUseCase` pattern:
 * stateless logic depending only on injected repositories, returning inline [UiText] errors (or
 * `null` when valid) without touching view-model state.
 */
internal interface DepositFieldValidator {

    /**
     * Returns a generic address-format error or `null` if [address] parses as valid for [chain].
     * Used where the field label is already chain-specific in the UI, so a single "Address is
     * invalid" message suffices.
     */
    fun addressErrorOrNull(chain: Chain?, address: String): UiText?

    /**
     * Returns a destination-address error specific to the IBC/Switch dst field, distinguishing
     * blank from invalid-format so the user sees the more actionable message.
     */
    fun dstAddressErrorOrNull(chain: Chain?, dstAddress: String): UiText?

    /**
     * Returns an error if [tokenAmount] is too long, non-numeric, or negative; `null` otherwise.
     */
    fun validateTokenAmount(tokenAmount: String): UiText?

    /**
     * Returns an error if [basisPoints] is null or outside the `1..100` range; `null` otherwise.
     */
    fun validateBasisPoints(basisPoints: Int?): UiText?

    /** Returns an error if [memo] is blank; `null` otherwise. */
    fun validateCustomMemo(memo: String): UiText?

    /**
     * Returns an error if [slippage] is blank, non-numeric, or outside `0..100`; `null` otherwise.
     */
    fun validateSlippage(slippage: String?): UiText?

    /** Returns `true` if [lpUnits] is a positive whole number. */
    fun isLpUnitCharsValid(lpUnits: String): Boolean
}

internal class DepositFieldValidatorImpl
@Inject
constructor(private val chainAccountAddressRepository: ChainAccountAddressRepository) :
    DepositFieldValidator {

    override fun addressErrorOrNull(chain: Chain?, address: String): UiText? {
        if (chain == null) return UiText.StringResource(R.string.dialog_default_error_title)
        if (address.isBlank() || !chainAccountAddressRepository.isValid(chain, address))
            return UiText.StringResource(R.string.send_error_no_address)
        return null
    }

    override fun dstAddressErrorOrNull(chain: Chain?, dstAddress: String): UiText? {
        if (chain == null) return UiText.StringResource(R.string.dialog_default_error_title)
        if (dstAddress.isBlank())
            return UiText.StringResource(R.string.deposit_error_destination_address)
        if (!chainAccountAddressRepository.isValid(chain, dstAddress))
            return UiText.StringResource(R.string.deposit_error_invalid_destination_address)
        return null
    }

    override fun validateTokenAmount(tokenAmount: String): UiText? {
        if (tokenAmount.length > TextFieldUtils.AMOUNT_MAX_LENGTH)
            return UiText.StringResource(R.string.send_from_invalid_amount)
        val tokenAmountBigDecimal = tokenAmount.toBigDecimalOrNull()
        if (tokenAmountBigDecimal == null || tokenAmountBigDecimal < BigDecimal.ZERO) {
            return UiText.StringResource(R.string.send_error_no_amount)
        }
        return null
    }

    override fun validateBasisPoints(basisPoints: Int?): UiText? {
        if (basisPoints == null || basisPoints <= 0 || basisPoints > 100) {
            return UiText.StringResource(R.string.send_from_invalid_amount)
        }
        return null
    }

    override fun validateCustomMemo(memo: String): UiText? =
        if (memo.isBlank()) {
            UiText.StringResource(R.string.dialog_default_error_title)
        } else {
            null
        }

    override fun validateSlippage(slippage: String?): UiText? {
        if (slippage.isNullOrBlank()) {
            return UiText.StringResource(R.string.slippage_required_error)
        }

        return try {
            val value = slippage.toBigDecimal()
            if (value < BigDecimal.ZERO || value > BigDecimal("100")) {
                UiText.StringResource(R.string.slippage_invalid_error)
            } else {
                null
            }
        } catch (e: NumberFormatException) {
            UiText.StringResource(R.string.slippage_format_error)
        }
    }

    override fun isLpUnitCharsValid(lpUnits: String): Boolean =
        lpUnits.toLongOrNull() != null && lpUnits.all { it.isDigit() } && lpUnits.toLong() > 0
}

/**
 * Hilt bindings that expose [DepositFieldValidatorImpl] as the [DepositFieldValidator] interface.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface DepositFieldValidatorModule {

    /** Binds the concrete [DepositFieldValidatorImpl] to the [DepositFieldValidator] interface. */
    @Binds fun bindDepositFieldValidator(impl: DepositFieldValidatorImpl): DepositFieldValidator
}
