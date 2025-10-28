package com.vultisig.wallet.ui.models.defi

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.ActiveBondedNode
import com.vultisig.wallet.data.usecases.ThorchainBondUseCase
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.screens.v2.defi.BONDED_TAB
import com.vultisig.wallet.ui.screens.v2.defi.BondNodeState
import com.vultisig.wallet.ui.screens.v2.defi.BondNodeState.Companion.fromApiStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    val totalAmountPrice: String = "$0.00",
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
    val status: BondNodeState,
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
    private val tokenPriceRepository: TokenPriceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
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
                val vault = vaultRepository.get(vaultId)
                val runeCoin = vault?.coins?.find { it.chain.id == Chain.ThorChain.id }
                
                if (runeCoin == null) {
                    Timber.e("Vault does not have RUNE coin")
                    state.update {
                        it.copy(
                            isLoading = false,
                        )
                    }
                    return@launch
                }
                
                val activeNodes = withContext(Dispatchers.IO) {
                    val address = runeCoin.address
                    // Refresh RUNE price to ensure we have latest
                    tokenPriceRepository.refresh(listOf(runeCoin))
                    bondUseCase.invoke("thor1pe0pspu4ep85gxr5h9l6k49g024vemtr80hg4c")
                }

                val nodeUiModels = activeNodes.map { it.toUiModel() }
                val totalBonded = calculateTotalBonded(activeNodes)
                val totalBondedRaw = calculateTotalBondedRaw(activeNodes)
                val totalValue = calculateTotalValue(totalBondedRaw)

                state.update {
                    it.copy(
                        isLoading = false,
                        totalAmountPrice = totalValue,
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
            status = node.state.fromApiStatus(),
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

    private fun calculateTotalBondedRaw(nodes: List<ActiveBondedNode>): BigDecimal {
        val total = nodes.fold(BigInteger.ZERO) { acc, node ->
            acc + node.amount
        }
        return Chain.ThorChain.coinType.toValue(total)
    }

    private suspend fun calculateTotalValue(totalRuneAmount: BigDecimal): String {
        return try {
            val currency = appCurrencyRepository.currency.first()
            // Token ID format is "${ticker}-${chain.id}" so for RUNE it's "RUNE-THORChain"
            val runePrice = tokenPriceRepository.getCachedPrice(
                tokenId = "RUNE-THORChain",
                appCurrency = currency
            ) ?: BigDecimal.ZERO
            
            Timber.d("RUNE price: $runePrice, amount: $totalRuneAmount")
            
            val totalValue = totalRuneAmount * runePrice
            val currencyFormat = appCurrencyRepository.getCurrencyFormat()

            currencyFormat.format(totalValue.toDouble())
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate total value")
            "$0.00"
        }
    }

    private fun formatRuneAmount(amount: BigInteger): String {
        val runeAmount = Chain.ThorChain.coinType.toValue(amount)
        val rounded = runeAmount.setScale(2, RoundingMode.HALF_UP)
        return "${rounded.toPlainString()} RUNE"
    }

    private fun formatRuneReward(reward: Double): String {
        val rewardBigInt = BigInteger.valueOf(reward.toLong())
        val runeAmount = Chain.ThorChain.coinType.toValue(rewardBigInt)
        return "%.2f RUNE".format(Locale.US, runeAmount.toDouble())
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