package com.vultisig.wallet.ui.models.deposit.load

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.usecases.DepositMemoAssetsValidatorUseCase
import com.vultisig.wallet.ui.models.deposit.DepositFieldStates
import com.vultisig.wallet.ui.models.deposit.DepositFieldValidator
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.models.deposit.bondLpUnitsCeiling
import com.vultisig.wallet.ui.models.deposit.unbondLpUnitsCeiling
import com.vultisig.wallet.ui.utils.UiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the field-input layer extracted from `DepositFormViewModel`: the `validate*()` handlers, the
 * `set*()` field setters and the field-touching [selectDstChain] forward. It is the single writer
 * of the per-field validation-error slice of [DepositFormUiModel] (`tokenAmountError`,
 * `nodeAddressError`, `providerError`, `dstAddressError`, etc.), mutating it through the shared
 * [state].
 *
 * The validation rules ([fieldValidator]), the asset-chars check ([isAssetCharsValid]) and the
 * [accountsRepository] are Hilt-injected here; the ViewModel keeps `viewModelScope` ownership and
 * supplies it (assisted) along with the shared UI [state], the form-owned [fields], the existing
 * [nodeWhitelistChecker] seam and the [chainProvider] / [vaultId] accessors so this coordinator
 * never owns its own scope or VM state. Field handlers are synchronous; only [selectDstChain]
 * launches work, and it does so on the supplied scope.
 */
internal class DepositFieldInputCoordinator
@AssistedInject
constructor(
    private val fieldValidator: DepositFieldValidator,
    private val isAssetCharsValid: DepositMemoAssetsValidatorUseCase,
    private val accountsRepository: AccountsRepository,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val state: MutableStateFlow<DepositFormUiModel>,
    @Assisted private val fields: DepositFieldStates,
    @Assisted private val nodeWhitelistChecker: NodeWhitelistChecker,
    @Assisted private val chainProvider: () -> Chain?,
    @Assisted private val vaultId: () -> String?,
) {

    /** @see DepositFieldInputCoordinator */
    @AssistedFactory
    interface Factory {
        /**
         * Creates a [DepositFieldInputCoordinator] bound to the given scope, shared state, form
         * fields, whitelist checker and the chain / vault accessors.
         */
        fun create(
            scope: CoroutineScope,
            state: MutableStateFlow<DepositFormUiModel>,
            fields: DepositFieldStates,
            nodeWhitelistChecker: NodeWhitelistChecker,
            chainProvider: () -> Chain?,
            vaultId: () -> String?,
        ): DepositFieldInputCoordinator
    }

    /**
     * Clears the node-address field, selects [chain] as the destination and loads that chain's
     * address into the node-address field.
     */
    fun selectDstChain(chain: Chain) {
        fields.nodeAddressFieldState.clearText()

        state.update { it.copy(selectedDstChain = chain, dstAddressError = null) }

        scope.launch {
            val vaultId = vaultId() ?: return@launch
            val address = accountsRepository.loadAddress(vaultId, chain).firstOrNull()

            if (address != null) {
                fields.nodeAddressFieldState.setTextAndPlaceCursorAtEnd(address.address)
            }
        }
    }

    /**
     * Validates the destination address shown on the IBC Transfer sub-form against the selected
     * destination chain, surfacing inline errors via [DepositFormUiModel.dstAddressError]. Other
     * deposit options leave the field error untouched.
     */
    fun validateDstAddress() {
        val depositOption = state.value.depositOption
        val validationChain =
            when (depositOption) {
                DepositOption.TransferIbc -> state.value.selectedDstChain
                else -> return
            }
        val dstAddress = fields.nodeAddressFieldState.text.toString()
        val error = fieldValidator.dstAddressErrorOrNull(validationChain, dstAddress)
        state.update { it.copy(dstAddressError = error) }
    }

    /** Validates the node-address field, delegating to [nodeWhitelistChecker] for MAYA Bond. */
    fun validateNodeAddress() {
        val nodeAddress = fields.nodeAddressFieldState.text.toString()
        val errorText = fieldValidator.addressErrorOrNull(chainProvider(), nodeAddress)
        if (errorText != null) {
            nodeWhitelistChecker.cancel()
            state.update { it.copy(nodeAddressError = errorText, isCheckingWhitelist = false) }
            return
        }
        if (chainProvider() == Chain.MayaChain && state.value.depositOption == DepositOption.Bond) {
            nodeWhitelistChecker.check(nodeAddress)
        } else {
            state.update { it.copy(nodeAddressError = null) }
        }
    }

    /** Validates the token-amount field, surfacing inline errors. */
    fun validateTokenAmount() {
        val errorText =
            fieldValidator.validateTokenAmount(fields.tokenAmountFieldState.text.toString())
        state.update { it.copy(tokenAmountError = errorText) }
    }

    /** Validates the provider-address field when non-empty; it is an optional field. */
    fun validateProvider() {
        val provider = fields.providerFieldState.text.toString()
        val errorText =
            if (provider.isNotEmpty()) {
                fieldValidator.addressErrorOrNull(chainProvider(), provider)
            } else {
                null
            }
        state.update { it.copy(providerError = errorText) }
    }

    /** Validates the operator-fee basis-points field when non-empty. */
    fun validateOperatorFee() {
        val text = fields.operatorFeeFieldState.text.toString()
        val errorText =
            if (text.isNotEmpty()) fieldValidator.validateOperatorFee(text.toIntOrNull()) else null
        state.update { it.copy(operatorFeeError = errorText) }
    }

    /** Validates the custom-memo field, surfacing inline errors. */
    fun validateCustomMemo() {
        val errorText =
            fieldValidator.validateCustomMemo(fields.customMemoFieldState.text.toString())
        state.update { it.copy(customMemoError = errorText) }
    }

    /** Validates the basis-points field when non-empty. */
    fun validateBasisPoints() {
        val text = fields.basisPointsFieldState.text.toString()
        if (text.isNotEmpty()) {
            val errorText = fieldValidator.validateBasisPoints(text.toIntOrNull())
            state.update { it.copy(basisPointsError = errorText) }
        }
    }

    /** Validates the slippage field, surfacing inline errors. */
    fun validateSlippage() {
        val text = fields.slippageFieldState.text.toString()
        val errorText = fieldValidator.validateSlippage(text)
        state.update { it.copy(slippageError = errorText) }
    }

    /** Validates the assets field, surfacing inline errors. */
    fun validateAssets() {
        val assets = fields.assetsFieldState.text.toString()
        state.update {
            it.copy(
                assetsError =
                    if (!isAssetCharsValid(assets))
                        UiText.StringResource(R.string.deposit_error_invalid_assets)
                    else null
            )
        }
    }

    /**
     * Validates the LP-units field: the character check, then the ceiling the loaded MayaChain
     * position allows.
     *
     * MayaChain rejects an over-ceiling BOND/UNBOND rather than clamping it, refunding the deposit
     * minus the network fee — so the figure has to be caught here, before the user spends a
     * multi-device keysign ceremony on a memo the chain will not apply.
     */
    fun validateLpUnits() {
        val lpUnits = fields.lpUnitsFieldState.text.toString()
        state.update { it.copy(lpUnitsError = lpUnitsErrorOrNull(lpUnits, it)) }
    }

    private fun lpUnitsErrorOrNull(lpUnits: String, current: DepositFormUiModel): UiText? {
        if (!fieldValidator.isLpUnitCharsValid(lpUnits)) {
            return UiText.StringResource(R.string.deposit_error_invalid_lpunits)
        }
        val ceiling =
            when (current.depositOption) {
                DepositOption.Bond -> current.bondLpUnitsCeiling()
                DepositOption.Unbond ->
                    current.unbondLpUnitsCeiling(
                        nodeAddress = fields.nodeAddressFieldState.text.toString(),
                        asset = fields.assetsFieldState.text.toString(),
                    )
                else -> null
            } ?: return null
        val entered = lpUnits.toBigIntegerOrNull() ?: return null
        if (entered <= ceiling) return null
        return if (current.depositOption == DepositOption.Unbond) {
            UiText.StringResource(R.string.deposit_error_lpunits_exceeds_bonded)
        } else {
            UiText.StringResource(R.string.deposit_error_lpunits_exceeds_available)
        }
    }

    /** Sets the provider-address field and revalidates. */
    fun setProvider(provider: String) {
        fields.providerFieldState.setTextAndPlaceCursorAtEnd(provider)
        validateProvider()
    }

    /** Sets the node-address field and revalidates. */
    fun setNodeAddress(address: String) {
        fields.nodeAddressFieldState.setTextAndPlaceCursorAtEnd(address)
        validateNodeAddress()
    }

    /** Sets the destination address on the IBC Transfer sub-form and revalidates. */
    fun setDstAddress(address: String) {
        fields.nodeAddressFieldState.setTextAndPlaceCursorAtEnd(address)
        validateDstAddress()
    }

    /** Sets the slippage field. */
    fun setSlippage(slippage: String) {
        fields.slippageFieldState.setTextAndPlaceCursorAtEnd(slippage)
    }
}
