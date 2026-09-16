package com.vultisig.wallet.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.chains.helpers.SubstrateDappTransactionDecoder
import com.vultisig.wallet.data.chains.helpers.SubstrateDappTx
import com.vultisig.wallet.data.chains.helpers.SubstrateDappTxFieldKey
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.SubstrateSignerPayload
import com.vultisig.wallet.ui.theme.Theme

/**
 * Verify-screen card for a dApp's Substrate signer payload. The call bytes in the payload are what
 * gets signed, so the card lists every field that goes into the signed bytes — call data, nonce,
 * tip, era, versions, genesis and block hash — and keeps the raw JSON available so a co-signer is
 * never shown less than the initiator saw.
 */
@Composable
fun SignSubstrateDisplayView(
    tx: SubstrateDappTx,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var isExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Absolute.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.substrate_signer_payload),
                style = Theme.brockmann.button.medium.regular,
                color = Theme.v2.colors.text.tertiary,
            )

            IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(10.dp)) {
                UiIcon(
                    drawableResId = R.drawable.chevron,
                    tint = Theme.v2.colors.neutrals.n100,
                    size = 8.dp,
                    modifier = Modifier.graphicsLayer(rotationZ = if (isExpanded) 180f else 0f),
                )
            }
        }

        // Outside the collapsible section on purpose: a Balances call whose recipient and value
        // could not be read has no amount hero, so the warning has to reach a co-signer who never
        // expands the card.
        if (tx.isTransferUnreadable) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                UiIcon(
                    drawableResId = R.drawable.ic_triangle_alert,
                    tint = Theme.v2.colors.alerts.warning,
                    size = 16.dp,
                )
                Text(
                    text = stringResource(R.string.substrate_transfer_unreadable),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.alerts.warning,
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tx.fields.forEach { field ->
                    VerifyCardJsonDetails(
                        title = stringResource(field.key.labelRes),
                        subtitle = field.value,
                        modifier = rowModifier,
                    )
                }
                VerifyCardJsonDetails(
                    title = stringResource(R.string.raw_transaction_data),
                    subtitle = tx.rawJson,
                    modifier = rowModifier,
                )
            }
        }
    }
}

@get:StringRes
private val SubstrateDappTxFieldKey.labelRes: Int
    get() =
        when (this) {
            SubstrateDappTxFieldKey.CALL_DATA -> R.string.substrate_field_call_data
            SubstrateDappTxFieldKey.NONCE -> R.string.substrate_field_nonce
            SubstrateDappTxFieldKey.TIP -> R.string.substrate_field_tip
            SubstrateDappTxFieldKey.ERA -> R.string.substrate_field_era
            SubstrateDappTxFieldKey.SPEC_VERSION -> R.string.substrate_field_spec_version
            SubstrateDappTxFieldKey.TRANSACTION_VERSION ->
                R.string.substrate_field_transaction_version
            SubstrateDappTxFieldKey.GENESIS_HASH -> R.string.substrate_field_genesis_hash
            SubstrateDappTxFieldKey.BLOCK_HASH -> R.string.substrate_field_block_hash
        }

private val rowModifier: Modifier
    @Composable
    get() =
        Modifier.fillMaxWidth()
            .background(color = Theme.v2.colors.variables.bordersLight, shape = Theme.v2.radius.md)
            .padding(horizontal = 12.dp)

private const val PREVIEW_SIGNER_PAYLOAD_JSON =
    "{\"address\":\"15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5\"," +
        "\"blockHash\":\"0x1f5a9d2c1b8e7f6a5d4c3b2a19087f6e5d4c3b2a19087f6e5d4c3b2a19087f6e\"," +
        "\"blockNumber\":\"0x01312d00\",\"era\":\"0xf502\"," +
        "\"genesisHash\":\"0x91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3\"," +
        "\"method\":\"0x0700d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d\"," +
        "\"nonce\":\"0x00000047\",\"specVersion\":\"0x000f4ef8\"," +
        "\"tip\":\"0x0000000000000000000000000012d687\",\"transactionVersion\":\"0x0000001a\"," +
        "\"signedExtensions\":[],\"version\":4}"

@Preview
@Composable
private fun PreviewSignSubstrateDisplayView() {
    SignSubstrateDisplayView(
        tx =
            SubstrateDappTransactionDecoder.decode(
                payload =
                    requireNotNull(SubstrateSignerPayload.fromMemo(PREVIEW_SIGNER_PAYLOAD_JSON)),
                chain = Chain.Polkadot,
                decimals = 10,
                ticker = "DOT",
                ss58Encode = { _, _ -> "" },
            ),
        initiallyExpanded = true,
    )
}
