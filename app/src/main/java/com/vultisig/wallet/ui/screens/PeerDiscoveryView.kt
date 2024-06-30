package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.QRCodeKeyGenImage
import com.vultisig.wallet.presenter.keygen.NetworkPromptOption
import com.vultisig.wallet.presenter.keygen.components.DeviceInfo
import com.vultisig.wallet.ui.components.NetworkPrompts
import com.vultisig.wallet.ui.components.UiScrollableColumn
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiCirclesLoader
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeerDiscoveryView(
    modifier: Modifier = Modifier,
    selectionState: List<String>,
    participants: List<String>,
    keygenPayloadState: String,
    networkPromptOption: NetworkPromptOption,
    onChangeNetwork: (NetworkPromptOption) -> Unit = {},
    onAddParticipant: (String) -> Unit = {},
    onRemoveParticipant: (String) -> Unit = {},
) {
    val textColor = Theme.colors.neutral0
    UiScrollableColumn(
        modifier,
        horizontalAlignment = CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.keygen_peer_discovery_pair_with_other_devices),
            color = textColor,
            style = Theme.montserrat.subtitle1
        )

        if (keygenPayloadState.isNotEmpty()) {
            QRCodeKeyGenImage(
                keygenPayloadState,
                modifier = Modifier
                    .padding(
                        top = 32.dp,
                        start = 32.dp,
                        end = 32.dp,
                        bottom = 20.dp
                    )
                    .fillMaxWidth(),
            )
        }

        NetworkPrompts(
            networkPromptOption = networkPromptOption,
            onChange = onChangeNetwork,
            modifier = Modifier.padding(horizontal = 32.dp),
        )


        if (participants.isNotEmpty()) {
            Text(
                text = stringResource(R.string.keygen_peer_discovery_select_the_pairing_devices),
                color = textColor,
                style = Theme.montserrat.subtitle3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            FlowRow(
                modifier = Modifier
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 8.dp,
                    alignment = CenterHorizontally
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
            UiSpacer(size = 24.dp)
            Text(
                text = stringResource(id = R.string.keygen_peer_discovery_looking_for_devices),
                color = textColor,
                style = Theme.montserrat.subtitle3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            UiCirclesLoader()
            UiSpacer(weight = 1f)
            NetworkPromptHint(networkPromptOption)
        }
    }
}

@Composable
private fun NetworkPromptHint(networkPromptOption: NetworkPromptOption) {
    UiIcon(
        drawableResId = when (networkPromptOption) {
            NetworkPromptOption.LOCAL -> R.drawable.wifi
            NetworkPromptOption.INTERNET -> R.drawable.baseline_signal_cellular_alt_24
        },
        size = 20.dp,
        tint = Theme.colors.turquoise600Main,
    )
    UiSpacer(size = 10.dp)
    Text(
        text = when (networkPromptOption) {
            NetworkPromptOption.LOCAL -> stringResource(R.string.peer_discovery_hint_connect_to_internet)
            NetworkPromptOption.INTERNET -> stringResource(R.string.peer_discovery_hint_connect_to_same_wifi)
        },
        style = Theme.menlo.body1,
        color = Theme.colors.neutral0,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(0.7f)
    )
}

@Preview
@Composable
private fun PeerDiscoveryPreview() {
    PeerDiscoveryView(
        selectionState = listOf("1", "2"),
        participants = listOf("1", "2", "3"),
        keygenPayloadState = "keygen payload",
        networkPromptOption = NetworkPromptOption.LOCAL,
        modifier = Modifier,
    )
}