package com.vultisig.wallet.ui.models.defi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.screens.v2.defi.BONDED_TAB
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class DefiPositionsUiModel(
    val totalAmountPrice: String = "0",
    val selectedTab: String = BONDED_TAB,
    val isLoading: Boolean = false,
    val bonded: BondedTabUiModel = BondedTabUiModel(),
)

internal data class BondedTabUiModel(
    val totalBondedAmount: String = "0",
    val nodes: List<BondedNodeUiModel> = emptyList(),
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
internal class DefiPositionsViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val thorChainApi: ThorChainApi,
) : ViewModel() {

    val state = MutableStateFlow(DefiPositionsUiModel())

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

    fun onClickBond(nodeAddress: String) {

    }

    fun onClickUnBond(nodeAddress: String) {

    }

    fun bondToNode() {

    }

    fun refreshBondedNodes() {
        loadBondedNodes()
    }
}