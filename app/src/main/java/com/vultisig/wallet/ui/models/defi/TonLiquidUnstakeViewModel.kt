package com.vultisig.wallet.ui.models.defi

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.blockchain.ton.TonLiquidPoolState
import com.vultisig.wallet.data.blockchain.ton.TonLiquidStakingService
import com.vultisig.wallet.data.blockchain.ton.Tonstakers
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.cosmosstaking.cachedSpendableBalance
import com.vultisig.wallet.ui.models.deposit.DepositGasFeeHelper
import com.vultisig.wallet.ui.models.deposit.submit.buildTonstakersUnstakeTransaction
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber

@Immutable
internal data class TonLiquidUnstakeUiState(
    val ticker: String = Coins.Ton.TSTON.ticker,
    val nativeTicker: String = "",
    /** tsTON the vault holds, in whole tokens. */
    val availableTsTon: BigDecimal = BigDecimal.ZERO,
    val percentageSelected: Int = -1,
    /** TON the pool pays for the entered tsTON at the current rate, or null while unknown. */
    val expectedTon: BigDecimal? = null,
    /** TON attached to the burn so it clears the pool's withdrawal fee; unused part refunded. */
    val attachedValue: BigDecimal = TON_ATTACHED_VALUE,
    /** The pool pays out immediately when it has liquidity, otherwise at round end. */
    val isOptimistic: Boolean = false,
    val isLoading: Boolean = true,
    /** Balances, wallet and rate all resolved; Continue is only ever enabled after this. */
    val isReady: Boolean = false,
    /** Native balance covers [attachedValue] plus the network fee. */
    val hasSufficientNativeBalance: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: UiText? = null,
) {
    companion object {
        val TON_ATTACHED_VALUE: BigDecimal =
            BigDecimal(Tonstakers.UNSTAKE_ATTACHED_VALUE).movePointLeft(Coins.Ton.TON.decimal)
    }
}

/**
 * View-model for the Tonstakers unstake screen. Unlike a nominator withdrawal, a tsTON burn can be
 * partial, so the form takes an amount in tsTON capped at the vault's balance and previews the TON
 * the pool pays at the current rate. The burn rides on 1.05 TON of the native balance, which is
 * checked up front the way the nominator unstake checks its 0.2 TON signal.
 */
@HiltViewModel
internal class TonLiquidUnstakeViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val liquidStakingService: TonLiquidStakingService,
    private val accountsRepository: AccountsRepository,
    private val balanceRepository: BalanceRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val depositGasFeeHelper: DepositGasFeeHelper,
    private val transactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<Destination>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val route: Route.TonLiquidUnstake = savedStateHandle.toRoute()

    val amountFieldState = TextFieldState()

    val state: StateFlow<TonLiquidUnstakeUiState>
        field = MutableStateFlow(TonLiquidUnstakeUiState())

    private var tsTonCoin: Coin? = null
    private var jettonWalletAddress: String? = null
    private var poolState: TonLiquidPoolState? = null

    init {
        load()
        observeAmount()
    }

    /** 25/50/75/100% chip → fill the amount field from the tsTON balance. */
    fun onPercentageChange(percent: Int) {
        state.update { it.copy(percentageSelected = percent) }
        val available = state.value.availableTsTon
        if (available <= BigDecimal.ZERO) return
        // Not locale-formatted: submit() parses this back with toBigDecimalOrNull(), which only
        // accepts `.` and rejects grouping separators.
        val amount =
            available
                .multiply(BigDecimal(percent))
                .divide(BigDecimal(100), Coins.Ton.TSTON.decimal, RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString()
        amountFieldState.edit { replace(0, length, amount) }
    }

    fun dismissError() {
        state.update { it.copy(errorMessage = null) }
    }

    fun back() {
        viewModelScope.safeLaunch { navigator.back() }
    }

    fun submit() {
        val current = state.value
        if (!current.isReady || current.isSubmitting || !current.hasSufficientNativeBalance) return
        val coin = tsTonCoin ?: return
        val jettonWallet = jettonWalletAddress ?: return

        val amount = amountFieldState.text.toString().trim().toBigDecimalOrNull()
        // The screen already surfaces the balance; block here as a backstop.
        if (amount == null || amount <= BigDecimal.ZERO || amount > current.availableTsTon) return

        state.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to build Tonstakers unstake transaction")
                setError(R.string.dialog_default_error_body.asUiText())
            }
        ) {
            try {
                val transaction =
                    buildTonstakersUnstakeTransaction(
                        vaultId = route.vaultId,
                        burnAmount = amount.movePointRight(coin.decimal).toBigInteger(),
                        jettonWalletAddress = jettonWallet,
                        tsTonCoin = coin,
                        accountsRepository = accountsRepository,
                        blockChainSpecificRepository = blockChainSpecificRepository,
                        calculateGasFee = { chain, token, src ->
                            depositGasFeeHelper.calculateGasFee(route.vaultId, chain, token, src)
                        },
                        getFeesFiatValue = { specific, gasFee, token ->
                            depositGasFeeHelper.getFeesFiatValue(Chain.Ton, specific, gasFee, token)
                        },
                    )

                transactionRepository.addTransaction(transaction)
                navigator.route(
                    Route.VerifyDeposit(vaultId = route.vaultId, transactionId = transaction.id)
                )
                state.update { it.copy(isSubmitting = false) }
            } catch (e: InvalidTransactionDataException) {
                setError(e.text)
            }
        }
    }

    private fun load() {
        viewModelScope.safeLaunch(
            // Fail closed: any failure resolving the vault, the tsTON wallet, the rate or the fee
            // leaves Continue disabled behind an explicit error rather than staging a burn against
            // a wallet or an amount this screen never confirmed.
            onError = { e ->
                Timber.e(e, "Failed to load Tonstakers unstake data")
                setError(R.string.error_view_default_description.asUiText())
            }
        ) {
            val vault =
                withContext(ioDispatcher) { vaultRepository.get(route.vaultId) }
                    ?: return@safeLaunch setError(
                        R.string.ton_defi_error_ton_not_in_vault.asUiText()
                    )
            val nativeCoin =
                vault.coins.firstOrNull { it.chain == Chain.Ton && it.isNativeToken }
                    ?: return@safeLaunch setError(
                        R.string.ton_defi_error_ton_not_in_vault.asUiText()
                    )
            // The vault's own tsTON coin when it is enabled, else the curated one on the vault's
            // address: the verify screen prices and labels the burn from it, nothing signs from it.
            tsTonCoin =
                vault.coins.firstOrNull {
                    it.chain == Chain.Ton && it.contractAddress == Tonstakers.TSTON_MASTER_ADDRESS
                }
                    ?: Coins.Ton.TSTON.copy(
                        address = nativeCoin.address,
                        hexPublicKey = nativeCoin.hexPublicKey,
                    )

            val position =
                withContext(ioDispatcher) { liquidStakingService.getPosition(nativeCoin.address) }
            val jettonWallet =
                position.jettonWalletAddress?.takeIf { position.hasPosition }
                    ?: return@safeLaunch setError(R.string.ton_liquid_no_position.asUiText())
            jettonWalletAddress = jettonWallet

            val pool =
                withContext(ioDispatcher) { liquidStakingService.getPoolState() }
                    ?: return@safeLaunch setError(
                        R.string.error_view_default_description.asUiText()
                    )
            poolState = pool

            val gasFee =
                withContext(ioDispatcher) {
                    depositGasFeeHelper.calculateGasFee(
                        route.vaultId,
                        Chain.Ton,
                        nativeCoin,
                        nativeCoin.address,
                    )
                }
            val required =
                BigDecimal(Tonstakers.UNSTAKE_ATTACHED_VALUE + gasFee.value)
                    .movePointLeft(nativeCoin.decimal)
            val nativeBalance =
                withContext(ioDispatcher) { balanceRepository.cachedSpendableBalance(nativeCoin) }

            state.update {
                it.copy(
                    nativeTicker = nativeCoin.ticker,
                    availableTsTon =
                        BigDecimal(position.tsTonBalance).movePointLeft(Coins.Ton.TSTON.decimal),
                    isOptimistic = pool.isOptimistic,
                    hasSufficientNativeBalance = nativeBalance >= required,
                    isLoading = false,
                    isReady = true,
                )
            }
            updateExpectedTon(amountFieldState.text.toString())
        }
    }

    private fun observeAmount() {
        viewModelScope.safeLaunch {
            amountFieldState.textAsFlow().collect { updateExpectedTon(it.toString()) }
        }
    }

    private fun updateExpectedTon(text: String) {
        val pool = poolState
        val amount = text.trim().toBigDecimalOrNull()
        val expected =
            if (pool == null || amount == null || amount.signum() <= 0) {
                null
            } else {
                val tsTon = amount.movePointRight(Coins.Ton.TSTON.decimal).toBigInteger()
                BigDecimal(pool.tonValueOf(tsTon)).movePointLeft(Coins.Ton.TON.decimal)
            }
        state.update { it.copy(expectedTon = expected) }
    }

    private fun setError(message: UiText) {
        state.update { it.copy(errorMessage = message, isSubmitting = false, isLoading = false) }
    }
}

private fun SavedStateHandle.toRoute(): Route.TonLiquidUnstake =
    Route.TonLiquidUnstake(vaultId = checkNotNull(get<String>("vaultId")) { "vaultId is required" })
