package com.vultisig.wallet.ui.models

import androidx.annotation.DrawableRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.blockchain.TierRemoteNFTService
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.calculateAccountsTotalFiatValue
import com.vultisig.wallet.data.models.calculateAddressesTotalFiatValue
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.models.isSwapSupported
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.CryptoConnectionTypeRepository
import com.vultisig.wallet.data.repositories.DefaultDeFiChainsRepository
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.TiersNFTRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.vault.VaultMetadataRepo
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.IsGlobalBackupReminderRequiredUseCase
import com.vultisig.wallet.data.usecases.NeverShowGlobalBackupReminderUseCase
import com.vultisig.wallet.ui.models.mappers.AddressToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.USDC_CIRCLE
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@Immutable
internal data class VaultAccountsUiModel(
    val vaultName: String = "",
    val isFastVault: Boolean = false,
    val showBackupWarning: Boolean = false,
    val showMonthlyBackupReminder: Boolean = false,
    val showMigration: Boolean = false,
    val isRefreshing: Boolean = false,
    val totalFiatValue: String? = null,
    val totalDeFiValue: String? = null,
    val isBalanceValueVisible: Boolean = true,
    val accounts: List<AccountUiModel> = emptyList(),
    val defiAccounts: List<AccountUiModel> = emptyList(),
    val searchTextFieldState: TextFieldState = TextFieldState(),
    val isBannerVisible: Boolean = true,
    val cryptoConnectionType: CryptoConnectionType = CryptoConnectionType.Wallet,
    val scanQrUiModel: ScanQrUiModel = ScanQrUiModel(),
) {
    val isSwapEnabled = accounts.any { it.model.chain.isSwapSupported }
    val noChainFound: Boolean
        get() = searchTextFieldState.text.isNotEmpty() && accounts.isEmpty()

    val getAccounts: List<AccountUiModel>
        get() = if (cryptoConnectionType == CryptoConnectionType.Wallet) {
            accounts
        } else {
            defiAccounts
        }
    }


@Immutable
internal data class AccountUiModel(
    val model: Address,
    val chainName: String,
    @DrawableRes val logo: Int,
    val address: String,
    val nativeTokenAmount: String?,
    val fiatAmount: String?,
    val assetsSize: Int = 0,
    val nativeTokenTicker: String = "",
    val isDeFiProvider: Boolean = false,
)

@HiltViewModel
internal class VaultAccountsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,

    private val navigator: Navigator<Destination>,
    private val requestResultRepository: RequestResultRepository,

    private val addressToUiModelMapper: AddressToUiModelMapper,
    private val fiatValueToStringMapper: FiatValueToStringMapper,

    private val vaultRepository: VaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val accountsRepository: AccountsRepository,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
    private val vaultMetadataRepo: VaultMetadataRepo,
    private val isGlobalBackupReminderRequired: IsGlobalBackupReminderRequiredUseCase,
    private val setNeverShowGlobalBackupReminder: NeverShowGlobalBackupReminderUseCase,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
    private val enableTokenUseCase: EnableTokenUseCase,
    private val cryptoConnectionTypeRepository: CryptoConnectionTypeRepository,
    private val defaultDeFiChainsRepository: DefaultDeFiChainsRepository,
    private val tiersNFTRepository: TiersNFTRepository,
    private val remoteNFTService: TierRemoteNFTService,
) : ViewModel() {

    private var requestedVaultId: String? = savedStateHandle.toRoute<Route.Home>().openVaultId
    private var vaultId: String? = null

    val uiState = MutableStateFlow(VaultAccountsUiModel())

    private var loadVaultNameJob: Job? = null
    private var loadAccountsJob: Job? = null
    private var loadDeFiBalancesJob: Job? = null

    init {
        collectCryptoConnectionType()
        collectLastOpenedVault()
    }

    private fun collectCryptoConnectionType() {
        cryptoConnectionTypeRepository
            .activeCryptoConnectionFlow
            .onEach { connectionType ->
                uiState.update { state ->
                    state.copy(
                        cryptoConnectionType = connectionType
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun updateLastOpenedVault() {
        val requestedVaultId = requestedVaultId
        if (requestedVaultId != null) {
            lastOpenedVaultRepository.setLastOpenedVaultId(requestedVaultId)
            this@VaultAccountsViewModel.requestedVaultId = null
        }
    }

    private fun collectLastOpenedVault() {
        viewModelScope.launch {
            updateLastOpenedVault()
            lastOpenedVaultRepository.lastOpenedVaultId
                .map { lastOpenedVaultId ->
                    lastOpenedVaultId?.let {
                        vaultRepository.get(it)
                    } ?: vaultRepository.getAll().firstOrNull()
                }.collect { vault ->
                    if (vault != null) {
                        loadData(vault.id)
                    }
                }
        }
    }

    private fun loadData(vaultId: VaultId) {
        val vaultChanged = this.vaultId != null && this.vaultId != vaultId

        this.vaultId = vaultId
        loadVaultNameAndShowBackup(vaultId)
        loadAccounts(vaultId)
        loadBalanceVisibility(vaultId)
        showGlobalBackupReminder()
        showVerifyFastVaultPasswordReminderIfRequired(vaultId)
        enableVultTokenIfNeeded(vaultId)
        loadDeFiBalances(vaultId, vaultChanged)
    }

    private fun enableVultTokenIfNeeded(vaultId: VaultId) {
        viewModelScope.launch {
            try {
                val vault = vaultRepository.get(vaultId) ?: return@launch
                
                // Check if vault has Ethereum chain enabled
                val hasEthereum = vault.coins.any { it.chain == Chain.Ethereum }
                if (!hasEthereum) {
                    Timber.d("Ethereum chain not enabled, skipping VULT token auto-enable")
                    return@launch
                }
                
                // Check if VULT token is already enabled
                val vultCoin = vault.coins.find { it.id == Coins.Ethereum.VULT.id }
                
                if (vultCoin == null) {
                    // Enable VULT token in background
                    Timber.d("VULT token not enabled, enabling it now for vault: $vaultId")
                    withContext(Dispatchers.IO) {
                        enableTokenUseCase(vaultId, Coins.Ethereum.VULT)
                    }
                    Timber.d("VULT token enabled successfully")
                }

                // fetch NFT
                val ethAddress = vault.coins.find { it.id == Coins.Ethereum.ETH.id }?.address
                if (ethAddress != null) {
                    withContext(Dispatchers.IO) {
                        val balance = remoteNFTService.checkNFTBalance(ethAddress)
                        tiersNFTRepository.saveTierNFT(vaultId, balance)
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to auto-enable VULT token")
            }
        }
    }


    private fun showGlobalBackupReminder() {
        viewModelScope.launch {
            val showReminder = isGlobalBackupReminderRequired()
            uiState.update {
                it.copy(showMonthlyBackupReminder = showReminder)
            }
        }
    }

    private fun showVerifyFastVaultPasswordReminderIfRequired(vaultId: VaultId) {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId) ?: return@launch
            if (vault.isFastVault() && vaultMetadataRepo.isFastVaultPasswordReminderRequired(vaultId)) {
                navigator.route(Route.FastVaultPasswordReminder(vaultId))
            }
        }
    }

    private fun loadBalanceVisibility(vaultId: String) {
        viewModelScope.launch {
            val isBalanceVisible = balanceVisibilityRepository.getVisibility(vaultId)
            uiState.update {
                it.copy(isBalanceValueVisible = isBalanceVisible)
            }
        }
    }

    fun refreshData() {
        val vaultId = vaultId ?: return
        updateRefreshing(true)
        loadAccounts(vaultId, true)
    }

    fun send() {
        val vaultId = vaultId ?: return
        viewModelScope.launch {
            navigator.route(Route.Send(vaultId = vaultId))
        }
    }

    fun swap() {
        val vaultId = vaultId ?: return
        viewModelScope.launch {
            navigator.route(Route.Swap(vaultId = vaultId))
        }
    }

    fun buy() {
        val vaultId = vaultId ?: return
        viewModelScope.launch {
            navigator.route(Route.OnRamp(
                vaultId = vaultId,
                chainId = Chain.ThorChain.raw,
            ))
        }
    }


    fun receive(){
        val vaultId = vaultId ?: return
        viewModelScope.launch {
            navigator.route(Route.Receive(vaultId = vaultId))
        }
    }

    fun openCamera() {
        viewModelScope.launch {
            navigator.route(Route.ScanQr(vaultId = vaultId))
        }
    }

    fun openAccount(account: AccountUiModel) {
        val vaultId = vaultId ?: return
        val chainId = account.model.chain.id

        viewModelScope.launch {
            when (uiState.value.cryptoConnectionType) {
                CryptoConnectionType.Wallet -> {
                    navigator.route(
                        Route.ChainDashboard(
                            route = ChainDashboardRoute.Wallet(
                                vaultId = vaultId,
                                chainId = chainId,
                            )
                        )
                    )
                }
                CryptoConnectionType.Defi -> {
                    // Exception for DeFi providers on home screen
                    if (account.chainName.equals(USDC_CIRCLE, true)) {
                        navigator.route(
                            Route.ChainDashboard(
                                route = ChainDashboardRoute.PositionCircle(
                                    vaultId = vaultId,
                                )
                            )
                        )
                    } else {
                        navigator.route(
                            Route.ChainDashboard(
                                route = ChainDashboardRoute.PositionTokens(
                                    vaultId = vaultId,
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    private fun loadVaultNameAndShowBackup(vaultId: String) {
        loadVaultNameJob?.cancel()
        loadVaultNameJob = viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)
                ?: return@launch
            uiState.update {
                it.copy(
                    vaultName = vault.name,
                    isFastVault = vault.isFastVault()
                )
            }
            val isVaultBackedUp = vaultDataStoreRepository.readBackupStatus(vaultId).first()
            uiState.update { it.copy(showBackupWarning = !isVaultBackedUp) }
            val showMigration = vault.libType == SigningLibType.GG20
            uiState.update { it.copy(showMigration = showMigration) }
        }
    }

    private fun loadAccounts(vaultId: String, isRefresh: Boolean = false) {
        loadAccountsJob?.cancel()
        loadAccountsJob = viewModelScope.launch {
            combine(
                accountsRepository
                    .loadAddresses(vaultId, isRefresh)
                    .map { it ->
                        it.sortByAccountsTotalFiatValue()
                    }
                    .catch {
                        updateRefreshing(false)
                        Timber.e(it)
                    },
                uiState.value.searchTextFieldState.textAsFlow(),
                uiState.map { it.cryptoConnectionType }.distinctUntilChanged()
            ) { accounts, searchQuery, cryptoConnectionType ->
                accounts
                    .filter {
                        when (cryptoConnectionType) {
                            CryptoConnectionType.Wallet -> true
                            CryptoConnectionType.Defi -> cryptoConnectionTypeRepository.isDefi(it.chain)
                        }
                    }
                    .updateUiStateFromList(
                        searchQuery = searchQuery.toString(),
                    )
            }
                .launchIn(this)
        }
    }

    private fun loadDeFiBalances(vaultId: String, isRefresh: Boolean = false) {
        loadDeFiBalancesJob?.cancel()
        loadDeFiBalancesJob = viewModelScope.launch {
            combine(
                accountsRepository
                    .loadDeFiAddresses(vaultId, isRefresh)
                    .map { addresses ->
                        addresses.sortByAccountsTotalFiatValue()
                    }
                    .catch { error ->
                        updateRefreshing(false)
                        Timber.e(error, "Error loading DeFi balances for vault: $vaultId")
                    },
                uiState.value.searchTextFieldState.textAsFlow(),
                defaultDeFiChainsRepository.getDefaultChains(vaultId),
            ) { accounts, searchQuery, selectedDeFiChains ->
                Timber.d("DeFi Accounts Loaded for vault $vaultId: ${accounts.size} accounts, selected chains: ${selectedDeFiChains.map { it.raw }}")
                
                val filteredAccounts = accounts.filter { address ->
                    val isSelected = selectedDeFiChains.contains(address.chain)
                    Timber.d("Chain ${address.chain.raw} is selected: $isSelected")
                    isSelected
                }
                
                Timber.d("Filtered DeFi accounts: ${filteredAccounts.size} accounts")
                
                filteredAccounts.updateUiStateFromList(
                    searchQuery = searchQuery.toString(),
                    isDefi = true,
                )
            }
                .launchIn(this)
        }
    }

    private fun List<Address>.sortByAccountsTotalFiatValue() =
        sortedWith(compareBy({
            it.accounts.calculateAccountsTotalFiatValue()?.value?.unaryMinus()
        }, {
            it.chain.raw
        }))

    private suspend fun List<Address>.updateUiStateFromList(
        searchQuery: String,
        isDefi: Boolean = false,
    ) {
        val totalFiatValue = this.calculateAddressesTotalFiatValue()
            ?.let { fiatValueToStringMapper(it) }
        val accountsUiModel = this.map {
            addressToUiModelMapper(it)
        }

        if (!isDefi) {
            uiState.update {
                it.copy(
                    totalFiatValue = totalFiatValue,
                    accounts = accountsUiModel.filteredAccounts(searchQuery),
                )
            }
        } else {
            uiState.update {
                it.copy(
                    totalDeFiValue = totalFiatValue,
                    defiAccounts = accountsUiModel.filteredAccounts(searchQuery),
                )
            }
        }
        updateRefreshing(false)

        Timber.d("Update updateUiStateFromList", "$this")
    }

    private fun List<AccountUiModel>.filteredAccounts(searchQuery: String): List<AccountUiModel> {
        if (searchQuery.isBlank()) return this
        val query = searchQuery.trim()
        return filter { account ->
            listOf(
                account.chainName,
                account.nativeTokenTicker
            ).any { field ->
                field.contains(
                    other = query,
                    ignoreCase = true
                )
            }
        }
    }


    private fun updateRefreshing(isRefreshing: Boolean) {
        Timber.d("UpdateRefresh $isRefreshing")
        uiState.update { it.copy(isRefreshing = isRefreshing) }
    }

    @Suppress("ReplaceNotNullAssertionWithElvisReturn")
    fun toggleBalanceVisibility() {
        val isBalanceValueVisible = !uiState.value.isBalanceValueVisible
        viewModelScope.launch {
            uiState.update {
                it.copy(isBalanceValueVisible = isBalanceValueVisible)
            }
            balanceVisibilityRepository.setVisibility(vaultId!!, isBalanceValueVisible)
        }
    }

    @Suppress("ReplaceNotNullAssertionWithElvisReturn")
    fun backupVault() {
        viewModelScope.launch {
            dismissBackupReminder()
            navigator.route(Route.BackupPasswordRequest(vaultId!!))
        }
    }

    fun migrate() {
        val vaultId = vaultId ?: return
        viewModelScope.launch {
            navigator.route(Route.Migration.Onboarding(vaultId))
        }
    }

    fun dismissBackupReminder() {
        uiState.update { it.copy(showMonthlyBackupReminder = false) }
    }

    fun doNotRemindBackup() = viewModelScope.launch {
        setNeverShowGlobalBackupReminder()
        dismissBackupReminder()
    }

    fun openSettings() {
        vaultId?.let { vaultId ->
            viewModelScope.launch {
                Timber.d("openSettings($vaultId)")
                navigator.route(Route.Settings(vaultId = vaultId))
            }
        }
    }

    fun openAddChainAccount() {
        vaultId?.let { vaultId ->
            viewModelScope.launch {
                if (uiState.value.cryptoConnectionType == CryptoConnectionType.Defi) {
                    navigator.route(Route.AddDeFiChainAccount(
                        vaultId = vaultId,
                    ))
                } else {
                    navigator.route(Route.AddChainAccount(
                        vaultId = vaultId,
                    ))
                }
                requestResultRepository.request<Unit>(REFRESH_CHAIN_DATA)

                loadData(vaultId)
            }
        }
    }

    fun openVaultList(){
        vaultId?.let {
            viewModelScope.launch {
                navigator.route(Route.VaultList(
                    openType = Route.VaultList.OpenType.Home(it)
                ))
            }
        }
    }

    fun tempRemoveBanner(){
        uiState.update {
            it.copy(
                isBannerVisible = false,
            )
        }
    }

    fun setCryptoConnectionType(type: CryptoConnectionType){
        cryptoConnectionTypeRepository.setActiveCryptoConnection(type)
        uiState.update {
            it.copy(
                cryptoConnectionType = type,
            )
        }

        val vaultId = vaultId ?: return

        if (type == CryptoConnectionType.Defi) {
            loadDeFiBalances(vaultId, true)
        }
    }

    fun handleScanQrError(error: String) {
        viewModelScope.launch {
            uiState.update {
                it.copy(
                    scanQrUiModel = ScanQrUiModel(error = error)
                )
            }
            delay(2.seconds)
            uiState.update {
                it.copy(
                    scanQrUiModel = ScanQrUiModel(error = null)
                )
            }
        }
    }

    companion object {
         internal const val REFRESH_CHAIN_DATA  = "refresh_chain_data"
    }
}

