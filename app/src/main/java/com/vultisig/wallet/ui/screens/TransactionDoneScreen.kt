package com.vultisig.wallet.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
import com.vultisig.wallet.ui.models.keysign.TransitionTypeUiModel
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.screens.send.AddressField
import com.vultisig.wallet.ui.screens.send.OtherField
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TransactionDoneScreen(
    navController: NavController,
    transactionHash: String,
    transactionLink: String,
    isThorChainSwap: Boolean,
    transitionTypeUiModel: TransitionTypeUiModel,
) {
    UiBarContainer(
        navController = navController,
        title = stringResource(R.string.transaction_done_title)
    ) {
        TransactionDoneView(
            transactionHash = transactionHash,
            transactionLink = transactionLink,
            onComplete = navController::popBackStack,
            isThorChainSwap = isThorChainSwap,
            transitionTypeUiModel = transitionTypeUiModel,
        )
    }
}

@Composable
internal fun TransactionDoneView(
    transactionHash: String,
    transactionLink: String,
    onComplete: () -> Unit,
    isThorChainSwap: Boolean = false,
    onBack: () -> Unit = {},
    transitionTypeUiModel: TransitionTypeUiModel?,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(all = 16.dp),
    ) {
        FormCard {
            Column(
                modifier = Modifier
                    .padding(all = 12.dp)
                    .fillMaxWidth(),
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

                    UiIcon(
                        drawableResId = R.drawable.copy,
                        size = 20.dp,
                        onClick = {
                            clipboard.setText(AnnotatedString(transactionLink))
                        }
                    )


                    UiIcon(
                        drawableResId = R.drawable.ic_link,
                        size = 20.dp,
                        onClick = {
                            uriHandler.openUri(transactionLink)
                        }
                    )
                }

                UiSpacer(size = 16.dp)

                Text(
                    text = transactionHash,
                    color = Theme.colors.turquoise800,
                    style = Theme.menlo.subtitle3,
                )
                if (isThorChainSwap) {
                    Text(
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickOnce {
                                uriHandler.openUri(
                                    context.getString(
                                        R.string.transaction_done_track_ninerealms,
                                        transactionHash
                                    )
                                )
                            },
                        text = stringResource(R.string.transaction_swap_tracking_link),
                        color = Theme.colors.turquoise800,
                        style = Theme.montserrat.body3.copy(
                            textDecoration = TextDecoration.Underline,
                            lineHeight = 22.sp
                        ),

                        )
                }

                when (transitionTypeUiModel) {
                    is TransitionTypeUiModel.Deposit ->
                        DepositTransactionDetail(transitionTypeUiModel.depositTransactionUiModel)

                    is TransitionTypeUiModel.Send ->
                        TransactionDetail(transaction = transitionTypeUiModel.transactionUiModel)

                    is TransitionTypeUiModel.Swap -> SwapTransactionDetail(
                        swapTransaction =
                        transitionTypeUiModel.swapTransactionUiModel
                    )
                    else -> Unit
                }

            }
        }
        UiSpacer(weight = 1f)

        MultiColorButton(
            text = stringResource(R.string.transaction_done_complete),
            textColor = Theme.colors.oxfordBlue800,
            minHeight = 44.dp,
            modifier = Modifier
                .fillMaxWidth(),
            onClick = onComplete,
        )
    }
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
private fun SwapTransactionDetail(swapTransaction: SwapTransactionUiModel?) {
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
                address = stringResource(R.string.verify_approve_amount_unlimited),
            )
        }

        OtherField(
            title = stringResource(R.string.verify_swap_screen_estimated_fees),
            value = swapTransaction.estimatedFees,
            divider = false,
        )
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

@Preview
@Composable
private fun TransactionDoneScreenPreview() {
    TransactionDoneScreen(
        navController = rememberNavController(),
        transactionHash = "0x1234567890",
        transactionLink = "",
        isThorChainSwap = true,
        transitionTypeUiModel = TransitionTypeUiModel.Send(
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