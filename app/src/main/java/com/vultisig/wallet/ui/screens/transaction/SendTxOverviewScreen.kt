package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.CopyIcon
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsOverviewToken
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.keysign.TransactionTypeUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.screens.send.EstimatedNetworkFee
import com.vultisig.wallet.ui.screens.swap.VerifyCardDivider
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsUriHandler

@Composable
internal fun SendTxOverviewScreen(
    showToolbar: Boolean = true,
    transactionHash: String,
    transactionLink: String,
    onComplete: () -> Unit,
    onBack: () -> Unit = {},
    tx: UiTransactionInfo,
) {

    TxDoneScaffold(
        transactionHash = transactionHash,
        transactionLink = transactionLink,
        showToolbar = showToolbar,
        onBack = onBack,
        bottomBarContent = {
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
        },
        tokenContent = {
            val tokenTitle = if (tx.type == UiTransactionInfoType.Send) {
                stringResource(R.string.tx_overview_screen_tx_send)
            } else {
                stringResource(R.string.tx_overview_screen_tx_deposit)
            }
            VsOverviewToken(
                header = tokenTitle,
                valuedToken = tx.token,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth(),
            )
        },
        detailContent = {
            Column {

                TextDetails(
                    title = stringResource(R.string.tx_overview_screen_tx_from),
                    subtitle = tx.from,
                )

                VerifyCardDivider(
                    size = 1.dp,
                )

                TextDetails(
                    title = stringResource(R.string.tx_overview_screen_tx_to),
                    subtitle = tx.to,
                )

                if (tx.memo.isNotEmpty()) {
                    VerifyCardDivider(
                        size = 1.dp,
                    )

                    TextDetails(
                        title = stringResource(R.string.tx_overview_screen_tx_memo),
                        subtitle = tx.memo,
                    )
                }

                VerifyCardDivider(
                    size = 1.dp,
                )

                Details(
                    modifier = Modifier.padding(
                        vertical = 12.dp,
                    ),
                    title = stringResource(R.string.tx_overview_screen_tx_network)
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(
                            4.dp,
                            Alignment.End
                        ),
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
    )
}

@Composable
internal fun TxDetails(
    title: String,
    hash: String,
    link: String,
    modifier: Modifier = Modifier,
    onTxHashCopied: (String) -> Unit = {},
) {
    val uriHandler = VsUriHandler()

    Details(
        modifier = modifier,
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

            CopyIcon(
                textToCopy = hash,
                size = 12.dp,
                onCopyCompleted = onTxHashCopied
            )

            UiSpacer(4.dp)

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
        transactionHash = "",
        transactionLink = "",
        onComplete = {},
        tx = TransactionTypeUiModel.Deposit(
            depositTransactionUiModel = DepositTransactionUiModel(
                token = ValuedToken.Empty,
                fromAddress = "abx123abx123abx123abx123ab",
                srcTokenValue = "1231232",
                estimateFeesFiat = "",
                memo = "sdfsdfsdfsdfs",
                nodeAddress = "abx123abx123abx123abx123ab"
            )
        ).toUiTransactionInfo()
    )
}

internal data class UiTransactionInfo(
    val type: UiTransactionInfoType,
    val token: ValuedToken,
    val from: String,
    val to: String,
    val memo: String,
    val networkFeeTokenValue: String,
    val networkFeeFiatValue: String,
    val signMethod: String = "",
)

internal enum class UiTransactionInfoType { Send, Deposit, Swap, SignMessage }