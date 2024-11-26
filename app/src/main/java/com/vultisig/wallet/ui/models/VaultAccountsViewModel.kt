package com.vultisig.wallet.ui.models

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.IsSwapSupported
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.calculateAccountsTotalFiatValue
import com.vultisig.wallet.data.models.calculateAddressesTotalFiatValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.vault.VaultMetadataRepo
import com.vultisig.wallet.data.usecases.GetDirectionByQrCodeUseCase
import com.vultisig.wallet.data.usecases.IsGlobalBackupReminderRequiredUseCase
import com.vultisig.wallet.data.usecases.NeverShowGlobalBackupReminderUseCase
import com.vultisig.wallet.ui.models.mappers.AddressToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Immutable
internal data class VaultAccountsUiModel(
    val vaultName: String = "",
    val showBackupWarning: Boolean = false,
    val showMonthlyBackupReminder: Boolean = false,
    val isRefreshing: Boolean = false,
    val totalFiatValue: String? = null,
    val isBalanceValueVisible: Boolean = true,
    val showCameraBottomSheet: Boolean = false,
    val accounts: List<AccountUiModel> = emptyList(),
) {
    val isSwapEnabled = accounts.any { it.model.chain.IsSwapSupported }
}

internal data class AccountUiModel(
    val model: Address,
    val chainName: String,
    @DrawableRes val logo: Int,
    val address: String,
    val nativeTokenAmount: String?,
    val fiatAmount: String?,
    val assetsSize: Int = 0,
)

@HiltViewModel
internal class VaultAccountsViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,

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
) : ViewModel() {
    private var vaultId: String? = null

    val uiState = MutableStateFlow(VaultAccountsUiModel())

    private var loadVaultNameJob: Job? = null
    private var loadAccountsJob: Job? = null

    fun loadData(vaultId: VaultId) {
        this.vaultId = vaultId
        loadVaultNameAndShowBackup(vaultId)
        loadAccounts(vaultId)
        loadBalanceVisibility(vaultId)
        showGlobalBackupReminder()
        // showVerifyServerBackupIfNeeded(vaultId)
    }

    private fun showGlobalBackupReminder() {
        viewModelScope.launch {
            val showReminder = isGlobalBackupReminderRequired()
            uiState.update {
                it.copy(showMonthlyBackupReminder = showReminder)
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
            navigator.navigate(Destination.Send(vaultId = vaultId))
        }
    }

    fun swap() {
        val vaultId = vaultId ?: return
        viewModelScope.launch {
            navigator.navigate(Destination.Swap(vaultId = vaultId))
        }
    }

    fun openCamera() {
        uiState.update { it.copy(showCameraBottomSheet = true) }
    }

    fun openAccount(account: AccountUiModel) {
        val vaultId = vaultId ?: return
        val chainId = account.model.chain.id

        viewModelScope.launch {
            navigator.navigate(
                Destination.ChainTokens(
                    vaultId = vaultId,
                    chainId = chainId,
                )
            )
        }
    }

    private fun loadVaultNameAndShowBackup(vaultId: String) {
        loadVaultNameJob?.cancel()
        loadVaultNameJob = viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)
                ?: return@launch
            uiState.update { it.copy(vaultName = vault.name) }
            val isVaultBackedUp = vaultDataStoreRepository.readBackupStatus(vaultId).first()
            uiState.update { it.copy(showBackupWarning = !isVaultBackedUp) }
        }
    }

    fun closeLoadAccountJob() {
        loadAccountsJob?.cancel()
    }

    private fun loadAccounts(vaultId: String, isRefresh: Boolean = false) {
        loadAccountsJob?.cancel()
        loadAccountsJob = viewModelScope.launch {
            accountsRepository
                .loadAddresses(vaultId, isRefresh)
                .updateUiStateFromFlow()
        }
    }

    private suspend fun Flow<List<Address>>.updateUiStateFromFlow() =
        this.map { it ->
            it.sortByAccountsTotalFiatValue()
        }
            .catch {
                updateRefreshing(false)

                // TODO handle error
                Timber.e(it)
            }.collect { accounts ->
                accounts.updateUiStateFromList()
            }

    private fun List<Address>.sortByAccountsTotalFiatValue() =
        sortedWith(compareBy({
            it.accounts.calculateAccountsTotalFiatValue()?.value?.unaryMinus()
        }, {
            it.chain.raw
        }))

    private fun List<Address>.updateUiStateFromList() {
        val totalFiatValue = this.calculateAddressesTotalFiatValue()
            ?.let(fiatValueToStringMapper::map)
        val accountsUiModel = this.map(addressToUiModelMapper::map)

        uiState.update {
            it.copy(
                totalFiatValue = totalFiatValue, accounts = accountsUiModel
            )
        }
        updateRefreshing(false)
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
            navigator.navigate(Destination.BackupPassword(vaultId!!))
        }
    }

    fun dismissBackupReminder() {
        uiState.update { it.copy(showMonthlyBackupReminder = false) }
    }

    fun dismissCameraBottomSheet() {
        uiState.update { it.copy(showCameraBottomSheet = false) }
    }

    fun onScanSuccess(qr: String) = viewModelScope.launch {
        navigator.navigate(getDirectionByQrCodeUseCase(qr, vaultId))
        uiState.update { it.copy(showCameraBottomSheet = false) }
    }

    fun doNotRemindBackup() = viewModelScope.launch {
        setNeverShowGlobalBackupReminder()
        dismissBackupReminder()
    }

    /*
    moved as a mandatory part of generation flow

    private fun showVerifyServerBackupIfNeeded(vaultId: VaultId) {
        viewModelScope.launch {
            if (vaultMetadataRepo.shouldVerifyServerBackup(vaultId)) {
                navigator.navigate(
                    Destination.VerifyServerBackup(
                        vaultId = vaultId,
                        shouldSuggestBackup = false
                    )
                )
            }
        }
    }
     */

}