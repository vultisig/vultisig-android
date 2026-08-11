package com.vultisig.wallet.ui.models.solanastaking

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.KaminoApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.blockchain.solana.kamino.BuildKaminoKeysignPayloadUseCase
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoAction
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoComputeBudget
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoPositionMath
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVault
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVaultRegistry
import com.vultisig.wallet.data.blockchain.solana.kamino.coin
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import timber.log.Timber

internal data class KaminoAmountUiState(
    val isWithdraw: Boolean = false,
    val vaultName: String = "",
    val ticker: String = "",
    /**
     * What the entered amount is capped against: wallet balance to deposit, position to withdraw.
     */
    val available: BigDecimal = BigDecimal.ZERO,
    /** Per-vault floor from live vault state, or null when it could not be read. */
    val minimum: BigDecimal? = null,
    val percentageSelected: Int = -1,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
)

/**
 * Amount entry for a Kamino Earn deposit or withdraw.
 *
 * One ViewModel covers both directions because the forms differ only in what caps the amount —
 * wallet balance going in, the position's token value coming out — and in the labels. The
 * transaction itself is built at submit time, never earlier: Kamino bakes a recent blockhash into
 * it that expires in about a minute, and an MPC ceremony can outlast that.
 */
@HiltViewModel
internal class KaminoAmountViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val kaminoApi: KaminoApi,
    private val solanaApi: SolanaApi,
    private val vaultRepository: VaultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val balanceRepository: BalanceRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val buildKeysignPayload: BuildKaminoKeysignPayloadUseCase,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.KaminoAmount>()

    val amountFieldState = TextFieldState()

    private val _state = MutableStateFlow(KaminoAmountUiState(isWithdraw = route.isWithdraw))
    val state: StateFlow<KaminoAmountUiState> = _state.asStateFlow()

    /**
     * Resolved from the local allow-list rather than taken from the navigation argument, so a
     * malformed or stale route cannot aim the flow at a vault the app does not curate.
     */
    private val vault: KaminoVault? = KaminoVaultRegistry.vaultFor(route.vaultAddress)

    private var tokenCoin: Coin? = null

    private val action: KaminoAction
        get() = if (route.isWithdraw) KaminoAction.WITHDRAW else KaminoAction.DEPOSIT

    init {
        load()
    }

    private fun load() {
        val vault = vault
        if (vault == null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = UiText.StringResource(R.string.kamino_error_unknown_vault),
                )
            }
            return
        }

        _state.update {
            it.copy(vaultName = vault.fallbackName, ticker = vault.coin?.ticker.orEmpty())
        }

        viewModelScope.safeLaunch(
            onError = { throwable ->
                Timber.e(throwable, "Failed to load Kamino amount form")
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = UiText.StringResource(R.string.kamino_error_load_failed),
                    )
                }
            }
        ) {
            val coin = resolveCoin(vault)
            tokenCoin = coin

            val available =
                if (route.isWithdraw) withdrawableAmount(vault, coin)
                else spendableBalance(vault, coin)

            val vaultState = runCatching { kaminoApi.getVaultState(vault.address) }.getOrNull()
            val minimumField =
                if (route.isWithdraw) {
                    vaultState?.state?.minWithdrawAmount
                } else {
                    vaultState?.state?.minDepositAmount
                }

            _state.update {
                it.copy(
                    isLoading = false,
                    vaultName =
                        vaultState?.state?.name?.takeIf { name -> name.isNotBlank() }
                            ?: vault.fallbackName,
                    ticker = coin.ticker,
                    available = available,
                    // The one Kamino field in base units rather than decimals.
                    minimum =
                        KaminoPositionMath.baseUnitsToDecimal(minimumField, vault.tokenDecimals),
                )
            }
        }
    }

    /**
     * Wallet balance less everything the deposit itself has to pay for, so a Max amount produces a
     * transaction that can actually land.
     *
     * For the SOL vault that is three things, not one: the base fee, the priority fee this
     * transaction will be charged (price × the compute limit, far from negligible at these limits),
     * and the rent-exempt reserve for the **wrapped-SOL account the deposit has to create** — the
     * vault's underlying is wSOL, not native SOL. Reserving only the base fee, as this first did,
     * leaves a Max deposit unable to fund that account and it fails on chain. The native staking
     * flow reserves rent the same way.
     *
     * A token deposit pays all of that in SOL, so the token balance is spendable in full.
     */
    private suspend fun spendableBalance(vault: KaminoVault, coin: Coin): BigDecimal {
        val balance = balanceRepository.getTokenValue(coin.address, coin).first().value
        if (!coin.isNativeToken) return BigDecimal(balance).movePointLeft(coin.decimal)

        val priorityFee =
            KaminoComputeBudget.priorityFeeLamports(
                vault = vault,
                action = KaminoAction.DEPOSIT,
                networkPrice = null,
            )
        val wrappedSolRent =
            if (vault.tokenMint == KaminoVaultRegistry.WRAPPED_SOL_MINT) {
                runCatching { solanaApi.getMinimumBalanceForRentExemption() }
                    .getOrElse { BigInteger.ZERO }
            } else {
                BigInteger.ZERO
            }

        val headroom = SolanaHelper.DefaultFeeInLamports + priorityFee + wrappedSolRent
        return BigDecimal((balance - headroom).coerceAtLeast(BigInteger.ZERO))
            .movePointLeft(coin.decimal)
    }

    /** What the position is actually worth in tokens — shares times the live share ratio. */
    private suspend fun withdrawableAmount(vault: KaminoVault, coin: Coin): BigDecimal {
        val walletAddress = coin.address
        val position =
            runCatching { kaminoApi.getUserPositions(walletAddress) }
                .getOrNull()
                .orEmpty()
                .firstOrNull { it.vaultAddress == vault.address }
        val shares = KaminoPositionMath.decimalOrNull(position?.totalShares) ?: BigDecimal.ZERO
        if (shares.signum() == 0) return BigDecimal.ZERO

        val metrics = runCatching { kaminoApi.getVaultMetrics(vault.address) }.getOrNull()
        val tokensPerShare =
            KaminoPositionMath.decimalOrNull(metrics?.tokensPerShare) ?: return BigDecimal.ZERO
        return KaminoPositionMath.tokenAmount(shares, tokensPerShare, vault.tokenDecimals)
    }

    private suspend fun resolveCoin(vault: KaminoVault): Coin {
        val template =
            vault.coin ?: error("No wallet coin maps to ${vault.fallbackName}'s underlying token")
        val storedVault =
            vaultRepository.get(route.vaultId) ?: error("Vault ${route.vaultId} not found")
        val (address, publicKey) =
            chainAccountAddressRepository.getAddress(Chain.Solana, storedVault)
        return template.copy(address = address, hexPublicKey = publicKey)
    }

    fun onPercentageChange(percentage: Int) {
        val available = _state.value.available
        val amount =
            available
                .multiply(BigDecimal(percentage))
                .divide(ONE_HUNDRED)
                .setScale(tokenCoin?.decimal ?: DEFAULT_SCALE, RoundingMode.DOWN)
        amountFieldState.setTextAndPlaceCursorAtEnd(amount.stripTrailingZeros().toPlainString())
        _state.update { it.copy(percentageSelected = percentage) }
    }

    fun submit() {
        val vault = vault ?: return
        val coin = tokenCoin ?: return
        val amount = amountFieldState.text.toString().toBigDecimalOrNull() ?: return

        _state.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.safeLaunch(
            onError = { throwable ->
                Timber.e(throwable, "Failed to build Kamino transaction")
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        // Surface the refusal reason rather than a generic failure: a rejected
                        // transaction means the app declined to sign something, which the user
                        // should be able to read.
                        error = UiText.DynamicString(throwable.message ?: ""),
                    )
                }
            }
        ) {
            require(amount.signum() > 0) { "Amount must be greater than zero" }
            require(amount <= _state.value.available) { "Amount exceeds the available balance" }
            _state.value.minimum?.let { minimum ->
                require(amount >= minimum) {
                    "Minimum is ${minimum.stripTrailingZeros().toPlainString()} ${coin.ticker}"
                }
            }

            val storedVault =
                vaultRepository.get(route.vaultId) ?: error("Vault ${route.vaultId} not found")

            val gasFee = TokenValue(value = SolanaHelper.DefaultFeeInLamports, token = coin)
            val specific =
                blockChainSpecificRepository.getSpecific(
                    chain = Chain.Solana,
                    address = coin.address,
                    token = coin,
                    gasFee = gasFee,
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = true,
                )

            // Built here, at submit, so the blockhash inside it is as fresh as possible.
            val keysignPayload =
                buildKeysignPayload(
                    vault = vault,
                    action = action,
                    amount = amount,
                    coin = coin,
                    blockChainSpecific = specific.blockChainSpecific,
                    vaultPublicKeyECDSA = storedVault.pubKeyECDSA,
                    vaultLocalPartyID = storedVault.localPartyID,
                    libType = storedVault.libType,
                )

            val depositTx =
                DepositTransaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = route.vaultId,
                    srcToken = coin,
                    srcAddress = coin.address,
                    srcTokenValue = TokenValue(value = keysignPayload.toAmount, token = coin),
                    memo = "",
                    dstAddress = vault.address,
                    estimatedFees = gasFee,
                    estimateFeesFiat = "",
                    blockChainSpecific = specific.blockChainSpecific,
                    validatorName = _state.value.vaultName,
                    signSolana = keysignPayload.signSolana,
                )
            depositTransactionRepository.addTransaction(depositTx)

            amountFieldState.clearText()
            _state.update { it.copy(isSubmitting = false) }
            navigator.route(
                Route.VerifyDeposit(vaultId = route.vaultId, transactionId = depositTx.id)
            )
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun back() {
        viewModelScope.safeLaunch(onError = { Timber.w(it, "back failed") }) { navigator.back() }
    }

    private companion object {
        val ONE_HUNDRED = BigDecimal(100)
        const val DEFAULT_SCALE = 6
    }
}
