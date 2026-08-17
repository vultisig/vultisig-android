package com.vultisig.wallet.ui.models.solanastaking

import androidx.annotation.StringRes
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
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoRate
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoShareAmount
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoTokenAmount
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVault
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVaultRegistry
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoWithdrawEligibility
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoWithdrawLiquidity
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoWithdrawMath
import com.vultisig.wallet.data.blockchain.solana.kamino.coin
import com.vultisig.wallet.data.blockchain.solana.kamino.kaminoDestinationAddress
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.OPERATION_KAMINO_DEPOSIT
import com.vultisig.wallet.data.models.OPERATION_KAMINO_WITHDRAW
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.runCatchingCancellable
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

/**
 * Why a Kamino form refused to build a transaction.
 *
 * A typed reason rather than an exception message: the message is for the log, and the user needs
 * the same fact in their own language. Each case maps to one string resource.
 */
internal enum class KaminoAmountRefusal(@StringRes val messageRes: Int) {
    BELOW_SMALLEST_UNIT(R.string.kamino_refusal_below_smallest_unit),
    EXCEEDS_AVAILABLE(R.string.kamino_amount_exceeds_available),
    BELOW_MINIMUM(R.string.kamino_refusal_below_minimum),
    NOTHING_TO_WITHDRAW(R.string.kamino_withdraw_nothing),
    POSITION_UNREADABLE(R.string.kamino_withdraw_unreadable),
    RATE_UNAVAILABLE(R.string.kamino_refusal_rate_unavailable),
    LARGER_THAN_POSITION(R.string.kamino_refusal_larger_than_position),
}

/** Thrown to carry a [KaminoAmountRefusal] out to the state, where it becomes localised text. */
internal class KaminoAmountRefused(val refusal: KaminoAmountRefusal) :
    IllegalStateException(refusal.name)

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
    /**
     * Why a withdraw is or is not possible. Null for a deposit. `FarmStaked` is a real, expected
     * state rather than a failure: every deposit auto-stakes, and the transaction that spends
     * staked shares has never been observed, so the app refuses it by name instead of guessing a
     * layout.
     */
    val eligibility: KaminoWithdrawEligibility? = null,
    /**
     * Whether the requested withdraw fits the vault's liquid buffer. Ordinary information, not an
     * error — the buffer is well under 1% of assets, so exceeding it is the common case.
     */
    val liquidity: KaminoWithdrawLiquidity = KaminoWithdrawLiquidity.Instant,
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

    // Withdraw sizing state, read at load and reused at submit so the form's maximum, its gate and
    // the amount actually sent cannot disagree.
    private var withdrawRate: KaminoRate? = null
    private var withdrawHeldShares: KaminoShareAmount? = null
    private var withdrawMaximumTokens: KaminoTokenAmount? = null
    private var liquidBuffer: KaminoTokenAmount? = null

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

            if (route.isWithdraw) {
                loadWithdrawState(vault, coin)
                return@safeLaunch
            }

            val available = spendableBalance(vault, coin)
            val vaultState =
                runCatchingCancellable { kaminoApi.getVaultState(vault.address) }.getOrNull()

            _state.update {
                it.copy(
                    isLoading = false,
                    vaultName =
                        vaultState?.state?.name?.takeIf { name -> name.isNotBlank() }
                            ?: vault.fallbackName,
                    ticker = coin.ticker,
                    available = available,
                    // Token base units for a deposit — the endpoint's own denomination.
                    minimum =
                        KaminoPositionMath.baseUnitsToDecimal(
                            vaultState?.state?.minDepositAmount,
                            vault.tokenDecimals,
                        ),
                )
            }
        }
    }

    /**
     * Wallet balance less everything the deposit itself has to pay for, so a Max amount produces a
     * transaction that can actually land.
     *
     * For a first SOL-vault deposit that is several things, not one: the base fee, the charged
     * Kamino priority fee (price × compute limit), and rent for every account the deposit creates:
     * wSOL ATA, share ATA and farm user-state. Reserving only the transfer-shaped fee leaves a Max
     * deposit unable to fund those accounts and it fails at preflight.
     *
     * A token deposit pays all of that in SOL, so the token balance is spendable in full.
     */
    private suspend fun spendableBalance(vault: KaminoVault, coin: Coin): BigDecimal {
        val balance = balanceRepository.getTokenValue(coin.address, coin).first().value
        if (!coin.isNativeToken) return BigDecimal(balance).movePointLeft(coin.decimal)

        val priorityFee = kaminoDepositPriorityFee(vault, coin)
        val rentReserve = kaminoNativeDepositRentReserve(vault, coin)

        val headroom = SolanaHelper.DefaultFeeInLamports + priorityFee + rentReserve
        return BigDecimal((balance - headroom).coerceAtLeast(BigInteger.ZERO))
            .movePointLeft(coin.decimal)
    }

    /**
     * Reads the network's median priority price directly rather than through
     * [BlockChainSpecificRepository.getSpecific], which also fetches a recent blockhash and ATA
     * lookups unrelated to pricing. Any one of those failing would fail the whole call, nulling out
     * the price too and dropping the floor from the app-wide 1,000,000 to
     * [KaminoComputeBudget.FALLBACK_UNIT_PRICE]'s 20,000 — under-reserving the exact way #5599
     * failed, just at a smaller scale. [SolanaApi.getMedianPriorityFee] already falls back to
     * 1,000,000 internally and never throws but for cancellation, so nothing here needs its own
     * fallback.
     */
    private suspend fun kaminoDepositPriorityFee(vault: KaminoVault, coin: Coin): BigInteger {
        val networkPrice = solanaApi.getMedianPriorityFee(listOf(coin.address))
        return KaminoComputeBudget.priorityFeeLamports(
            vault = vault,
            action = KaminoAction.DEPOSIT,
            networkPrice = networkPrice,
        )
    }

    private suspend fun kaminoNativeDepositRentReserve(vault: KaminoVault, coin: Coin): BigInteger {
        if (vault.tokenMint != KaminoVaultRegistry.WRAPPED_SOL_MINT) return BigInteger.ZERO

        val tokenAccountRent = tokenAccountRentReserve()
        val wrappedSolRent =
            tokenAccountRent.takeUnless { tokenAccountExists(coin.address, vault.tokenMint) }
                ?: BigInteger.ZERO
        val shareAccountRent =
            tokenAccountRent.takeUnless { tokenAccountExists(coin.address, vault.sharesMint) }
                ?: BigInteger.ZERO
        val farmUserStateRent =
            FARM_USER_STATE_RENT_LAMPORTS.takeUnless { hasKaminoVaultPosition(coin.address, vault) }
                ?: BigInteger.ZERO

        return wrappedSolRent + shareAccountRent + farmUserStateRent
    }

    private suspend fun tokenAccountRentReserve(): BigInteger =
        runCatchingCancellable { solanaApi.getMinimumBalanceForRentExemption() }
            .getOrNull()
            ?.takeIf { it.signum() > 0 } ?: SPL_TOKEN_ACCOUNT_RENT_LAMPORTS

    private suspend fun tokenAccountExists(walletAddress: String, mint: String): Boolean =
        runCatchingCancellable {
                solanaApi.getTokenAssociatedAccountByOwner(walletAddress, mint).first != null
            }
            .getOrDefault(false)

    /**
     * Whether the wallet already has a farm UserState account for this vault, so a redeposit does
     * not need to pay its rent again.
     *
     * Goes through [KaminoWithdrawEligibility.resolve] rather than checking for a bare entry in the
     * response: the same endpoint keeps a zero-share row for a wallet that has fully exited, and
     * the withdraw flow already treats that row as no position. Checking entry presence alone would
     * disagree with that and waive the rent on an account that may no longer exist.
     */
    private suspend fun hasKaminoVaultPosition(walletAddress: String, vault: KaminoVault): Boolean =
        runCatchingCancellable {
                val entry =
                    kaminoApi.getUserPositions(walletAddress).firstOrNull {
                        it.vaultAddress == vault.address
                    }
                KaminoWithdrawEligibility.resolve(entry, vault.sharesDecimals) is
                    KaminoWithdrawEligibility.Withdrawable
            }
            .getOrDefault(false)

    /**
     * Resolves what the withdraw form may offer, in shares first and tokens only as a projection.
     *
     * Shares are the primary unit throughout: the endpoint takes shares, the vault's minimum is in
     * shares, and a full withdraw must send the exact held share count. The token figure the form
     * is denominated in is derived from those, never the other way round.
     */
    private suspend fun loadWithdrawState(vault: KaminoVault, coin: Coin) {
        val positions =
            runCatchingCancellable { kaminoApi.getUserPositions(coin.address) }.getOrNull()
        val eligibility =
            if (positions == null) {
                // A failed read is neither "nothing to withdraw" nor "everything": refuse it.
                KaminoWithdrawEligibility.Unreadable
            } else {
                KaminoWithdrawEligibility.resolve(
                    response = positions.firstOrNull { it.vaultAddress == vault.address },
                    shareDecimals = vault.sharesDecimals,
                )
            }

        val metrics =
            runCatchingCancellable { kaminoApi.getVaultMetrics(vault.address) }.getOrNull()
        val rate = KaminoRate.parse(metrics?.tokensPerShare)
        val held = eligibility.withdrawableShares?.maximum

        // A real position whose rate could not be read is not "nothing to withdraw" — it is a
        // failed read, same as the positions call failing outright. Without this, a `/metrics`
        // outage would publish `Withdrawable` with a zero maximum, which reads as an emptied
        // position rather than an unresolved one. `rate == null` alone misses a `tokensPerShare`
        // of `"0"`: `KaminoRate.parse` accepts it as a valid non-positive rate, so it must be
        // caught via `!rate.isPositive` too, or `maximumTokens` rejecting it downstream leaves the
        // same zero-maximum-with-no-warning fold this guard exists to close.
        val resolvedEligibility =
            if (
                eligibility is KaminoWithdrawEligibility.Withdrawable &&
                    (rate == null || !rate.isPositive)
            ) {
                KaminoWithdrawEligibility.Unreadable
            } else {
                eligibility
            }

        val maximum =
            if (held != null && rate != null) {
                KaminoWithdrawMath.maximumTokens(held, rate, vault.tokenDecimals)
            } else {
                null
            }

        val vaultState =
            runCatchingCancellable { kaminoApi.getVaultState(vault.address) }.getOrNull()
        // Share base units, not token — comparing it against a token amount would be wrong by the
        // whole share rate (about 930x on the SOL vault).
        val minimumShares =
            vaultState?.state?.minWithdrawAmount?.let { raw ->
                runCatching { KaminoShareAmount(BigInteger(raw), vault.sharesDecimals) }.getOrNull()
            }
        val minimum =
            if (minimumShares != null && rate != null) {
                KaminoWithdrawMath.minimumTokens(minimumShares, rate, vault.tokenDecimals)
            } else {
                null
            }

        withdrawRate = rate
        withdrawHeldShares = held
        withdrawMaximumTokens = maximum
        liquidBuffer = KaminoTokenAmount.parse(metrics?.tokensAvailable, vault.tokenDecimals)

        _state.update {
            it.copy(
                isLoading = false,
                vaultName =
                    vaultState?.state?.name?.takeIf { name -> name.isNotBlank() }
                        ?: vault.fallbackName,
                ticker = coin.ticker,
                available = maximum.toDecimalOrZero(vault.tokenDecimals),
                minimum = minimum?.let { m -> BigDecimal(m.baseUnits).movePointLeft(m.decimals) },
                eligibility = resolvedEligibility,
            )
        }
    }

    private fun KaminoTokenAmount?.toDecimalOrZero(decimals: Int): BigDecimal =
        if (this == null) BigDecimal.ZERO.setScale(decimals)
        else BigDecimal(baseUnits).movePointLeft(this.decimals)

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
        val rawAmount = amountFieldState.text.toString().toBigDecimalOrNull() ?: return

        _state.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.safeLaunch(
            onError = { throwable ->
                Timber.e(throwable, "Failed to build Kamino transaction")
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        // Surface the refusal reason rather than a generic failure: a rejected
                        // transaction means the app declined to sign something, which the user
                        // should be able to read. An exception carrying no message would otherwise
                        // render as a blank line.
                        // A typed refusal carries its own localised text. Anything else is a fault
                        // rather than a rejection, so it gets the generic message — with the real
                        // exception already in the log above.
                        error =
                            UiText.StringResource(
                                (throwable as? KaminoAmountRefused)?.refusal?.messageRes
                                    ?: R.string.kamino_error_build_failed
                            ),
                    )
                }
            }
        ) {
            // Quantised to the token's own precision first, so the amount sent, the amount shown on
            // the verify screen and the amount checked against the balance are the same number.
            // Sending an unrounded 1.0000009 while displaying 1.000000 would sign one thing and
            // show another.
            val amount = rawAmount.setScale(coin.decimal, RoundingMode.DOWN)
            if (amount.signum() <= 0) refuse(KaminoAmountRefusal.BELOW_SMALLEST_UNIT)
            if (amount > _state.value.available) refuse(KaminoAmountRefusal.EXCEEDS_AVAILABLE)
            _state.value.minimum?.let { minimum ->
                if (amount < minimum) refuse(KaminoAmountRefusal.BELOW_MINIMUM)
            }

            // The denomination the endpoint expects, sized here rather than anywhere downstream.
            val apiAmount =
                if (route.isWithdraw) withdrawShares(amount, coin)
                else amount.stripTrailingZeros().toPlainString()

            val storedVault =
                vaultRepository.get(route.vaultId) ?: error("Vault ${route.vaultId} not found")

            // The fee is paid in SOL, never in the vault's underlying token. Denominating it in
            // `coin` rendered a lamport count against USDC's six decimals, so the verify screen
            // read "1 USDC" for a fee of 0.001 SOL. Every sibling Solana flow uses the native coin.
            //
            // Falls back to the coin definition rather than refusing when the vault has no native
            // SOL enabled — the fee is paid out of the same wallet either way, this value is only
            // ever displayed, and a deposit the user can otherwise afford must not be blocked by
            // which tokens they happen to have switched on.
            val solCoin =
                storedVault.coins.firstOrNull { it.chain == Chain.Solana && it.isNativeToken }
                    ?: Coins.Solana.SOL.copy(address = coin.address)

            val specific =
                blockChainSpecificRepository.getSpecific(
                    chain = Chain.Solana,
                    address = coin.address,
                    token = coin,
                    gasFee = TokenValue(SolanaHelper.DefaultFeeInLamports, solCoin),
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = true,
                )

            // Built here, at submit, so the blockhash inside it is as fresh as possible.
            val keysignPayload =
                buildKeysignPayload(
                    vault = vault,
                    action = action,
                    apiAmount = apiAmount,
                    tokenAmount = amount,
                    coin = coin,
                    blockChainSpecific = specific.blockChainSpecific,
                    vaultPublicKeyECDSA = storedVault.pubKeyECDSA,
                    vaultLocalPartyID = storedVault.localPartyID,
                    libType = storedVault.libType,
                )

            // What this transaction will be charged, read back from the compute budget the payload
            // records rather than from a flat constant, so the number on this screen is the number
            // inside the bytes.
            //
            // Deliberately the same arithmetic iOS applies to the relayed payload — its base term
            // is `SolanaHelper.defaultFeeInLamports`, the same 1,000,000, plus price × limit — so
            // the two devices quote one figure for one transaction instead of two.
            val recordedBudget = keysignPayload.blockChainSpecific as BlockChainSpecific.Solana
            // A first native-SOL deposit also spends the rent [spendableBalance] reserved against
            // the same balance, on the wSOL ATA, share ATA and farm UserState it creates. Folded in
            // here too, or the amount plus this fee would undercount the wallet's starting balance
            // by exactly that rent on the verify screen.
            val rentReserve =
                if (!route.isWithdraw && coin.isNativeToken) {
                    kaminoNativeDepositRentReserve(vault, coin)
                } else {
                    BigInteger.ZERO
                }
            val gasFee =
                TokenValue(
                    value =
                        SolanaHelper.DefaultFeeInLamports +
                            KaminoComputeBudget.priorityFeeLamports(
                                vault = vault,
                                action = action,
                                networkPrice = recordedBudget.priorityFee,
                            ) +
                            rentReserve,
                    token = solCoin,
                )

            val depositTx =
                DepositTransaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = route.vaultId,
                    srcToken = coin,
                    srcAddress = coin.address,
                    srcTokenValue = TokenValue(value = keysignPayload.toAmount, token = coin),
                    memo = "",
                    // The verify screen reads this stored copy, not the payload above, so the
                    // withdraw branch has to be applied here too or nothing the user sees changes.
                    dstAddress = kaminoDestinationAddress(vault, action, coin.address),
                    estimatedFees = gasFee,
                    estimateFeesFiat = "",
                    // The payload's, not `specific`'s. `KeysignShareViewModel` rebuilds the relayed
                    // KeysignPayload out of this transaction, so whatever is stored here is what a
                    // co-signer receives — storing the generic values would put them straight back
                    // and an iPhone would still see a fee that is not the one in the bytes.
                    blockChainSpecific = keysignPayload.blockChainSpecific,
                    operation =
                        if (route.isWithdraw) OPERATION_KAMINO_WITHDRAW
                        else OPERATION_KAMINO_DEPOSIT,
                    // Shown on verify as the destination vault, not as a validator.
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

    /**
     * Converts the entered token amount into the share quantity the withdraw endpoint expects.
     *
     * Everything hazardous about a withdraw lives here. The endpoint validates nothing: naming more
     * shares than the wallet holds is silently rewritten to `u64::MAX`, meaning *withdraw
     * everything*. So an over-request by a single base unit turns a partial withdraw into a full
     * exit, which is why an amount above the maximum is refused outright rather than clamped, and
     * why a withdraw of exactly the maximum sends the held balance verbatim instead of a number
     * round-tripped through tokens.
     */
    private fun withdrawShares(amount: BigDecimal, coin: Coin): String {
        val eligibility = _state.value.eligibility
        if (eligibility !is KaminoWithdrawEligibility.Withdrawable) {
            refuse(
                if (eligibility == KaminoWithdrawEligibility.Empty) {
                    KaminoAmountRefusal.NOTHING_TO_WITHDRAW
                } else {
                    KaminoAmountRefusal.POSITION_UNREADABLE
                }
            )
        }

        val rate = withdrawRate ?: refuse(KaminoAmountRefusal.RATE_UNAVAILABLE)
        val held = eligibility.shares.maximum
        val maximum = withdrawMaximumTokens ?: refuse(KaminoAmountRefusal.RATE_UNAVAILABLE)

        val requested =
            KaminoTokenAmount(
                baseUnits = amount.movePointRight(coin.decimal).toBigIntegerExact(),
                decimals = coin.decimal,
            )

        val shares =
            KaminoWithdrawMath.sharesForTokens(
                tokens = requested,
                held = held,
                maximumTokens = maximum,
                rate = rate,
                shareDecimals = vault?.sharesDecimals ?: held.decimals,
            )
        if (shares == null || shares.baseUnits > held.baseUnits) {
            // The second half is belt and braces on the one invariant that matters: never request
            // more shares than the position holds.
            refuse(KaminoAmountRefusal.LARGER_THAN_POSITION)
        }
        return shares.apiString()
    }

    /** Recomputes whether the entered amount fits the vault's liquid buffer. */
    fun onAmountChanged(amount: BigDecimal?) {
        if (!route.isWithdraw) return
        val decimals = tokenCoin?.decimal ?: return
        val requested =
            amount
                ?.takeIf { it.signum() > 0 }
                ?.let {
                    runCatching {
                            KaminoTokenAmount(it.movePointRight(decimals).toBigInteger(), decimals)
                        }
                        .getOrNull()
                }
        val liquidity =
            if (requested == null) KaminoWithdrawLiquidity.Instant
            else KaminoWithdrawLiquidity.resolve(requested, liquidBuffer)
        if (liquidity != _state.value.liquidity) {
            _state.update { it.copy(liquidity = liquidity) }
        }
    }

    private fun refuse(refusal: KaminoAmountRefusal): Nothing = throw KaminoAmountRefused(refusal)

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun back() {
        viewModelScope.safeLaunch(onError = { Timber.w(it, "back failed") }) { navigator.back() }
    }

    private companion object {
        /**
         * Rent-exempt minimum for a 165-byte SPL token account. Used only when the live read fails
         * — reserving nothing would be worse than reserving a value that has not moved in practice.
         */
        val SPL_TOKEN_ACCOUNT_RENT_LAMPORTS: BigInteger = BigInteger.valueOf(2_039_280)

        /**
         * Rent-exempt minimum Kamino charges when a first deposit creates the farms `UserState`.
         * The account size is Kamino-owned, so keep the observed mainnet lamport value here rather
         * than pretending the app can derive it from SPL token-account rent.
         */
        val FARM_USER_STATE_RENT_LAMPORTS: BigInteger = BigInteger.valueOf(6_299_080)

        val ONE_HUNDRED = BigDecimal(100)
        const val DEFAULT_SCALE = 6
    }
}
