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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.play.core.review.ReviewManagerFactory
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.VaultId
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
import com.vultisig.wallet.ui.models.ChainTokensViewModel
import com.vultisig.wallet.ui.screens.ResourceTwoCardsRow
import com.vultisig.wallet.ui.screens.qbtc.ClaimQbtcBottomCta
import com.vultisig.wallet.ui.screens.qbtc.ClaimQbtcPromoBanner
import com.vultisig.wallet.ui.screens.v2.chaintokens.components.ChainAccount
import com.vultisig.wallet.ui.screens.v2.chaintokens.components.ChainLogo
import com.vultisig.wallet.ui.screens.v2.chaintokens.components.ChainTokensTabMenuAndSearchBar
import com.vultisig.wallet.ui.screens.v2.home.components.AssetAction
import com.vultisig.wallet.ui.screens.v2.home.components.AssetActionButton
import com.vultisig.wallet.ui.screens.v2.home.components.CopiableAddress
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.KeyboardAware
import com.vultisig.wallet.ui.utils.VsUriHandler
import com.vultisig.wallet.ui.utils.showReviewPopUp

@Composable
internal fun ChainTokensScreen(
    vaultId: VaultId,
    chainId: ChainId,
    onBackClick: () -> Unit,
    viewModel: ChainTokensViewModel = hiltViewModel<ChainTokensViewModel>(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val reviewManager = remember { ReviewManagerFactory.create(context) }

    KeyboardAware(viewModel::handleKeyboardState)

    LaunchedEffect(Unit) { viewModel.initData(vaultId = vaultId, chainId = chainId) }

    ChainTokensScreen(
        uiModel = uiModel,
        onBackClick = onBackClick,
        onRefresh = viewModel::refresh,
        onSend = viewModel::send,
        onSwap = viewModel::swap,
        onDeposit = viewModel::deposit,
        onBuy = viewModel::buy,
        onReceive = viewModel::openAddressQr,
        onHistory = viewModel::history,
        onSelectTokens = viewModel::selectTokens,
        onTokenClick = viewModel::openToken,
        onHideSearchBar = viewModel::hideSearchBar,
        onShowSearchBar = viewModel::showSearchBar,
        onShowReviewPopUp = { reviewManager.showReviewPopUp(context) },
        onClaimQbtc = viewModel::onClaimQbtc,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChainTokensScreen(
    uiModel: ChainTokensUiModel,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onShowSearchBar: () -> Unit,
    onHideSearchBar: () -> Unit,
    onSend: () -> Unit,
    onSwap: () -> Unit,
    onBuy: () -> Unit,
    onDeposit: () -> Unit,
    onReceive: () -> Unit,
    onHistory: () -> Unit,
    onSelectTokens: () -> Unit,
    onTokenClick: (ChainTokenUiModel) -> Unit,
    onShowReviewPopUp: () -> Unit,
    onClaimQbtc: () -> Unit = {},
) {
    val snackbarState = rememberVsSnackbarState()
    val uriHandler = VsUriHandler()
    val addressCopiedMessage = stringResource(R.string.address_copied, uiModel.chainName)

    var isAddressBottomSheetVisible by remember { mutableStateOf(false) }

    ScaffoldWithExpandableTopBar(
        modifier =
            Modifier.then(if (isAddressBottomSheetVisible) Modifier.blur(10.dp) else Modifier),
        onRefresh = onRefresh,
        isRefreshing = uiModel.isRefreshing,
        snackbarState = snackbarState,
        backgroundColor = Theme.v2.colors.backgrounds.primary,
        topBarExpandedContent = {
            ExpandedTopbarContainer(
                shineSpotColor = Theme.v2.colors.primary.accent2.copy(alpha = 0.35f),
                shineSpotCenterXRatio = 0.92f,
                shineSpotCenterYRatio = -0.15f,
                shineSpotRadiusRatio = 0.45f,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VsCircleButton(
                        onClick = onBackClick,
                        size = VsCircleButtonSize.Small,
                        icon = R.drawable.ic_caret_left,
                        type = VsCircleButtonType.Secondary,
                        designType = DesignType.Shined,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VsCircleButton(
                            onClick = onHistory,
                            size = VsCircleButtonSize.Small,
                            icon = R.drawable.calendar_clock,
                            type = VsCircleButtonType.Secondary,
                            designType = DesignType.Shined,
                        )
                        if (uiModel.explorerURL.isNotEmpty()) {
                            VsCircleButton(
                                onClick = { uriHandler.openUri(uiModel.explorerURL) },
                                size = VsCircleButtonSize.Small,
                                icon = R.drawable.explor,
                                type = VsCircleButtonType.Secondary,
                                designType = DesignType.Shined,
                            )
                        }
                    }
                }

                UiSpacer(size = 10.dp)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChainLogo(name = uiModel.chainName, logo = uiModel.chainLogo)
                    UiSpacer(size = 8.dp)
                    Text(
                        text = uiModel.chainName,
                        style = Theme.brockmann.supplementary.footnote,
                        color = Theme.v2.colors.text.primary,
                    )
                }

                UiSpacer(size = 12.dp)

                LoadableValue(
                    value = uiModel.totalBalance,
                    isVisible = uiModel.isBalanceVisible,
                    style = Theme.satoshi.price.title1,
                    color = Theme.v2.colors.text.primary,
                )

                UiSpacer(size = 12.dp)

                CopiableAddress(
                    address = uiModel.chainAddress,
                    onAddressCopied = {
                        snackbarState.show(addressCopiedMessage)
                        onShowReviewPopUp()
                    },
                    modifier =
                        Modifier.clip(RoundedCornerShape(size = 8.dp))
                            .background(color = Theme.v2.colors.text.button.dim.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    tint = Theme.v2.colors.alerts.info,
                    maxLength = 108.dp,
                )

                UiSpacer(size = 32.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp, alignment = Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uiModel.canSwap) {
                        AssetActionButton(
                            action = AssetAction.SWAP,
                            isSelected = true,
                            onClick = onSwap,
                        )
                    }

                    AssetActionButton(
                        action = AssetAction.SEND,
                        isSelected = false,
                        onClick = onSend,
                    )

                    if (uiModel.canBuy) {
                        AssetActionButton(
                            action = AssetAction.BUY,
                            isSelected = false,
                            onClick = onBuy,
                        )
                    }

                    if (uiModel.canDeposit) {
                        AssetActionButton(
                            action = AssetAction.FUNCTIONS,
                            isSelected = false,
                            onClick = onDeposit,
                        )
                    }

                    AssetActionButton(
                        action = AssetAction.RECEIVE,
                        isSelected = false,
                        onClick = onReceive,
                    )
                }

                UiSpacer(size = 10.dp)
                uiModel.tronResourceStats?.let { resourceUsage ->
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                space = 6.dp,
                                alignment = Alignment.CenterHorizontally,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ResourceTwoCardsRow(
                            modifier = Modifier.weight(1f),
                            resourceUsage = resourceUsage,
                        )
                    }
                }
            }
        },
        content = { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Column(
                    modifier =
                        Modifier.background(Theme.v2.colors.backgrounds.primary).fillMaxSize()
                ) {
                    ChainTokensTabMenuAndSearchBar(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onEditClick = onSelectTokens,
                        onSearchClick = onShowSearchBar,
                        onTokensClick = {},
                        isTabMenu = uiModel.isSearchMode.not(),
                        onCancelSearchClick = onHideSearchBar,
                        searchTextFieldState = uiModel.searchTextFieldState,
                    )

                    TopShineContainer(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        LazyColumn {
                            itemsIndexed(items = uiModel.tokens, key = { _, token -> token.id }) {
                                index,
                                token ->
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
                                        modifier =
                                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    )
                                    if (index != uiModel.tokens.lastIndex) {
                                        UiHorizontalDivider()
                                    }
                                }
                            }
                        }
                    }

                    // Promo banner sits BELOW the asset list (Bitcoin token first, then the
                    // "Claim your QBTC" banner), matching Figma 75201:107954.
                    if (uiModel.showQbtcClaimBanner) {
                        ClaimQbtcPromoBanner(
                            onClaim = onClaimQbtc,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                if (uiModel.showClaimQbtcButton) {
                    ClaimQbtcBottomCta(
                        onClaim = onClaimQbtc,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                } else {
                    BottomFadeEffect(modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        },
    )
}

@Preview
@Composable
private fun PreviewChainCoinScreen1() {
    ChainTokensScreen(
        uiModel =
            ChainTokensUiModel(
                chainName = "Tron",
                chainAddress = "0x1234567890",
                totalBalance = "0.000000",
                explorerURL = "https://etherscan.io/",
                tokens =
                    listOf(
                        ChainTokenUiModel(
                            id = "usdt-1",
                            name = "USDT",
                            balance = "1,250.42",
                            fiatBalance = "$1,250.42",
                            tokenLogo = R.drawable.usdt,
                            chainLogo = R.drawable.ethereum,
                        ),
                        ChainTokenUiModel(
                            id = "eth-1",
                            name = "ETH",
                            balance = "0.875",
                            fiatBalance = "$2,840.10",
                            tokenLogo = R.drawable.ethereum,
                            chainLogo = R.drawable.ethereum,
                        ),
                        ChainTokenUiModel(
                            id = "dai-1",
                            name = "DAI",
                            balance = "320.00",
                            fiatBalance = "$320.00",
                            tokenLogo = R.drawable.dai,
                            chainLogo = R.drawable.ethereum,
                        ),
                    ),
            ),
        onBackClick = {},
        onRefresh = {},
        onShowSearchBar = {},
        onHideSearchBar = {},
        onSend = {},
        onSwap = {},
        onBuy = {},
        onDeposit = {},
        onReceive = {},
        onHistory = {},
        onSelectTokens = {},
        onTokenClick = {},
        onShowReviewPopUp = {},
    )
}
