package com.vultisig.wallet.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.CopyIcon
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.components.library.form.FormDetails
import com.vultisig.wallet.ui.components.scaffold.VsScaffold
import com.vultisig.wallet.ui.components.topbar.VsTopbar
import com.vultisig.wallet.ui.models.SendTxUiModel
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.keysign.TransactionTypeUiModel
import com.vultisig.wallet.ui.models.sign.SignMessageTransactionUiModel
import com.vultisig.wallet.ui.screens.send.AddressField
import com.vultisig.wallet.ui.screens.send.OtherField
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TransactionDoneView(
    transactionHash: String,
    approveTransactionHash: String,
    transactionLink: String,
    approveTransactionLink: String,
    onComplete: () -> Unit,
    onBack: () -> Unit,
    onUriClick: (String) -> Unit,
    transactionTypeUiModel: TransactionTypeUiModel?,
    showToolbar: Boolean,
) {
    BackHandler(onBack = onBack)

    VsScaffold(
        applyDefaultPaddings = true,
        applyScaffoldPaddings = true,
        topBar = {
            if (showToolbar) {
                VsTopbar(
                    title = "Overview",
                    onBackClick = {},
                )
            }
        },
        content = {
            FormCard(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .padding(all = 12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (transactionTypeUiModel !is TransactionTypeUiModel.SignMessage) {
                        if (approveTransactionHash.isNotEmpty()) {
                            TxLinkAndHash(
                                transactionLink = approveTransactionLink,
                                onUriClick = onUriClick,
                                transactionHash = approveTransactionHash,
                                isApproved = true
                            )
                            UiHorizontalDivider()
                        }
                        TxLinkAndHash(
                            transactionLink = transactionLink,
                            transactionHash = transactionHash,
                            onUriClick = onUriClick,
                        )
                    }

                    when (transactionTypeUiModel) {
                        is TransactionTypeUiModel.Deposit -> DepositTransactionDetail(
                            transactionTypeUiModel.depositTransactionUiModel
                        )

                        is TransactionTypeUiModel.Send -> TransactionDetail(transaction = transactionTypeUiModel.tx)

                        is TransactionTypeUiModel.SignMessage -> CustomMessageDetail(
                            transactionTypeUiModel.model,
                            transactionHash
                        )

                        else -> Unit
                    }

                }
            }
        },
        bottomBar = {
            VsButton(
                label = stringResource(R.string.transaction_done_complete),
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 24.dp
                    ),
            )
        },
    )
}

@Composable
private fun TxLinkAndHash(
    transactionLink: String,
    onUriClick: (String) -> Unit,
    transactionHash: String,
    isApproved: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(if (isApproved) R.string.transaction_done_form_approve else R.string.transaction_done_form_title),
            color = Theme.colors.neutrals.n50,
            style = Theme.montserrat.heading5,
        )

        CopyIcon(textToCopy = transactionLink)

        UiIcon(drawableResId = R.drawable.ic_link, size = 20.dp, onClick = {
            onUriClick(transactionLink)
        })
    }
    Text(
        text = transactionHash,
        color = Theme.colors.backgrounds.teal,
        style = Theme.menlo.subtitle3,
    )
}

@Composable
private fun DepositTransactionDetail(depositTransaction: DepositTransactionUiModel?) {
    if (depositTransaction != null) {
        AddressField(
            title = stringResource(R.string.verify_transaction_from_title),
            address = depositTransaction.srcAddress,
        )

        OtherField(
            title = stringResource(R.string.deposit_screen_amount_title),
            value = depositTransaction.token.value
        )

        AddressField(
            title = stringResource(R.string.verify_deposit_memo_title),
            address = depositTransaction.memo,
        )

        if (depositTransaction.dstAddress.isNotBlank()) {
            AddressField(
                title = stringResource(R.string.verify_deposit_node_address_title),
                address = depositTransaction.dstAddress,
            )
        }

        OtherField(
            title = stringResource(R.string.verify_deposit_gas_title),
            value = depositTransaction.networkFeeTokenValue,
        )
    }
}

@Composable
private fun TransactionDetail(transaction: SendTxUiModel?) {
    if (transaction != null) {

        UiHorizontalDivider()

        AddressField(
            title = stringResource(R.string.verify_transaction_to_title),
            address = transaction.dstAddress
        )

        if (!transaction.memo.isNullOrEmpty())
            OtherField(
                title = stringResource(R.string.verify_transaction_memo_title),
                value = transaction.memo
            )

        OtherField(
            title = stringResource(R.string.verify_transaction_amount_title),
            value = transaction.token.value,
        )

        OtherField(
            title = stringResource(R.string.verify_transaction_value),
            value = transaction.token.fiatValue,
        )

        FormDetails(modifier = Modifier
            .padding(
                vertical = 12.dp,
            )
            .fillMaxWidth(),
            title = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        color = Theme.colors.neutrals.n100,
                        fontSize = 14.sp,
                        fontFamily = Theme.montserrat.subtitle1.fontFamily,
                        fontWeight = Theme.montserrat.subtitle1.fontWeight,

                        )
                ) {
                    append(stringResource(R.string.verify_transaction_network_fee))
                }
            },
            value = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        color = Theme.colors.neutrals.n100,
                        fontSize = 14.sp,
                        fontFamily = Theme.montserrat.subtitle1.fontFamily,
                        fontWeight = Theme.montserrat.subtitle1.fontWeight,
                    )
                ) {
                    append(transaction.networkFeeTokenValue)
                }
                withStyle(
                    style = SpanStyle(
                        color = Theme.colors.neutrals.n400,
                        fontSize = 14.sp,
                        fontFamily = Theme.montserrat.subtitle1.fontFamily,
                        fontWeight = Theme.montserrat.subtitle1.fontWeight,
                    )
                ) {
                    append(" (~${transaction.networkFeeFiatValue})")
                }
            })
    }
}

@Composable
private fun CustomMessageDetail(
    signMessage: SignMessageTransactionUiModel?,
    signature: String,
) {
    if (signMessage == null) return

    AddressField(
        title = stringResource(R.string.verify_sign_message_method_field_title),
        address = signMessage.method,
    )

    AddressField(
        title = stringResource(R.string.verify_sign_message_message_field_title),
        address = signMessage.message,
    )

    AddressField(
        title = stringResource(R.string.verify_sign_message_message_field_signature),
        address = signature,
        divider = false,
    )
}

@Preview
@Composable
private fun TransactionDoneViewPreview() {
    TransactionDoneView(
        showToolbar = true,
        transactionHash = "0x1234567890",
        approveTransactionHash = "0x1234567890",
        transactionLink = "",
        approveTransactionLink = "",
        onComplete = {},
        onUriClick = {},
        onBack = {},
        transactionTypeUiModel = TransactionTypeUiModel.Send(
            SendTxUiModel(
                srcAddress = "0x1234567890",
                dstAddress = "0x1234567890",
                memo = "some memo",
            )
        ),
    )
}