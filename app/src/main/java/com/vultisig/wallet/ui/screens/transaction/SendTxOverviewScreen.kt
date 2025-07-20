package com.vultisig.wallet.ui.screens.transaction

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.SendTxUiModel
import com.vultisig.wallet.ui.screens.send.EstimatedNetworkFee
import com.vultisig.wallet.ui.screens.swap.VerifyCardDivider
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.LocalVsUriHandler

@Composable
internal fun SendTxOverviewScreen(
    showToolbar: Boolean = true,
    transactionHash: String,
    transactionLink: String,
    onComplete: () -> Unit,
    onBack: () -> Unit = {},
    tx: SendTxUiModel,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            if (showToolbar) {
                VsTopAppBar(
                    title = "Overview",
                    onBackClick = onBack,
                )
            }
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(
                        all = 16.dp,
                    ),
            ) {
                Box {
                    Image(
                        painter = painterResource(R.drawable.img_tx_overview_bg),
                        contentDescription = null,
                        alignment = Alignment.Center,
                        modifier = Modifier
                            .padding(horizontal = 48.dp)
                            .fillMaxWidth(),
                    )

                    Text(
                        text = "Transaction successful",
                        textAlign = TextAlign.Center,
                        style = Theme.brockmann.body.l.medium
                            .copy(
                                brush = Theme.colors.gradients.primary,
                            ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom = 48.dp,
                            ),
                    )
                }

                SwapToken(
                    valuedToken = tx.token,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth(),
                )

                UiSpacer(8.dp)

                Column(
                    modifier = Modifier
                        .background(
                            color = Theme.colors.backgrounds.disabled,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Theme.colors.borders.light,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(all = 24.dp),
                ) {
                    TxDetails(
                        title = "Transaction Hash",
                        hash = transactionHash,
                        link = transactionLink,
                    )

                    VerifyCardDivider(
                        size = 1.dp,
                    )

                    TextDetails(
                        title = "From",
                        subtitle = tx.srcAddress,
                    )

                    VerifyCardDivider(
                        size = 1.dp,
                    )

                    TextDetails(
                        title = "To",
                        subtitle = tx.dstAddress,
                    )

                    VerifyCardDivider(
                        size = 1.dp,
                    )

                    Details(
                        title = "Network"
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val chain = tx.token.token.chain

                            Image(
                                painter = painterResource(chain.logo),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp),
                            )

                            Text(
                                text = chain.raw,
                                style = Theme.brockmann.body.s.medium,
                                color = Theme.colors.text.primary,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                        }
                    }

                    VerifyCardDivider(
                        size = 1.dp,
                    )

                    UiSpacer(12.dp)

                    EstimatedNetworkFee(
                        tokenGas = tx.networkFeeTokenValue,
                        fiatGas = tx.networkFeeFiatValue,
                    )
                }
            }

        },
        bottomBar = {
            VsButton(
                label = "Done",
                variant = VsButtonVariant.Primary,
                size = VsButtonSize.Small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 24.dp,
                        vertical = 12.dp,
                    ),
                onClick = onComplete,
            )
        }
    )
}

@Composable
private fun TxDetails(
    title: String,
    hash: String,
    link: String,
) {
    val uriHandler = LocalVsUriHandler.current

    Details(
        title = title
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f),
        ) {
            Text(
                text = hash,
                style = Theme.brockmann.body.s.medium,
                color = Theme.colors.text.primary,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f),
            )

            UiIcon(
                drawableResId = R.drawable.ic_square_arrow_top_right,
                size = 16.dp,
                tint = Theme.colors.text.primary,
                onClick = {
                    uriHandler.openUri(link)
                }
            )
        }
    }
}

@Preview
@Composable
private fun PreviewSendTxOverviewScreen() {
    SendTxOverviewScreen(
        transactionHash = "abx123abx123abx123abx123abx123abx123abx123abx123abx123",
        transactionLink = "",
        onComplete = {},
        tx = SendTxUiModel()
    )
}