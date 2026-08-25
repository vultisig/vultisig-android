package com.vultisig.wallet.ui.models.referral

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.cosmos.NativeTxFeeRune
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.ThorChainPoolCoin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCaseImpl
import com.vultisig.wallet.data.utils.decimals
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.models.referral.CreateReferralViewModel.Companion.BLOCKS_PER_YEAR
import com.vultisig.wallet.ui.models.referral.CreateReferralViewModel.Companion.DATE_FORMAT
import com.vultisig.wallet.ui.models.referral.CreateReferralViewModel.Companion.DEFAULT_BLOCK_FEES
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import vultisig.keysign.v1.TransactionType
import wallet.core.jni.CoinType

internal data class EditVaultReferralUiState(
    val referralCounter: Int = 0,
    val referralCostAmountFormatted: String = "",
    val referralCostFiatFormatted: String = "",
    val referralExpiration: String = "",
    val costFeesTokenAmount: String = "",
    val payoutAsset: PayoutAssetUiModel? = null,
    val isSaveEnabled: Boolean = false,
    val error: ReferralError? = null,
)

@HiltViewModel
internal class EditVaultReferralViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val gasFeeToEstimate: GasFeeToEstimatedFeeUseCaseImpl,
    private val accountsRepository: AccountsRepository,
    private val transactionRepository: DepositTransactionRepository,
    private val thorChainApi: ThorChainApi,
    private val requestResultRepository: RequestResultRepository,
    private val vaultRepository: VaultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val enableToken: EnableTokenUseCase,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.ReferralVaultEdition>()
    private val vaultId: String = args.vaultId
    private val vaultReferralCode: String = args.code
    private val vaultReferralExpiration: String = args.expiration
    private var nativeRuneFees: NativeTxFeeRune? = null

    val referralTextFieldState = TextFieldState()
    val state = MutableStateFlow(EditVaultReferralUiState())
    private var address: Address? = null

    /** The asset the THORName is registered with today; null while unknown or when it has none. */
    private var initialPayoutAsset: ThorChainPoolCoin? = null
    private var selectedPayoutAsset: ThorChainPoolCoin? = null
    private var hasPickedPayoutAsset = false

    init {
        loadAddress()
        initData()
        calculateFees()
    }

    private fun initData() {
        viewModelScope.launch {
            referralTextFieldState.setTextAndPlaceCursorAtEnd(vaultReferralCode)

            state.update {
                it.copy(referralExpiration = vaultReferralExpiration, payoutAsset = RUNE_PAYOUT)
            }

            try {
                nativeRuneFees =
                    withContext(Dispatchers.IO) { thorChainApi.getTHORChainReferralFees() }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w(e, "Falling back to default referral fees")
                nativeRuneFees = null
            }
        }

        loadPayoutAsset()
    }

    /**
     * Reads the payout asset the THORName already carries so the picker opens on it and an edit
     * that only extends the expiry keeps it, rather than silently re-registering the name without
     * one.
     */
    private fun loadPayoutAsset() {
        viewModelScope.launch {
            val preferredAsset =
                try {
                    withContext(Dispatchers.IO) {
                            thorChainApi.getReferralCodeInfo(vaultReferralCode)
                        }
                        .preferredAsset
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    Timber.w(t, "Failed to load the referral's payout asset")
                    return@launch
                }

            val asset = ThorChainPoolCoin.from(preferredAsset) ?: return@launch
            initialPayoutAsset = asset
            // A pick made while this was in flight is the newer intent, and stands.
            if (hasPickedPayoutAsset) {
                state.update { it.copy(isSaveEnabled = isSaveEnabled(it.referralCounter)) }
                return@launch
            }
            selectedPayoutAsset = asset
            state.update { it.copy(payoutAsset = asset.toUiModel()) }
        }
    }

    private fun updateReferralCounter(yearsDelta: Long) {
        try {
            val formatter = DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.getDefault())
            val stateValue = state.value

            val newExpiration =
                LocalDate.parse(stateValue.referralExpiration, formatter)
                    .plusYears(yearsDelta)
                    .format(formatter)

            val newCounter = (stateValue.referralCounter + yearsDelta.toInt()).coerceAtLeast(0)

            state.update {
                it.copy(
                    referralCounter = newCounter,
                    referralExpiration = newExpiration,
                    isSaveEnabled = isSaveEnabled(newCounter),
                )
            }
        } catch (t: Throwable) {
            Timber.e(t, "Failed to parse date")
        }
    }

    fun onIncrementCounter() {
        viewModelScope.launch {
            updateReferralCounter(yearsDelta = 1)
            calculateFees()
        }
    }

    fun onDecrementCounter() {
        viewModelScope.launch {
            updateReferralCounter(yearsDelta = -1)
            calculateFees()
        }
    }

    fun onSelectPayoutAsset() {
        viewModelScope.launch {
            val requestId = UUID.randomUUID().toString()
            navigator.route(
                Route.ReferralPayoutAsset(
                    requestId = requestId,
                    selectedAsset = selectedPayoutAsset?.asset,
                )
            )

            val asset =
                requestResultRepository.request<ThorChainPoolCoin>(requestId) ?: return@launch

            selectedPayoutAsset = asset
            hasPickedPayoutAsset = true
            state.update {
                it.copy(
                    payoutAsset = asset.toUiModel(),
                    isSaveEnabled = isSaveEnabled(it.referralCounter),
                )
            }
        }
    }

    /**
     * Save covers two independent edits: buying more years, and switching the payout asset. Either
     * alone is a valid transaction, so an unchanged asset with no added year is the only state that
     * has nothing to sign.
     */
    private fun isSaveEnabled(counter: Int): Boolean =
        counter > 0 || selectedPayoutAsset?.asset != initialPayoutAsset?.asset

    private fun calculateFees() {
        viewModelScope.launch {
            val years = state.value.referralCounter

            if (years == 0) {
                val totalFeesFiat = withContext(Dispatchers.IO) { BigInteger.ZERO.convertToFiat() }
                state.update {
                    it.copy(
                        referralCostFiatFormatted = totalFeesFiat,
                        referralCostAmountFormatted = "0 ${CoinType.THORCHAIN.symbol}",
                        costFeesTokenAmount = "0",
                    )
                }
                return@launch
            }

            val totalFees =
                (nativeRuneFees?.feePerBlock ?: DEFAULT_BLOCK_FEES).toBigInteger() *
                    years.toBigInteger() *
                    BLOCKS_PER_YEAR

            val totalFeesFiat = withContext(Dispatchers.IO) { totalFees.convertToFiat() }

            val formattedRegistrationTokenFees =
                "${CoinType.THORCHAIN.toValue(totalFees)} ${CoinType.THORCHAIN.symbol}"

            state.update {
                it.copy(
                    costFeesTokenAmount = totalFees.toString(),
                    referralCostFiatFormatted = totalFeesFiat,
                    referralCostAmountFormatted = formattedRegistrationTokenFees,
                )
            }
        }
    }

    fun onSavedReferral() {
        viewModelScope.launch {
            try {
                val account =
                    address?.accounts?.find { it.token.isNativeToken }
                        ?: error("Can't load account")
                val balance = account.tokenValue?.value ?: BigInteger.ZERO
                val totalFees = state.value.costFeesTokenAmount.toBigInteger()
                val gasFeeValue = nativeRuneFees?.value?.toBigInteger() ?: "2000000".toBigInteger()
                if (balance < totalFees + gasFeeValue) {
                    state.update { it.copy(error = ReferralError.BALANCE_ERROR) }
                    return@launch
                }

                val address = account.token.address
                val payoutAsset = selectedPayoutAsset
                val memo =
                    buildEditReferralMemo(
                        referralCode = vaultReferralCode,
                        thorAddress = address,
                        payoutAsset = payoutAsset,
                        payoutAssetAddress = payoutAsset?.let { payoutAddress(it.coin) },
                    )
                val gasFees =
                    TokenValue(
                        value = gasFeeValue,
                        unit = CoinType.THORCHAIN.symbol,
                        decimals = CoinType.THORCHAIN.decimals,
                    )
                val toAmount = state.value.costFeesTokenAmount.toBigInteger()
                val blockchainSpecific =
                    withContext(Dispatchers.IO) {
                        blockChainSpecificRepository
                            .getSpecific(
                                chain = Chain.ThorChain,
                                address = account.token.address,
                                token = account.token,
                                isDeposit = true,
                                memo = memo,
                                isSwap = false,
                                isMaxAmountEnabled = false,
                                transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                                tokenAmountValue = state.value.costFeesTokenAmount.toBigInteger(),
                                gasFee = gasFees,
                            )
                            .blockChainSpecific
                    }

                val tx =
                    DepositTransaction(
                        id = UUID.randomUUID().toString(),
                        vaultId = vaultId,
                        srcToken = account.token,
                        srcAddress = address,
                        dstAddress = "",
                        memo = memo,
                        srcTokenValue = TokenValue(value = toAmount, token = account.token),
                        estimatedFees = gasFees,
                        estimateFeesFiat =
                            withContext(Dispatchers.IO) { gasFees.value.convertToFiat() },
                        blockChainSpecific = blockchainSpecific,
                    )

                transactionRepository.addTransaction(tx)

                navigator.route(Route.VerifyDeposit(vaultId, tx.id))
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Timber.e(t, "Failed to save edited referral")
                state.update { it.copy(error = ReferralError.UNKNOWN_ERROR) }
            }
        }
    }

    /**
     * This vault's address on the payout asset's chain — the alias THORChain pays the asset out to.
     * The token is enabled alongside it, so the payouts land somewhere the vault actually shows.
     */
    private suspend fun payoutAddress(coin: Coin): String {
        val vault = vaultRepository.get(vaultId) ?: error("Can't load vault")
        val address =
            withContext(Dispatchers.IO) {
                chainAccountAddressRepository.getAddress(coin, vault).first
            }
        enablePayoutToken(coin, vault)
        return address
    }

    private suspend fun enablePayoutToken(coin: Coin, vault: Vault) {
        if (vault.coins.any { it.id == coin.id }) return
        try {
            enableToken(vaultId, coin)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            // Only costs the user the token's row in the vault — the memo, and with it the
            // payout itself, is unaffected.
            Timber.w(t, "Failed to add the payout asset to the vault")
        }
    }

    private fun loadAddress() {
        viewModelScope.launch {
            try {
                accountsRepository.loadAddress(vaultId, Chain.ThorChain).collect { address ->
                    this@EditVaultReferralViewModel.address = address
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "Failed to load address")
                Timber.e(e)
            }
        }
    }

    private suspend fun BigInteger.convertToFiat(): String {
        val gasFeeParams =
            Coins.coins[Chain.ThorChain]?.first()?.let { selectedCoin ->
                GasFeeParams(
                    gasLimit = BigInteger.ONE,
                    gasFee = TokenValue(this, "RUNE", 8),
                    selectedToken = selectedCoin,
                )
            } ?: error("Can't calculate fees")

        return gasFeeToEstimate.invoke(gasFeeParams).formattedFiatValue
    }

    fun onDismissError() {
        viewModelScope.launch { state.update { it.copy(error = null) } }
    }

    private fun ThorChainPoolCoin.toUiModel(): PayoutAssetUiModel =
        PayoutAssetUiModel(
            asset = asset,
            logo = getCoinLogo(coin.logo),
            ticker = coin.ticker,
            chain = coin.chain.raw,
        )

    private companion object {
        /** What a THORName pays out in until an asset is chosen. */
        val RUNE_PAYOUT =
            PayoutAssetUiModel(
                asset = "",
                logo = getCoinLogo(Coins.ThorChain.RUNE.logo),
                ticker = Coins.ThorChain.RUNE.ticker,
                chain = Chain.ThorChain.raw,
            )
    }
}
