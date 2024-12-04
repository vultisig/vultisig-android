package com.vultisig.wallet.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.ui.components.MiddleEllipsisText
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.library.UiCirclesLoader
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.models.CustomTokenUiModel
import com.vultisig.wallet.ui.models.CustomTokenViewModel
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun CustomTokenScreen(
    navController: NavHostController,
    viewModel: CustomTokenViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiModel.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    CustomTokenScreen(
        navController = navController,
        state = uiModel,
        searchFieldState = viewModel.searchFieldState,
        onPasteClick = {
            viewModel.pasteToSearchField(clipboardManager.getText()?.text ?: "")
        },
        onSearchClick = viewModel::searchCustomToken,
        onAddTokenClick = viewModel::addCoinToTempRepo
    )
}

@Composable
private fun CustomTokenScreen(
    navController: NavHostController,
    state: CustomTokenUiModel,
    searchFieldState: TextFieldState,
    onPasteClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAddTokenClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopBar(
                navController = navController,
                startIcon = R.drawable.ic_caret_left,
                centerText = stringResource(R.string.custom_token_find_your_custom_token)
            )
        },
    ) {
        Column(
            Modifier
                .background(Theme.colors.oxfordBlue800)
                .padding(it)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SearchTokenTextField(
                searchFieldState,
                onPasteClick = onPasteClick,
                onSearchClick = onSearchClick
            )
            when {
                state.isLoading -> {
                    LoadingSearchCustomToken()
                }

                state.hasError -> {
                    TokenNotFoundError()
                }

                state.token != null -> {
                    SearchTokenResult(
                        token = state.token,
                        price = state.price,
                        chainLogo = state.chainLogo,
                        onAddTokenClick = onAddTokenClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.LoadingSearchCustomToken() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center
    ) {
        UiCirclesLoader()
    }
}

@Composable
private fun ColumnScope.TokenNotFoundError() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.danger),
            contentDescription = "token not found",
            modifier = Modifier.size(64.dp)
        )
        UiSpacer(size = 8.dp)
        Text(
            text = stringResource(R.string.custom_token_token_not_found),
            color = Theme.colors.neutral0,
            style = Theme.menlo.body1
        )
    }
}

@Composable
private fun SearchTokenResult(
    onAddTokenClick: () -> Unit,
    token: Coin,
    @DrawableRes chainLogo: Int,
    price: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Theme.colors.oxfordBlue600Main
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(all = 12.dp),
        ) {
            Box(
                Modifier
                    .padding(end = 12.dp)
            ) {
                val tokenLogoModifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                TokenLogo(
                    errorLogoModifier = tokenLogoModifier
                        .background(Theme.colors.neutral100),
                    logo = token.logo,
                    title = token.ticker,
                    modifier = tokenLogoModifier
                )
                Image(
                    painter = painterResource(id = chainLogo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(14.dp)
                        .offset(6.dp)
                        .border(
                            width = 1.dp,
                            color = Theme.colors.oxfordBlue600Main,
                            shape = CircleShape
                        )
                        .align(Alignment.BottomEnd)
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = token.ticker,
                    color = Theme.colors.neutral100,
                    style = Theme.montserrat.subtitle1,
                )
                Text(
                    text = token.chain.raw,
                    color = Theme.colors.neutral100,
                    style = Theme.montserrat.body3,
                )
                MiddleEllipsisText(
                    text = token.contractAddress,
                    color = Theme.colors.turquoise600Main,
                    style = Theme.menlo.body3,
                )
            }

            UiSpacer(size = 12.dp)
            Text(
                text = price,
                color = Theme.colors.neutral100,
                style = Theme.menlo.subtitle1,
            )
        }
    }
    UiSpacer(size = 16.dp)

    MultiColorButton(modifier = Modifier
        .fillMaxWidth()
        .imePadding(),
        onClick = onAddTokenClick,
        content = {
            Text(
                text = stringResource(
                    R.string.custom_token_add_token,
                    token.ticker
                ),
                style = Theme.montserrat.subtitle1
            )
        })

}


@Composable
private fun SearchTokenTextField(
    searchTextFieldState: TextFieldState,
    onPasteClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            FormTextFieldCard(
                hint = stringResource(R.string.custom_token_enter_contract_address),
                error = null,
                keyboardType = KeyboardType.Text,
                textFieldState = searchTextFieldState,
                content = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_paste),
                        contentDescription = null,
                        tint = Theme.colors.neutral0,
                        modifier = Modifier.clickOnce(onClick = onPasteClick)
                    )
                }
            )
        }

        UiSpacer(size = 8.dp)

        Icon(
            painter = painterResource(id = R.drawable.ic_search),
            contentDescription = null,
            tint = Theme.colors.neutral0,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Theme.colors.turquoise600Main)
                .padding(6.dp)
                .clickOnce(onClick = onSearchClick)
        )
    }

    UiSpacer(size = 16.dp)
}

@Composable
@Preview
private fun CustomTokenScreenPreview() {
    CustomTokenScreen(
        navController = rememberNavController(),
        state = CustomTokenUiModel(chainLogo = R.drawable.chainflip),
        searchFieldState = rememberTextFieldState(),
        onPasteClick = {},
        onSearchClick = {},
        onAddTokenClick = {},
    )
}