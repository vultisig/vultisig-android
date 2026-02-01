package com.vultisig.wallet.ui.screens.transaction

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsOverviewToken
import com.vultisig.wallet.ui.components.errors.ErrorState
import com.vultisig.wallet.ui.components.errors.ErrorView
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.v2.topbar.V2Topbar
import com.vultisig.wallet.ui.models.keysign.TransactionStatus
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.screens.send.EstimatedNetworkFee
import com.vultisig.wallet.ui.screens.swap.VerifyCardDivider
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun TxDoneScaffold(
    showToolbar: Boolean = true,
    transactionHash: String,
    transactionLink: String,
    transactionStatus: TransactionStatus,
    onBack: () -> Unit = {},
    tokenContent: @Composable () -> Unit,
    detailContent: @Composable () -> Unit,
    bottomBarContent: @Composable () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        containerColor = Theme.v2.colors.backgrounds.primary,
        topBar = {
            if (showToolbar) {
                V2Topbar(
                    title = stringResource(R.string.tx_overview_screen_title),
                    onBackClick = onBack,
                )
            }
        },
        content = {
            when (transactionStatus) {
                TransactionStatus.Broadcasted,
                TransactionStatus.Confirmed -> {
                    SuccessTransaction(
                        modifier = Modifier.padding(it),
                        tokenContent = tokenContent,
                        transactionHash = transactionHash,
                        transactionLink = transactionLink,
                        coroutineScope = coroutineScope,
                        snackbarHostState = snackbarHostState,
                        context = context,
                        detailContent = detailContent
                    )
                }

                is TransactionStatus.Failed -> {
                    ErrorView(
                        title = "Transaction failed",
                        errorState = ErrorState.CRITICAL,
                        description = transactionStatus.cause.asString(),
                        onButtonClick = onBack
                    )
                }

                TransactionStatus.Pending -> {
                    TransactionPending(
                        modifier = Modifier.padding(it),
                    )
                }
            }
        },
        bottomBar = {
            bottomBarContent()
        }
    )
}

@Composable
private fun SuccessTransaction(
    modifier: Modifier,
    tokenContent: @Composable (() -> Unit),
    transactionHash: String,
    transactionLink: String,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    context: Context,
    detailContent: @Composable (() -> Unit)
) {

    var isTransactionDetailVisible by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
    ) {
        AnimatedVisibility(isTransactionDetailVisible.not()) {
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
                    text = stringResource(R.string.tx_transaction_successful_screen_title),
                    textAlign = TextAlign.Center,
                    style = Theme.brockmann.body.l.medium
                        .copy(
                            brush = Theme.v2.colors.gradients.primary,
                        ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(
                            bottom = 48.dp,
                        ),
                )
            }
        }

        tokenContent()

        UiSpacer(8.dp)

        Column(
            modifier = Modifier
                .background(
                    color = Theme.v2.colors.backgrounds.disabled,
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            if (transactionHash.isNotEmpty()) {
                TxDetails(
                    title = stringResource(R.string.tx_overview_screen_tx_hash),
                    hash = transactionHash,
                    link = transactionLink,
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
                    },
                )

                VerifyCardDivider(
                    size = 1.dp,
                )
            }

            AnimatedVisibility(isTransactionDetailVisible.not()) {
                Details(
                    title = stringResource(R.string.tx_done_transaction_details),
                    modifier = Modifier
                        .clickable(onClick = {
                            isTransactionDetailVisible = true
                        })
                        .padding(vertical = 12.dp),
                    titleColor = Theme.v2.colors.text.light,
                    content = {
                        UiIcon(
                            R.drawable.ic_caret_right,
                            modifier = Modifier
                                .weight(1f)
                                .wrapContentSize(align = Alignment.CenterEnd),
                            size = 16.dp
                        )
                    }
                )
            }

            AnimatedVisibility(isTransactionDetailVisible) {
                detailContent()
            }
        }
    }
}

@Composable
private fun TransactionPending(modifier: Modifier = Modifier) {
    RiveAnimation(
        animation = R.raw.riv_transaction_pending,
        modifier = modifier
            .fillMaxSize()
            .wrapContentSize()
    )
}

@Preview
@Composable
private fun SuccessTransactionPreview() {
    Scaffold {
        SuccessTransaction(
            modifier = Modifier.padding(it),
            tokenContent = {
                VsOverviewToken(
                    header = "",
                    valuedToken = ValuedToken.Empty,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth(),
                )
            },
            transactionHash = "",
            transactionLink = "tx link",
            coroutineScope = rememberCoroutineScope(),
            snackbarHostState = remember { SnackbarHostState() },
            context = LocalContext.current,
            detailContent = {
                Column {

                    TextDetails(
                        title = stringResource(R.string.tx_overview_screen_tx_from),
                        subtitle = "tx.from",
                    )

                    VerifyCardDivider(
                        size = 1.dp,
                    )

                    TextDetails(
                        title = stringResource(R.string.tx_overview_screen_tx_to),
                        subtitle = " tx.to",
                    )

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
                            val chain = Chain.Ethereum

                            Image(
                                painter = painterResource(chain.logo),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp),
                            )

                            Text(
                                text = chain.raw,
                                style = Theme.brockmann.body.s.medium,
                                color = Theme.v2.colors.text.primary,
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
                        tokenGas = "tx.networkFeeTokenValue",
                        fiatGas = "tx.networkFeeFiatValue",
                    )
                }
            }
        )
    }
}

@Preview
@Composable
private fun TransactionPendingPreview() {
    Scaffold {
        TransactionPending(modifier = Modifier.padding(it))
    }
}