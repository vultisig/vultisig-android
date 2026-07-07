package com.vultisig.wallet.ui.models.deposit.load

import com.vultisig.wallet.R
import com.vultisig.wallet.data.crypto.ThorChainHelper.Companion.SECURE_ASSETS_TICKERS
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.ticker
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.models.deposit.TokenMergeInfo
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.parseDepositType
import com.vultisig.wallet.ui.utils.asUiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns the core address/metadata-loading primitives extracted from `DepositFormViewModel`:
 * resolving the vault's address for the active chain into the shared [address] flow and consuming
 * the pending `depositTypeAction` deep-link into the matching [DepositOption].
 *
 * The repository is Hilt-injected here; the ViewModel keeps `viewModelScope` ownership and supplies
 * it (assisted) along with the shared [address] flow, the [depositTypeActionProvider] /
 * [clearDepositTypeAction] accessors and the [selectDepositOption] callback so this loader never
 * owns its own scope or VM state.
 */
internal class DepositDataLoader
@AssistedInject
constructor(
    private val accountsRepository: AccountsRepository,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val address: MutableStateFlow<Address?>,
    @Assisted private val depositTypeActionProvider: () -> String?,
    @Assisted private val clearDepositTypeAction: () -> Unit,
    @Assisted private val selectDepositOption: (DepositOption) -> Unit,
) {

    /** @see DepositDataLoader */
    @AssistedFactory
    interface Factory {
        /**
         * Creates a [DepositDataLoader] bound to the given scope, shared address flow, accessors
         * and callback.
         */
        fun create(
            scope: CoroutineScope,
            address: MutableStateFlow<Address?>,
            depositTypeActionProvider: () -> String?,
            clearDepositTypeAction: () -> Unit,
            selectDepositOption: (DepositOption) -> Unit,
        ): DepositDataLoader
    }

    private var addressJob: Job? = null

    /**
     * Resolves the [vaultId]'s address for [chain] and writes each emission into the shared
     * [address] flow, cancelling any previous in-flight resolution first so a superseded load can't
     * overwrite a fresher one.
     */
    fun loadAddress(vaultId: String, chain: Chain) {
        addressJob?.cancel()
        addressJob =
            scope.safeLaunch(
                onError = { e ->
                    Timber.e(
                        e,
                        "Failed to load address for vaultId=%s chain=%s",
                        vaultId,
                        chain.raw,
                    )
                }
            ) {
                accountsRepository.loadAddress(vaultId, chain).collect { loadedAddress ->
                    address.value = loadedAddress
                }
            }
    }

    /**
     * Consumes the pending `depositTypeAction` deep-link (read-once, then cleared) and selects the
     * matching [DepositOption], defaulting to [DepositOption.Bond]. No-op when no action is
     * pending.
     */
    fun setMetadataInfo() {
        val action = depositTypeActionProvider()?.takeIf { it.isNotEmpty() } ?: return
        clearDepositTypeAction()

        val depositOption =
            when (parseDepositType(action)) {
                DeFiNavActions.BOND -> DepositOption.Bond
                DeFiNavActions.UNBOND -> DepositOption.Unbond
                DeFiNavActions.STAKE_CACAO -> DepositOption.AddCacaoPool
                DeFiNavActions.UNSTAKE_CACAO -> DepositOption.RemoveCacaoPool
                DeFiNavActions.ADD_LP -> DepositOption.AddLiquidity
                DeFiNavActions.REMOVE_LP -> DepositOption.RemoveLiquidity
                else -> DepositOption.Bond
            }
        selectDepositOption(depositOption)
    }

    /**
     * Wires the init-time deposit flow at screen entry: builds the per-[chain] option list and
     * seeds the initial [state], derives the merge coin list, resolves the vault [address], and
     * starts the three sequence-sensitive flow collectors (native-token reselection + ADD_LP gas
     * display, the `selectedCoin × address × depositOption × selectedToken`
     * token-amount/secured-asset collector, and the `selectedCoin × depositOption` IBC/Switch
     * destination-chain collector).
     *
     * Collector order and structure are preserved exactly; the SecuredAsset address population
     * stays inside the `collect` (never as a transform side effect). All coroutines run on the
     * assisted [scope] so the ViewModel retains scope ownership.
     *
     * @param vaultId active vault id used for address resolution and balance lookups.
     * @param chain active deposit chain that drives the option list and default token.
     * @param tokensToMerge merge-token catalogue seeded into the coin list (filtered to LVN on
     *   Osmosis).
     * @param state the ViewModel's mutable UI state, read for derivations and updated in place.
     * @param updateTokenAmount callback that refreshes the displayed balance for the resolved
     *   account.
     * @param selectDstChain callback that selects the first IBC/Switch destination chain.
     * @param collectSecuredAssetAddresses trigger that populates the user's own THORChain address
     *   on the SecuredAsset form.
     * @param loadGasFeeForDisplay callback that loads and displays gas fees for the ADD_LP
     *   deep-link.
     */
    fun wireInitialState(
        vaultId: String,
        chain: Chain,
        tokensToMerge: List<TokenMergeInfo>,
        state: MutableStateFlow<DepositFormUiModel>,
        updateTokenAmount: suspend (Account?, Chain, String?, String) -> Unit,
        selectDstChain: (Chain) -> Unit,
        collectSecuredAssetAddresses: () -> Unit,
        loadGasFeeForDisplay: (String, Chain, Address) -> Unit,
    ) {
        val depositOptions =
            when (chain) {
                Chain.ThorChain ->
                    listOf(
                        DepositOption.Bond,
                        DepositOption.Unbond,
                        DepositOption.Leave,
                        DepositOption.Custom,
                        DepositOption.Merge,
                        DepositOption.UnMerge,
                        DepositOption.WithdrawSecuredAsset,
                    )

                Chain.MayaChain -> listOf(DepositOption.Leave, DepositOption.Custom)

                Chain.Kujira,
                Chain.Osmosis -> listOf(DepositOption.TransferIbc)

                Chain.GaiaChain -> listOf(DepositOption.TransferIbc, DepositOption.Switch)
                // TON staking moved to the dedicated DeFi-tab Stake/Unstake screens; it no longer
                // surfaces Stake/Unstake in the generic deposit form (iOS Functions-flow parity).
                else ->
                    buildList {
                        //                    add(DepositOption.Stake)
                        //                    add(DepositOption.Unstake)
                        if (chain.ticker() in SECURE_ASSETS_TICKERS) add(DepositOption.SecuredAsset)
                    }
            }
        val depositOption = depositOptions.first()
        val defaultToken =
            when (chain) {
                Chain.MayaChain -> Coins.MayaChain.CACAO
                else -> Coins.ThorChain.RUNE
            }
        state.update {
            it.copy(
                depositMessage = R.string.deposit_message_deposit_title.asUiText(chain.raw),
                depositOptions = depositOptions,
                depositOption = depositOption,
                depositChain = chain,
                selectedToken = defaultToken,
            )
        }

        val coinList =
            tokensToMerge.let {
                if (chain == Chain.Osmosis) it.filter { it.ticker.equals("LVN", ignoreCase = true) }
                else it
            }
        state.update {
            it.copy(
                selectedCoin = coinList.first(),
                coinList = coinList,
                selectedUnMergeCoin = coinList.first(),
            )
        }

        loadAddress(vaultId, chain)

        address
            .filterNotNull()
            .onEach { address ->
                val selectedToken = address.accounts.find { it.token.isNativeToken }?.token
                selectedToken?.let { state.update { it.copy(selectedToken = selectedToken) } }
                if (depositTypeActionProvider() == DeFiNavActions.ADD_LP.type) {
                    loadGasFeeForDisplay(vaultId, chain, address)
                }
            }
            .launchIn(scope)

        scope.launch {
            combine(
                    state.map { it.selectedCoin }.distinctUntilChanged(),
                    address.filterNotNull(),
                    state.map { it.depositOption }.distinctUntilChanged(),
                    state.map { it.selectedToken }.distinctUntilChanged(),
                ) { selectedMergeToken, address, depositOption, selectedToken ->
                    var targetTicker: String?

                    val account =
                        when (depositOption) {
                            DepositOption.Switch,
                            DepositOption.TransferIbc,
                            DepositOption.Merge -> {
                                targetTicker = selectedMergeToken.ticker
                                address.accounts.find {
                                    it.token.ticker.equals(
                                        selectedMergeToken.ticker,
                                        ignoreCase = true,
                                    )
                                }
                            }

                            DepositOption.Custom -> {
                                targetTicker = selectedToken.ticker
                                address.accounts.find { it.token.id == selectedToken.id }
                            }

                            else -> {
                                val account = address.accounts.find { it.token.isNativeToken }
                                targetTicker = account?.token?.ticker
                                account
                            }
                        }

                    if (depositOption != DepositOption.RemoveLiquidity) {
                        updateTokenAmount(account, chain, targetTicker, vaultId)
                    }

                    depositOption
                }
                .collect { depositOption ->
                    // Populate the user's own THORChain address for the SecuredAsset form here,
                    // outside the (pure) transform, and never resolve the inbound vault as a side
                    // effect — that is done synchronously in SecuredAssetStrategy so the
                    // destination always matches the currently-selected asset's chain.
                    if (depositOption == DepositOption.SecuredAsset) {
                        collectSecuredAssetAddresses()
                    }
                    setMetadataInfo()
                }
        }

        scope.launch {
            combine(
                    state.map { it.selectedCoin }.distinctUntilChanged(),
                    state.map { it.depositOption }.distinctUntilChanged(),
                ) { selectedMergeToken, depositOption ->
                    when (depositOption) {
                        DepositOption.TransferIbc,
                        DepositOption.Switch -> {
                            // special case, because of all supported merge tokens only lvn is
                            // osmosis native
                            val dstChainList =
                                if (selectedMergeToken.ticker == "LVN") {
                                    when (chain) {
                                        Chain.Osmosis -> listOf(Chain.GaiaChain)
                                        else -> listOf(Chain.Osmosis)
                                    }
                                } else {
                                    listOf(
                                            Chain.GaiaChain,
                                            Chain.Kujira,
                                            Chain.Osmosis,
                                            Chain.Noble,
                                            Chain.Akash,
                                        )
                                        .filter { it != chain }
                                }

                            state.update { it.copy(dstChainList = dstChainList) }

                            selectDstChain(dstChainList.first())
                        }

                        else -> Unit
                    }
                }
                .collect {}
        }
    }
}
