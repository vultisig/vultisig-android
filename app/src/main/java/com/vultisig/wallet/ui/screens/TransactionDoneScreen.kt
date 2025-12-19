package com.vultisig.wallet.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.UriHandler
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
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.SendTxUiModel
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.keysign.TransactionTypeUiModel
import com.vultisig.wallet.ui.models.sign.SignMessageTransactionUiModel
import com.vultisig.wallet.ui.screens.send.AddressField
import com.vultisig.wallet.ui.screens.send.OtherField
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsUriHandler

@Composable
internal fun TransactionDoneView(
    transactionHash: String,
    approveTransactionHash: String,
    transactionLink: String,
    approveTransactionLink: String,
    onComplete: () -> Unit,
    onBack: () -> Unit = {},
    transactionTypeUiModel: TransactionTypeUiModel?,
    showToolbar: Boolean,
) {
    val uriHandler = VsUriHandler()
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = Theme.v2.colors.backgrounds.primary,
        topBar = {
            if (showToolbar) {
                VsTopAppBar(
                    title = "Overview",
                )
            }
        },
        content = { contentPadding ->
            FormCard(
                modifier = Modifier
                    .padding(contentPadding)
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
                                approveTransactionLink,
                                uriHandler,
                                approveTransactionHash,
                                isApproved = true
                            )
                            UiHorizontalDivider()
                        }
                        TxLinkAndHash(
                            transactionLink,
                            uriHandler,
                            transactionHash
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
                    .fillMaxWidth(),
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(all = 16.dp),
    )
}

@Composable
private fun TxLinkAndHash(
    transactionLink: String,
    uriHandler: UriHandler,
    transactionHash: String,
    isApproved: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(if (isApproved) R.string.transaction_done_form_approve else R.string.transaction_done_form_title),
            color = Theme.v2.colors.neutrals.n50,
            style = Theme.montserrat.heading5,
        )

        CopyIcon(textToCopy = transactionLink)

        UiIcon(drawableResId = R.drawable.ic_link, size = 20.dp, onClick = {
            uriHandler.openUri(transactionLink)
        })
    }
    Text(
        text = transactionHash,
        color = Theme.v2.colors.backgrounds.teal,
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

        if (!depositTransaction.operation.isNullOrEmpty()){
            AddressField(
                title = stringResource(R.string.operation),
                address = depositTransaction.operation,
            )
        }
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
                        color = Theme.v2.colors.neutrals.n100,
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
                        color = Theme.v2.colors.neutrals.n100,
                        fontSize = 14.sp,
                        fontFamily = Theme.montserrat.subtitle1.fontFamily,
                        fontWeight = Theme.montserrat.subtitle1.fontWeight,
                    )
                ) {
                    append(transaction.networkFeeTokenValue)
                }
                withStyle(
                    style = SpanStyle(
                        color = Theme.v2.colors.neutrals.n400,
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
        transactionTypeUiModel = TransactionTypeUiModel.Send(
        transactionTypeUiModel = TransactionTypeUiModel.Deposit(
            DepositTransactionUiModel(
                srcAddress = "0x1234567890",
                dstAddress = "0x1234567890",
                memo = "some memo",
                operation = "some operation",
            )
        ),
    )
}