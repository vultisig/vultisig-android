package com.vultisig.wallet.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.isLayer2
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.FormSearchBar
import com.vultisig.wallet.ui.components.TokenSelectionItem
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiPlusButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.TokenSelectionUiModel
import com.vultisig.wallet.ui.models.TokenSelectionViewModel
import com.vultisig.wallet.ui.models.TokenUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TokenSelectionScreen(
    navController: NavHostController,
    viewModel: TokenSelectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkCustomToken()
    }

    TokenSelectionScreen(
        navController = navController,
        searchTextFieldState = viewModel.searchTextFieldState,
        state = state,
        hasTokenSwitch = true,
        hasCustomToken = true,
        onEnableToken = viewModel::enableToken,
        onDisableToken = viewModel::disableToken,
        onAddCustomToken = viewModel::navigateToCustomTokenScreen,
    )
}


@Composable
internal fun TokenSelectionScreen(
    navController: NavHostController,
    searchTextFieldState: TextFieldState,
    state: TokenSelectionUiModel,
    hasTokenSwitch: Boolean,
    hasCustomToken: Boolean,
    onEnableToken: (Coin) -> Unit = {},
    onDisableToken: (Coin) -> Unit = {},
    onAddCustomToken: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .fillMaxSize(),
    ) {
        TopBar(
            centerText = stringResource(R.string.tokens),
            startIcon = R.drawable.ic_caret_left,
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

        if (hasCustomToken) {
            UiPlusButton(
                modifier = Modifier.padding(16.dp),
                title = stringResource(id = R.string.token_selection_screen_custom_token),
                onClick = onAddCustomToken
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            tokensSection(
                title = R.string.token_selection_selected_tokens_title,
                tokens = state.selectedTokens,
                hasTokenSwitch = hasTokenSwitch,
                onEnableToken = onEnableToken,
                onDisableToken = onDisableToken,
            )
            tokensSection(
                title = R.string.token_selection_tokens_title,
                tokens = state.otherTokens,
                hasTokenSwitch = hasTokenSwitch,
                onEnableToken = onEnableToken,
                onDisableToken = onDisableToken,
            )
        }
    }
}

private fun LazyListScope.tokensSection(
    @StringRes title: Int,
    tokens: List<TokenUiModel>,
    hasTokenSwitch: Boolean,
    onEnableToken: (Coin) -> Unit,
    onDisableToken: (Coin) -> Unit,
) {
    if (tokens.isNotEmpty()) {
        item {
            TokensTitle(text = stringResource(id = title))
        }
    }
    items(tokens) { token ->
        val coin = token.coin
        TokenSelectionItem(
            title = coin.ticker,
            subtitle = coin.chain.raw,
            logo = getCoinLogo(logoName = coin.logo),
            chainLogo = token.coin.chain.logo.takeIf { token.coin.chain.isLayer2 || !token.coin.isNativeToken },
            hasTokenSwitch = hasTokenSwitch,
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
        navController = rememberNavController(),
        searchTextFieldState = TextFieldState(),
        state = TokenSelectionUiModel(),
        hasTokenSwitch = true,
        hasCustomToken = true,
    )
}
