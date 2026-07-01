package com.vultisig.wallet.ui.models.defi

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.blockchain.ton.TonNominatorPool
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.cosmosstaking.cachedSpendableBalance
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositGasFeeHelper
import com.vultisig.wallet.ui.models.deposit.submit.TonStakingAction
import com.vultisig.wallet.ui.models.deposit.submit.buildTonStakingTransaction
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import wallet.core.jni.TONAddressConverter

@Immutable
internal data class TonUnstakeUiState(
    val ticker: String = "",
    val poolAddress: String = "",
    val stakedDisplay: String = "",
    /** Liquid balance covers the 0.2 TON withdraw signal + network fee. */
    val hasSufficientBalance: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: UiText? = null,
)

/**
 * View-model for the TON nominator-pool unstake confirmation (mirrors iOS
 * `TonUnstakeTransactionViewModel`). Nominator pools support full withdrawal only, so there is no
 * amount input — Continue builds the fixed-fee "Withdraw" message via the shared
 * [buildTonStakingTransaction] core, persists it, and routes to the existing verify screen.
 */
@HiltViewModel
internal class TonUnstakeViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val tonStakingApi: TonStakingApi,
    private val accountsRepository: AccountsRepository,
    private val balanceRepository: BalanceRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val depositGasFeeHelper: DepositGasFeeHelper,
    private val transactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<com.vultisig.wallet.ui.navigation.Destination>,
    private val sendNavigator: Navigator<SendDst>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val route: Route.TonUnstake = savedStateHandle.toRoute()

    private val _state =
        MutableStateFlow(
            TonUnstakeUiState(poolAddress = route.poolAddress, stakedDisplay = route.stakedDisplay)
        )
    val state: StateFlow<TonUnstakeUiState> = _state.asStateFlow()

    private var coin: Coin? = null

    init {
        loadCoin()
    }

    fun back() {
        viewModelScope.safeLaunch { navigator.back() }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting || !current.hasSufficientBalance) return

        _state.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to build TON unstake transaction")
                setError(R.string.dialog_default_error_body.asUiText())
            }
        ) {
            try {
                val transaction =
                    buildTonStakingTransaction(
                        action = TonStakingAction.WITHDRAW,
                        vaultIdProvider = { route.vaultId },
                        chainProvider = { Chain.Ton },
                        stateProvider = { DepositFormUiModel(depositChain = Chain.Ton) },
                        nodeAddressFieldState = TextFieldState(route.poolAddress),
                        tokenAmountFieldState = TextFieldState(),
                        accountsRepository = accountsRepository,
                        tonStakingApi = tonStakingApi,
                        toBounceableAddress = ::toTonBounceableAddress,
                        blockChainSpecificRepository = blockChainSpecificRepository,
                        calculateGasFee = { chain, token, src ->
                            depositGasFeeHelper.calculateGasFee(route.vaultId, chain, token, src)
                        },
                        getFeesFiatValue = { specific, gasFee, token ->
                            depositGasFeeHelper.getFeesFiatValue(Chain.Ton, specific, gasFee, token)
                        },
                    )

                transactionRepository.addTransaction(transaction)
                sendNavigator.navigate(
                    SendDst.VerifyTransaction(
                        transactionId = transaction.id,
                        vaultId = route.vaultId,
                    )
                )
                _state.update { it.copy(isSubmitting = false) }
            } catch (e: InvalidTransactionDataException) {
                setError(e.text)
            }
        }
    }

    private fun loadCoin() {
        viewModelScope.safeLaunch(
            onError = { e -> Timber.e(e, "Failed to load TON coin for unstake flow") }
        ) {
            val vault = withContext(ioDispatcher) { vaultRepository.get(route.vaultId) }
            val nativeCoin =
                vault?.coins?.firstOrNull { it.chain == Chain.Ton && it.isNativeToken }
                    ?: return@safeLaunch
            coin = nativeCoin

            // The withdraw message carries the 0.2 TON signal fee plus the network gas; block the
            // action if the liquid balance can't cover both (the broadcast would otherwise fail).
            val gasFee =
                runCatching {
                        withContext(ioDispatcher) {
                            depositGasFeeHelper.calculateGasFee(
                                route.vaultId,
                                Chain.Ton,
                                nativeCoin,
                                nativeCoin.address,
                            )
                        }
                    }
                    .getOrNull()
            val required =
                BigDecimal(
                        TonNominatorPool.WITHDRAW_FEE + (gasFee?.value ?: java.math.BigInteger.ZERO)
                    )
                    .movePointLeft(nativeCoin.decimal)
            val balance =
                withContext(ioDispatcher) { balanceRepository.cachedSpendableBalance(nativeCoin) }

            _state.update {
                it.copy(ticker = nativeCoin.ticker, hasSufficientBalance = balance >= required)
            }
        }
    }

    private fun setError(message: UiText) {
        _state.update { it.copy(errorMessage = message, isSubmitting = false) }
    }
}

private fun toTonBounceableAddress(address: String): String =
    TONAddressConverter.toUserFriendly(address, /* bounceable= */ true, /* testnet= */ false)

private fun SavedStateHandle.toRoute(): Route.TonUnstake =
    Route.TonUnstake(
        vaultId = checkNotNull(get<String>("vaultId")) { "vaultId is required" },
        poolAddress = checkNotNull(get<String>("poolAddress")) { "poolAddress is required" },
        stakedDisplay = get<String>("stakedDisplay").orEmpty(),
    )
