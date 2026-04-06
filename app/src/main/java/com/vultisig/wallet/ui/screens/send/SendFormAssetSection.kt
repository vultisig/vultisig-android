package com.vultisig.wallet.ui.screens.send

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.selectors.ChainSelector
import com.vultisig.wallet.ui.models.send.SendFormUiModel
import com.vultisig.wallet.ui.models.send.SendSections
import com.vultisig.wallet.ui.screens.swap.TokenChip
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun FoldableAssetWidget(
    state: SendFormUiModel,
    onExpandSection: (SendSections) -> Unit,
    onSelectNetworkRequest: () -> Unit,
    onNetworkDragCancel: () -> Unit,
    onNetworkDrag: (Offset) -> Unit,
    onNetworkDragStart: (Offset) -> Unit,
    onNetworkDragEnd: () -> Unit,
    onNetworkLongPressStarted: (Offset) -> Unit,
    onSelectTokenRequest: () -> Unit,
    onAssetDragCancel: () -> Unit,
    onAssetDrag: (Offset) -> Unit,
    onAssetDragStart: (Offset) -> Unit,
    onAssetDragEnd: () -> Unit,
    onAssetLongPressStarted: (Offset) -> Unit,
) {
    FoldableSection(
        expanded = state.expandedSection == SendSections.Asset,
        onToggle = { onExpandSection(SendSections.Asset) },
        complete = state.selectedCoin != null,
        title = stringResource(R.string.form_token_selection_asset),
        completeTitleContent = {
            Row(modifier = Modifier.weight(1f)) {
                val selectedToken = state.selectedCoin

                TokenLogo(
                    errorLogoModifier =
                        Modifier.size(16.dp).background(Theme.v2.colors.neutrals.n100),
                    logo = selectedToken?.tokenLogo ?: "",
                    title = selectedToken?.title ?: "",
                    modifier = Modifier.size(16.dp),
                )

                UiSpacer(4.dp)

                Text(
                    text = selectedToken?.title ?: "",
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 12.dp)
        ) {
            val chain = state.selectedCoin?.model?.address?.chain
            if (chain != null) {
                Box(modifier = Modifier.testTag("SendFormScreen.chainSelector")) {
                    ChainSelector(
                        title = stringResource(R.string.send_from_address),
                        chain = chain,
                        onClick = onSelectNetworkRequest,
                        onDragCancel = onNetworkDragCancel,
                        onDrag = onNetworkDrag,
                        onDragStart = onNetworkDragStart,
                        onDragEnd = onNetworkDragEnd,
                        onLongPressStarted = onNetworkLongPressStarted,
                    )
                }
            }

            UiSpacer(12.dp)

            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.testTag("SendFormScreen.tokenSelector")) {
                    TokenChip(
                        selectedToken = state.selectedCoin,
                        onSelectTokenClick = onSelectTokenRequest,
                        onDragCancel = onAssetDragCancel,
                        onDrag = onAssetDrag,
                        onDragStart = onAssetDragStart,
                        onDragEnd = onAssetDragEnd,
                        onLongPressStarted = onAssetLongPressStarted,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f),
                ) {
                    state.selectedCoin?.let { token ->
                        Text(
                            text =
                                stringResource(
                                    R.string.form_token_selection_balance,
                                    token.balance ?: "",
                                ),
                            color = Theme.v2.colors.text.secondary,
                            style = Theme.brockmann.body.s.medium,
                            textAlign = TextAlign.End,
                        )

                        UiSpacer(2.dp)

                        token.fiatValue?.let { fiatValue ->
                            Text(
                                text = fiatValue,
                                textAlign = TextAlign.End,
                                color = Theme.v2.colors.text.tertiary,
                                style = Theme.brockmann.supplementary.caption,
                            )
                        }
                    }
                }
            }
        }
    }
}
