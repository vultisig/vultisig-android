package com.vultisig.wallet.ui.screens.qbtc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimAmountFormatter
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.models.keysign.TransactionStatus
import com.vultisig.wallet.ui.screens.transaction.TransactionStatusRow
import com.vultisig.wallet.ui.screens.transaction.TxDoneScaffold
import com.vultisig.wallet.ui.theme.Theme

/**
 * The shared QBTC claim "done" screen. Rendered both by the initiator ([QbtcClaimScreen]) and the
 * co-signing peer (the join-keysign flow) so both devices land on the same success screen with the
 * on-chain tx hash once the claim broadcasts.
 */
@Composable
internal fun QbtcClaimDoneContent(
    txHash: String,
    explorerUrl: String,
    totalSats: Long,
    onComplete: () -> Unit,
) {
    var detailsVisible by remember { mutableStateOf(false) }
    TxDoneScaffold(
        transactionHash = txHash,
        transactionLink = explorerUrl,
        transactionStatus = TransactionStatus.Confirmed,
        isTransactionDetailVisible = detailsVisible,
        onTransactionDetailVisibleChange = { detailsVisible = it },
        onBack = onComplete,
        successTitle = stringResource(R.string.qbtc_claim_successful),
        tokenContent = { ClaimedAmountCard(totalSats) },
        detailContent = { TransactionStatusRow(TransactionStatus.Confirmed) },
        bottomBarContent = {
            VsButton(
                label = stringResource(R.string.transaction_done_complete),
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            )
        },
    )
}

@Composable
private fun ClaimedAmountCard(totalSats: Long) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier.fillMaxWidth()
                .background(color = Theme.v2.colors.backgrounds.secondary, shape = shape)
                .border(width = 1.dp, color = Theme.v2.colors.border.light, shape = shape)
                .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.qbtc),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = QbtcClaimAmountFormatter.formatQbtc(totalSats),
            style = Theme.satoshi.price.title1,
            color = Theme.v2.colors.text.primary,
        )
    }
}
