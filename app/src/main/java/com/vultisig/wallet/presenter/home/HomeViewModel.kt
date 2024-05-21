package com.vultisig.wallet.presenter.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.components.reorderable.utils.ItemPosition
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val vaultDB: VaultDB,
    private val navigator: Navigator<Destination>
) : ViewModel() {

    val vaults = MutableStateFlow<List<Vault>> (emptyList())
    private var reIndexJob: Job? = null

    init {
        vaults.value = vaultDB.selectAll()
    }

    fun navigateToSettingsScreen(){
        viewModelScope.launch {
            navigator.navigate(Destination.Settings)
        }
    }

    fun onMove(from: ItemPosition, to: ItemPosition) {
        val updatedPositionsList = vaults.value.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        vaults.update { updatedPositionsList }
        reIndexJob?.cancel()
        reIndexJob = viewModelScope.launch {
            delay(500)
            val indexedVaultList = vaultDB.updateVaultsFileIndex(updatedPositionsList)
            vaults.update { indexedVaultList }
        }
    }

}