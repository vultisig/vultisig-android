package com.vultisig.wallet.ui.models

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.RIPPLE_TRUST_LINE_LIMIT
import com.vultisig.wallet.data.models.RippleTrustLineQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.groupedLimit
import com.vultisig.wallet.data.models.rippleCurrencyTicker
import com.vultisig.wallet.data.models.rippleTokenIdentity
import com.vultisig.wallet.data.models.rippleTrustSetDisplay
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.RippleTrustLines
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import vultisig.keysign.v1.TransactionType

@Immutable
internal data class RippleTrustLineActivationUiModel(
    val ticker: String = "",
    val issuer: String = "",
    val reserve: String = "",
    val networkFee: String = "",
    val spendableAfter: String = "",
    val limit: String = "",
    val isAffordable: Boolean = false,
    val isLoading: Boolean = true,
    val isActivating: Boolean = false,
    val error: UiText? = null,
)

@HiltViewModel
internal class RippleTrustLineActivationViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val accountsRepository: AccountsRepository,
    private val vaultRepository: VaultRepository,
    private val transactionRepository: TransactionRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val feeServiceComposite: FeeServiceComposite,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val rippleTrustLines: RippleTrustLines,
    private val appCurrencyRepository: AppCurrencyRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.RippleTrustLineActivation>()

    val uiState = MutableStateFlow(RippleTrustLineActivationUiModel())

    private var quoted: Quoted? = null

    private data class Quoted(val token: Coin, val issuer: String, val fee: TokenValue)

    init {
        quote()
    }

    private fun quote() {
        viewModelScope.safeLaunch(onError = { showError() }) {
            val accounts =
                accountsRepository.loadCachedAddress(args.vaultId, Chain.Ripple).first().accounts
            val token = accounts.firstOrNull { it.token.id == args.tokenId }?.token
            val identity = token?.rippleTokenIdentity()
            val native = accounts.firstOrNull { it.token.isNativeToken }
            if (identity == null || native == null) {
                showError()
                return@safeLaunch
            }

            val fee = fetchFee(native.token, identity.issuer)
            val quote =
                RippleTrustLineQuote(
                    ownerReserveDrops =
                        withContext(Dispatchers.IO) { rippleTrustLines.fetchOwnerReserve() },
                    feeDrops = fee.value,
                    spendableDrops = native.tokenValue?.value ?: BigInteger.ZERO,
                )

            quoted = Quoted(token = token, issuer = identity.issuer, fee = fee)
            uiState.update {
                it.copy(
                    ticker = rippleCurrencyTicker(identity.currency),
                    issuer = identity.issuer,
                    reserve = quote.ownerReserveDrops.asXrp(native.token),
                    networkFee = mapTokenValueToStringWithUnit(fee),
                    spendableAfter = quote.remainingSpendableDrops.asXrp(native.token),
                    limit = limitDisplay(token),
                    isAffordable = quote.isAffordable,
                    isLoading = false,
                )
            }
        }
    }

    private suspend fun fetchFee(xrp: Coin, issuer: String): TokenValue {
        // FeeServiceComposite falls back to a default fee, so an unreachable node is not fatal.
        val vault = vaultRepository.get(args.vaultId) ?: error("No vault ${args.vaultId}")
        val fees =
            withContext(Dispatchers.IO) {
                feeServiceComposite.calculateFees(
                    Transfer(
                        coin = xrp,
                        vault =
                            VaultData(
                                vaultHexPublicKey = vault.getPubKeyByChain(Chain.Ripple),
                                vaultHexChainCode = vault.hexChainCode,
                            ),
                        amount = BigInteger.ZERO,
                        to = issuer,
                    )
                )
            }
        return TokenValue(value = fees.amount, token = xrp)
    }

    private fun showError() {
        uiState.update {
            it.copy(
                isLoading = false,
                error = UiText.StringResource(R.string.error_view_default_description),
            )
        }
    }

    private fun BigInteger.asXrp(xrp: Coin) =
        mapTokenValueToStringWithUnit(TokenValue(value = this, token = xrp))

    // The same terms the Verify screen will show, from the same helper.
    private fun limitDisplay(token: Coin): String {
        val display = rippleTrustSetDisplay(token, RIPPLE_TRUST_LINE_LIMIT) ?: return ""
        return "${display.groupedLimit} ${display.ticker}"
    }

    fun activate() {
        val (token, issuer, gasFee) = quoted ?: return
        if (uiState.value.isActivating) return
        uiState.update { it.copy(isActivating = true) }

        viewModelScope.safeLaunch(onError = { showError() }) {
            val specific =
                withContext(Dispatchers.IO) {
                    blockChainSpecificRepository.getSpecific(
                        chain = Chain.Ripple,
                        address = token.address,
                        token = token,
                        gasFee = gasFee,
                        isSwap = false,
                        isMaxAmountEnabled = false,
                        isDeposit = false,
                        dstAddress = issuer,
                        transactionType = TransactionType.TRANSACTION_TYPE_RIPPLE_TRUST_SET,
                    )
                }
            val estimatedFee =
                gasFeeToEstimatedFee(
                    GasFeeParams(gasLimit = BigInteger.ONE, gasFee = gasFee, selectedToken = token)
                )

            val transaction =
                Transaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = args.vaultId,
                    chainId = Chain.Ripple.raw,
                    token = token,
                    srcAddress = token.address,
                    // A TrustSet has no destination; the issuer is the account being trusted.
                    dstAddress = issuer,
                    tokenValue =
                        TokenValue(
                            value = RIPPLE_TRUST_LINE_LIMIT,
                            unit = token.ticker,
                            decimals = token.decimal,
                        ),
                    // Zero, but the ticker must be a real ISO code: the verify mapper formats it
                    // through java.util.Currency.
                    fiatValue =
                        FiatValue(BigDecimal.ZERO, appCurrencyRepository.currency.first().ticker),
                    gasFee = gasFee,
                    blockChainSpecific = specific.blockChainSpecific,
                    memo = null,
                    estimatedFee = estimatedFee.formattedFiatValue,
                    totalGas = estimatedFee.formattedTokenValue,
                )

            transactionRepository.addTransaction(transaction)
            navigator.route(Route.VerifySend(transaction.id, args.vaultId))
        }
    }

    private fun onActivateFailed() {
        uiState.update { it.copy(isActivating = false) }
        showError()
    }

    fun dismiss() {
        viewModelScope.safeLaunch { navigator.navigate(Destination.Back) }
    }
}
