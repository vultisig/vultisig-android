package com.vultisig.wallet.presenter.home

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
internal class HomeViewModel @Inject constructor(
    vaultDB: VaultDB,
    private val navigator: Navigator<Destination>
) : ViewModel() {
    val vaults: MutableLiveData<List<Vault>> = MutableLiveData(listOf())

    init {
        vaults.postValue(vaultDB.selectAll())
    }

    fun navigateToSettingsScreen(){
        viewModelScope.launch {
            navigator.navigate(Destination.Settings)
        }
    }

}