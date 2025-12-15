package com.vultisig.wallet.ui.screens.v2.defi.circle

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.CircleApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.ScaCircleAccountRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.model.DefiUiModel
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText.StringResource
import com.vultisig.wallet.ui.utils.asString
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
internal class CircleDeFiPositionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val scaCircleAccountRepository: ScaCircleAccountRepository,
    private val circleApi: CircleApi,
    private val evmApi: EvmApiFactory,
    private val vaultRepository: VaultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val snackbarFlow: SnackbarFlow,
    private val stakingDetailsRepository: StakingDetailsRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private var vaultId: String = savedStateHandle.toRoute<Route.PositionCircle>().vaultId
    private var mscaAddress: String? = null

    private val _state = MutableStateFlow(
        DefiUiModel(
            totalAmountPrice = "$0.00",
            isTotalAmountLoading = true,
            isBalanceVisible = true,
            supportEditChains = false,
            selectedTab = DeFiTab.DEPOSITED.displayName,
            bannerImage = R.drawable.circle_defi_banner,
        )
    )

    val state: StateFlow<DefiUiModel> = _state.asStateFlow()

    init {
        loadCirclePositions()
    }

    private fun loadCirclePositions() {
        viewModelScope.launch {
            // Initial UI + Warning Status
            _state.update { currentState ->
                currentState.copy(
                    isTotalAmountLoading = true,
                    circleDefi = currentState.circleDefi.copy(
                        isLoading = true
                    )
                )
            }
            val hideWarning = withContext(Dispatchers.IO) {
                scaCircleAccountRepository.getCloseWarning()
            }
            _state.update { currentState ->
                currentState.copy(
                    circleDefi = currentState.circleDefi.copy(
                        closeWarning = hideWarning
                    )
                )
            }

            // Check account exists
            val addressSca = withContext(Dispatchers.IO) {
                scaCircleAccountRepository.getAccount(vaultId)
            }

            // If not cache or don't exists, check network for MSCA and fetch balance
            if (addressSca == null) {
                val fetchedAddress = fetchAssociatedMscaAccount()
                if (fetchedAddress != null) {
                    mscaAddress = fetchedAddress
                    fetchUSDCBalanceFromNetwork(fetchedAddress)
                }
            } else { // If account exists, show cache, then fetch and update from network
                mscaAddress = addressSca
                _state.update { currentState ->
                    currentState.copy(
                        circleDefi = currentState.circleDefi.copy(
                            isAccountOpen = true,
                        )
                    )
                }
                fetchUSDCBalanceFromNetwork(addressSca)
            }
        }
    }

    fun onTabSelected(tab: String) {
        _state.update { currentState ->
            currentState.copy(selectedTab = tab)
        }
    }

    fun onBackClick() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    fun onClickCloseWarning() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                scaCircleAccountRepository.saveCloseWarning()
            }
            _state.update { currentState ->
                currentState.copy(
                    circleDefi = currentState.circleDefi.copy(
                        closeWarning = true,
                    )
                )
            }
        }
    }

    fun onCreateAccount() {
        viewModelScope.launch {
            val mscAddress = withContext(Dispatchers.IO) {
                try {
                    val ethereumVaultAddress = getEvmVaultAddress()
                    val mscaAddress = circleApi.createScAccount(ethereumVaultAddress)
                    scaCircleAccountRepository.saveAccount(vaultId, mscaAddress)
                    mscaAddress
                } catch (t: Throwable) {
                    Timber.e(t)
                    null
                }
            }

            if (mscAddress == null) {
                snackbarFlow.showMessage(
                    StringResource(R.string.circle_msca_account_created_failed).asString(context)
                )
            } else {
                _state.update { currentState ->
                    currentState.copy(
                        circleDefi = currentState.circleDefi.copy(
                            isAccountOpen = true,
                        )
                    )
                }
                snackbarFlow.showMessage(
                    StringResource(R.string.circle_msca_account_created_success).asString(context)
                )
            }
        }
    }

    fun onDepositAccount() {
        viewModelScope.launch {
            val tokenId = Coins.Ethereum.USDC.id
            if (!mscaAddress.isNullOrBlank()) {
                navigator.route(
                    Route.Send(
                        vaultId = vaultId,
                        chainId = Chain.Ethereum.id,
                        tokenId = tokenId,
                        address = mscaAddress,
                    )
                )
            }
        }
    }

    private suspend fun getEvmVaultAddress(): String {
        val vault = vaultRepository.get(vaultId)
        if (vault != null) {
            val (address, _) = chainAccountAddressRepository.getAddress(
                Chain.Ethereum,
                vault
            )
            return address
        } else {
            Timber.e("CircleDeFiPositionsViewModel: Vault Null for $vaultId")
            error("CircleDeFiPositionsViewModel: Vault Null for $vaultId")
        }
    }

    private suspend fun fetchAssociatedMscaAccount(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val evmAddress = getEvmVaultAddress()
                val mscaAddress = circleApi.getScAccount(evmAddress)
                if (mscaAddress != null) {
                    scaCircleAccountRepository.saveAccount(vaultId, mscaAddress)
                }
                mscaAddress
            } catch (t: Throwable) {
                Timber.e(t)
                null
            }
        }
    }

    private suspend fun fetchUSDCBalanceFromNetwork(address: String, forceRefresh: Boolean = false) {
        val api = evmApi.createEvmApi(Chain.Ethereum)
        val usdc = Coins.Ethereum.USDC.copy(address = address)
        val usdcDepositedBalance = withContext(Dispatchers.IO){
            api.getBalance(usdc)
        }

        val usdcFormattedBalance = usdcDepositedBalance.toValue(usdc.decimal)
        val currency = withContext(Dispatchers.IO) {
            appCurrencyRepository.currency.first()
        }
        val currencyFormat = withContext(Dispatchers.IO) {
            appCurrencyRepository.getCurrencyFormat()
        }
        val usdcTokenPrice = createFiatValue(
            amount = usdcFormattedBalance,
            currency = currency,
            coin = usdc,
        )

        _state.update { currentState ->
            currentState.copy(
                totalAmountPrice = currencyFormat.format(usdcTokenPrice.value),
                isTotalAmountLoading = false,
                supportEditChains = false,
                circleDefi = currentState.circleDefi.copy(
                    isLoading = false,
                    isAccountOpen = true,
                    totalDeposit = "$usdcFormattedBalance USDC",
                )
            )
        }
    }

    private suspend fun createFiatValue(
        amount: BigDecimal,
        coin: Coin,
        currency: AppCurrency
    ): FiatValue {
        try {
            if (amount == BigDecimal.ZERO) {
                return FiatValue(BigDecimal.ZERO, currency.ticker)
            }

            val price = tokenPriceRepository.getCachedPrice(
                tokenId = coin.id,
                appCurrency = currency
            ) ?: tokenPriceRepository.getPriceByContactAddress(coin.chain.id, coin.contractAddress)

            return FiatValue(
                value = amount.multiply(price).setScale(2, RoundingMode.DOWN),
                currency = currency.ticker
            )
        } catch (t: Throwable) {
            Timber.e(t)

            return FiatValue(
                value = BigDecimal.ZERO,
                currency = currency.ticker
            )
        }
    }
}