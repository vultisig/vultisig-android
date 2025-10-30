package com.vultisig.wallet.ui.screens.peer

import android.icu.text.MessageFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.usecases.tss.ParticipantName
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.components.ShowQrHelperBottomSheet
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.banners.Banner
import com.vultisig.wallet.ui.components.banners.BannerVariant
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.errors.ErrorUiModel
import com.vultisig.wallet.ui.components.errors.ErrorView
import com.vultisig.wallet.ui.components.errors.WarningView
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.components.topbar.VsTopAppBarAction
import com.vultisig.wallet.ui.components.util.dashedBorder
import com.vultisig.wallet.ui.models.peer.KeygenPeerDiscoveryViewModel
import com.vultisig.wallet.ui.models.peer.NetworkOption
import com.vultisig.wallet.ui.models.peer.PeerDiscoveryUiModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks
import com.vultisig.wallet.ui.utils.VsUriHandler
import com.vultisig.wallet.ui.utils.asString
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun KeygenPeerDiscoveryScreen(
    model: KeygenPeerDiscoveryViewModel = hiltViewModel(),
) {
    KeepScreenOn()

    val state by model.state.collectAsState()

    val context = LocalContext.current
    val uriHandler = VsUriHandler()

    val connectingToServer = state.connectingToServer
    val error = state.error
    val warning = state.warning
    when {
        error != null -> {
            Error(
                state = error,
                onTryAgainClick = model::tryAgain,
            )
        }

        warning != null -> {
            WarningView(
                title = warning.title.asString(),
                description = warning.description.asString(),
                onTryAgainClick = model::tryAgain,
            )
        }

        connectingToServer != null -> {
            ConnectingToServer(connectingToServer.isSuccess)
        }

        else -> {
            PeerDiscoveryScreen(
                state = state,
                onBackClick = model::back,
                onHelpClick = {
                    uriHandler.openUri(VsAuxiliaryLinks.CREATE_VAULT)
                },
                onShareQrClick = { model.shareQr(context) },
                onCloseHintClick = model::closeDevicesHint,
                onSwitchModeClick = model::switchMode,
                onDeviceClick = model::selectDevice,
                onNextClick = model::next,
                onDismissQrHelpModal = model::dismissQrHelpModal
            )
        }
    }
}

@Composable
internal fun PeerDiscoveryScreen(
    state: PeerDiscoveryUiModel,
    onBackClick: () -> Unit,
    onHelpClick: () -> Unit,
    onShareQrClick: () -> Unit,
    onCloseHintClick: () -> Unit,
    onSwitchModeClick: () -> Unit,
    onDeviceClick: (ParticipantName) -> Unit,
    onNextClick: () -> Unit,
    onDismissQrHelpModal: () -> Unit,
    showHelp: Boolean = true,
) {
    val selectedDevicesSize = state.selectedDevices.size + 1 // we always have our device
    val devicesSize = state.devices.size + 1
    val remainedDevicesSize = maxOf(1, state.minimumDevicesDisplayed - devicesSize)
    val totalDevicesSize = devicesSize + remainedDevicesSize
    val hasEnoughDevices = selectedDevicesSize >= state.minimumDevices

    val ordinalFormatter = remember { MessageFormat("{0,ordinal}") }

    var isExpanded by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.peer_discovery_topbar_title),
                iconLeft = R.drawable.ic_caret_left,
                onIconLeftClick = onBackClick,
                actions = {
                    if (showHelp) {
                        VsTopAppBarAction(
                            icon = R.drawable.ic_question_mark,
                            onClick = onHelpClick,
                        )
                    }

                    VsTopAppBarAction(
                        icon = R.drawable.ic_share,
                        onClick = onShareQrClick,
                    )
                },
            )
        },
        content = { contentPadding ->

            Box(
                modifier = Modifier
                    .padding(paddingValues = contentPadding)
            ) {
                if (state.showQrHelpModal) {
                    ShowQrHelperBottomSheet(
                        onDismiss = onDismissQrHelpModal
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    QrCodeContainer(
                        qrCode = state.qr,
                        modifier = Modifier
                            .padding(
                                vertical = 20.dp,
                            )
                            .fillMaxWidth(0.80f),
                        devicesSize = devicesSize,
                        onEnlargeImageClick = {
                            isExpanded = true
                        }
                    )

                    AnimatedVisibility(
                        visible = state.showDevicesHint,
                    ) {
                        Column {
                            Banner(
                                text = stringResource(R.string.peer_discovery_recommended_devices_hint),
                                variant = BannerVariant.Info,
                                onCloseClick = onCloseHintClick,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            UiSpacer(16.dp)
                        }
                    }

                    AnimatedVisibility(
                        visible = state.network == NetworkOption.Local
                    ) {
                        Column {
                            LocalModeHint()
                            UiSpacer(16.dp)
                        }
                    }

                    Text(
                        text = stringResource(
                            R.string.peer_discovery_devices_n_of_n,
                            selectedDevicesSize,
                            state.minimumDevicesDisplayed,
                        ),
                        textAlign = TextAlign.Start,
                        style = Theme.brockmann.headings.title2,
                        color = Theme.colors.text.primary,
                    )

                    UiSpacer(24.dp)

                    FlowRow(
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {

                        PeerDeviceItem(
                            title = state.localPartyId,
                            caption = stringResource(R.string.peer_discovery_this_device),
                            state = PeerDeviceState.ThisDevice,
                            modifier = Modifier
                                .weight(1f)
                                .animateContentSize()
                        )

                        state.devices.forEach { device ->
                            val nameParts = device.split("-")
                            val name = nameParts.take(nameParts.size - 1)
                                .joinToString(separator = "")

                            val suffix = nameParts.lastOrNull() ?: ""
                            PeerDeviceItem(
                                title = name,
                                caption = suffix,
                                state = if (device in state.selectedDevices)
                                    PeerDeviceState.Selected
                                else PeerDeviceState.NotSelected,
                                onClick = {
                                    onDeviceClick(device)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .animateContentSize()
                            )
                        }

                        repeat(times = remainedDevicesSize) { remindedItemIndex ->
                            val totalIndex = devicesSize + remindedItemIndex + 1
                            val isLastDevice = totalIndex == totalDevicesSize
                            val ordinalDeviceIndex = ordinalFormatter.format(arrayOf(totalIndex))

                            PeerDeviceItem(
                                title = stringResource(
                                    R.string.peer_discovery_scan_with_n_device,
                                    ordinalDeviceIndex
                                ),
                                caption = null,
                                state = PeerDeviceState.Waiting,
                                modifier = Modifier
                                    .weight(1f)
                                    .animateContentSize()
                            )

                            // Spacer to preserve 2-column layout spacing
                            if (isLastDevice && totalDevicesSize % 2 == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (isExpanded && state.qr != null) {
                    ExpandedQrOverlay(
                        qrCode = state.qr,
                        onDismiss = { isExpanded = false }
                    )
                }
            }
        },
        bottomBar = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(
                        vertical = 12.dp,
                        horizontal = 24.dp,
                    )
            ) {
                VsButton(
                    label = if (hasEnoughDevices) stringResource(R.string.peer_discovery_action_next_title)
                    else stringResource(R.string.peer_discovery_waiting_for_devices_action),
                    state = if (hasEnoughDevices)
                        VsButtonState.Enabled
                    else VsButtonState.Disabled,
                    onClick = onNextClick,
                    modifier = Modifier
                        .fillMaxWidth()
                )

                Text(
                    text = buildNetworkModeText(
                        network = state.network,
                        onSwitchModeClick = onSwitchModeClick
                    ),
                    color = Theme.colors.text.extraLight,
                    style = Theme.brockmann.supplementary.caption,
                    textAlign = TextAlign.Center
                )
            }
        }
    )

}

@Composable
private fun buildNetworkModeText(
    network: NetworkOption,
    onSwitchModeClick: () -> Unit,
) =
    when (network) {
        NetworkOption.Internet -> buildAnnotatedString {
            append(stringResource(R.string.peer_discovery_switch_network_mode_want_to_sign_privately))
            append(" ")
            withLink(
                link = LinkAnnotation.Clickable(
                    tag = "Switch to local mode",
                    linkInteractionListener = {
                        onSwitchModeClick()
                    },
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            textDecoration = TextDecoration.Underline,
                        )
                    )
                ),
            ) {
                append(stringResource(R.string.peer_discovery_switch_network_mode_switch_to_local))
            }
        }

        NetworkOption.Local -> buildAnnotatedString {
            withLink(
                link = LinkAnnotation.Clickable(
                    tag = "Switch to internet mode",
                    linkInteractionListener = {
                        onSwitchModeClick()
                    },
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = Theme.colors.text.primary,
                            textDecoration = TextDecoration.Underline,
                        )
                    )
                ),
            ) {
                append(stringResource(R.string.peer_discovery_switch_network_mode_switch_to_internet))
            }
        }
    }

@Composable
private fun QrCodeContainer(
    modifier: Modifier = Modifier,
    devicesSize: Int = 0,
    qrCode: BitmapPainter? = null,
    onEnlargeImageClick : () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            RiveAnimation(
                animation = R.raw.riv_qr_scanned,
                onInit = { riveAnimationView ->
                    if (devicesSize > 1)
                        riveAnimationView.fireState(
                            stateMachineName = "State Machine 1",
                            inputName = "isSucces"
                        )
                }
            )
            androidx.compose.animation.AnimatedVisibility(
                modifier = Modifier.padding(28.dp),
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

        if (qrCode != null) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .background(
                        Theme.colors.backgrounds.secondary,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Theme.colors.borders.normal,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onEnlargeImageClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                UiIcon(
                    drawableResId = R.drawable.enlarge,
                    size = 24.dp,
                    tint = Theme.colors.text.primary
                )
            }
        }
    }
}

@Composable
private fun ExpandedQrOverlay(
    qrCode: BitmapPainter,
    onDismiss: () -> Unit
) {
    val scale = remember { Animatable(0.8f) }
    val alpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, tween(300)) }
        launch { alpha.animateTo(1f, tween(300)) }
    }

    suspend fun close() {
        coroutineScope {
            launch {
                scale.animateTo(0.8f, tween(300))
            }
            launch {
                alpha.animateTo(0f, tween(300))
            }
        }
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha.value * 0.9f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                scope.launch { close() }
            },
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = {
                scope.launch { close() }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Image(
            painter = qrCode,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize(0.9f)
                .graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value,
                    alpha = alpha.value
                )
        )
    }
}
@Composable
private fun LocalModeHint() {
    val shape = RoundedCornerShape(12.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Theme.colors.backgrounds.secondary,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = Theme.colors.primary.accent4,
                shape = shape,
            )
            .padding(
                all = 16.dp,
            )
    ) {
        UiIcon(
            drawableResId = R.drawable.ic_cloud_off,
            size = 16.dp,
            tint = Theme.colors.primary.accent4,
        )

        Text(
            text = stringResource(R.string.peer_discovery_local_mode_hint),
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.light,
        )
    }
}

private enum class PeerDeviceState {
    Selected,
    NotSelected,
    Waiting,
    ThisDevice
}

@Composable
private fun PeerDeviceItem(
    title: String,
    caption: String?,
    state: PeerDeviceState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(16.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = when (state) {
                    PeerDeviceState.Selected, PeerDeviceState.ThisDevice ->
                        Theme.colors.backgrounds.success

                    PeerDeviceState.NotSelected -> Theme.colors.backgrounds.secondary
                    PeerDeviceState.Waiting -> Theme.colors.backgrounds.primary
                },
                shape = shape,
            )
            .then(
                when (state) {
                    PeerDeviceState.Selected -> Modifier.border(
                        width = 1.dp,
                        color = Theme.colors.alerts.success,
                        shape = shape,
                    )

                    PeerDeviceState.ThisDevice -> Modifier.border(
                        width = 1.dp,
                        color = Color(0x4013C89D),
                        shape = shape,
                    )

                    PeerDeviceState.Waiting -> Modifier.dashedBorder(
                        width = 1.dp,
                        color = Theme.colors.borders.normal,
                        cornerRadius = 16.dp,
                        dashLength = 4.dp,
                        intervalLength = 4.dp,
                    )

                    PeerDeviceState.NotSelected -> Modifier.border(
                        width = 1.dp,
                        color = Theme.colors.borders.light,
                        shape = shape,
                    )
                }
            )
            .padding(
                horizontal = 20.dp,
                vertical = 16.dp,
            )
            .clickable(onClick = onClick)
    ) {
        if (state == PeerDeviceState.Waiting) {
            RiveAnimation(
                animation = R.raw.riv_waiting_on_device,
                modifier = Modifier
                    .size(24.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
        ) {
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

        when (state) {
            PeerDeviceState.Selected -> {
                Icon(
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
            }

            PeerDeviceState.NotSelected -> {
                Spacer(
                    modifier = Modifier
                        .size(24.dp)
                        .border(
                            width = 1.dp,
                            color = Theme.colors.borders.light,
                            shape = CircleShape,
                        )
                )
            }

            else -> Unit
        }
    }
}

@Composable
internal fun ConnectingToServer(
    isSuccess: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.backgrounds.primary)
            .padding(all = 24.dp),
    ) {
        RiveAnimation(
            animation = R.raw.riv_connecting_with_server,
            modifier = Modifier
                .size(24.dp),
            onInit = {
                if (isSuccess) {
                    it.fireState("State Machine 1", "Succes")
                }
            }
        )

        UiSpacer(24.dp)

        Text(
            text = stringResource(R.string.keygen_connecting_with_server),
            style = Theme.brockmann.headings.title2,
            color = Theme.colors.text.primary,
            textAlign = TextAlign.Center,
        )

        UiSpacer(16.dp)

        Text(
            text = stringResource(R.string.keygen_connecting_with_server_take_a_minute),
            style = Theme.brockmann.body.s.medium,
            color = Theme.colors.text.light,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Error(
    state: ErrorUiModel,
    onTryAgainClick: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.backgrounds.primary)
            .padding(all = 24.dp),
    ) {
        ErrorView(
            title = state.title.asString(),
            description = state.description.asString(),
            onTryAgainClick = onTryAgainClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview
@Composable
private fun PeerDiscoveryScreenPreview() {
    PeerDiscoveryScreen(
        state = PeerDiscoveryUiModel(
            localPartyId = "Device",
            network = NetworkOption.Local,
        ),
        onBackClick = {},
        onHelpClick = {},
        onShareQrClick = {},
        onCloseHintClick = {},
        onSwitchModeClick = {},
        onDeviceClick = {},
        onNextClick = {},
        onDismissQrHelpModal = {}
    )
}