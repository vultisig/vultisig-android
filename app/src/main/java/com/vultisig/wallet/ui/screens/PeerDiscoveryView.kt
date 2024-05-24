package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.QRCodeKeyGenImage
import com.vultisig.wallet.presenter.keygen.NetworkPromptOption
import com.vultisig.wallet.presenter.keygen.components.DeviceInfo
import com.vultisig.wallet.ui.components.DevicesOnSameNetworkHint
import com.vultisig.wallet.ui.components.NetworkPrompts
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeerDiscoveryView(
    selectionState: List<String>,
    participants: List<String>,
    keygenPayloadState: String,
    networkPromptOption: NetworkPromptOption,
    onChangeNetwork: (NetworkPromptOption) -> Unit = {},
    onAddParticipant: (String) -> Unit = {},
    onRemoveParticipant: (String) -> Unit = {},
) {
    val textColor = Theme.colors.neutral0

    UiSpacer(size = 8.dp)

    Text(
        text = stringResource(R.string.keygen_peer_discovery_pair_with_other_devices),
        color = textColor,
        style = Theme.montserrat.body3
    )

    if (keygenPayloadState.isNotEmpty()) {
        QRCodeKeyGenImage(
            keygenPayloadState,
            modifier = Modifier
                .padding(all = 32.dp)
                .fillMaxWidth(),
        )
    }

    NetworkPrompts(
        networkPromptOption = networkPromptOption,
        onChange = onChangeNetwork,
        modifier = Modifier.padding(horizontal = 12.dp),
    )

    UiSpacer(size = 24.dp)

    if (participants.isNotEmpty()) {
        FlowRow(
            modifier = Modifier
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(
                space = 8.dp,
                alignment = Alignment.CenterHorizontally
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            participants.forEach { participant ->
                val isSelected = selectionState.contains(participant)
                DeviceInfo(
                    R.drawable.ipad,
                    participant,
                    isSelected = isSelected
                ) { isChecked ->
                    if (isChecked) {
                        onAddParticipant(participant)
                    } else {
                        onRemoveParticipant(participant)
                    }
                }
            }
        }
    } else {
        Text(
            text = stringResource(R.string.keygen_peer_discovery_waiting_for_other_devices_to_connect),
            color = textColor,
            style = Theme.montserrat.body2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(
                    horizontal = 24.dp,
                    vertical = 24.dp,
                )
        )
    }

    UiSpacer(size = 24.dp)

    DevicesOnSameNetworkHint(
        title = stringResource(R.string.keygen_peer_discovery_desc1)
    )

    UiSpacer(size = 72.dp)
}

@Preview
@Composable
private fun PeerDiscoveryPreview() {
    PeerDiscoveryView(
        selectionState = listOf("1", "2"),
        participants = listOf("1", "2", "3"),
        keygenPayloadState = "",
        networkPromptOption = NetworkPromptOption.WIFI,
    )
}