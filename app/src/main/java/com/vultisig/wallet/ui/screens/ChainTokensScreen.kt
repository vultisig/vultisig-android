package com.vultisig.wallet.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.google.android.play.core.review.ReviewManagerFactory
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.components.ToggleVisibilityText
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.models.ChainTokensViewModel
import com.vultisig.wallet.ui.screens.v2.chaintokens.ChainTokensScreen
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.showReviewPopUp
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

    ChainTokensScreen(
        uiModel = uiModel,
        onRefresh = viewModel::refresh,
        onSend = viewModel::send,
        onSwap = viewModel::swap,
        onDeposit = viewModel::deposit,
        onBuy = viewModel::buy,
        onReceive = viewModel::openAddressQr,
        onSelectTokens = viewModel::selectTokens,
        onTokenClick = viewModel::openToken,
        onBackClick = navController::popBackStack,
        onShowReviewPopUp = {
            reviewManager.showReviewPopUp(context)
        }
    )
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
                    modifier = Modifier
                        .width(48.dp)
                )
            }

            if (!balance.isNullOrBlank() && !mergedBalance.isNullOrBlank() && mergedBalance != "0") {
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
