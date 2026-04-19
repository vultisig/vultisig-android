package com.vultisig.wallet.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.vultisig.wallet.ui.components.VsOverviewToken
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.components.library.form.FormDetails
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.keysign.TransactionTypeUiModel
import com.vultisig.wallet.ui.models.sign.SignMessageTransactionUiModel
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
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

    V2Scaffold(
        applyDefaultPaddings = true,
        applyScaffoldPaddings = true,
        topBar = {
            if (showToolbar) {
                VsTopAppBar(title = stringResource(R.string.transaction_done_overview))
            }
        },
        content = {
            FormCard(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Column(
                    modifier = Modifier.padding(all = 12.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (transactionTypeUiModel is TransactionTypeUiModel.Send) {
                        val transaction = transactionTypeUiModel.tx
                        if (transaction.functionName != null) {
                            Text(
                                text = transaction.functionName,
                                style = Theme.brockmann.headings.title3,
                                color = Theme.v2.colors.text.primary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            VsOverviewToken(
                                header = stringResource(R.string.tx_overview_screen_tx_send),
                                valuedToken = transaction.token,
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (transactionTypeUiModel !is TransactionTypeUiModel.SignMessage) {
                        if (approveTransactionHash.isNotEmpty()) {
                            TxLinkAndHash(
                                transactionLink = approveTransactionLink,
                                onUriClick = onUriClick,
                                transactionHash = approveTransactionHash,
                                isApproved = true,
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
                        is TransactionTypeUiModel.Deposit ->
                            DepositTransactionDetail(
                                transactionTypeUiModel.depositTransactionUiModel
                            )

                        is TransactionTypeUiModel.Send ->
                            TransactionDetail(transaction = transactionTypeUiModel.tx)

                        is TransactionTypeUiModel.Swap ->
                            SwapTransactionDetail(transactionTypeUiModel.swapTransactionUiModel)

                        is TransactionTypeUiModel.SignMessage ->
                            CustomMessageDetail(transactionTypeUiModel.model, transactionHash)

                        else -> Unit
                    }
                }
            }
        },
        bottomBar = {
            VsButton(
                label = stringResource(R.string.transaction_done_complete),
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                stringResource(
                    if (isApproved) R.string.transaction_done_form_approve
                    else R.string.transaction_done_form_title
                ),
            color = Theme.v2.colors.text.primary,
            style = Theme.brockmann.headings.subtitle,
        )

        CopyIcon(textToCopy = transactionLink.ifEmpty { transactionHash })

        if (transactionLink.isNotEmpty()) {
            UiIcon(
                drawableResId = R.drawable.ic_link,
                size = 20.dp,
                onClick = { onUriClick(transactionLink) },
            )
        }
    }
    Text(
        text = transactionHash,
        color = Theme.v2.colors.text.primary,
        style = Theme.brockmann.body.s.medium,
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
            value = depositTransaction.token.value,
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
private fun SwapTransactionDetail(swapTransaction: SwapTransactionUiModel) {
    UiHorizontalDivider()

    OtherField(
        title = stringResource(R.string.swap_form_from_title),
        value = "${swapTransaction.src.value} ${swapTransaction.src.token.ticker}",
    )

    OtherField(
        title = stringResource(R.string.swap_form_dst_token_title),
        value = "${swapTransaction.dst.value} ${swapTransaction.dst.token.ticker}",
    )

    if (swapTransaction.provider.isNotEmpty()) {
        OtherField(
            title = stringResource(R.string.swap_screen_provider_title),
            value = swapTransaction.provider,
        )
    }

    OtherField(
        title = stringResource(R.string.swap_form_estimated_fees_title),
        value = swapTransaction.providerFee.fiatValue,
    )

    OtherField(
        title = stringResource(R.string.verify_transaction_network_fee),
        value = swapTransaction.networkFeeFormatted,
    )

    OtherField(
        title = stringResource(R.string.verify_swap_screen_total_fees),
        value = swapTransaction.totalFee,
    )
}

@Composable
private fun TransactionDetail(transaction: TransactionDetailsUiModel?) {
    if (transaction != null) {

        UiHorizontalDivider()

        AddressField(
            title = stringResource(R.string.verify_transaction_to_title),
            address = transaction.dstAddress,
        )

        if (!transaction.memo.isNullOrEmpty())
            OtherField(
                title = stringResource(R.string.verify_transaction_memo_title),
                value = transaction.memo,
            )

        if (transaction.tokenDisplay != null) {
            UiHorizontalDivider()
            OtherField(
                title = stringResource(R.string.verify_transaction_amount_title),
                value = transaction.tokenDisplay,
            )
        }
        if (transaction.functionSignature != null) {
            UiHorizontalDivider()
            OtherField(
                title = stringResource(R.string.deposit_screen_title),
                value = transaction.functionSignature,
            )
        }
        if (transaction.functionInputs != null) {
            UiHorizontalDivider()
            OtherField(
                title = stringResource(R.string.verify_transaction_function_inputs_title),
                value = transaction.functionInputs,
            )
        }

        // Native amount fallback. Suppressed when a resolved contract-call token was
        // rendered upstream OR when the decoded call already provided its own display
        // (e.g. "Unlimited USDC" for an approve MAX) — otherwise the screen shows the
        // same amount twice, with the native row being the misleading one.
        if (
            transaction.functionName == null &&
                transaction.resolvedToken == null &&
                transaction.tokenDisplay == null
        ) {
            OtherField(
                title = stringResource(R.string.verify_transaction_amount_title),
                value = transaction.token.value,
            )

            OtherField(
                title = stringResource(R.string.verify_transaction_value),
                value = transaction.token.fiatValue,
            )
        }

        FormDetails(
            modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
            title =
                buildAnnotatedString {
                    withStyle(
                        style =
                            SpanStyle(
                                color = Theme.v2.colors.text.tertiary,
                                fontSize = 14.sp,
                                fontFamily = Theme.brockmann.body.s.medium.fontFamily,
                                fontWeight = Theme.brockmann.body.s.medium.fontWeight,
                            )
                    ) {
                        append(stringResource(R.string.verify_transaction_network_fee))
                    }
                },
            value =
                buildAnnotatedString {
                    withStyle(
                        style =
                            SpanStyle(
                                color = Theme.v2.colors.text.primary,
                                fontSize = 14.sp,
                                fontFamily = Theme.brockmann.body.s.medium.fontFamily,
                                fontWeight = Theme.brockmann.body.s.medium.fontWeight,
                            )
                    ) {
                        append(transaction.networkFeeTokenValue)
                    }
                    withStyle(
                        style =
                            SpanStyle(
                                color = Theme.v2.colors.text.tertiary,
                                fontSize = 14.sp,
                                fontFamily = Theme.brockmann.body.s.medium.fontFamily,
                                fontWeight = Theme.brockmann.body.s.medium.fontWeight,
                            )
                    ) {
                        append(" (~${transaction.networkFeeFiatValue})")
                    }
                },
        )
    }
}

@Composable
private fun CustomMessageDetail(signMessage: SignMessageTransactionUiModel?, signature: String) {
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
        transactionTypeUiModel =
            TransactionTypeUiModel.Send(
                TransactionDetailsUiModel(
                    srcAddress = "0x1234567890",
                    dstAddress = "0x1234567890",
                    memo = "some memo",
                )
            ),
    )
}
