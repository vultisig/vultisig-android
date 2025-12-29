package com.vultisig.wallet.ui.screens.v2.defi.circle

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.CircleApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
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
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.DefiUiModel
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText.StringResource
import com.vultisig.wallet.ui.utils.asString
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
internal class CircleDeFiPositionsViewModel @Inject constructor(
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
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private lateinit var vaultId: String
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

    fun setData(vaultId: String) {
        this.vaultId = vaultId
        loadBalanceVisibility()
        loadAccountStatus()
        loadCirclePositions()
    }

    private fun loadAccountStatus() {
        viewModelScope.launch {
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
        }
    }

    private fun loadBalanceVisibility() {
        viewModelScope.launch {
            val isVisible = withContext(Dispatchers.IO) {
                balanceVisibilityRepository.getVisibility(vaultId)
            }
            _state.update { it.copy(isBalanceVisible = isVisible) }
        }
    }

    private fun loadCirclePositions() {
        viewModelScope.launch {
            // Initial UI
            _state.update { currentState ->
                currentState.copy(
                    isTotalAmountLoading = true,
                    circleDefi = currentState.circleDefi.copy(
                        isLoading = true
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
                } else {
                    _state.update { currentState ->
                        currentState.copy(
                            isTotalAmountLoading = false,
                            circleDefi = currentState.circleDefi.copy(
                                isLoading = false,
                                isAccountOpen = false,
                            )
                        )
                    }
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
                val cachePosition =
                    withContext(Dispatchers.IO) {
                        stakingDetailsRepository.getStakingDetailsByCoindId(vaultId, Coins.Ethereum.USDC.id)
                    }
                if (cachePosition != null) {
                    showUSDCPosition(cachePosition.stakeAmount, cachePosition.coin)
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
                    this@CircleDeFiPositionsViewModel.mscaAddress = mscaAddress
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
                snackbarFlow.showMessage(
                    StringResource(R.string.circle_msca_account_created_success).asString(context)
                )
            }

            _state.update { currentState ->
                currentState.copy(
                    circleDefi = currentState.circleDefi.copy(
                        isAccountOpen = mscAddress != null,
                    )
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
                        type = DeFiNavActions.DEPOSIT_USDC_CIRCLE.type,
                    )
                )
            }
        }
    }

    fun onWithdrawAccount() {
        viewModelScope.launch {
            val usdc = Coins.Ethereum.USDC
            val tokenId = usdc.id
            if (!mscaAddress.isNullOrBlank()) {
                navigator.route(
                    Route.Send(
                        vaultId = vaultId,
                        chainId = Chain.Ethereum.id,
                        type = DeFiNavActions.WITHDRAW_USDC_CIRCLE.type,
                        tokenId = tokenId,
                        address = usdc.contractAddress,
                        mscaAddress = mscaAddress,
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

    private suspend fun fetchUSDCBalanceFromNetwork(mscaAddress: String) {
        val api = evmApi.createEvmApi(Chain.Ethereum)
        val usdc = Coins.Ethereum.USDC.copy(address = mscaAddress)
        val usdcDepositedBalance = withContext(Dispatchers.IO) {
            api.getBalance(usdc)
        }

        showUSDCPosition(usdcDepositedBalance, usdc)

        val usdcCircleStakingDetails = StakingDetails(
            id = usdc.generateId(mscaAddress),
            coin = usdc,
            stakeAmount = usdcDepositedBalance,
            apr = null,
            estimatedRewards = null,
            nextPayoutDate = null,
            rewards = null,
            rewardsCoin = usdc,
        )

        // Save position in  cache
        withContext(Dispatchers.IO) {
            stakingDetailsRepository.saveStakingDetails(vaultId, usdcCircleStakingDetails)
        }
    }

    private suspend fun showUSDCPosition(
        usdcDepositedBalance: BigInteger,
        usdc: Coin
    ) = supervisorScope {
        val usdcFormattedBalance = usdcDepositedBalance.toValue(usdc.decimal)
        val currency = async(Dispatchers.IO) {
            appCurrencyRepository.currency.first()
        }
        val currencyFormat = async(Dispatchers.IO) {
            appCurrencyRepository.getCurrencyFormat()
        }

        val usdcTokenPrice = createFiatValue(
            amount = usdcFormattedBalance,
            currency = currency.await(),
            coin = usdc,
        )

        _state.update { currentState ->
            currentState.copy(
                totalAmountPrice = currencyFormat.await().format(usdcTokenPrice.value),
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