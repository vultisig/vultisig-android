package com.vultisig.wallet.ui.screens.vault_settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.vultisig.wallet.R
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.common.encodeToHex
import com.vultisig.wallet.data.mappers.VaultAndroidToIOSMapper
import com.vultisig.wallet.data.on_board.db.OrderDB
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsUiEvent.BackupFailed
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsUiEvent.BackupFile
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsUiEvent.BackupSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
internal open class VaultSettingsViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val vaultAndroidToIOSMapper: VaultAndroidToIOSMapper,
    private val orderDB: OrderDB,
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val gson: Gson,
) : ViewModel() {

    private val vault = MutableStateFlow<Vault?>(null)

    val uiModel = MutableStateFlow(
        VaultSettingsState(
            cautionsBeforeDelete = listOf(
                R.string.vault_settings_delete_vault_caution1,
                R.string.vault_settings_delete_vault_caution2,
                R.string.vault_settings_delete_vault_caution3,
            )
        )
    )

    private val vaultId: String =
        savedStateHandle.get<String>(Destination.VaultSettings.ARG_VAULT_ID)!!

    init {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)
                ?: return@launch

            this@VaultSettingsViewModel.vault.value = vault

            uiModel.update {
                it.copy(id = vault.id)
            }
        }
    }

    private val channel = Channel<VaultSettingsUiEvent>()
    val channelFlow = channel.receiveAsFlow()


    fun dismissConfirmDeleteDialog() {
        uiModel.update {
            it.copy(showDeleteConfirmScreen = false)
        }
    }

    fun changeCheckCaution(index: Int, checked: Boolean) {
        val checkedCautionIndexes = uiModel.value.checkedCautionIndexes.toMutableList()
        if (checked) checkedCautionIndexes.add(index)
        else checkedCautionIndexes.remove(index)
        uiModel.update {
            it.copy(
                checkedCautionIndexes = checkedCautionIndexes,
                isDeleteButtonEnabled = checkedCautionIndexes.size == it.cautionsBeforeDelete.size
            )
        }
    }

    fun successBackup(fileName: String) {
        viewModelScope.launch {
            channel.send(BackupSuccess(fileName + FILE_POSTFIX))
        }
    }


    fun errorBackUp() {
        viewModelScope.launch {
            channel.send(BackupFailed)
        }
    }


    fun backupVault() {
        viewModelScope.launch {
            val vault = vault.firstOrNull() ?: return@launch
            val thresholds = Utils.getThreshold(vault.signers.count())
            val date = Date()
            val format = SimpleDateFormat("yyyy-MM")
            val formattedDate = format.format(date)
            val fileName =
                "vultisig-${vault.name}-$formattedDate-${thresholds + 1}of${vault.signers.count()}-${
                    vault.pubKeyECDSA.takeLast(4)
                }-${vault.localPartyID}.dat"

            val vaultJson = gson.toJson(vaultAndroidToIOSMapper(vault)).encodeToHex()
            channel.send(BackupFile(vaultJson, fileName))
        }
    }

    fun showConfirmDeleteDialog() {
        uiModel.update {
            it.copy(showDeleteConfirmScreen = true, checkedCautionIndexes = emptyList())
        }
    }

    fun delete() {
        viewModelScope.launch {
            val vault = vault.firstOrNull() ?: return@launch

            vaultRepository.delete(vaultId)
            orderDB.removeOrder(vault.name)

            if (vaultRepository.hasVaults()) {
                navigator.navigate(Destination.Home())
            } else {
                navigator.navigate(
                    Destination.AddVault, NavigationOptions(
                        clearBackStack = true
                    )
                )
            }
        }
    }

    companion object {
        private const val FILE_POSTFIX = "-vault.dat"
    }

}