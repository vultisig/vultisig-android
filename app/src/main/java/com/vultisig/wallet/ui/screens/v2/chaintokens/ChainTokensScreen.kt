package com.vultisig.wallet.ui.screens.v2.chaintokens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.CopyIcon
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.containers.ExpandedTopbarContainer
import com.vultisig.wallet.ui.components.v2.containers.TopShineContainer
import com.vultisig.wallet.ui.components.v2.scaffold.ScaffoldWithExpandableTopBar
import com.vultisig.wallet.ui.components.v2.snackbar.rememberVsSnackbarState
import com.vultisig.wallet.ui.components.v2.texts.LoadableValue
import com.vultisig.wallet.ui.components.v2.visuals.BottomFadeEffect
import com.vultisig.wallet.ui.models.ChainTokenUiModel
import com.vultisig.wallet.ui.models.ChainTokensUiModel
import com.vultisig.wallet.ui.screens.v2.chaintokens.bottomsheets.TokenAddressQrBottomSheet
import com.vultisig.wallet.ui.screens.v2.chaintokens.components.ChainLogo
import com.vultisig.wallet.ui.screens.v2.chaintokens.components.ChainTokensTabMenuAndSearchBar
import com.vultisig.wallet.ui.screens.v2.chaintokens.components.ChainAccount
import com.vultisig.wallet.ui.screens.v2.home.components.CopiableAddress
import com.vultisig.wallet.ui.screens.v2.home.components.TransactionType
import com.vultisig.wallet.ui.screens.v2.home.components.TransactionTypeButton
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsClipboardService
import com.vultisig.wallet.ui.utils.VsUriHandler


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChainTokensScreen(
    uiModel: ChainTokensUiModel,
    onRefresh: () -> Unit = {},
    onSend: () -> Unit = {},
    onSwap: () -> Unit = {},
    onDeposit: () -> Unit = {},
    onSelectTokens: () -> Unit = {},
    onTokenClick: (ChainTokenUiModel) -> Unit = {},
    onBackClick: () -> Unit = {},
    onShowReviewPopUp: () -> Unit = {},
    onShareQrClick: () -> Unit = {},
) {
    val snackbarState = rememberVsSnackbarState()
    val uriHandler = VsUriHandler()
    val context = LocalContext.current
    var isTabMenu by remember {
        mutableStateOf(true)
    }
    var isAddressBottomSheetVisible by remember {
        mutableStateOf(false)
    }

    ScaffoldWithExpandableTopBar(
        onRefresh = onRefresh,
        isRefreshing = uiModel.isRefreshing,
        snackbarState = snackbarState,
        backgroundColor = Theme.colors.backgrounds.primary,
        topBarExpandedContent = {
            ExpandedTopbarContainer {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VsCircleButton(
                        onClick = onBackClick,
                        size = VsCircleButtonSize.Small,
                        icon = R.drawable.ic_caret_left,
                        type = VsCircleButtonType.Secondary,
                        designType = DesignType.Shined
                    )
                    VsCircleButton(
                        onClick = {
                            uriHandler.openUri(uiModel.explorerURL)
                        },
                        size = VsCircleButtonSize.Small,
                        icon = com.vultisig.wallet.R.drawable.explor,
                        type = VsCircleButtonType.Secondary,
                        designType = DesignType.Shined
                    )
                }

                UiSpacer(
                    size = 10.dp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChainLogo(
                        name = uiModel.chainName,
                        logo = uiModel.chainLogo
                    )
                    UiSpacer(size = 8.dp)
                    Text(
                        text = uiModel.chainName,
                        style = Theme.brockmann.supplementary.footnote,
                        color = Theme.colors.text.primary,
                    )
                }

                UiSpacer(
                    size = 12.dp,
                )

                LoadableValue(
                    value = uiModel.totalBalance,
                    isVisible = uiModel.isBalanceVisible,
                    style = Theme.satoshi.price.title1,
                    color = Theme.colors.text.primary,
                )

                UiSpacer(
                    size = 12.dp,
                )

                CopiableAddress(
                    address = uiModel.chainAddress,
                    onAddressCopied = {
                        snackbarState.show(
                            context.getString(
                                R.string.address_copied,
                                uiModel.chainName
                            ))
                        onShowReviewPopUp()
                    },
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                size = 8.dp
                            )
                        )
                        .background(
                            color = Theme.v2.colors.text.button.dim.copy(alpha = 0.12f)
                        )
                        .padding(
                            horizontal = 6.dp,
                            vertical = 4.dp,
                        ),
                    tint = Theme.colors.alerts.info,
                    maxLength = 108.dp
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
                    TransactionTypeButton(
                        txType = TransactionType.SEND,
                        isSelected = false,
                        onClick = onSend
                    )

                    if (uiModel.canSwap) {
                        TransactionTypeButton(
                            txType = TransactionType.SWAP,
                            isSelected = false,
                            onClick = onSwap
                        )
                    }
                    if (uiModel.canDeposit) {
                        TransactionTypeButton(
                            txType = TransactionType.FUNCTIONS,
                            isSelected = false,
                            onClick = onDeposit
                        )
                    }

                    TransactionTypeButton(
                        txType = TransactionType.RECEIVE,
                        isSelected = false,
                        onClick = {
                            isAddressBottomSheetVisible = true
                        }
                    )
                }

                UiSpacer(
                    size = 16.dp
                )
            }
        },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .background(Theme.colors.backgrounds.primary)
                        .fillMaxSize()
                ) {
                    ChainTokensTabMenuAndSearchBar(
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                        ),
                        onEditClick = onSelectTokens,
                        onSearchClick = {
                            isTabMenu = false
                        },
                        onTokensClick = {},
                        isTabMenu = isTabMenu,
                        onCancelSearchClick = {
                            isTabMenu = true
                        },
                        searchTextFieldState = uiModel.searchTextFieldState
                    )

                    TopShineContainer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        LazyColumn {
                            itemsIndexed(items = uiModel.tokens) { index, token ->
                                Column {
                                    ChainAccount(
                                        title = token.name,
                                        isBalanceVisible = uiModel.isBalanceVisible,
                                        balance = token.balance,
                                        fiatBalance = token.fiatBalance,
                                        tokenLogo = token.tokenLogo,
                                        chainLogo = token.chainLogo,
                                        monoToneChainLogo = token.monotoneChainLogo,
                                        price = token.price,
                                        onClick = clickOnce { onTokenClick(token) },
                                        mergedBalance = token.mergeBalance,
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 12.dp,
                                        )
                                    )
                                    if (index != uiModel.tokens.lastIndex) {
                                        UiHorizontalDivider()
                                    }
                                }
                            }
                        }
                    }

                }

                BottomFadeEffect(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                )

                if (isAddressBottomSheetVisible)
                    TokenAddressQrBottomSheet(
                        chainName = uiModel.chainName,
                        chainAddress = uiModel.chainAddress,
                        qrBitmapPainter = uiModel.qrCode,
                        onShareQrClick = {
                            isAddressBottomSheetVisible = false
                            onShareQrClick()
                        },
                        onDismiss = {
                            isAddressBottomSheetVisible = false
                        },
                        onCopyAddressClick = {
                            isAddressBottomSheetVisible = false
                            VsClipboardService.copy(
                                context,
                                uiModel.chainAddress
                            )
                            snackbarState.show(
                                context.getString(
                                    R.string.chain_token_screen_address_copied,
                                    uiModel.chainName
                                )
                            )
                        }
                    )
            }
        },
    )
}

@Preview
@Composable
private fun PreviewChainCoinScreen1() {
    ChainTokensScreen(
        uiModel = ChainTokensUiModel(
            chainName = "Ethereum",
            chainAddress = "0x1234567890",
            totalBalance = "0.000000",
            explorerURL = "https://etherscan.io/",
            tokens = listOf(
                ChainTokenUiModel(
                    name = "USDT",
                    balance = "0.000",
                    fiatBalance = "$0.000000",
                    tokenLogo = R.drawable.usdt,
                    chainLogo = R.drawable.ethereum
                ),
                ChainTokenUiModel(
                    name = "USDT",
                    balance = "0.000",
                    fiatBalance = "$0.000000",
                    tokenLogo = R.drawable.usdt,
                    chainLogo = R.drawable.ethereum
                ),
            )
        )
    )
}

