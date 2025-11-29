package com.vultisig.wallet.ui.screens.transaction

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.screens.swap.VerifyCardDivider
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.launch

@Composable
internal fun TxDoneScaffold(
    showToolbar: Boolean = true,
    transactionHash: String,
    transactionLink: String,
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
                VsTopAppBar(
                    title = stringResource(R.string.tx_overview_screen_title),
                    onBackClick = onBack,
                )
            }
        },
        content = { contentPadding ->
            var isTransactionDetailVisible by remember {
                mutableStateOf(false)
            }

            Column(
                modifier = Modifier.Companion
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
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
                            alignment = Alignment.Companion.Center,
                            modifier = Modifier.Companion
                                .padding(horizontal = 48.dp)
                                .fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.tx_transaction_successful_screen_title),
                            textAlign = TextAlign.Companion.Center,
                            style = Theme.brockmann.body.l.medium
                                .copy(
                                    brush = Theme.v2.colors.gradients.primary,
                                ),
                            modifier = Modifier.Companion
                                .fillMaxWidth()
                                .align(Alignment.Companion.BottomCenter)
                                .padding(
                                    bottom = 48.dp,
                                ),
                        )
                    }
                }

                tokenContent()

                UiSpacer(8.dp)

                Column(
                    modifier = Modifier.Companion
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

        },
        bottomBar = {
            bottomBarContent()
        }
    )
}