package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.tokenitem.GridPlusUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.GridTokenUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionGridUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionList
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionUiModel.TokenUiSingle
import com.vultisig.wallet.ui.models.TokenSelectionUiModel
import com.vultisig.wallet.ui.models.TokenSelectionViewModel
import com.vultisig.wallet.ui.models.TokenUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TokenSelectionScreen(
    viewModel: TokenSelectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkCustomToken()
    }

    TokenSelectionScreen(
        searchTextFieldState = viewModel.searchTextFieldState,
        state = state,
        hasCustomToken = true,
        onEnableToken = viewModel::enableTokenTemp,
        onDisableToken = viewModel::disableTokenTemp,
        onDoneClick = viewModel::onCommitChanges,
        onAddCustomToken = viewModel::navigateToCustomTokenScreen,
        onCancelClick = viewModel::back
    )
}


@Composable
internal fun TokenSelectionScreen(
    searchTextFieldState: TextFieldState,
    state: TokenSelectionUiModel,
    hasCustomToken: Boolean,
    onEnableToken: (Coin) -> Unit = {},
    onDisableToken: (Coin) -> Unit = {},
    onAddCustomToken: () -> Unit = {},
    onDoneClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
) {

    TokenSelectionList(
        items = state.tokens.map {
            GridTokenUiModel.SingleToken(
                data = it
            )
        },
        titleContent = {
            Column {
                Text(
                    text = stringResource(R.string.token_selection_screen_select_tokens),
                    style = Theme.brockmann.headings.title2,
                    color = Theme.colors.neutrals.n100,
                )
                UiSpacer(16.dp)
                Text(
                    text = stringResource(R.string.token_selecton_screen_enable_at_least_one),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.extraLight,
                )

            }
        },
        mapper = {
            TokenSelectionGridUiModel(
                tokenSelectionUiModel = TokenUiSingle(
                    name = it.data.coin.ticker,
                    logo = getCoinLogo(logoName = it.data.coin.logo),
                ),
                isChecked = it.data.isEnabled
            )
        },
        searchTextFieldState = searchTextFieldState,
        onDoneClick = onDoneClick,
        onCancelClick = onCancelClick,
        onCheckChange = { isSelected, uiCoin ->
            if(isSelected)
                onEnableToken(uiCoin.coin)
            else
                onDisableToken(uiCoin.coin)
        },
        plusUiModel = GridPlusUiModel(
            title = stringResource(R.string.deposit_option_custom),
            onClick = onAddCustomToken
        ).takeIf { hasCustomToken },
        notFoundContent = {
            Column {

            }
        }
    )
}

@Preview
@Composable
fun TokenSelectionPreview() {
    TokenSelectionScreen(
        searchTextFieldState = TextFieldState(),
        state = TokenSelectionUiModel(
            tokens = listOf(
                TokenUiModel(
                    coin= Coins.Ethereum.ETH,
                    isEnabled = true
                ),
                TokenUiModel(
                    coin= Coins.Ethereum.USDC,
                    isEnabled = false
                ),
                TokenUiModel(
                    coin= Coins.Ethereum.DAI,
                    isEnabled = true
                ),
                TokenUiModel(
                    coin= Coins.Ethereum.GRT,
                    isEnabled = false
                ),
            )
        ),
        hasCustomToken = true,
        onEnableToken = {  },
        onDisableToken = {  },
        onAddCustomToken = {  },
        onDoneClick = {  },
        onCancelClick = {  },
    )
}
