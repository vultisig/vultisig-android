package com.vultisig.wallet.ui.models.home

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


@HiltViewModel
internal class VaultListViewModel @Inject constructor(
    vaultDB: VaultDB,
) : ViewModel() {
    val vaults: MutableLiveData<List<Vault>> = MutableLiveData(listOf())

    init {
        vaults.postValue(vaultDB.selectAll())
    }

}