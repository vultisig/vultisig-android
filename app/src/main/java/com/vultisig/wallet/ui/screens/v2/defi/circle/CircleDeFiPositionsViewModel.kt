package com.vultisig.wallet.ui.screens.v2.defi.circle

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
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
import com.vultisig.wallet.ui.components.v2.snackbar.SnackbarType
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
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
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

@HiltViewModel
internal class CircleDeFiPositionsViewModel
@Inject
constructor(
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private lateinit var vaultId: String
    private var mscaAddress: String? = null
    private var loadJob: Job? = null

    private val _state =
        MutableStateFlow(
            DefiUiModel(
                totalAmountPrice = "$0.00",
                isTotalAmountLoading = true,
                isBalanceVisible = true,
                supportEditChains = false,
                selectedTab = DeFiTab.DEPOSITED.displayNameRes,
                bannerImage = R.drawable.circle_defi_banner,
            )
        )

    val state: StateFlow<DefiUiModel> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    /** True while a user-initiated pull-to-refresh is in flight. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun setData(vaultId: String) {
        this.vaultId = vaultId
        loadBalanceVisibility()
        loadAccountStatus()
        loadCirclePositions()
    }

    /** Triggers a user-initiated pull-to-refresh; resets [isRefreshing] when complete. */
    fun refresh() {
        _isRefreshing.value = true
        loadCirclePositions()
    }

    private fun loadAccountStatus() {
        viewModelScope.launch {
            try {
                val hideWarning =
                    withContext(ioDispatcher) { scaCircleAccountRepository.getCloseWarning() }
                _state.update { currentState ->
                    currentState.copy(
                        circleDefi = currentState.circleDefi.copy(closeWarning = hideWarning)
                    )
                }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    private fun loadBalanceVisibility() {
        viewModelScope.launch {
            try {
                val isVisible =
                    withContext(ioDispatcher) { balanceVisibilityRepository.getVisibility(vaultId) }
                _state.update { it.copy(isBalanceVisible = isVisible) }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    private fun loadCirclePositions() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                try {
                    // Initial UI
                    _state.update { currentState ->
                        currentState.copy(
                            isTotalAmountLoading = true,
                            circleDefi = currentState.circleDefi.copy(isLoading = true),
                        )
                    }

                    // Check account exists
                    val addressSca =
                        withContext(ioDispatcher) { scaCircleAccountRepository.getAccount(vaultId) }

                    // If not cache or don't exists, check network for MSCA and fetch balance
                    if (addressSca == null) {
                        val fetchedAddress = fetchAssociatedMscaAccount()
                        if (fetchedAddress != null) {
                            mscaAddress = fetchedAddress
                            fetchUSDCBalanceFromNetwork(fetchedAddress)
                        } else {
                            // Preserve `isAccountOpen` from current state: if `onCreateAccount`
                            // raced ahead and already marked the account open, don't stomp it back
                            // to false based on this now-stale "no account" finding.
                            _state.update { currentState ->
                                currentState.copy(
                                    isTotalAmountLoading = false,
                                    circleDefi = currentState.circleDefi.copy(isLoading = false),
                                )
                            }
                        }
                    } else { // If account exists, show cache, then fetch and update from network
                        mscaAddress = addressSca
                        _state.update { currentState ->
                            currentState.copy(
                                circleDefi = currentState.circleDefi.copy(isAccountOpen = true)
                            )
                        }
                        val cachePosition =
                            withContext(ioDispatcher) {
                                stakingDetailsRepository.getStakingDetailsByCoindId(
                                    vaultId,
                                    Coins.Ethereum.USDC.id,
                                )
                            }
                        if (cachePosition != null) {
                            showUSDCPosition(cachePosition.stakeAmount, cachePosition.coin)
                        }
                        fetchUSDCBalanceFromNetwork(addressSca)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Timber.e(t)
                    _state.update { currentState ->
                        currentState.copy(
                            isTotalAmountLoading = false,
                            circleDefi = currentState.circleDefi.copy(isLoading = false),
                        )
                    }
                } finally {
                    if (!coroutineContext[Job]!!.isCancelled) {
                        _isRefreshing.value = false
                    }
                }
            }
    }

    fun onTabSelected(tab: DeFiTab) {
        _state.update { currentState -> currentState.copy(selectedTab = tab.displayNameRes) }
    }

    fun onBackClick() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    fun onClickCloseWarning() {
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { scaCircleAccountRepository.saveCloseWarning() }
                _state.update { currentState ->
                    currentState.copy(
                        circleDefi = currentState.circleDefi.copy(closeWarning = true)
                    )
                }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    fun onCreateAccount() {
        viewModelScope.launch {
            val createdAddress =
                withContext(ioDispatcher) {
                    runCatching {
                        val ethereumVaultAddress = getEvmVaultAddress()
                        circleApi.createScAccount(ethereumVaultAddress).also { newAddress ->
                            scaCircleAccountRepository.saveAccount(vaultId, newAddress)
                        }
                    }
                }

            createdAddress
                .onSuccess { newAddress ->
                    mscaAddress = newAddress
                    showSnackbar(R.string.circle_msca_account_created_success, SnackbarType.Success)
                }
                .onFailure { t ->
                    Timber.e(t)
                    showSnackbar(R.string.circle_msca_account_created_failed, SnackbarType.Error)
                }

            _state.update { currentState ->
                currentState.copy(
                    circleDefi =
                        currentState.circleDefi.copy(isAccountOpen = createdAddress.isSuccess)
                )
            }
        }
    }

    private suspend fun showSnackbar(@StringRes messageRes: Int, type: SnackbarType) {
        snackbarFlow.showMessage(StringResource(messageRes).asString(context), type)
    }

    fun onDepositAccount() {
        viewModelScope.launch {
            try {
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
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    fun onWithdrawAccount() {
        viewModelScope.launch {
            try {
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
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }
    }

    private suspend fun getEvmVaultAddress(): String {
        val vault = vaultRepository.get(vaultId)
        if (vault != null) {
            val (address, _) = chainAccountAddressRepository.getAddress(Chain.Ethereum, vault)
            return address
        } else {
            Timber.e("CircleDeFiPositionsViewModel: Vault Null for $vaultId")
            error("CircleDeFiPositionsViewModel: Vault Null for $vaultId")
        }
    }

    private suspend fun fetchAssociatedMscaAccount(): String? {
        return withContext(ioDispatcher) {
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
        val usdcDepositedBalance = withContext(ioDispatcher) { api.getBalance(usdc) }

        showUSDCPosition(usdcDepositedBalance, usdc)

        val usdcCircleStakingDetails =
            StakingDetails(
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
        withContext(ioDispatcher) {
            stakingDetailsRepository.saveStakingDetails(vaultId, usdcCircleStakingDetails)
        }
    }

    private suspend fun showUSDCPosition(usdcDepositedBalance: BigInteger, usdc: Coin) =
        supervisorScope {
            val usdcFormattedBalance = usdcDepositedBalance.toValue(usdc.decimal)
            val currency = async(ioDispatcher) { appCurrencyRepository.currency.first() }
            val currencyFormat = async(ioDispatcher) { appCurrencyRepository.getCurrencyFormat() }

            val usdcTokenPrice =
                createFiatValue(
                    amount = usdcFormattedBalance,
                    currency = currency.await(),
                    coin = usdc,
                )

            val formattedPrice = currencyFormat.await().format(usdcTokenPrice.value)

            _state.update { currentState ->
                currentState.copy(
                    totalAmountPrice = formattedPrice,
                    isTotalAmountLoading = false,
                    supportEditChains = false,
                    circleDefi =
                        currentState.circleDefi.copy(
                            isLoading = false,
                            isAccountOpen = true,
                            totalDeposit = "$usdcFormattedBalance USDC",
                            totalDepositCurrency = formattedPrice,
                        ),
                )
            }
        }

    private suspend fun createFiatValue(
        amount: BigDecimal,
        coin: Coin,
        currency: AppCurrency,
    ): FiatValue {
        try {
            if (amount == BigDecimal.ZERO) {
                return FiatValue(BigDecimal.ZERO, currency.ticker)
            }

            val price =
                tokenPriceRepository.getCachedPrice(tokenId = coin.id, appCurrency = currency)
                    ?: tokenPriceRepository.getPriceByContactAddress(
                        coin.chain.id,
                        coin.contractAddress,
                    )

            return FiatValue(
                value = amount.multiply(price).setScale(2, RoundingMode.DOWN),
                currency = currency.ticker,
            )
        } catch (t: Throwable) {
            Timber.e(t)

            return FiatValue(value = BigDecimal.ZERO, currency = currency.ticker)
        }
    }
}
