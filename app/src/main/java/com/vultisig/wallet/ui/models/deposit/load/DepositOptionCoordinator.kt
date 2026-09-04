package com.vultisig.wallet.ui.models.deposit.load

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.crypto.getChainName
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.isSecuredAsset
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.ui.models.deposit.DepositFieldStates
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.models.deposit.TokenWithdrawSecureAsset
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns the per-option selection orchestration extracted from `DepositFormViewModel`: the `when`
 * dispatch that runs when the user switches [DepositOption], including Bond/Unbond/Leave
 * default-token selection, MAYA bondable-asset load, RemoveLiquidity per-chain routing, the
 * WithdrawSecuredAsset address collector and the RemoveCacao unstake-balance fetch.
 *
 * The repos / API / mapper are Hilt-injected here; the ViewModel keeps `viewModelScope` ownership
 * and supplies it (assisted) along with the shared UI [state], the [address] flow, the form-owned
 * [fields], the existing [liquidityDataLoader] / [securedAssetLoader] / [cacaoMaturityLoader] seams
 * and the [chainProvider] / [vaultId] / [bondAddress] accessors so this coordinator never owns its
 * own scope or VM state. It owns the `withdrawSecuredAssetJob` reference but launches it on the
 * supplied scope.
 */
internal class DepositOptionCoordinator
@AssistedInject
constructor(
    private val mayaChainApi: MayaChainApi,
    private val accountsRepository: AccountsRepository,
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val state: MutableStateFlow<DepositFormUiModel>,
    @Assisted private val address: StateFlow<Address?>,
    @Assisted private val fields: DepositFieldStates,
    @Assisted private val liquidityDataLoader: LiquidityDataLoader,
    @Assisted private val securedAssetLoader: SecuredAssetLoader,
    @Assisted private val cacaoMaturityLoader: CacaoMaturityLoader,
    @Assisted private val chainProvider: () -> Chain?,
    @Assisted("vaultId") private val vaultId: () -> String?,
    @Assisted("bondAddress") private val bondAddress: () -> String?,
) {

    /** @see DepositOptionCoordinator */
    @AssistedFactory
    interface Factory {
        /**
         * Creates a [DepositOptionCoordinator] bound to the given scope, shared state, address
         * flow, form fields, existing loaders and the chain / vault / bond-address accessors.
         */
        fun create(
            scope: CoroutineScope,
            state: MutableStateFlow<DepositFormUiModel>,
            address: StateFlow<Address?>,
            fields: DepositFieldStates,
            liquidityDataLoader: LiquidityDataLoader,
            securedAssetLoader: SecuredAssetLoader,
            cacaoMaturityLoader: CacaoMaturityLoader,
            chainProvider: () -> Chain?,
            @Assisted("vaultId") vaultId: () -> String?,
            @Assisted("bondAddress") bondAddress: () -> String?,
        ): DepositOptionCoordinator
    }

    private var withdrawSecuredAssetJob: Job? = null
    private var nodeAddressWatchJob: Job? = null

    /**
     * Switches the active deposit form to [option], resetting the form fields and running the
     * option-specific load/auto-population. Cancels any in-flight Remove LP / WithdrawSecuredAsset
     * work before re-selecting so stale callbacks can't write into the new option.
     */
    fun selectDepositOption(option: DepositOption) {
        // Stop any in-flight Remove LP fetch so it can't write stale state into the new option.
        liquidityDataLoader.cancelLoad()
        // Stop the previous WithdrawSecuredAsset address collector so re-selecting the option does
        // not leak an additional permanent collector running handleWithdrawSecuredAsset.
        withdrawSecuredAssetJob?.cancel()
        // Likewise the Unbond node-address watcher: it belongs to the option being left.
        nodeAddressWatchJob?.cancel()
        scope.launch {
            resetTextFields()
            state.update { it.copy(depositOption = option) }
            val chain = chainProvider()

            when (option) {
                DepositOption.Bond,
                DepositOption.Unbond -> {
                    val defaultBondToken =
                        if (chain == Chain.MayaChain) Coins.MayaChain.CACAO
                        else Coins.ThorChain.RUNE
                    state.update {
                        it.copy(selectedToken = defaultBondToken, unstakableAmount = null)
                    }
                    // The Maya asset load runs after the node-address prefill below: Unbond is
                    // scoped to that node, and reading the field before it is filled would ask
                    // about no node at all.
                }

                DepositOption.Leave -> {
                    val leaveToken =
                        if (chain == Chain.MayaChain) Coins.MayaChain.CACAO
                        else Coins.ThorChain.RUNE
                    state.update { it.copy(selectedToken = leaveToken, unstakableAmount = null) }
                }

                DepositOption.RemoveCacaoPool -> {
                    handleRemoveCacaoOption()
                }

                DepositOption.AddLiquidity -> {
                    val token =
                        if (chain == Chain.MayaChain) Coins.MayaChain.CACAO
                        else Coins.ThorChain.RUNE
                    state.update { it.copy(selectedToken = token, unstakableAmount = null) }
                }

                DepositOption.RemoveLiquidity -> {
                    val token =
                        if (chain == Chain.MayaChain) Coins.MayaChain.CACAO
                        else Coins.ThorChain.RUNE
                    state.update { it.copy(selectedToken = token, unstakableAmount = null) }
                    when (chain) {
                        Chain.MayaChain -> liquidityDataLoader.loadRemoveLpData()
                        Chain.ThorChain -> liquidityDataLoader.loadThorChainRemoveLpData()
                        else -> Unit
                    }
                }

                DepositOption.WithdrawSecuredAsset -> {
                    withdrawSecuredAssetJob =
                        scope.launch {
                            address.filterNotNull().collect { address ->
                                handleWithdrawSecuredAsset(address)
                            }
                        }
                }

                else -> Unit
            }

            val bondAddress = bondAddress()
            if (!bondAddress.isNullOrEmpty()) {
                fields.nodeAddressFieldState.setTextAndPlaceCursorAtEnd(bondAddress)
            }

            if (chain == Chain.MayaChain) {
                when (option) {
                    DepositOption.Bond -> liquidityDataLoader.loadMayaBondableAssets()
                    DepositOption.Unbond -> watchNodeAddressForUnbond()
                    else -> Unit
                }
            }
        }
    }

    /**
     * Keeps the Unbond asset list and its LP-unit ceiling tied to the node in the address field.
     *
     * The node cannot be read once and kept: the field is editable, and the route only prefills it.
     * Every edit re-scopes what is bonded, hence what is unbondable — so the whole position is
     * dropped on the keystroke rather than when the replacement lands. In between, the field
     * already names the new node while the pools, the ceiling and the units would still describe
     * the previous one, and the memo is built from the field.
     */
    private fun watchNodeAddressForUnbond() {
        nodeAddressWatchJob =
            scope.launch {
                fields.nodeAddressFieldState
                    .textAsFlow()
                    .map { it.toString() }
                    .distinctUntilChanged()
                    .collectLatest { nodeAddress ->
                        liquidityDataLoader.clearBondedAssets()
                        delay(NODE_ADDRESS_DEBOUNCE_MS)
                        liquidityDataLoader.loadMayaBondedAssets(nodeAddress)
                    }
            }
    }

    /** Re-runs the MayaChain asset load for the option on screen, after a failed fetch. */
    fun retryLoadMayaBondAssets() {
        if (chainProvider() != Chain.MayaChain) return
        when (state.value.depositOption) {
            DepositOption.Bond -> liquidityDataLoader.loadMayaBondableAssets()
            DepositOption.Unbond ->
                liquidityDataLoader.loadMayaBondedAssets(
                    fields.nodeAddressFieldState.text.toString()
                )
            else -> Unit
        }
    }

    /**
     * Resolves the THORChain inbound vault address for a secured-asset deposit of [selectedToken],
     * translating halt/unsupported/fetch-failure outcomes into user-facing errors.
     *
     * @param selectedToken the UTXO/asset token being deposited.
     * @return the inbound vault address to deposit to.
     */
    suspend fun requireSecuredAssetInboundAddress(selectedToken: Coin): String =
        when (val result = securedAssetLoader.fetchSecuredAssetInboundAddress()) {
            is InboundAddressResult.Available -> result.address
            InboundAddressResult.Halted ->
                throw InvalidTransactionDataException(
                    UiText.FormattedText(
                        R.string.deposit_error_thorchain_chain_halted,
                        listOf(selectedToken.getChainName()),
                    )
                )
            InboundAddressResult.Unsupported ->
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.deposit_error_not_secured_asset)
                )
            InboundAddressResult.FetchFailed ->
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.deposit_error_thorchain_inbound_unavailable)
                )
        }

    private fun handleWithdrawSecuredAsset(address: Address) {
        fields.thorAddressFieldState.setTextAndPlaceCursorAtEnd(address.address)
        val availableSecuredAssets =
            address.accounts
                .filter { account -> account.token.isSecuredAsset() }
                .map {
                    TokenWithdrawSecureAsset(
                        ticker = it.token.ticker,
                        contract = it.token.contractAddress,
                        coin = it.token,
                        tokenValue = it.tokenValue,
                    )
                }
        val selectedSecuredAsset = availableSecuredAssets.firstOrNull()
        val balance = selectedSecuredAsset?.tokenValue?.let(mapTokenValueToStringWithUnit)
        state.update {
            it.copy(
                availableSecuredAssets = availableSecuredAssets,
                securedAssetsLoaded = true,
                selectedSecuredAsset = selectedSecuredAsset ?: TokenWithdrawSecureAsset.EMPTY,
                balance = balance?.asUiText() ?: UiText.Empty,
            )
        }
    }

    private suspend fun handleRemoveCacaoOption() {
        val addressValue = address.value?.address ?: return
        cacaoMaturityLoader.loadCacaoMaturityStatus(addressValue)
        try {
            val balance = mayaChainApi.getUnStakeCacaoBalance(addressValue)
            balance?.let {
                val balanceInt = it.toBigIntegerOrNull()
                if (balanceInt == null) {
                    Timber.e("Invalid balance format: %s", it)
                    state.update { state -> state.copy(unstakableAmount = null) }
                    return
                }
                val unstakableAmount =
                    mapTokenValueToStringWithUnit(
                        TokenValue(value = balanceInt, token = Coins.MayaChain.CACAO)
                    )
                state.update { state -> state.copy(unstakableAmount = unstakableAmount) }
            } ?: run { state.update { state -> state.copy(unstakableAmount = null) } }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to fetch unstakable CACAO balance")
            state.update { state ->
                state.copy(
                    unstakableAmount = null,
                    errorText = UiText.StringResource(R.string.dialog_default_error_body),
                )
            }
        }
    }

    private fun resetTextFields() {
        fields.tokenAmountFieldState.clearText()
        fields.nodeAddressFieldState.clearText()
        fields.providerFieldState.clearText()
        fields.operatorFeeFieldState.clearText()
        fields.customMemoFieldState.clearText()
        fields.basisPointsFieldState.clearText()
        fields.lpUnitsFieldState.clearText()
        fields.assetsFieldState.clearText()
        fields.rewardsAmountFieldState.clearText()
        state.update {
            it.copy(
                tokenAmountError = null,
                nodeAddressError = null,
                dstAddressError = null,
                operatorFeeError = null,
                providerError = null,
            )
        }
    }

    companion object {
        // Long enough that typing a node address does not fire a request per keystroke, short
        // enough that a paste is answered before the user reaches the units field.
        private const val NODE_ADDRESS_DEBOUNCE_MS = 300L
    }
}
