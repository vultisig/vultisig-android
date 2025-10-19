package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.VsOverviewToken
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.util.CutoutPosition
import com.vultisig.wallet.ui.components.util.RoundedWithCutoutShape
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.screens.swap.VerifyCardDivider
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsUriHandler
import kotlinx.coroutines.launch

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

    val snackbarHostState = remember { SnackbarHostState() }

    TxDoneScaffold(
        snackbarHostState = snackbarHostState,
        transactionHash = transactionHash,
        showToolbar = showToolbar,
        transactionLink = transactionLink,
        onBack = onBack,
        tokenContent = {
            Box {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VsOverviewToken(
                        header = stringResource(R.string.swap_form_from_title),
                        valuedToken = transactionTypeUiModel.src,
                        shape = RoundedWithCutoutShape(
                            cutoutPosition = CutoutPosition.End,
                            cutoutOffsetX = (-4).dp,
                            cutoutRadius = 18.dp,
                        ),
                        modifier = Modifier
                            .weight(1f),
                    )

                    VsOverviewToken(
                        header = stringResource(R.string.swap_form_dst_token_title),
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
        },
        detailContent = {
            Column {
                val coroutineScope = rememberCoroutineScope()
                val context = LocalContext.current
                // TODO make check more sane
                if (approveTransactionHash.isNotEmpty()) {
                    TxDetails(
                        title = stringResource(R.string.swap_transaction_overview_approval_tx_hash),
                        hash = approveTransactionHash,
                        link = approveTransactionLink,
                        modifier = Modifier.padding(
                            vertical = 12.dp,
                        ),
                        onTxHashCopied = { tx ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(
                                        R.string.tx_done_address_copied,
                                        tx
                                    )
                                )
                            }
                        }
                    )

                    VerifyCardDivider(
                        size = 1.dp,
                    )
                }

                TextDetails(
                    title = stringResource(R.string.swap_form_from_title),
                    subtitle = transactionTypeUiModel.src.token.address,
                )

                VerifyCardDivider(
                    size = 1.dp,
                )

                TextDetails(
                    title = stringResource(R.string.swap_form_dst_token_title),
                    subtitle = transactionTypeUiModel.dst.token.address,
                )

                VerifyCardDivider(
                    size = 1.dp,
                )

                TextDetails(
                    title = stringResource(R.string.swap_form_total_fees_title),
                    subtitle = transactionTypeUiModel.totalFee,
                )
            }
        },
        bottomBarContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 24.dp,
                        vertical = 12.dp,
                    ),
            ) {
                if (!progressLink.isNullOrBlank()) {
                    val uriHandler = VsUriHandler()
                    VsButton(
                        label = stringResource(R.string.swap_transaction_overview_track),
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
                    label = stringResource(R.string.transaction_done_title),
                    variant = VsButtonVariant.Primary,
                    size = VsButtonSize.Small,
                    modifier = Modifier
                        .weight(1f),
                    onClick = onComplete,
                )
            }
        }
    )
}

@Composable
internal fun TextDetails(
    title: String,
    subtitle: String,
) {
    Details(
        title = title,
        modifier = Modifier.padding(
            vertical = 12.dp,
        ),
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
    titleColor: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()

    ) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.footnote,
            color = titleColor ?: Theme.colors.text.extraLight,
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