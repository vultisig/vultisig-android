package com.vultisig.wallet.ui.screens.peer

import android.icu.text.MessageFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.components.topbar.VsTopAppBarAction
import com.vultisig.wallet.ui.components.util.dashedBorder
import com.vultisig.wallet.ui.models.peer.PeerDiscoveryUiModel
import com.vultisig.wallet.ui.models.peer.PeerDiscoveryViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun PeerDiscoveryScreen(
    model: PeerDiscoveryViewModel = hiltViewModel(),
) {
    KeepScreenOn()

    val state by model.state.collectAsState()

    val context = LocalContext.current

    PeerDiscoveryScreen(
        state = state,
        onBackClick = model::back,
        onHelpClick = model::openHelp,
        onShareQrClick = { model.shareQr(context) },
        onNextClick = model::next,
    )
}

@Composable
private fun PeerDiscoveryScreen(
    state: PeerDiscoveryUiModel,
    onBackClick: () -> Unit,
    onHelpClick: () -> Unit,
    onShareQrClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    val devicesSize = state.devices.size + 1 // we always have our device
    val hasEnoughDevices = devicesSize >= state.minimumDevices

    val ordinalFormatter = remember { MessageFormat("{0,ordinal}") }

    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.peer_discovery_topbar_title),
                iconLeft = R.drawable.ic_caret_left,
                onIconLeftClick = onBackClick,
                actions = {
                    VsTopAppBarAction(
                        icon = R.drawable.ic_question_mark,
                        onClick = onHelpClick,
                    )

                    VsTopAppBarAction(
                        icon = R.drawable.ic_share,
                        onClick = onShareQrClick,
                    )
                },
            )
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(
                        horizontal = 24.dp,
                    ),
            ) {

                // TODO qr_scanned.riv
                QrCodeContainer(
                    qrCode = state.qr,
                    modifier = Modifier
                        .padding(
                            vertical = 36.dp,
                            horizontal = 46.dp,
                        )
                        .fillMaxWidth()
                )

                Text(
                    text = stringResource(
                        R.string.peer_discovery_devices_n_of_n,
                        devicesSize,
                        state.minimumDevicesDisplayed,
                    ),
                    style = Theme.brockmann.headings.title2,
                    color = Theme.colors.text.primary,
                )

                UiSpacer(24.dp)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        PeerDeviceItem(
                            title = state.localPartyId,
                            caption = stringResource(R.string.peer_discovery_this_device),
                            hasDevice = true,
                            modifier = Modifier
                                .animateItem()
                        )
                    }

                    items(state.devices) { device ->
                        PeerDeviceItem(
                            title = device,
                            caption = null,
                            hasDevice = true,
                            modifier = Modifier
                                .animateItem()
                        )
                    }

                    items(
                        count = maxOf(1, state.minimumDevicesDisplayed - devicesSize)
                    ) { index ->
                        val ordinalDeviceIndex = ordinalFormatter
                            .format(arrayOf(devicesSize + index + 1))

                        PeerDeviceItem(
                            title = stringResource(
                                R.string.peer_discovery_scan_with_n_device,
                                ordinalDeviceIndex
                            ),
                            caption = if (hasEnoughDevices)
                                stringResource(R.string.peer_discovery_optional_device_caption) else null,
                            hasDevice = false,
                            modifier = Modifier
                                .animateItem()
                        )
                    }
                }
            }
        },
        bottomBar = {
            VsButton(
                label = if (hasEnoughDevices) stringResource(R.string.peer_discovery_action_next_title)
                else stringResource(R.string.peer_discovery_waiting_for_devices_action),
                state = if (hasEnoughDevices)
                    VsButtonState.Enabled
                else VsButtonState.Disabled,
                onClick = onNextClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 12.dp,
                        horizontal = 24.dp,
                    )
            )
        }
    )

}

@Composable
private fun QrCodeContainer(
    qrCode: BitmapPainter? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .background(
                color = Theme.colors.backgrounds.secondary,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = shape,
            )
            .aspectRatio(1f)
            .padding(28.dp)
    ) {
        AnimatedVisibility(
            visible = qrCode != null,
            enter = fadeIn(),
        ) {
            if (qrCode != null) {
                Image(
                    painter = qrCode,
                    contentDescription = "QR",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PeerDeviceItem(
    title: String,
    caption: String?,
    hasDevice: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (hasDevice)
                    Theme.colors.backgrounds.success
                else
                    Theme.colors.backgrounds.primary,
                shape = shape,
            )
            .then(
                if (hasDevice) {
                    Modifier.border(
                        width = 1.dp,
                        color = Theme.colors.alerts.success,
                        shape = shape,
                    )
                } else {
                    Modifier.dashedBorder(
                        width = 1.dp,
                        color = Theme.colors.borders.normal,
                        cornerRadius = 16.dp,
                        dashLength = 4.dp,
                        intervalLength = 4.dp,
                    )
                }
            )
            .padding(
                horizontal = 20.dp,
                vertical = 16.dp,
            )
    ) {
        if (hasDevice) {
            Icon(
                // TODO update checkmark icon
                painter = painterResource(R.drawable.check),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = Theme.colors.alerts.success,
                        shape = CircleShape,
                    )
                    .padding(4.dp)
            )
        } else {
            // rive doesn't work in preview mode.
            if (!LocalInspectionMode.current) {
                RiveAnimation(
                    animation = R.raw.waiting_on_device,
                    modifier = Modifier
                        .size(24.dp)
                )
            } else {
                UiSpacer(24.dp)
            }
        }

        UiSpacer(12.dp)

        val titleLines = if (caption == null) 2 else 1

        Text(
            text = title,
            style = Theme.brockmann.body.s.medium,
            color = Theme.colors.text.primary,
            overflow = TextOverflow.Ellipsis,
            maxLines = titleLines,
            minLines = titleLines,
        )

        if (caption != null) {
            UiSpacer(4.dp)

            Text(
                text = caption,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.colors.text.light,
                maxLines = 1,
            )
        }
    }
}

@Preview
@Composable
private fun PeerDiscoveryScreenPreview() {
    PeerDiscoveryScreen(
        state = PeerDiscoveryUiModel(localPartyId = "Device"),
        onBackClick = {},
        onHelpClick = {},
        onShareQrClick = {},
        onNextClick = {},
    )
}