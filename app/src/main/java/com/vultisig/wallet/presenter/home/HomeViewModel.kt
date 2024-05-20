package com.vultisig.wallet.presenter.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.components.reorderable.utils.ItemPosition
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val vaultDB: VaultDB,
    private val navigator: Navigator<Destination>
) : ViewModel() {

    var vaults = MutableStateFlow<List<Vault>> (emptyList())

    init {
        vaults.update {
            vaultDB.selectAll()
        }
    }

    fun navigateToSettingsScreen(){
        viewModelScope.launch {
            navigator.navigate(Destination.Settings)
        }
    }

    fun onMove(from: ItemPosition, to: ItemPosition) {
        vaults.update {
            it.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        }
    }

}