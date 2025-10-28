package com.vultisig.wallet.ui.models.defi

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.ActiveBondedNode
import com.vultisig.wallet.data.usecases.ThorchainBondUseCase
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.screens.v2.defi.BONDED_TAB
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

internal data class DefiPositionsUiModel(
    val totalAmountPrice: String = "$3,017.12",
    val selectedTab: String = BONDED_TAB,
    val isLoading: Boolean = false,
    val bonded: BondedTabUiModel = BondedTabUiModel(),
)

internal data class BondedTabUiModel(
    val totalBondedAmount: String = "0 RUNE",
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
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val bondUseCase: ThorchainBondUseCase,
) : ViewModel() {

    private var vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID])

    val state = MutableStateFlow(DefiPositionsUiModel())

    init {
        loadBondedNodes()
    }

    private fun loadBondedNodes() {
        viewModelScope.launch {
            state.update { it.copy(isLoading = true) }

            try {
                val activeNodes = withContext(Dispatchers.IO) {
                    val address =
                        vaultRepository.get(vaultId)
                            ?.coins
                            ?.find { it.chain.id == Chain.ThorChain.id }
                            ?.address
                            ?: error("Vault does not have address")
                    bondUseCase.invoke(address)
                }

                val nodeUiModels = activeNodes.map { it.toUiModel() }
                val totalBonded = calculateTotalBonded(activeNodes)

                state.update {
                    it.copy(
                        isLoading = false,
                        bonded = BondedTabUiModel(
                            totalBondedAmount = totalBonded,
                            nodes = nodeUiModels
                        )
                    )
                }
            } catch (t: Throwable) {
                Timber.e(t)
                state.update {
                    it.copy(
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun ActiveBondedNode.toUiModel(): BondedNodeUiModel {
        return BondedNodeUiModel(
            address = formatAddress(node.address),
            status = node.state,
            apy = formatApy(apy),
            bondedAmount = formatRuneAmount(amount),
            nextAward = formatRuneReward(nextReward),
            nextChurn = formatChurnDate(nextChurn)
        )
    }

    private fun formatAddress(address: String): String {
        return if (address.length > 13) {
            "${address.take(9)}...${address.takeLast(3)}"
        } else {
            address
        }
    }

    private fun calculateTotalBonded(nodes: List<ActiveBondedNode>): String {
        val total = nodes.fold(BigInteger.ZERO) { acc, node ->
            acc + node.amount
        }
        return formatRuneAmount(total)
    }

    private fun formatRuneAmount(amount: BigInteger): String {
        val runeDecimals = 8
        val divisor = BigDecimal.TEN.pow(runeDecimals)
        val runeAmount = amount.toBigDecimal().divide(divisor, 2, RoundingMode.HALF_UP)
        return "${runeAmount.toPlainString()} RUNE"
    }

    private fun formatRuneReward(reward: Double): String {
        return "%.2f RUNE".format(Locale.US, reward / 100_000_000)
    }

    private fun formatApy(apy: Double): String {
        return "%.2f%%".format(Locale.US, apy * 100)
    }

    private fun formatChurnDate(date: Date?): String {
        return date?.let {
            val formatter = SimpleDateFormat("MMM dd, yy", Locale.US)
            formatter.format(it)
        } ?: "N/A"
    }

    fun onClickBond(nodeAddress: String) {

    }

    fun onClickUnBond(nodeAddress: String) {

    }

    fun bondToNode() {

    }
}