package com.vultisig.wallet.presenter.keygen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.models.TssAction
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.presenter.common.QRCodeKeyGenImage
import com.vultisig.wallet.presenter.keygen.components.DeviceInfo
import com.vultisig.wallet.ui.components.DevicesOnSameNetworkHint
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.NetworkPrompts
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@Composable
internal fun KeygenPeerDiscovery(
    navController: NavHostController,
    viewModel: KeygenFlowViewModel,
) {
    val uriHandler = LocalUriHandler.current
    val link = stringResource(id = R.string.link_docs_create_vault)

    val selectionState = viewModel.selection.asFlow().collectAsState(initial = emptyList()).value
    val participants = viewModel.participants.asFlow().collectAsState(initial = emptyList()).value

    val keygenPayloadState = viewModel.keygenPayloadState.value

    val networkPromptOption = viewModel.networkOption.value

    val context = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        viewModel.initVault()
        val action = if (viewModel.initVault?.pubKeyECDSA?.isEmpty() == true)
            TssAction.KEYGEN
        else
            TssAction.ReShare
        viewModel.setData(action)
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopParticipantDiscovery()
        }
    }

    KeygenPeerDiscoveryScreen(
        navController = navController,
        selectionState = selectionState,
        participants = participants,
        keygenPayloadState = keygenPayloadState,
        networkPromptOption = networkPromptOption,
        onOpenHelp = {
            uriHandler.openUri(link)
        },
        onChangeNetwork = { viewModel.changeNetworkPromptOption(it, context) },
        onAddParticipant = { viewModel.addParticipant(it) },
        onRemoveParticipant = { viewModel.removeParticipant(it) },
        onStopParticipantDiscovery = {
            viewModel.stopParticipantDiscovery()
            viewModel.moveToState(KeygenFlowState.DEVICE_CONFIRMATION)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeygenPeerDiscoveryScreen(
    navController: NavHostController,
    selectionState: List<String>,
    participants: List<String>,
    keygenPayloadState: String,
    networkPromptOption: NetworkPromptOption,
    onOpenHelp: () -> Unit = {},
    onChangeNetwork: (NetworkPromptOption) -> Unit = {},
    onAddParticipant: (String) -> Unit = {},
    onRemoveParticipant: (String) -> Unit = {},
    onStopParticipantDiscovery: () -> Unit = {},
) {
    val textColor = Theme.colors.neutral0

    UiBarContainer(
        navController = navController,
        title = stringResource(R.string.keygen_peer_discovery_keygen),
        endIcon = R.drawable.question,
        onEndIconClick = onOpenHelp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = CenterHorizontally,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxSize(),
            ) {
                UiSpacer(size = 8.dp)

                if (selectionState.isNotEmpty() && selectionState.count() > 1) {
                    Text(
                        text = stringResource(
                            R.string.keygen_peer_descovery_of_vault,
                            Utils.getThreshold(selectionState.count()),
                            selectionState.count()
                        ),
                        color = textColor,
                        style = Theme.montserrat.subtitle2
                    )
                }

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

            MultiColorButton(
                text = stringResource(R.string.keygen_peer_discovery_continue),
                backgroundColor = Theme.colors.turquoise600Main,
                textColor = Theme.colors.oxfordBlue600Main,
                minHeight = MaterialTheme.dimens.minHeightButton,
                textStyle = Theme.montserrat.subtitle1,
                disabled = selectionState.size < 2,
                modifier = Modifier
                    .align(BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 16.dp,
                    ),
                onClick = onStopParticipantDiscovery,
            )
        }
    }
}

@Preview
@Composable
private fun KeygenPeerDiscoveryScreenPreview() {
    KeygenPeerDiscoveryScreen(
        navController = rememberNavController(),
        selectionState = listOf("1", "2"),
        participants = listOf("1", "2", "3"),
        keygenPayloadState = "",
        networkPromptOption = NetworkPromptOption.WIFI,
    )
}
