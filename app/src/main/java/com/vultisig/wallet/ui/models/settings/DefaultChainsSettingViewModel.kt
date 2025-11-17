package com.vultisig.wallet.ui.models.settings

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.DefaultChainsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


internal data class DefaultChainsUiModel(
    val chains: List<DefaultChain> = emptyList(),
    val selectedDefaultChains: List<DefaultChain> = emptyList(),
)

internal data class DefaultChain(
    val title: String,
    val subtitle: String,
    @DrawableRes val logo: Int,
)

@HiltViewModel
internal class DefaultChainsSettingViewModel @Inject constructor(
    private val defaultChainsRepository: DefaultChainsRepository,
) : ViewModel() {

    val state = MutableStateFlow(
        DefaultChainsUiModel(
            chains = Chain.entries.toUiModel()
                .sortedBy { it.title }
        )
    )

    fun initialize() {
        viewModelScope.launch {
            defaultChainsRepository.selectedDefaultChains.collect { chains ->
                state.update {
                    it.copy(selectedDefaultChains = chains.toUiModel())
                }
            }
        }
    }


    fun changeChaneState(chain: DefaultChain, isChecked: Boolean) {
        viewModelScope.launch {
            val selectedDefaultChains = state.value.selectedDefaultChains
            val newSelection =
                if (isChecked) selectedDefaultChains + chain else selectedDefaultChains - chain
            defaultChainsRepository.setSelectedDefaultChains(newSelection.map { it.toDataModel() })
        }
    }


    private fun DefaultChain.toDataModel() = Chain.entries.first { it.raw == title }

    private fun Chain.toUiModel() = DefaultChain(title = raw, subtitle = ticker, logo = logo)

    private fun List<Chain>.toUiModel() = map { it.toUiModel() }

    internal val Chain.ticker: String
        get() = when (this) {
            Chain.ThorChain -> "RUNE"
            Chain.Solana -> "SOL"
            Chain.Ethereum -> "ETH"
            Chain.Avalanche -> "AVAX"
            Chain.Base -> "BASE"
            Chain.Blast -> "BLAST"
            Chain.Arbitrum -> "ARB"
            Chain.Polygon -> "POL"
            Chain.Optimism -> "OP"
            Chain.BscChain -> "BNB"
            Chain.Bitcoin -> "BTC"
            Chain.BitcoinCash -> "BCH"
            Chain.Litecoin -> "LTC"
            Chain.Dogecoin -> "DOGE"
            Chain.Dash -> "DASH"
            Chain.GaiaChain -> "UATOM"
            Chain.Kujira -> "KUJI"
            Chain.MayaChain -> "CACAO"
            Chain.CronosChain -> "CRO"
            Chain.Polkadot -> "DOT"
            Chain.Dydx -> "DYDX"
            Chain.ZkSync -> "ZK"
            Chain.Sui -> "SUI"
            Chain.Ton -> "TON"
            Chain.Osmosis -> "OSMO"
            Chain.Terra -> "LUNA"
            Chain.TerraClassic -> "LUNC"
            Chain.Noble -> "USDC"
            Chain.Ripple -> "XRP"
            Chain.Akash -> "AKT"
            Chain.Tron -> "TRX"
            Chain.Zcash -> "ZEC"
            Chain.Cardano -> "ADA"
            Chain.Mantle -> "MNT"
            Chain.Sei -> "SEI"
        }
}