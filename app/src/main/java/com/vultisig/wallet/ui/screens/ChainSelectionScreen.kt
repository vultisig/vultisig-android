package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.containers.TopShineContainer
import com.vultisig.wallet.ui.models.ChainSelectionUiModel
import com.vultisig.wallet.ui.models.ChainSelectionViewModel
import com.vultisig.wallet.ui.screens.v2.home.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.screens.v2.home.bottomsheets.chainselectionbottomsheet.components.ChainSelectionItem
import com.vultisig.wallet.ui.screens.v2.home.components.SearchBar
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ChainSelectionScreen(
    navController: NavHostController,
    viewModel: ChainSelectionViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsState().value

    ChainSelectionScreen(
        state = state,
        navController = navController,
        searchTextFieldState = viewModel.searchTextFieldState,
        onEnableAccount = viewModel::enableAccount,
        onDisableAccount = viewModel::disableAccount,
    )
}

@Composable
private fun ChainSelectionScreen(
    state: ChainSelectionUiModel,
    navController: NavHostController,
    searchTextFieldState: TextFieldState,
    onEnableAccount: (Coin) -> Unit,
    onDisableAccount: (Coin) -> Unit,
) {

    V2BottomSheet(
        onDismissRequest = navController::popBackStack,
        leftAction = {
            VsCircleButton(
                icon = R.drawable.x,
                onClick = navController::popBackStack,
                type = VsCircleButtonType.Tertiary,
                size = VsCircleButtonSize.Small,
            )
        },
        rightAction = {
            VsCircleButton(
                icon = R.drawable.ic_check,
                onClick = navController::popBackStack,
                size = VsCircleButtonSize.Small,
            )

        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            UiSpacer(
                size = 24.dp
            )

            Text(
                text = stringResource(R.string.chain_selectionـselect_chains),
                style = Theme.brockmann.headings.title2,
                color = Theme.colors.neutrals.n100,
            )

            UiSpacer(
                size = 16.dp
            )

            SearchBar(
                state = searchTextFieldState,
                onCancelClick = {},
                isInitiallyFocused = false
            )
            UiSpacer(
                size = 24.dp
            )

            if (state.chains.isEmpty()) {
                NoChainFound()
            } else
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 74.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.chains) { chain ->
                        val token = chain.coin
                        ChainSelectionItem(
                            chain = token.chain,
                            isChecked = chain.isEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    onEnableAccount(token)
                                } else {
                                    onDisableAccount(token)
                                }
                            }
                        )
                    }
                }

        }
    }

}

@Preview
@Composable
private fun NoChainFound() {
    TopShineContainer {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        ) {
            Image(
                painter = painterResource(com.vultisig.wallet.R.drawable.iconcrypto),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp),
            )
            UiSpacer(14.dp)
            Text(
                text = stringResource(R.string.chain_selection_no_chains_found),
                style = Theme.brockmann.headings.subtitle,
                color = Theme.colors.text.primary,
            )
        }
    }
}

@Preview
@Composable
fun ChainSelectionViewPreview() {
    ChainSelectionScreen(
        state = ChainSelectionUiModel(),
        navController = rememberNavController(),
        searchTextFieldState = rememberTextFieldState(),
        onEnableAccount = {},
        onDisableAccount = {},
    )
}
