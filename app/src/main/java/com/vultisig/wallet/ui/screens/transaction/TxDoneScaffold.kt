package com.vultisig.wallet.ui.screens.transaction

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.vultisig.wallet.data.models.payload.DAppMetadata
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsOverviewToken
import com.vultisig.wallet.ui.components.dapp.DappRequestBanner
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.v2.topbar.V2Topbar
import com.vultisig.wallet.ui.models.keysign.TransactionStatus
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.screens.send.EstimatedNetworkFee
import com.vultisig.wallet.ui.screens.swap.VerifyCardDivider
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun TxDoneScaffold(
    showToolbar: Boolean = true,
    transactionHash: String,
    transactionLink: String,
    transactionStatus: TransactionStatus,
    isTransactionDetailVisible: Boolean,
    onTransactionDetailVisibleChange: (Boolean) -> Unit,
    onBack: () -> Unit = {},
    dappMetadata: DAppMetadata? = null,
    successTitle: String? = null,
    tokenContent: @Composable () -> Unit,
    detailContent: @Composable () -> Unit,
    bottomBarContent: @Composable () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Theme.v2.colors.backgrounds.primary,
        topBar = {
            if (showToolbar) {
                V2Topbar(
                    title = stringResource(R.string.transaction_complete_screen_title),
                    onBackClick = onBack,
                )
            }
        },
        content = {
            SuccessTransaction(
                modifier = Modifier.padding(it),
                tokenContent = tokenContent,
                transactionHash = transactionHash,
                transactionLink = transactionLink,
                transactionStatus = transactionStatus,
                coroutineScope = coroutineScope,
                snackbarHostState = snackbarHostState,
                context = context,
                detailContent = detailContent,
                dappMetadata = dappMetadata,
                successTitle = successTitle,
                isTransactionDetailVisible = isTransactionDetailVisible,
                onTransactionDetailVisibleChange = onTransactionDetailVisibleChange,
            )
        },
        bottomBar = {
            // Terminal actions (Track / Done / Try again) slide up and fade in as the result
            // settles, rather than snapping into place with the rest of the scaffold.
            val bottomBarVisibility = remember {
                MutableTransitionState(false).apply { targetState = true }
            }
            AnimatedVisibility(
                visibleState = bottomBarVisibility,
                enter =
                    fadeIn(animationSpec = tween(durationMillis = 300, delayMillis = 120)) +
                        slideInVertically(
                            animationSpec = tween(durationMillis = 300, delayMillis = 120),
                            initialOffsetY = { it / 2 },
                        ),
            ) {
                bottomBarContent()
            }
        },
    )
}

@Composable
private fun SuccessTransaction(
    modifier: Modifier,
    tokenContent: @Composable (() -> Unit),
    transactionHash: String,
    transactionLink: String,
    transactionStatus: TransactionStatus,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    context: Context,
    detailContent: @Composable (() -> Unit),
    isTransactionDetailVisible: Boolean,
    onTransactionDetailVisibleChange: (Boolean) -> Unit,
    dappMetadata: DAppMetadata? = null,
    successTitle: String? = null,
) {

    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        AnimatedVisibility(isTransactionDetailVisible.not()) {
            Column {
                val isTransactionPending = transactionStatus == TransactionStatus.Pending
                val isTransactionFailed = transactionStatus is TransactionStatus.Failed
                val isTransactionRefunded = transactionStatus is TransactionStatus.Refunded
                val isTransactionSigned = transactionStatus == TransactionStatus.Signed
                val statusTitle =
                    when {
                        isTransactionPending -> stringResource(R.string.transaction_status_pending)
                        isTransactionRefunded ->
                            stringResource(R.string.transaction_status_refunded)
                        isTransactionFailed -> stringResource(R.string.transaction_failed)
                        // PSBT co-signing flow: wallet signed, dApp broadcasts. Avoid
                        // "Transaction successful" copy that would imply the tx is
                        // already on-chain.
                        isTransactionSigned ->
                            stringResource(R.string.transaction_status_signed_label)
                        else ->
                            successTitle
                                ?: stringResource(R.string.tx_transaction_successful_screen_title)
                    }
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // The status hero swaps in place as broadcasted → pending → successful/failed
                    // lands: the icon scales+fades between the loading, error, and success visuals
                    // instead of hard-cutting.
                    AnimatedContent(
                        targetState = transactionStatus,
                        contentKey = { status -> statusHeroKey(status) },
                        transitionSpec = {
                            (scaleIn(initialScale = 0.8f, animationSpec = tween(300)) +
                                fadeIn(animationSpec = tween(300))) togetherWith
                                fadeOut(animationSpec = tween(150))
                        },
                        modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                        label = "tx_status_icon",
                    ) { status ->
                        when {
                            status == TransactionStatus.Pending -> TransactionPending()
                            status is TransactionStatus.Failed ||
                                status is TransactionStatus.Refunded ->
                                RiveAnimation(
                                    animation = R.raw.riv_transaction_error,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            else ->
                                Image(
                                    painter = painterResource(R.drawable.img_tx_overview_bg),
                                    contentDescription = null,
                                    alignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 48.dp).fillMaxWidth(),
                                )
                        }
                    }
                    // Title cross-fades as the status label changes.
                    Crossfade(
                        targetState = statusTitle,
                        animationSpec = tween(durationMillis = 220),
                        modifier =
                            Modifier.fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 48.dp),
                        label = "tx_status_title",
                    ) { title ->
                        Text(
                            text = title,
                            textAlign = TextAlign.Center,
                            style =
                                Theme.brockmann.body.l.medium.copy(
                                    brush = Theme.v2.colors.gradients.primary
                                ),
                        )
                    }
                }
            }
        }

        // Surface the protocol's refund reason (paused pool, slip exceeded, etc.) on the done
        // screen. Kept outside the collapsed block above so it stays visible when the user expands
        // Transaction Details. Only Refunded carries a clean Midgard reason; Failed.cause is a raw
        // provider error string (JSON blob, untranslated timeout literal) and is not shown here.
        (transactionStatus as? TransactionStatus.Refunded)
            ?.reason
            ?.asString()
            ?.takeIf { it.isNotBlank() }
            ?.let { reason ->
                RefundReasonBanner(reason = reason)
                UiSpacer(8.dp)
            }

        dappMetadata
            ?.takeUnless { it.isEmpty }
            ?.let {
                DappRequestBanner(metadata = it)
                UiSpacer(8.dp)
            }

        tokenContent()

        UiSpacer(8.dp)

        Column(
            modifier =
                Modifier.background(
                        color = Theme.v2.colors.backgrounds.disabled,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = Theme.v2.colors.border.light,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            if (transactionHash.isNotEmpty()) {
                TxDetails(
                    title = stringResource(R.string.tx_overview_screen_tx_hash),
                    hash = transactionHash,
                    link = transactionLink,
                    modifier = Modifier.padding(vertical = 12.dp),
                    onTxHashCopied = { tx ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.tx_done_address_copied, tx)
                            )
                        }
                    },
                )

                VerifyCardDivider(size = 1.dp)
            }

            AnimatedVisibility(isTransactionDetailVisible.not()) {
                Details(
                    title = stringResource(R.string.tx_done_transaction_details),
                    modifier =
                        Modifier.clickable(onClick = { onTransactionDetailVisibleChange(true) })
                            .padding(vertical = 12.dp),
                    titleColor = Theme.v2.colors.text.secondary,
                    content = {
                        UiIcon(
                            R.drawable.ic_caret_right,
                            modifier =
                                Modifier.weight(1f).wrapContentSize(align = Alignment.CenterEnd),
                            size = 16.dp,
                        )
                    },
                )
            }

            AnimatedVisibility(isTransactionDetailVisible) { detailContent() }
        }
    }
}

@Composable
private fun TransactionPending(modifier: Modifier = Modifier) {
    RiveAnimation(animation = R.raw.riv_transaction_pending, modifier = modifier.fillMaxWidth())
}

// Groups statuses that share a hero visual so the AnimatedContent only animates on a real visual
// change: 0 = loading (pending), 1 = error (failed/refunded), 2 = success (broadcasted/signed/
// confirmed).
private fun statusHeroKey(status: TransactionStatus): Int =
    when {
        status == TransactionStatus.Pending -> 0
        status is TransactionStatus.Failed || status is TransactionStatus.Refunded -> 1
        else -> 2
    }

@Composable
internal fun TransactionStatusRow(status: TransactionStatus, modifier: Modifier = Modifier) {
    val (label, color) =
        when (status) {
            TransactionStatus.Confirmed ->
                stringResource(R.string.transaction_status_confirmed_label) to
                    Theme.v2.colors.alerts.success

            is TransactionStatus.Failed ->
                stringResource(R.string.transaction_status_failed_label) to
                    Theme.v2.colors.alerts.error

            is TransactionStatus.Refunded ->
                stringResource(R.string.transaction_status_refunded_label) to
                    Theme.v2.colors.alerts.warning

            TransactionStatus.Signed ->
                stringResource(R.string.transaction_status_signed_label) to
                    Theme.v2.colors.alerts.success

            TransactionStatus.Broadcasted,
            TransactionStatus.Pending ->
                stringResource(R.string.transaction_status_in_progress_label) to
                    Theme.v2.colors.text.secondary
        }
    Details(
        title = stringResource(R.string.transaction_history_detail_status),
        modifier = modifier.padding(vertical = 12.dp),
    ) {
        Text(text = label, style = Theme.brockmann.body.s.medium, color = color)
    }
}

@Preview
@Composable
private fun SuccessTransactionPreview() {
    Scaffold {
        SuccessTransaction(
            modifier = Modifier.padding(it),
            tokenContent = {
                VsOverviewToken(
                    header = "token header",
                    valuedToken = ValuedToken.Empty,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            transactionHash = "tx hash",
            transactionLink = "tx link",
            coroutineScope = rememberCoroutineScope(),
            snackbarHostState = remember { SnackbarHostState() },
            transactionStatus = TransactionStatus.Failed(UiText.Empty),
            context = LocalContext.current,
            isTransactionDetailVisible = true,
            onTransactionDetailVisibleChange = {},
            detailContent = {
                Column {
                    TransactionStatusRow(TransactionStatus.Failed(UiText.Empty))

                    VerifyCardDivider(size = 1.dp)

                    TextDetails(
                        title = stringResource(R.string.tx_overview_screen_tx_from),
                        subtitle = "tx.from",
                    )

                    VerifyCardDivider(size = 1.dp)

                    TextDetails(
                        title = stringResource(R.string.tx_overview_screen_tx_to),
                        subtitle = " tx.to",
                    )

                    VerifyCardDivider(size = 1.dp)

                    Details(
                        modifier = Modifier.padding(vertical = 12.dp),
                        title = stringResource(R.string.tx_overview_screen_tx_network),
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val chain = Chain.Ethereum

                            Image(
                                painter = painterResource(chain.logo),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
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

                    VerifyCardDivider(size = 1.dp)

                    UiSpacer(12.dp)

                    EstimatedNetworkFee(
                        tokenGas = "tx.networkFeeTokenValue",
                        fiatGas = "tx.networkFeeFiatValue",
                    )
                }
            },
        )
    }
}
