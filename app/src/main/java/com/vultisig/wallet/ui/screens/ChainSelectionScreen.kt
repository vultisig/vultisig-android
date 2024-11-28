package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.FormSearchBar
import com.vultisig.wallet.ui.components.TokenSelectionItem
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.ChainSelectionUiModel
import com.vultisig.wallet.ui.models.ChainSelectionViewModel
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
    Column(
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .fillMaxSize(),
    ) {
        TopBar(
            centerText = stringResource(R.string.chains), startIcon = R.drawable.ic_caret_left,
            navController = navController
        )

        UiSpacer(size = 8.dp)

        FormSearchBar(
            textFieldState = searchTextFieldState,
            modifier = Modifier
                .padding(
                    horizontal = 16.dp,
                )
        )

        UiSpacer(size = 8.dp)

        LazyColumn(
            contentPadding = PaddingValues(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.chains) { chain ->
                val token = chain.coin
                TokenSelectionItem(
                    title = token.ticker,
                    subtitle = token.chain.raw,
                    logo = token.chain.logo,
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
