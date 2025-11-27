package com.vultisig.wallet.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.ui.models.ChainSelectionUiModel
import com.vultisig.wallet.ui.models.ChainUiModel
import com.vultisig.wallet.ui.models.DeFiChainSelectionViewModel

@Composable
internal fun DeFiChainSelectionScreen(
    viewModel: DeFiChainSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val chainUiModels = uiState.chains.map { chain ->
        ChainUiModel(
            coin = createDummyCoinForChain(chain),
            isEnabled = chain in uiState.selectedChains
        )
    }

    ChainSelectionScreen(
        title = stringResource(R.string.chain_selection_select_defi_chains),
        state = ChainSelectionUiModel(
            chains = chainUiModels,
        ),
        searchTextFieldState = viewModel.searchTextFieldState,
        onEnableAccount = { coin ->
            viewModel.toggleChain(coin.chain)
        },
        onDisableAccount = { coin ->
            viewModel.toggleChain(coin.chain)
        },
        onCommitChanges = {
            viewModel.saveSelection()
        },
        onBackClick = {
            viewModel.navigateBack()
        },
    )
}

private fun createDummyCoinForChain(chain: Chain): Coin {
    val nativeCoin = Coins.all.firstOrNull { coin ->
        coin.chain == chain && coin.isNativeToken
    }
    
    return nativeCoin ?: Coin(
        chain = chain,
        ticker = chain.raw,
        logo = chain.raw.lowercase().replace("-", "_"),
        address = "",
        hexPublicKey = "",
        isNativeToken = true,
        priceProviderID = "",
        contractAddress = "",
        decimal = 18, // Default decimals for most chains
    )
}