package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.bottomsheets.DottyBottomSheet
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.TopShineContainer
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.texts.LoadableValue
import com.vultisig.wallet.ui.models.ChainTokenUiModel
import com.vultisig.wallet.ui.models.TokenDetailUiModel
import com.vultisig.wallet.ui.models.TokenDetailViewModel
import com.vultisig.wallet.ui.screens.v2.chaintokens.components.ChainLogo
import com.vultisig.wallet.ui.screens.v2.home.components.TransactionType
import com.vultisig.wallet.ui.screens.v2.home.components.TransactionTypeButton
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TokenDetailScreen(
    viewModel: TokenDetailViewModel = hiltViewModel<TokenDetailViewModel>(),
) {
    val uiModel by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    TokenDetailScreen(
        uiModel = uiModel,
        onSend = viewModel::send,
        onSwap = viewModel::swap,
        onDeposit = viewModel::deposit,
        onDismiss = viewModel::back,
        onBuy = viewModel::buy,
    )
}

@Composable
private fun TokenDetailScreen(
    uiModel: TokenDetailUiModel,
    onSend: () -> Unit = {},
    onSwap: () -> Unit = {},
    onDeposit: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onBuy: () -> Unit = {},
) {
    DottyBottomSheet(
        onDismiss = onDismiss
    ) {
        TokenDetailsContent(
            uiModel = uiModel,
            onSend = onSend,
            onSwap = onSwap,
            onDeposit = onDeposit,
            onBuy = onBuy,
        )
    }
}

@Composable
private fun TokenDetailsContent(
    uiModel: TokenDetailUiModel,
    onSend: () -> Unit = {},
    onSwap: () -> Unit = {},
    onDeposit: () -> Unit = {},
    onBuy: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .padding(
                all = 24.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    )
    {

        UiSpacer(
            size = 24.dp
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChainLogo(
                name = uiModel.token.name,
                logo = uiModel.token.tokenLogo
            )
            UiSpacer(size = 8.dp)
            Text(
                text = uiModel.token.name,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.colors.text.primary,
            )
        }

        UiSpacer(
            size = 12.dp,
        )

        LoadableValue(
            value = uiModel.token.fiatBalance,
            isVisible = uiModel.isBalanceVisible,
            style = Theme.satoshi.price.title1,
            color = Theme.colors.text.primary,
        )

        UiSpacer(
            size = 12.dp,
        )

        LoadableValue(
            value = uiModel.token.balance,
            isVisible = uiModel.isBalanceVisible,
            style = Theme.brockmann.headings.subtitle,
            color = Theme.colors.text.extraLight,
        )

        UiSpacer(
            size = 32.dp,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(
                space = 12.dp,
                alignment = Alignment.CenterHorizontally
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiModel.canSwap) {
                TransactionTypeButton(
                    txType = TransactionType.SWAP,
                    isSelected = false,
                    onClick = onSwap
                )
            }
            
            TransactionTypeButton(
                txType = TransactionType.SEND,
                isSelected = false,
                onClick = onSend
            )

            if (uiModel.canBuy) {
                TransactionTypeButton(
                    txType = TransactionType.BUY,
                    isSelected = false,
                    onClick = onBuy
                )
            }

            if (uiModel.canDeposit) {
                TransactionTypeButton(
                    txType = TransactionType.FUNCTIONS,
                    isSelected = false,
                    onClick = onDeposit
                )
            }
        }

        UiSpacer(
            size = 40.dp,
        )

        TopShineContainer(
            backgroundColor = Theme.colors.backgrounds.primary
        ) {
            Column {
                TokenMeta(
                    key = stringResource(R.string.token_details_bottom_sheet_price),
                    value = uiModel.token.price,
                )
                UiHorizontalDivider()
                TokenMeta(
                    key = stringResource(R.string.token_details_bottom_sheet_network),
                    value = uiModel.token.network,
                )
            }
        }
    }

    UiSpacer(
        size = 12.dp,
    )
}

@Composable
private fun TokenMeta(
    key: String,
    value: String?,
    isVisible: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                all = 16.dp
            )
    ) {
        V2Container {
            Text(
                text = key,
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
                modifier = Modifier
                    .padding(all = 4.dp)
            )
        }
        UiSpacer(
            weight = 1f
        )
        V2Container(
            type = ContainerType.TERTIARY
        ) {
            LoadableValue(
                value = value,
                color = Theme.colors.text.primary,
                style = Theme.satoshi.price.bodyS,
                isVisible = isVisible,
                modifier = Modifier
                    .padding(
                        horizontal = 8.dp,
                        vertical = 4.dp
                    )
            )
        }
    }
}


@Preview
@Composable
private fun TokenDetailsScreenPreview() {
    TokenDetailsContent(
        uiModel = TokenDetailUiModel(
            token = ChainTokenUiModel(
                name = "USDT",
                balance = "0.000",
                fiatBalance = "$0.000000",
                tokenLogo = R.drawable.usdt,
                chainLogo = R.drawable.ethereum,
                price = "$1.00",
                network = "Ethereum",
            ),
            canSwap = true,
            canDeposit = true,
        )
    )
}