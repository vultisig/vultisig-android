@file:OptIn(ExperimentalFoundationApi::class)

package com.vultisig.wallet.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.BasicFormTextField
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.models.TokenSelectionUiModel
import com.vultisig.wallet.ui.models.TokenSelectionViewModel
import com.vultisig.wallet.ui.models.TokenUiModel
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TokenSelectionScreen(
    navController: NavHostController,
    viewModel: TokenSelectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    TokenSelectionScreen(
        navController = navController,
        searchTextFieldState = viewModel.searchTextFieldState,
        state = state,
        onEnableToken = viewModel::enableToken,
        onDisableToken = viewModel::disableToken
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TokenSelectionScreen(
    navController: NavHostController,
    searchTextFieldState: TextFieldState,
    state: TokenSelectionUiModel,
    onEnableToken: (Coin) -> Unit = {},
    onDisableToken: (Coin) -> Unit = {},
) {
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

        UiSpacer(size = 8.dp)

        FormCard(
            modifier = Modifier
                .padding(
                    horizontal = 16.dp,
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(
                        horizontal = 12.dp,
                    )
            ) {
                UiIcon(
                    drawableResId = R.drawable.ic_search,
                    size = 24.dp,
                    tint = Theme.colors.neutral500
                )

                BasicFormTextField(
                    textFieldState = searchTextFieldState,
                    hint = stringResource(R.string.token_selection_search_hint),
                    keyboardType = KeyboardType.Text,
                    onLostFocus = {
                        // todo no validation neeeded
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                )
            }
        }

        UiSpacer(size = 8.dp)

        LazyColumn(
            contentPadding = PaddingValues(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TokensSection(
                title = R.string.token_selection_selected_tokens_title,
                tokens = state.selectedTokens,
                onEnableToken = onEnableToken,
                onDisableToken = onDisableToken,
            )
            TokensSection(
                title = R.string.token_selection_tokens_title,
                tokens = state.otherTokens,
                onEnableToken = onEnableToken,
                onDisableToken = onDisableToken,
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
        navController = rememberNavController(),
        searchTextFieldState = TextFieldState(),
        state = TokenSelectionUiModel()
    )
}
