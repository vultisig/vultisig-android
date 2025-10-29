package com.vultisig.wallet.ui.models

import androidx.annotation.DrawableRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.IsSwapSupported
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.calculateAccountsTotalFiatValue
import com.vultisig.wallet.data.models.calculateAddressesTotalFiatValue
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.CryptoConnectionTypeRepository
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.vault.VaultMetadataRepo
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.GetDirectionByQrCodeUseCase
import com.vultisig.wallet.data.usecases.IsGlobalBackupReminderRequiredUseCase
import com.vultisig.wallet.data.usecases.NeverShowGlobalBackupReminderUseCase
import com.vultisig.wallet.ui.models.mappers.AddressToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@Immutable
internal data class VaultAccountsUiModel(
    val vaultName: String = "",
    val isFastVault: Boolean = false,
    val showBackupWarning: Boolean = false,
    val showMonthlyBackupReminder: Boolean = false,
    val showMigration: Boolean = false,
    val isRefreshing: Boolean = false,
    val totalFiatValue: String? = null,
    val isBalanceValueVisible: Boolean = true,
    val showCameraBottomSheet: Boolean = false,
    val accounts: List<AccountUiModel> = emptyList(),
    val searchTextFieldState: TextFieldState = TextFieldState(),
    val isBannerVisible: Boolean = true,
    val cryptoConnectionType: CryptoConnectionType = CryptoConnectionType.Wallet,
) {
    val isSwapEnabled = accounts.any { it.model.chain.IsSwapSupported }
    val noChainFound: Boolean
        get() = searchTextFieldState.text.isNotEmpty() && accounts.isEmpty()
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
    private val getDirectionByQrCodeUseCase: GetDirectionByQrCodeUseCase,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
    private val enableTokenUseCase: EnableTokenUseCase,
    private val cryptoConnectionTypeRepository: CryptoConnectionTypeRepository
) : ViewModel() {

    private var requestedVaultId: String? = savedStateHandle[Destination.ARG_VAULT_ID]
    private var vaultId: String? = null

    val uiState = MutableStateFlow(VaultAccountsUiModel())

    private var loadVaultNameJob: Job? = null
    private var loadAccountsJob: Job? = null

    init {
        collectLastOpenedVault()
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
        this.vaultId = vaultId
        loadVaultNameAndShowBackup(vaultId)
        loadAccounts(vaultId)
        loadBalanceVisibility(vaultId)
        showGlobalBackupReminder()
        showVerifyFastVaultPasswordReminderIfRequired(vaultId)
        enableVultTokenIfNeeded(vaultId)
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

    fun openCamera() {
        uiState.update { it.copy(showCameraBottomSheet = true) }
    }

    fun openAccount(account: AccountUiModel) {
        val vaultId = vaultId ?: return
        val chainId = account.model.chain.id

        viewModelScope.launch {
            when (uiState.value.cryptoConnectionType) {
                CryptoConnectionType.Wallet -> {
                    navigator.navigate(
                        Destination.ChainTokens(
                            vaultId = vaultId,
                            chainId = chainId,
                        )
                    )
                }
                CryptoConnectionType.Defi -> {
                    navigator.navigate(
                        Destination.PositionTokens(
                            vaultId = vaultId,
                        )
                    )
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
                        // TODO handle error
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

    private fun List<Address>.sortByAccountsTotalFiatValue() =
        sortedWith(compareBy({
            it.accounts.calculateAccountsTotalFiatValue()?.value?.unaryMinus()
        }, {
            it.chain.raw
        }))

    private suspend fun List<Address>.updateUiStateFromList(
        searchQuery: String,
    ) {
        val totalFiatValue = this.calculateAddressesTotalFiatValue()
            ?.let { fiatValueToStringMapper(it) }
        val accountsUiModel = this.map {
            addressToUiModelMapper(it)
        }

        uiState.update {
            it.copy(
                totalFiatValue = totalFiatValue,
                accounts = accountsUiModel.filteredAccounts(searchQuery),
            )
        }
        updateRefreshing(false)
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

    fun dismissCameraBottomSheet() {
        uiState.update { it.copy(showCameraBottomSheet = false) }
    }

    fun onScanSuccess(qr: String) = viewModelScope.launch {
        val dst = getDirectionByQrCodeUseCase(qr, vaultId)

        navigator.route(dst)

        uiState.update { it.copy(showCameraBottomSheet = false) }
    }

    fun doNotRemindBackup() = viewModelScope.launch {
        setNeverShowGlobalBackupReminder()
        dismissBackupReminder()
    }

    fun openSettings() {
        vaultId?.let { vaultId ->
            viewModelScope.launch {
                Timber.d("openSettings($vaultId)")
                navigator.navigate(Destination.Settings(vaultId = vaultId))
            }
        }
    }

    fun openAddChainAccount() {
        vaultId?.let { vaultId ->
            viewModelScope.launch {
                navigator.route(Route.AddChainAccount(
                    vaultId = vaultId,
                ))
                requestResultRepository.request<Unit>(REFRESH_CHAIN_DATA)

                // Manually trigger loadData because dialog popBackStack in NavGraph
                // doesn't automatically re-trigger LaunchedEffect
                loadData(vaultId)
            }
        }
    }

    fun openVaultList(){
        vaultId?.let {
            viewModelScope.launch {
                navigator.route(Route.VaultList(it))
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
    }

    companion object {
         internal const val REFRESH_CHAIN_DATA  = "refresh_chain_data"
    }
}

