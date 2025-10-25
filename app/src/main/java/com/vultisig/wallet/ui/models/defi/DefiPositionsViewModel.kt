package com.vultisig.wallet.ui.models.defi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.repositories.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class DefiPositionsUiModel(
    val totalAmountPrice: String,
    val selectedTab: String,
    val bonded: BondedTabUiModel,
)

internal data class BondedTabUiModel(
    val totalBondedAmount: String,
    val nodes: BondedNodeUiModel,
)

internal data class BondedNodeUiModel(
    val address: String,
    val status: String,
    val apy: String,
    val bondedAmount: String,
    val nextAward: String,
    val nextChurn: String,
)

@HiltViewModel
class DefiPositionsViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val thorChainApi: ThorChainApi,
) : ViewModel() {

    init {
        loadBondedNodes()
    }

    fun selectTab(tab: String) {

    }

    private fun loadBondedNodes() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { // thor1pe0pspu4ep85gxr5h9l6k49g024vemtr80hg4c
                val nodesResponse = thorChainApi.getBondedNodes("thor1pe0pspu4ep85gxr5h9l6k49g024vemtr80hg4c")
                println(nodesResponse)
                val node = nodesResponse.nodes.first().address
                val details = thorChainApi.getNodeDetails(node)
                println(details)

                val churns = thorChainApi.getChurns()
                println(churns)

                val churnsInterval = thorChainApi.getChurnInterval()
                println(churnsInterval)
            }
        }
    }

    fun refreshBondedNodes() {
        loadBondedNodes()
    }
}