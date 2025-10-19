package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.keysign.TransactionTypeUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.screens.send.EstimatedNetworkFee
import com.vultisig.wallet.ui.screens.swap.VerifyCardDivider
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsUriHandler
import java.math.BigInteger

@Composable
internal fun SendTxOverviewScreen(
    showToolbar: Boolean = true,
    showSaveToAddressBook: Boolean,
    transactionHash: String,
    transactionLink: String,
    onComplete: () -> Unit,
    onBack: () -> Unit = {},
    onAddToAddressBook: () -> Unit,
    tx: UiTransactionInfo,
) {

    TxDoneScaffold(
        transactionHash = transactionHash,
        transactionLink = transactionLink,
        showToolbar = showToolbar,
        onBack = onBack,
        bottomBarContent = {
            VsButton(
                label = stringResource(R.string.transaction_done_title),
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


                if (showSaveToAddressBook) {
                    Column {
                        TextDetails(
                            title = stringResource(R.string.tx_overview_screen_tx_to),
                            subtitle = tx.to,
                        )

                        UiSpacer(
                            8.dp
                        )

                        AddToAddressBookButton(
                            modifier = Modifier
                                .align(Alignment.End),
                            onClick = onAddToAddressBook
                        )
                        UiSpacer(
                            size = 12.dp
                        )
                    }
                } else {
                    TextDetails(
                        title = stringResource(R.string.tx_overview_screen_tx_to),
                        subtitle = tx.to,
                    )
                }

                if (tx.memo.isNotEmpty()) {
                    VerifyCardDivider(
                        size = 1.dp,
                    )

                    TextDetails(
                        title = stringResource(R.string.tx_overview_screen_tx_memo),
                        subtitle = tx.memo,
                    )
                }

                if (tx.token.value.isNotEmpty() && try {
                        tx.token.value.toBigInteger() > BigInteger.ZERO
                    } catch (e: Exception) {
                        false
                    }
                ) {
                    VerifyCardDivider(
                        size = 1.dp,
                    )

                    TextDetails(
                        title = stringResource(R.string.deposit_screen_amount_title),
                        subtitle = tx.token.value
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
private fun AddToAddressBookButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            4.dp,
            Alignment.End
        ),
        modifier = modifier
            .clip(CircleShape)
            .background(
                color = Theme.colors.backgrounds.states.success,
                shape = CircleShape
            )
            .border(
                width = 1.dp,
                color = Theme.colors.alerts.success,
                shape = CircleShape
            )
            .clickOnce(
                onClick = onClick
            )
            .padding(
                vertical = 8.dp,
                horizontal = 10.dp
            ),
    ) {
        UiIcon(
            drawableResId = R.drawable.plus,
            size = 16.dp,
            tint = Theme.colors.alerts.success,
        )


        Text(
            text = stringResource(R.string.send_tx_overview_add_to_address_book),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.colors.alerts.success,
        )
    }
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
                textToCopy = link,
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
        onAddToAddressBook = {},
        tx = TransactionTypeUiModel.Deposit(
            depositTransactionUiModel = DepositTransactionUiModel(
                token = ValuedToken.Empty,
                srcAddress = "abx123abx123abx123abx123ab",
                networkFeeFiatValue = "",
                memo = "sdfsdfsdfsdfs",
                dstAddress = "abx123abx123abx123abx123ab"
            )
        ).toUiTransactionInfo(),
        showSaveToAddressBook = true,
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