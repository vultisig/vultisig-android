package com.vultisig.wallet.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.components.library.form.FormDetails
import com.vultisig.wallet.ui.models.TransactionUiModel
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.keysign.TransactionTypeUiModel
import com.vultisig.wallet.ui.models.sign.SignMessageTransactionUiModel
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.screens.send.AddressField
import com.vultisig.wallet.ui.screens.send.OtherField
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TransactionDoneScreen(
    navController: NavController,
    transactionHash: String,
    transactionLink: String,
    progressLink: String?,
    transactionTypeUiModel: TransactionTypeUiModel,
) {
    UiBarContainer(
        navController = navController,
        title = stringResource(R.string.transaction_done_title)
    ) {
        TransactionDoneView(
            transactionHash = transactionHash,
            transactionLink = transactionLink,
            onComplete = navController::popBackStack,
            progressLink = progressLink,
            transactionTypeUiModel = transactionTypeUiModel,
        )
    }
}

@Composable
internal fun TransactionDoneView(
    transactionHash: String,
    transactionLink: String,
    onComplete: () -> Unit,
    progressLink: String?,
    onBack: () -> Unit = {},
    transactionTypeUiModel: TransactionTypeUiModel?,
) {
    val uriHandler = LocalUriHandler.current
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = Theme.colors.oxfordBlue800,
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
                        TxLinkAndHash(transactionLink, uriHandler, transactionHash)
                    }

                    when (transactionTypeUiModel) {
                        is TransactionTypeUiModel.Deposit -> DepositTransactionDetail(
                            transactionTypeUiModel.depositTransactionUiModel
                        )

                        is TransactionTypeUiModel.Send -> TransactionDetail(transaction = transactionTypeUiModel.transactionUiModel)

                        is TransactionTypeUiModel.Swap -> SwapTransactionDetail(
                            swapTransaction = transactionTypeUiModel.swapTransactionUiModel,
                            progressLink = progressLink,
                        ) { progressLink ->
                            uriHandler.openUri(progressLink)
                        }

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
            MultiColorButton(
                text = stringResource(R.string.transaction_done_complete),
                textColor = Theme.colors.oxfordBlue800,
                minHeight = 44.dp,
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = onComplete,
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
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.transaction_done_form_title),
            color = Theme.colors.neutral0,
            style = Theme.montserrat.heading5,
        )

        val clipboard = LocalClipboardManager.current

        UiIcon(drawableResId = R.drawable.copy, size = 20.dp, onClick = {
            clipboard.setText(AnnotatedString(transactionLink))
        })


        UiIcon(drawableResId = R.drawable.ic_link, size = 20.dp, onClick = {
            uriHandler.openUri(transactionLink)
        })
    }
    Text(
        text = transactionHash,
        color = Theme.colors.turquoise800,
        style = Theme.menlo.subtitle3,
    )
}

@Composable
private fun DepositTransactionDetail(depositTransaction: DepositTransactionUiModel?) {
    if (depositTransaction != null) {
        AddressField(
            title = stringResource(R.string.verify_transaction_from_title),
            address = depositTransaction.fromAddress,
        )

        OtherField(
            title = stringResource(R.string.deposit_screen_amount_title),
            value = depositTransaction.srcTokenValue
        )

        AddressField(
            title = stringResource(R.string.verify_deposit_memo_title),
            address = depositTransaction.memo,
        )

        if (depositTransaction.nodeAddress.isNotBlank()) {
            AddressField(
                title = stringResource(R.string.verify_deposit_node_address_title),
                address = depositTransaction.nodeAddress,
            )
        }

        OtherField(
            title = stringResource(R.string.verify_deposit_gas_title),
            value = depositTransaction.estimatedFees,
        )
    }
}

@Composable
private fun ColumnScope.SwapTransactionDetail(
    swapTransaction: SwapTransactionUiModel?,
    progressLink: String?,
    onProgressLinkClick: (String) -> Unit,
) {
    if (swapTransaction != null) {
        AddressField(
            title = stringResource(R.string.verify_transaction_from_title),
            address = swapTransaction.srcTokenValue,
        )

        AddressField(
            title = stringResource(R.string.verify_transaction_to_title),
            address = swapTransaction.dstTokenValue
        )

        if (swapTransaction.hasConsentAllowance) {
            AddressField(
                title = stringResource(R.string.verify_approve_amount_title),
                address = swapTransaction.srcTokenValue,
            )
        }

        OtherField(
            title = stringResource(R.string.verify_swap_screen_total_fees),
            value = swapTransaction.totalFee,
        )

        if (progressLink != null) {
            Text(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.End)
                    .clickOnce(onClick = {
                        onProgressLinkClick(progressLink)
                    }),
                text = stringResource(R.string.transaction_swap_tracking_link),
                color = Theme.colors.turquoise800,
                style = Theme.montserrat.body3.copy(
                    textDecoration = TextDecoration.Underline,
                    lineHeight = 22.sp
                ),
            )
        }
    }
}

@Composable
private fun TransactionDetail(transaction: TransactionUiModel?) {
    if (transaction != null) {

        UiSpacer(size = 12.dp)
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
            value = transaction.tokenValue,
        )

        OtherField(
            title = stringResource(R.string.verify_transaction_value),
            value = transaction.fiatValue,
        )

        FormDetails(modifier = Modifier
            .padding(
                vertical = 12.dp,
            )
            .fillMaxWidth(),
            title = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        color = Theme.colors.neutral100,
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
                        color = Theme.colors.neutral100,
                        fontSize = 14.sp,
                        fontFamily = Theme.montserrat.subtitle1.fontFamily,
                        fontWeight = Theme.montserrat.subtitle1.fontWeight,
                    )
                ) {
                    append(transaction.totalGas)
                }
                withStyle(
                    style = SpanStyle(
                        color = Theme.colors.neutral400,
                        fontSize = 14.sp,
                        fontFamily = Theme.montserrat.subtitle1.fontFamily,
                        fontWeight = Theme.montserrat.subtitle1.fontWeight,
                    )
                ) {
                    append(" (~${transaction.estimatedFee})")
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
private fun TransactionDoneScreenPreview() {
    TransactionDoneScreen(
        navController = rememberNavController(),
        transactionHash = "0x1234567890",
        transactionLink = "",
        progressLink = "https://track.ninerealms.com/",
        transactionTypeUiModel = TransactionTypeUiModel.Send(
            TransactionUiModel(
                srcAddress = "0x1234567890",
                dstAddress = "0x1234567890",
                tokenValue = "1.1",
                fiatValue = "1.1",
                fiatCurrency = "USD",
                gasFeeValue = "1.1",
                memo = "some memo",
                estimatedFee = "0.75 USd",
                totalGas = "0.00031361"
            )
        ),
    )
}