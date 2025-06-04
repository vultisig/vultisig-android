package com.vultisig.wallet.ui.screens.transaction

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Tokens
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.components.util.CutoutPosition
import com.vultisig.wallet.ui.components.util.RoundedWithCutoutShape
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.screens.swap.VerifyCardDivider
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SwapTransactionOverviewScreen(
    showToolbar: Boolean = true,
    transactionHash: String,
    approveTransactionHash: String,
    transactionLink: String,
    approveTransactionLink: String,
    onComplete: () -> Unit,
    progressLink: String?,
    onBack: () -> Unit = {},
    transactionTypeUiModel: SwapTransactionUiModel,
) {
    val uriHandler = LocalUriHandler.current
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

                Box {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SwapToken(
                            valuedToken = transactionTypeUiModel.src,
                            shape = RoundedWithCutoutShape(
                                cutoutPosition = CutoutPosition.End,
                                cutoutOffsetX = (-4).dp,
                                cutoutRadius = 18.dp,
                            ),
                            modifier = Modifier
                                .weight(1f),
                        )

                        SwapToken(
                            valuedToken = transactionTypeUiModel.dst,
                            shape = RoundedWithCutoutShape(
                                cutoutPosition = CutoutPosition.Start,
                                cutoutOffsetX = (-4).dp,
                                cutoutRadius = 18.dp,
                            ),
                            modifier = Modifier
                                .weight(1f),
                        )
                    }

                    Icon(
                        painter = painterResource(R.drawable.ic_caret_right),
                        contentDescription = null,
                        tint = Theme.colors.text.button.disabled,
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                color = Theme.colors.borders.light,
                                shape = CircleShape,
                            )
                            .padding(6.dp)
                            .align(Alignment.Center)
                    )

                }

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
                        title = "Swap Tx Hash",
                        hash = transactionHash,
                        link = transactionLink,
                    )

                    VerifyCardDivider(
                        size = 1.dp,
                    )

                    // TODO make check more sane
                    if (approveTransactionHash.isNotEmpty()) {
                        TxDetails(
                            title = "Approval Tx Hash",
                            hash = approveTransactionHash,
                            link = approveTransactionLink,
                        )

                        VerifyCardDivider(
                            size = 1.dp,
                        )
                    }

                    TextDetails(
                        title = "From",
                        subtitle = transactionTypeUiModel.src.token.address,
                    )

                    VerifyCardDivider(
                        size = 1.dp,
                    )

                    TextDetails(
                        title = "To",
                        subtitle = transactionTypeUiModel.dst.token.address,
                    )

                    VerifyCardDivider(
                        size = 1.dp,
                    )

                    TextDetails(
                        title = "Total Fees",
                        subtitle = transactionTypeUiModel.totalFee,
                    )

                    UiSpacer(12.dp)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (progressLink != null && progressLink.isNotEmpty()) {
                            VsButton(
                                label = "Track",
                                variant = VsButtonVariant.Secondary,
                                size = VsButtonSize.Small,
                                modifier = Modifier
                                    .weight(1f),
                                onClick = {
                                    uriHandler.openUri(progressLink)
                                }
                            )
                        }

                        VsButton(
                            label = "Done",
                            variant = VsButtonVariant.Primary,
                            size = VsButtonSize.Small,
                            modifier = Modifier
                                .weight(1f),
                            onClick = onComplete,
                        )
                    }
                }
            }

        }
    )
}

@Composable
private fun TxDetails(
    title: String,
    hash: String,
    link: String,
) {
    val uriHandler = LocalUriHandler.current

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

@Composable
internal fun SwapToken(
    valuedToken: ValuedToken,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val token: Coin = valuedToken.token
    val value: String = valuedToken.value

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(
                color = Theme.colors.backgrounds.disabled,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = shape,
            )
            .padding(
                horizontal = 16.dp,
                vertical = 24.dp,
            )
    ) {
        TokenLogo(
            logo = Tokens.getCoinLogo(token.logo),
            title = token.ticker,
            errorLogoModifier = Modifier
                .size(24.dp),
            modifier = Modifier
                .size(24.dp)
                .border(
                    width = 1.dp,
                    color = Theme.colors.borders.light,
                    shape = CircleShape,
                ),
        )

        UiSpacer(12.dp)

        val text = buildAnnotatedString {
            append(value)
            append(" ")
            withStyle(SpanStyle(color = Theme.colors.text.extraLight)) {
                append(token.ticker)
            }
        }

        Text(
            text = text,
            style = Theme.brockmann.body.s.medium,
            color = Theme.colors.text.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        Text(
            text = valuedToken.fiatValue,
            style = Theme.brockmann.supplementary.captionSmall,
            color = Theme.colors.text.extraLight,
        )
    }
}

@Composable
internal fun TextDetails(
    title: String,
    subtitle: String,
) {
    Details(
        title = title
    ) {
        Text(
            text = subtitle,
            style = Theme.brockmann.body.s.medium,
            color = Theme.colors.text.primary,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
    }
}

@Composable
internal fun Details(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 12.dp,
            )
    ) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.extraLight,
            modifier = Modifier
                .weight(1f)
        )

        content()
    }
}

@Preview
@Composable
private fun SwapTransactionOverviewScreenPreview() {
    SwapTransactionOverviewScreen(
        transactionHash = "abx123abx123abx123abx123abx123abx123abx123abx123abx123",
        approveTransactionHash = "321xba",
        transactionLink = "",
        approveTransactionLink = "",
        onComplete = {},
        progressLink = "",
        transactionTypeUiModel = SwapTransactionUiModel()
    )
}