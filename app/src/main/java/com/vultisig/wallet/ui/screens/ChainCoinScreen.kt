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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiPlusButton
import com.vultisig.wallet.ui.models.ChainCoinViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChainCoinScreen(navController: NavHostController) {
    val viewModel = hiltViewModel<ChainCoinViewModel>()
    val uiModel by viewModel.uiState.collectAsState()
    val textColor = MaterialTheme.colorScheme.onBackground
    val appColor = Theme.colors
    val dimens = MaterialTheme.dimens
    val buyVltiButtonVisible = false

    LaunchedEffect(key1 = Unit) {
        viewModel.loadData()
    }

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
                            style = Theme.montserrat.titleLarge,
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
                        IconButton(onClick = {}) {
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
                        textStyle = Theme.montserrat.titleLarge,
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
                                    style = Theme.montserrat.titleLarge
                                )
                            }
                        }
                    ) {

                    }
                }
            }
        ) {
            Column(modifier = Modifier.padding(it)) {
                Row(
                    horizontalArrangement = Arrangement.Absolute.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.width(dimens.marginMedium))
                    VaultAction(
                        text = stringResource(R.string.chain_account_view_send),
                        color = appColor.turquoise600Main,
                        onClick = viewModel::send,
                    )
                    if (uiModel.canSwap) {
                        Spacer(modifier = Modifier.width(24.dp))
                        VaultAction(
                            text = stringResource(R.string.chain_account_view_swap),
                            color = appColor.persianBlue200,
                            onClick = viewModel::swap
                        )
                    }
                    if (uiModel.canDeposit) {
                        Spacer(modifier = Modifier.width(24.dp))
                        VaultAction(
                            stringResource(R.string.chain_account_view_deposit),
                            appColor.mediumPurple,
                            onClick = viewModel::deposit
                        )
                    }
                    Spacer(modifier = Modifier.width(dimens.marginMedium))
                }

                LazyColumn(
                    modifier = Modifier
                        .padding(
                            start = dimens.marginMedium,
                            end = dimens.marginMedium,
                            top = 24.dp
                        )
                        .background(appColor.oxfordBlue600Main)
                        .clip(MaterialTheme.shapes.large),
                ) {
                    item {
                        CoinListHeader(
                            uiModel.chainAddress,
                            uiModel.chainName,
                            uiModel.totalBalance,
                            uiModel.explorerURL,
                        )
                    }
                    items(items = uiModel.tokens) { token ->
                        CoinItem(
                            title = token.name,
                            balance = token.balance ?: "",
                            fiatBalance = token.fiatBalance ?: "",
                            tokenLogo = token.tokenLogo,
                            chainLogo = token.chainLogo,
                        )
                    }
                }

                UiPlusButton(
                    title = stringResource(R.string.choose_tokens),
                    onClick = viewModel::selectTokens,
                    modifier = Modifier
                        .padding(all = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun CoinListHeader(
    address: String,
    name: String,
    totalBalance: String,
    explorerURL: String,
) {
    val context = LocalContext.current
    val appColor = Theme.colors
    val dimens = MaterialTheme.dimens
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(key1 = Unit) {
        Timber.d(address)
    }
    Column(modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = Theme.montserrat.titleLarge,
                color = appColor.neutral0
            )
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(address))
            }) {
                Icon(
                    painter = painterResource(id = R.drawable.copy),
                    contentDescription = null
                )
            }

            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                painter = painterResource(id = R.drawable.icon_qr),
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(onClick = {
                uriHandler.openUri(explorerURL)
            }) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = null
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = totalBalance,
                style = Theme.menlo.titleMedium,
                color = appColor.neutral0
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = address,
            style = Theme.montserrat.titleSmall,
            color = appColor.turquoise800,
            modifier = Modifier.padding(top = dimens.marginSmall)
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(
            color = appColor.oxfordBlue400,
            thickness = 1.dp
        )
    }
}

@Composable
private fun CoinItem(
    title: String,
    balance: String,
    fiatBalance: String,
    @DrawableRes tokenLogo: Int,
    @DrawableRes chainLogo: Int,
) {
    val appColor = Theme.colors
    val dimens = MaterialTheme.dimens
    Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(48.dp)) {
                Image(
                    painter = painterResource(id = tokenLogo),
                    contentDescription = null,
                    Modifier.width(48.dp)
                )
                Image(
                    painter = painterResource(id = chainLogo),
                    contentDescription = null,
                    Modifier
                        .width(16.dp)
                        .offset(x = 5.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .border(
                            width = 1.dp,
                            color = appColor.neutral0,
                            shape = MaterialTheme.shapes.extraLarge
                        )
                        .align(BottomEnd)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = Theme.montserrat.titleMedium,
                color = appColor.neutral100
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = balance,
                style = Theme.menlo.titleMedium,
                color = appColor.neutral0
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = fiatBalance,
            style = Theme.menlo.titleMedium,
            color = appColor.neutral100,
            modifier = Modifier.padding(top = dimens.marginSmall)
        )
    }
}

@Preview(showBackground = true, name = "chain coin screen")
@Composable
private fun ChainCoinScreenPreview() {
    ChainCoinScreen(navController = rememberNavController())
}

@Composable
private fun RowScope.VaultAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        color = color,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .border(
                width = 1.dp, brush = Brush.horizontalGradient(
                    colors = listOf(
                        Theme.colors.turquoise600Main,
                        Theme.colors.persianBlue600Main
                    )
                ), shape = MaterialTheme.shapes.extraLarge
            )
            .padding(vertical = 8.dp)
            .weight(1f)
            .clickable(onClick = onClick),
    )
}

@Composable
@Preview
private fun VaultActionPreview() {
    Row {
        VaultAction(
            "SEND",
            Theme.colors.turquoise600Main,
            {}
        )
    }
}

