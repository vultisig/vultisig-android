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
 * `nodeAddressError`, `providerError`, `dstAddressError`, `thorAddressError`, etc.), mutating it
 * through the shared [state].
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
     * Validates the destination address shown on the IBC Transfer and Switch sub-forms against the
     * appropriate chain (selected destination chain for IBC, source/Gaia chain for Switch),
     * surfacing inline errors via [DepositFormUiModel.dstAddressError]. Other deposit options leave
     * the field error untouched.
     */
    fun validateDstAddress() {
        val depositOption = state.value.depositOption
        val validationChain =
            when (depositOption) {
                DepositOption.TransferIbc -> state.value.selectedDstChain
                DepositOption.Switch -> chainProvider()
                else -> return
            }
        val dstAddress = fields.nodeAddressFieldState.text.toString()
        // For Switch the dst field is auto-populated from the THORChain inbound vault. When the
        // fetch returns halt/unavailable, the field is left blank and dstAddressError carries the
        // actionable reason; running the generic blank-check here would clobber that context.
        // Only skip when dstAddressError is already set (halt/unavailable) — if it's null the user
        // manually cleared the field in the healthy path, so we must validate and surface the blank
        // error to block Continue.
        if (
            depositOption == DepositOption.Switch &&
                dstAddress.isBlank() &&
                state.value.dstAddressError != null
        )
            return
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

    /** Validates the provider-address field, surfacing inline errors. */
    fun validateProvider() {
        val errorText =
            fieldValidator.addressErrorOrNull(
                chainProvider(),
                fields.providerFieldState.text.toString(),
            )
        state.update { it.copy(providerError = errorText) }
    }

    /** Validates the operator-fee basis-points field when non-empty. */
    fun validateOperatorFee() {
        val text = fields.operatorFeeFieldState.text.toString()
        if (text.isNotEmpty()) {
            val errorText = fieldValidator.validateBasisPoints(text.toIntOrNull())
            state.update { it.copy(operatorFeeError = errorText) }
        }
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

    /**
     * Validates the destination THORChain address on the Switch sub-form against ThorChain,
     * surfacing inline errors via [DepositFormUiModel.thorAddressError]. No-op outside the Switch
     * flow so SECURE+ auto-populated values do not trigger inline errors.
     */
    fun validateThorAddress() {
        if (state.value.depositOption != DepositOption.Switch) return
        val errorText =
            fieldValidator.addressErrorOrNull(
                Chain.ThorChain,
                fields.thorAddressFieldState.text.toString(),
            )
        state.update { it.copy(thorAddressError = errorText) }
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

    /** Validates the LP-units field, surfacing inline errors. */
    fun validateLpUnits() {
        val lpUnits = fields.lpUnitsFieldState.text.toString()
        state.update {
            it.copy(
                lpUnitsError =
                    if (!fieldValidator.isLpUnitCharsValid(lpUnits))
                        UiText.StringResource(R.string.deposit_error_invalid_lpunits)
                    else null
            )
        }
    }

    /** Sets the provider-address field. */
    fun setProvider(provider: String) {
        fields.providerFieldState.setTextAndPlaceCursorAtEnd(provider)
    }

    /** Sets the node-address field and revalidates. */
    fun setNodeAddress(address: String) {
        fields.nodeAddressFieldState.setTextAndPlaceCursorAtEnd(address)
        validateNodeAddress()
    }

    /** Sets the destination address on the IBC Transfer / Switch sub-forms and revalidates. */
    fun setDstAddress(address: String) {
        fields.nodeAddressFieldState.setTextAndPlaceCursorAtEnd(address)
        validateDstAddress()
    }

    /** Sets the THORChain destination address on the Switch sub-form and revalidates. */
    fun setThorAddress(address: String) {
        fields.thorAddressFieldState.setTextAndPlaceCursorAtEnd(address)
        validateThorAddress()
    }

    /** Sets the slippage field. */
    fun setSlippage(slippage: String) {
        fields.slippageFieldState.setTextAndPlaceCursorAtEnd(slippage)
    }
}
