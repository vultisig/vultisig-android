package com.vultisig.wallet.ui.models.defi

import androidx.lifecycle.ViewModel
import com.vultisig.wallet.data.repositories.BondedNodesRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DefiPositionsViewModel @Inject constructor(
    private val bondedNodesRepository: BondedNodesRepository,
    private val vaultRepository: VaultRepository,
): ViewModel() {

    init {
        loadBondedNodes()
    }

    fun selectTab(tab: String) {

    }

    private fun loadBondedNodes() {

    }

    fun refreshBondedNodes() {
        loadBondedNodes()
    }
}