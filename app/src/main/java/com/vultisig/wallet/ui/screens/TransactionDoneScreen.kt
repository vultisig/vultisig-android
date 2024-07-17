package com.vultisig.wallet.ui.screens

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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TransactionDoneScreen(
    navController: NavController,
    transactionHash: String,
    transactionLink: String,
) {
    UiBarContainer(
        navController = navController,
        title = stringResource(R.string.transaction_done_title)
    ) {
        TransactionDoneView(
            transactionHash = transactionHash,
            transactionLink = transactionLink,
            onComplete = navController::popBackStack,
        )
    }
}

@Composable
internal fun TransactionDoneView(
    transactionHash: String,
    transactionLink: String,
    onComplete: () -> Unit,
) {
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
                        //found
                        text = stringResource(R.string.transation_done_form_title),
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

                    val uriHandler = LocalUriHandler.current

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
                Text(
                    modifier = Modifier.align(Alignment.End),
                    text= stringResource(R.string.transaction_done_swap_progress),
                    color = Theme.colors.neutral0,
                    style = Theme.menlo.subtitle2,
                )
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

@Preview
@Composable
private fun TransactionDoneScreenPreview() {
    TransactionDoneScreen(
        navController = rememberNavController(),
        transactionHash = "0x1234567890",
        transactionLink = ""
    )
}