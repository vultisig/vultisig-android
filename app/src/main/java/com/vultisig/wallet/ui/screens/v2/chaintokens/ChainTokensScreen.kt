package com.vultisig.wallet.ui.screens.v2.chaintokens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil.compose.SubcomposeAsyncImage
import com.google.android.play.core.review.ReviewManagerFactory
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.components.BoxWithSwipeRefresh
import com.vultisig.wallet.ui.components.CopyIcon
import com.vultisig.wallet.ui.components.MiddleEllipsisText
import com.vultisig.wallet.ui.components.ToggleVisibilityText
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiPlusButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VaultActionButton
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.containers.ExpandedTopbarContainer
import com.vultisig.wallet.ui.components.v2.containers.TopShineContainer
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.scaffold.ScaffoldWithExpandableTopBar
import com.vultisig.wallet.ui.components.v2.snackbar.rememberVsSnackbarState
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup
import com.vultisig.wallet.ui.components.v2.texts.LoadableValue
import com.vultisig.wallet.ui.models.ChainTokenUiModel
import com.vultisig.wallet.ui.models.ChainTokensUiModel
import com.vultisig.wallet.ui.models.ChainTokensViewModel
import com.vultisig.wallet.ui.screens.v2.chaintokens.components.ChainTokensTabMenuAndSearchBar
import com.vultisig.wallet.ui.screens.v2.home.components.BalanceBanner
import com.vultisig.wallet.ui.screens.v2.home.components.CopiableAddress
import com.vultisig.wallet.ui.screens.v2.home.components.TransactionType
import com.vultisig.wallet.ui.screens.v2.home.components.TransactionTypeButton
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsUriHandler
import com.vultisig.wallet.ui.utils.showReviewPopUp
import kotlinx.coroutines.launch
import wallet.core.jni.CoinType

@Composable
internal fun ChainTokensScreen(
    navController: NavHostController,
    viewModel: ChainTokensViewModel = hiltViewModel<ChainTokensViewModel>(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val reviewManager = remember { ReviewManagerFactory.create(context) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    ChainCoinScreen(
        uiModel = uiModel,
        onRefresh = viewModel::refresh,
        onSend = viewModel::send,
        onSwap = viewModel::swap,
        onDeposit = viewModel::deposit,
        onSelectTokens = viewModel::selectTokens,
        onTokenClick = viewModel::openToken,
        onQrBtnClick = viewModel::navigateToQrAddressScreen,
        onShowReviewPopUp = {
            reviewManager.showReviewPopUp(context)
        }
    )
}

@Composable
private fun ChainTokensScreen(
    navController: NavHostController,
    uiModel: ChainTokensUiModel,
    onRefresh: () -> Unit = {},
    onSend: () -> Unit = {},
    onSwap: () -> Unit = {},
    onDeposit: () -> Unit = {},
    onSelectTokens: () -> Unit = {},
    onTokenClick: (ChainTokenUiModel) -> Unit = {},

    onQrBtnClick: () -> Unit = {},
    onShowReviewPopUp: () -> Unit = {},
) {
    val appColor = Theme.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember {
        SnackbarHostState()
    }

    BoxWithSwipeRefresh(
        onSwipe = onRefresh,
        isRefreshing = uiModel.isRefreshing,
        modifier = Modifier.Companion.fillMaxSize()
    ) {
        Scaffold(
            contentColor = Theme.colors.oxfordBlue800,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            topBar = {
                TopBar(
                    navController = navController,
                    centerText = uiModel.chainName,
                    startIcon = R.drawable.ic_caret_left,
                )
            },
            bottomBar = {}
        ) {
            Box(
                modifier = Modifier.Companion
                    .verticalScroll(rememberScrollState())
                    .padding(it)
            ) {
                Column(
                    modifier = Modifier.Companion
                        .padding(all = 16.dp)
                ) {
                    UiSpacer(size = 8.dp)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.Companion.fillMaxWidth(),
                    ) {
                        VaultActionButton(
                            text = stringResource(R.string.chain_account_view_send),
                            color = appColor.turquoise600Main,
                            modifier = Modifier.Companion.weight(1f),
                            onClick = onSend,
                        )
                        if (uiModel.canSwap) {
                            VaultActionButton(
                                text = stringResource(R.string.chain_account_view_swap),
                                color = appColor.persianBlue200,
                                modifier = Modifier.Companion.weight(1f),
                                onClick = onSwap,
                            )
                        }
                        if (uiModel.canDeposit) {
                            VaultActionButton(
                                stringResource(R.string.chain_account_view_deposit),
                                appColor.mediumPurple,
                                modifier = Modifier.Companion.weight(1f),
                                onClick = onDeposit,
                            )
                        }
                    }

                    UiSpacer(size = 22.dp)

                    FormCard {
                        Column(
                            modifier = Modifier.Companion
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 12.dp,
                                )
                        ) {
                            ChainAccountInfo(
                                address = uiModel.chainAddress,
                                name = uiModel.chainName,
                                chainLogo = uiModel.chainLogo,
                                totalBalance = uiModel.totalBalance,
                                explorerURL = uiModel.explorerURL,
                                onCopy = { message ->
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(
                                                R.string.chain_token_screen_address_copied, message
                                            )
                                        )
                                    }
                                    onShowReviewPopUp()
                                },
                                isBalanceVisible = uiModel.isBalanceVisible,
                                onQrBtnClick = onQrBtnClick,
                            )

                            uiModel.tokens.forEach { token ->
                                UiHorizontalDivider()

                                CoinItem(
                                    title = token.name,
                                    isBalanceVisible = uiModel.isBalanceVisible,
                                    balance = token.balance,
                                    fiatBalance = token.fiatBalance,
                                    tokenLogo = token.tokenLogo,
                                    chainLogo = token.chainLogo,
                                    onClick = clickOnce { onTokenClick(token) },
                                    mergedBalance = token.mergeBalance,
                                )
                            }
                        }
                    }

                    if (uiModel.canSelectTokens) {
                        UiPlusButton(
                            title = stringResource(R.string.choose_tokens),
                            onClick = onSelectTokens,
                            modifier = Modifier.Companion
                                .padding(vertical = 16.dp),
                        )
                    }

                }
            }
        }
    }
}

@Composable
private fun ChainAccountInfo(
    address: String,
    name: String,
    isBalanceVisible: Boolean,
    @DrawableRes chainLogo: Int?,
    totalBalance: String?,
    explorerURL: String,
    onQrBtnClick: () -> Unit = {},
    onCopy: (String) -> Unit,
) {
    val appColor = Theme.colors
    val uriHandler = VsUriHandler()

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.Companion
            .padding(vertical = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Companion.CenterVertically,
        ) {
            if (chainLogo != null) {
                Image(
                    painter = painterResource(id = chainLogo),
                    contentDescription = null,
                    modifier = Modifier.Companion
                        .size(32.dp)
                )
            }

            Text(
                text = name,
                style = Theme.montserrat.heading5,
                color = appColor.neutral0,
                maxLines = 1,
                overflow = TextOverflow.Companion.Ellipsis,
                modifier = Modifier.Companion.weight(1f)
            )

            CopyIcon(
                textToCopy = address,
                onCopyCompleted = onCopy
            )

            UiIcon(
                drawableResId = R.drawable.icon_qr,
                size = 20.dp,
                onClick = onQrBtnClick,
            )

            UiIcon(
                drawableResId = R.drawable.ic_link,
                size = 20.dp,
                onClick = {
                    uriHandler.openUri(explorerURL)
                },
            )
        }

        if (totalBalance != null) {
            ToggleVisibilityText(
                text = totalBalance,
                isVisible = isBalanceVisible,
                style = Theme.menlo.heading5,
                color = appColor.neutral0,
                maxLines = 1,
                overflow = TextOverflow.Companion.Ellipsis,
            )
        } else {
            UiPlaceholderLoader(
                modifier = Modifier.Companion
                    .width(48.dp)
            )
        }

        MiddleEllipsisText(
            text = address,
            style = Theme.menlo.body1,
            color = appColor.turquoise600Main,
        )
    }
}

@Composable
internal fun CoinItem(
    title: String,
    balance: String?,
    fiatBalance: String?,
    isBalanceVisible: Boolean,
    tokenLogo: ImageModel,
    @DrawableRes chainLogo: Int?,
    onClick: () -> Unit = {},
    mergedBalance: String? = null,
) {
    val appColor = Theme.colors

    Column(
        modifier = Modifier.Companion
            .padding(
                vertical = 12.dp
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.Companion.CenterVertically
        ) {
            Box {
                TokenLogo(
                    logo = tokenLogo,
                    title = title,
                    modifier = Modifier.Companion
                        .size(36.dp)
                        .padding(4.dp)
                        .align(Alignment.Companion.Center),
                    errorLogoModifier = Modifier.Companion
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Theme.colors.neutral100),
                )
                chainLogo.takeIf { it != tokenLogo }?.let {
                    Image(
                        painter = painterResource(id = it),
                        contentDescription = null,
                        modifier = Modifier.Companion
                            .size(12.dp)
                            .border(
                                width = 1.dp,
                                color = appColor.neutral0,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .align(Alignment.Companion.BottomEnd)
                    )
                }
            }

            UiSpacer(size = 6.dp)

            Text(
                text = title,
                style = Theme.menlo.subtitle1,
                color = appColor.neutral0,
                maxLines = 1,
                overflow = TextOverflow.Companion.Ellipsis,
                modifier = Modifier.Companion.weight(1f),
            )

            UiSpacer(size = 8.dp)

            if (fiatBalance != null) {
                ToggleVisibilityText(
                    text = fiatBalance,
                    isVisible = isBalanceVisible,
                    style = Theme.menlo.subtitle1,
                    color = appColor.neutral100,
                    maxLines = 1,
                    overflow = TextOverflow.Companion.Ellipsis,
                )
            } else {
                UiPlaceholderLoader(
                    modifier = Modifier.Companion
                        .width(48.dp)
                )
            }
        }

        UiSpacer(size = 12.dp)

        Row {
            if (balance != null) {
                ToggleVisibilityText(
                    text = balance,
                    isVisible = isBalanceVisible,
                    style = Theme.menlo.subtitle1,
                    color = appColor.neutral100,
                )
            } else {
                UiPlaceholderLoader(
                    modifier = Modifier.Companion
                        .width(48.dp)
                )
            }

            if (balance != null && mergedBalance != null && mergedBalance != "0") {
                UiSpacer(1f)

                ToggleVisibilityText(
                    text = "${CoinType.THORCHAIN.toValue(mergedBalance.toBigInteger())} Merged",
                    isVisible = isBalanceVisible,
                    style = Theme.menlo.subtitle1,
                    color = appColor.neutral100,
                )
            }
        }
    }
}

@Composable
private fun ChainCoinScreenPreview() {
    ChainTokensScreen(
        navController = rememberNavController(),
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
            )
        )
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChainCoinScreen(
    modifier: Modifier = Modifier,
    uiModel: ChainTokensUiModel,
    onRefresh: () -> Unit = {},
    onSend: () -> Unit = {},
    onSwap: () -> Unit = {},
    onDeposit: () -> Unit = {},
    onSelectTokens: () -> Unit = {},
    onTokenClick: (ChainTokenUiModel) -> Unit = {},

    onQrBtnClick: () -> Unit = {},
    onShowReviewPopUp: () -> Unit = {},
) {
    val snackbarState = rememberVsSnackbarState()

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
                        onClick = {},
                        size = VsCircleButtonSize.Small,
                        icon = R.drawable.ic_caret_left,
                        type = VsCircleButtonType.Secondary,
                        designType = DesignType.Shined
                    )
                    VsCircleButton(
                        onClick = {},
                        size = VsCircleButtonSize.Small,
                        icon = R.drawable.ic_caret_right,
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
                    ChainLogo(uiModel)
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
                            txType = TransactionType.DEPOSIT,
                            isSelected = false,
                            onClick = onDeposit
                        )
                    }
                }

                UiSpacer(
                    size = 16.dp
                )
            }
        },
        topBarCollapsedContent = {},
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
                    )

                    TopShineContainer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        LazyColumn {
                            items(uiModel.tokens) { token ->
                                CoinItem(
                                    title = token.name,
                                    isBalanceVisible = uiModel.isBalanceVisible,
                                    balance = token.balance,
                                    fiatBalance = token.fiatBalance,
                                    tokenLogo = token.tokenLogo,
                                    chainLogo = token.chainLogo,
                                    onClick = clickOnce { onTokenClick(token) },
                                    mergedBalance = token.mergeBalance,
                                )
                            }
                        }
                    }

                }
            }
        },
    )
}

@Composable
private fun ChainLogo(uiModel: ChainTokensUiModel) {
    SubcomposeAsyncImage(
        model = uiModel.chainLogo,
        contentDescription = null,
        modifier = Modifier
            .size(24.dp)
            .clip(
                RoundedCornerShape(
                    size = 8.dp
                )
            ),

        error = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(
                        RoundedCornerShape(
                            size = 8.dp
                        )
                    )
                    .background(
                        color = Theme.colors.backgrounds.tertiary
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiModel.chainName.firstOrNull()?.toString() ?: "",
                    color = Theme.colors.text.primary,
                    style = Theme.brockmann.supplementary.caption
                )
            }
        }
    )
}

@Preview
@Composable
private fun PreviewChainCoinScreen1() {
    OnBoardingComposeTheme {

        ChainCoinScreen(
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
                )
            )
        )
    }
}