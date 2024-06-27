package com.vultisig.wallet.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
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
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.ui.components.TokenSelectionItem
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.models.TokenSelectionViewModel
import com.vultisig.wallet.ui.models.TokenUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TokenSelectionScreen(
    navController: NavHostController,
    viewModel: TokenSelectionViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsState().value

    Column(
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .fillMaxSize(),
    ) {
        TopBar(
            centerText = stringResource(R.string.tokens),
            startIcon = R.drawable.caret_left,
            navController = navController
        )

        LazyColumn(
            contentPadding = PaddingValues(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TokensSection(
                title = R.string.token_selection_selected_tokens_title,
                tokens = state.selectedTokens,
                onEnableToken = viewModel::enableToken,
                onDisableToken = viewModel::disableToken
            )
            TokensSection(
                title = R.string.token_selection_tokens_title,
                tokens = state.otherTokens,
                onEnableToken = viewModel::enableToken,
                onDisableToken = viewModel::disableToken
            )
        }
    }
}

private fun LazyListScope.TokensSection(
    @StringRes title: Int,
    tokens: List<TokenUiModel>,
    onEnableToken: (Coin) -> Unit,
    onDisableToken: (Coin) -> Unit,
) {
    item {
        TokensTitle(text = stringResource(id = title))
    }
    items(tokens) { token ->
        val coin = token.coin
        TokenSelectionItem(
            title = coin.ticker,
            subtitle = coin.chain.raw,
            logo = Coins.getCoinLogo(logoName = coin.logo),
            isChecked = token.isEnabled,
            onCheckedChange = { checked ->
                if (checked) {
                    onEnableToken(coin)
                } else {
                    onDisableToken(coin)
                }
            }
        )
    }
}

@Composable
private fun TokensTitle(
    text: String,
) {
    Text(
        text = text.uppercase(),
        color = Theme.colors.neutral300,
        style = Theme.montserrat.body2,
    )
}

@Preview
@Composable
fun TokenSelectionPreview() {
    TokenSelectionScreen(
        navController = rememberNavController()
    )
}
