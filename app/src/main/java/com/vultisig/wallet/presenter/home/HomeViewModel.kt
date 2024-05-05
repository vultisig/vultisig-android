package com.vultisig.wallet.presenter.home

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Vault


class HomeViewModel : ViewModel() {
    val vaults: MutableLiveData<List<Vault>> = MutableLiveData(listOf())
    private var _vaultDB: VaultDB? = null
    suspend fun setData(context: Context) {
        _vaultDB = VaultDB(context)
        vaults.postValue(_vaultDB?.selectAll())
    }
}