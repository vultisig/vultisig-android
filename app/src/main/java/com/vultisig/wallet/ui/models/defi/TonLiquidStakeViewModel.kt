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
import com.vultisig.wallet.ui.models.deposit.submit.buildTonstakersStakeTransaction
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.screens.v2.defi.formatPercentage
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
internal data class TonLiquidStakeUiState(
    val ticker: String = "",
    val tsTonTicker: String = Coins.Ton.TSTON.ticker,
    /** Native balance less the network-fee reserve, in TON. */
    val stakeableBalance: BigDecimal = BigDecimal.ZERO,
    val percentageSelected: Int = -1,
    val apy: String? = null,
    /** The pool's deposit fee the entered amount has to include, in TON. */
    val depositFee: BigDecimal = TON_DEPOSIT_FEE,
    /** Smallest amount the field accepts: the pool minimum plus [depositFee], in TON. */
    val minimumDeposit: BigDecimal = TON_DEPOSIT_FEE + BigDecimal.ONE,
    /** tsTON the pool would mint for the entered amount, or null while the rate is unknown. */
    val expectedTsTon: BigDecimal? = null,
    /** Pool state and balances are still loading. */
    val isLoading: Boolean = true,
    /** Everything a deposit is sized against resolved; Continue is only ever enabled after this. */
    val isReady: Boolean = false,
    /** The pool is not taking deposits right now; Continue stays disabled with a notice. */
    val isDepositClosed: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: UiText? = null,
) {
    companion object {
        val TON_DEPOSIT_FEE: BigDecimal =
            BigDecimal(Tonstakers.DEPOSIT_FEE).movePointLeft(Coins.Ton.TON.decimal)
    }
}

/**
 * View-model for the Tonstakers stake screen. The entered amount is what the transfer carries — the
 * same convention as the nominator stake, where the pool commission sits inside the entered figure
 * — so the minimum is the pool's `min_stake` plus the 1 TON deposit fee the pool takes off the top,
 * and the tsTON preview is priced on the amount net of that fee.
 *
 * Submit delegates to [buildTonstakersStakeTransaction], persists the deposit and routes to the
 * shared verify screen; fees come from [DepositGasFeeHelper] like every other deposit.
 */
@HiltViewModel
internal class TonLiquidStakeViewModel
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

    private val route: Route.TonLiquidStake = savedStateHandle.toRoute()

    val amountFieldState = TextFieldState()

    val state: StateFlow<TonLiquidStakeUiState>
        field = MutableStateFlow(TonLiquidStakeUiState())

    private var coin: Coin? = null
    private var poolState: TonLiquidPoolState? = null

    init {
        load()
        observeAmount()
    }

    /** 25/50/75/100% chip → fill the amount field from the stakeable balance. */
    fun onPercentageChange(percent: Int) {
        state.update { it.copy(percentageSelected = percent) }
        val available = state.value.stakeableBalance
        if (available <= BigDecimal.ZERO) return
        // Not locale-formatted: submit() parses this back with toBigDecimalOrNull(), which only
        // accepts `.` and rejects grouping separators.
        val amount =
            available
                .multiply(BigDecimal(percent))
                .divide(BigDecimal(100), 9, RoundingMode.DOWN)
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
        if (!current.isReady || current.isSubmitting || current.isDepositClosed) return
        val nativeCoin = coin ?: return

        val amount = amountFieldState.text.toString().trim().toBigDecimalOrNull()
        // The screen already surfaces the minimum and the balance; block here as a backstop.
        if (amount == null || amount < current.minimumDeposit) return
        if (amount > current.stakeableBalance) {
            setError(
                UiText.FormattedText(R.string.insufficient_native_token, listOf(nativeCoin.ticker))
            )
            return
        }

        state.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to build Tonstakers stake transaction")
                setError(R.string.dialog_default_error_body.asUiText())
            }
        ) {
            try {
                val transaction =
                    buildTonstakersStakeTransaction(
                        vaultId = route.vaultId,
                        depositValue = amount.movePointRight(nativeCoin.decimal).toBigInteger(),
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
            // Fail closed: without the coin, the fee or the pool rate there is nothing to size a
            // deposit against, so Continue stays disabled behind an explicit error.
            onError = { e ->
                Timber.e(e, "Failed to load Tonstakers stake data")
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
            coin = nativeCoin

            val gasFee =
                withContext(ioDispatcher) {
                    depositGasFeeHelper.calculateGasFee(
                        route.vaultId,
                        Chain.Ton,
                        nativeCoin,
                        nativeCoin.address,
                    )
                }
            val gasReservation = BigDecimal(gasFee.value).movePointLeft(nativeCoin.decimal)
            val total =
                withContext(ioDispatcher) { balanceRepository.cachedSpendableBalance(nativeCoin) }
            val stakeable = (total - gasReservation).coerceAtLeast(BigDecimal.ZERO)

            val pool =
                withContext(ioDispatcher) { liquidStakingService.getPoolState() }
                    ?: return@safeLaunch setError(
                        R.string.error_view_default_description.asUiText()
                    )
            poolState = pool

            val minimumDeposit =
                BigDecimal(Tonstakers.minimumDepositValue(pool.minStake))
                    .movePointLeft(nativeCoin.decimal)

            state.update {
                it.copy(
                    ticker = nativeCoin.ticker,
                    stakeableBalance = stakeable,
                    // tonapi `apy` is a percentage (13.27 = 13.27%); formatPercentage ×100.
                    apy = pool.apy?.let { apy -> (apy / 100).formatPercentage() },
                    minimumDeposit = minimumDeposit,
                    isDepositClosed = !pool.isDepositOpen,
                    isLoading = false,
                    isReady = true,
                )
            }
            updateExpectedTsTon(amountFieldState.text.toString())
        }
    }

    private fun observeAmount() {
        viewModelScope.safeLaunch {
            amountFieldState.textAsFlow().collect { updateExpectedTsTon(it.toString()) }
        }
    }

    /** What the pool mints for the entered amount net of its fee, at the current rate. */
    private fun updateExpectedTsTon(text: String) {
        val pool = poolState
        val nativeCoin = coin
        val amount = text.trim().toBigDecimalOrNull()
        val expected =
            if (pool == null || nativeCoin == null || amount == null) {
                null
            } else {
                val depositValue = amount.movePointRight(nativeCoin.decimal).toBigInteger()
                val net = depositValue - Tonstakers.DEPOSIT_FEE
                if (net.signum() <= 0) null
                else BigDecimal(pool.tsTonFor(net)).movePointLeft(Coins.Ton.TSTON.decimal)
            }
        state.update { it.copy(expectedTsTon = expected) }
    }

    private fun setError(message: UiText) {
        state.update { it.copy(errorMessage = message, isSubmitting = false, isLoading = false) }
    }
}

private fun SavedStateHandle.toRoute(): Route.TonLiquidStake =
    Route.TonLiquidStake(vaultId = checkNotNull(get<String>("vaultId")) { "vaultId is required" })
