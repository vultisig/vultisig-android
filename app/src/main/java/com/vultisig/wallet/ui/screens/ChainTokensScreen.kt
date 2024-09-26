package com.vultisig.wallet.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment.Companion.BottomEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.android.play.core.review.ReviewManagerFactory
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.components.BoxWithSwipeRefresh
import com.vultisig.wallet.ui.components.MiddleEllipsisText
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.ToggleVisibilityText
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiPlusButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VaultActionButton
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.models.ChainTokenUiModel
import com.vultisig.wallet.ui.models.ChainTokensUiModel
import com.vultisig.wallet.ui.models.ChainTokensViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.showReviewPopUp
import kotlinx.coroutines.launch

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

    ChainTokensScreen(
        navController = navController,
        uiModel = uiModel,
        onRefresh = viewModel::refresh,
        onSend = viewModel::send,
        onSwap = viewModel::swap,
        onDeposit = viewModel::deposit,
        onSelectTokens = viewModel::selectTokens,
        onTokenClick = viewModel::openToken,
        onBuyWeweClick = viewModel::buyWewe,
        onQrBtnClick = viewModel::navigatoToQrAddressScreen,
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
    onBuyWeweClick: () -> Unit = {},
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
        modifier = Modifier.fillMaxSize()
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
                    startIcon = R.drawable.caret_left,
                )
            },
            bottomBar = {
                if (uiModel.isBuyWeweVisible) {
                    MultiColorButton(
                        minHeight = 44.dp,
                        backgroundColor = appColor.turquoise800,
                        textColor = appColor.oxfordBlue800,
                        iconColor = appColor.turquoise800,
                        textStyle = Theme.montserrat.subtitle1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp,
                            ),
                        content = {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_wewe),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(id = R.string.chain_account_buy_wewe),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = Theme.montserrat.subtitle1
                                )
                            }
                        },
                        onClick = onBuyWeweClick
                    )
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(it)
            ) {
                Column(
                    modifier = Modifier
                        .padding(all = 16.dp)
                ) {
                    UiSpacer(size = 8.dp)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        VaultActionButton(
                            text = stringResource(R.string.chain_account_view_send),
                            color = appColor.turquoise600Main,
                            modifier = Modifier.weight(1f),
                            onClick = onSend,
                        )
                        if (uiModel.canSwap) {
                            VaultActionButton(
                                text = stringResource(R.string.chain_account_view_swap),
                                color = appColor.persianBlue200,
                                modifier = Modifier.weight(1f),
                                onClick = onSwap,
                            )
                        }
                        if (uiModel.canDeposit) {
                            VaultActionButton(
                                stringResource(R.string.chain_account_view_deposit),
                                appColor.mediumPurple,
                                modifier = Modifier.weight(1f),
                                onClick = onDeposit,
                            )
                        }
                    }

                    UiSpacer(size = 22.dp)

                    FormCard {
                        Column(
                            modifier = Modifier
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
                                    onClick = { onTokenClick(token) },
                                )
                            }
                        }
                    }

                    if (uiModel.canSelectTokens) {
                        UiPlusButton(
                            title = stringResource(R.string.choose_tokens),
                            onClick = onSelectTokens,
                            modifier = Modifier
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
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .padding(vertical = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (chainLogo != null) {
                Image(
                    painter = painterResource(id = chainLogo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                )
            }

            Text(
                text = name,
                style = Theme.montserrat.heading5,
                color = appColor.neutral0,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            UiIcon(
                drawableResId = R.drawable.copy,
                size = 20.dp,
                onClick = {
                    clipboard.setText(AnnotatedString(address))
                    onCopy(address)
                }
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
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            UiPlaceholderLoader(
                modifier = Modifier
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
) {
    val appColor = Theme.colors

    Column(
        modifier = Modifier
            .padding(
                vertical = 12.dp
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                TokenLogo(
                    logo = tokenLogo,
                    title = title,
                    modifier = Modifier
                        .size(36.dp)
                        .padding(4.dp)
                        .align(Alignment.Center),
                    errorLogoModifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Theme.colors.neutral100),
                )
                chainLogo.takeIf { it != tokenLogo }?.let {
                    Image(
                        painter = painterResource(id = it),
                        contentDescription = null,
                        modifier = Modifier
                            .size(12.dp)
                            .border(
                                width = 1.dp,
                                color = appColor.neutral0,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .align(BottomEnd)
                    )
                }
            }

            UiSpacer(size = 6.dp)

            Text(
                text = title,
                style = Theme.menlo.subtitle1,
                color = appColor.neutral0,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            UiSpacer(size = 8.dp)

            if (fiatBalance != null) {
                ToggleVisibilityText(
                    text = fiatBalance,
                    isVisible = isBalanceVisible,
                    style = Theme.menlo.subtitle1,
                    color = appColor.neutral100,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                UiPlaceholderLoader(
                    modifier = Modifier
                        .width(48.dp)
                )
            }
        }

        UiSpacer(size = 12.dp)

        if (balance != null) {
            ToggleVisibilityText(
                text = balance,
                isVisible = isBalanceVisible,
                style = Theme.menlo.subtitle1,
                color = appColor.neutral100,
            )
        } else {
            UiPlaceholderLoader(
                modifier = Modifier
                    .width(48.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "chain coin screen")
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