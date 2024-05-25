package com.vultisig.wallet.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.FormCard
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiPlusButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VaultActionButton
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.models.ChainTokenUiModel
import com.vultisig.wallet.ui.models.ChainTokensUiModel
import com.vultisig.wallet.ui.models.ChainTokensViewModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@Composable
internal fun ChainTokensScreen(
    navController: NavHostController,
    viewModel: ChainTokensViewModel = hiltViewModel<ChainTokensViewModel>(),
) {
    val uiModel by viewModel.uiState.collectAsState()
    
    ChainTokensScreen(
        navController = navController,
        uiModel = uiModel,
        onRefresh = viewModel::refresh,
        onSend = viewModel::send,
        onSwap = viewModel::swap,
        onDeposit = viewModel::deposit,
        onSelectTokens = viewModel::selectTokens,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChainTokensScreen(
    navController: NavHostController,
    uiModel: ChainTokensUiModel,
    onRefresh: () -> Unit = {},
    onSend: () -> Unit = {},
    onSwap: () -> Unit = {},
    onDeposit: () -> Unit = {},
    onSelectTokens: () -> Unit = {},
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val appColor = Theme.colors
    val dimens = MaterialTheme.dimens
    val buyVltiButtonVisible = false

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColor.oxfordBlue800)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = uiModel.chainName,
                            style = Theme.montserrat.subtitle1,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier
                                .padding(
                                    start = dimens.marginMedium,
                                    end = dimens.marginMedium,
                                )
                                .wrapContentHeight(align = Alignment.CenterVertically)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = appColor.oxfordBlue800,
                        titleContentColor = textColor
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            navController.popBackStack()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "back", tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                painter = painterResource(id = R.drawable.clockwise),
                                contentDescription = "refresh",
                                tint = Color.White
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (buyVltiButtonVisible) {
                    MultiColorButton(
                        minHeight = dimens.minHeightButton,
                        backgroundColor = appColor.turquoise800,
                        textColor = appColor.oxfordBlue800,
                        iconColor = appColor.turquoise800,
                        textStyle = Theme.montserrat.subtitle1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = dimens.marginMedium,
                                end = dimens.marginMedium,
                                bottom = dimens.marginMedium,
                            ),
                        centerContent = {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.logo_button),
                                    contentDescription = null,
                                    modifier = Modifier.width(dimens.medium1),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    text = stringResource(id = R.string.chain_account_buy_vtx),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = Theme.montserrat.subtitle1
                                )
                            }
                        },
                        onClick = {
                            // TODO what to do here?
                        }
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
                                onQrBtnClick = {
                                    navController.navigate(Destination.QrAddressScreen(uiModel.chainAddress).route)
                                }
                            )

                            uiModel.tokens.forEach { token ->
                                UiHorizontalDivider()

                                CoinItem(
                                    title = token.name,
                                    balance = token.balance,
                                    fiatBalance = token.fiatBalance,
                                    tokenLogo = token.tokenLogo,
                                    chainLogo = token.chainLogo,
                                )
                            }
                        }
                    }

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

@Composable
private fun ChainAccountInfo(
    address: String,
    name: String,
    @DrawableRes chainLogo: Int?,
    totalBalance: String?,
    explorerURL: String,
    onQrBtnClick: () -> Unit = {},
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
            Text(
                text = totalBalance,
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

        Text(
            text = address,
            style = Theme.menlo.body1,
            color = appColor.turquoise600Main,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CoinItem(
    title: String,
    balance: String?,
    fiatBalance: String?,
    @DrawableRes tokenLogo: Int,
    @DrawableRes chainLogo: Int,
) {
    val appColor = Theme.colors

    Column(
        modifier = Modifier
            .padding(
                vertical = 12.dp
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Image(
                    painter = painterResource(id = tokenLogo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .padding(4.dp)
                        .align(Alignment.Center)
                )
                Image(
                    painter = painterResource(id = chainLogo),
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

            if (balance != null) {
                Text(
                    text = balance,
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

        if (fiatBalance != null) {
            Text(
                text = fiatBalance,
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