package com.vultisig.wallet.ui.screens.peer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import app.rive.Fit
import app.rive.ViewModelSource
import app.rive.rememberViewModelInstance
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.usecases.tss.ParticipantName
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.components.ShowQrHelperBottomSheet
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.errors.ErrorUiModel
import com.vultisig.wallet.ui.components.errors.ErrorView
import com.vultisig.wallet.ui.components.errors.ErrorViewButtonUiModel
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.rive.rememberRiveResourceFile
import com.vultisig.wallet.ui.components.topbar.VsTopAppBarAction
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.peer.KeygenPeerDiscoveryViewModel
import com.vultisig.wallet.ui.models.peer.NetworkOption
import com.vultisig.wallet.ui.models.peer.PeerDiscoveryUiModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks
import com.vultisig.wallet.ui.utils.VsUriHandler
import com.vultisig.wallet.ui.utils.asString
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Gradient border that wraps the QR card (Figma: top #4879FD -> bottom #0D39B1). Not a theme token
// because this exact pairing is unique to the keygen QR frame.
private val QrFrameGradient =
    Brush.verticalGradient(colors = listOf(Color(0xFF4879FD), Color(0xFF0D39B1)))

// "∞" badge label for vaults that let the initiator add more devices than the threshold (4+).
private const val UNBOUNDED_DEVICES_LABEL = "∞"

@Composable
internal fun KeygenPeerDiscoveryScreen(model: KeygenPeerDiscoveryViewModel = hiltViewModel()) {
    KeepScreenOn()

    val state by model.state.collectAsState()

    val context = LocalContext.current
    val uriHandler = VsUriHandler()

    val connectingToServer = state.connectingToServer
    val error = state.error
    val warning = state.warning
    when {
        error != null -> {
            Error(state = error, onTryAgainClick = model::tryAgain)
        }

        warning != null -> {
            ErrorView(
                title = warning.title.asString(),
                description = warning.description.asString(),
                errorState = warning.errorState,
                rawError = warning.rawError,
                buttonUiModel =
                    ErrorViewButtonUiModel(
                        text = stringResource(R.string.try_again),
                        onClick = model::tryAgain,
                    ),
            )
        }

        connectingToServer != null -> {

            val riveFile = rememberRiveResourceFile(resId = R.raw.riv_keygen).value ?: return
            val vmi =
                rememberViewModelInstance(
                    file = riveFile,
                    source = ViewModelSource.Named("ViewModel").defaultInstance(),
                )

            var showRive by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(300)
                showRive = true
            }

            // Solid backdrop so the 300ms pre-Rive window renders the app background rather than
            // an empty/blank frame between the password screen and the connecting animation.
            Box(modifier = Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary)) {
                if (showRive) {
                    RiveAnimation(
                        file = riveFile,
                        viewModelInstance = vmi,
                        modifier = Modifier.fillMaxSize(),
                        fit = Fit.Cover(),
                    )
                }
            }
        }

        else -> {
            PeerDiscoveryScreen(
                state = state,
                onBackClick = model::back,
                onHelpClick = { uriHandler.openUri(VsAuxiliaryLinks.CREATE_VAULT) },
                onShareQrClick = { model.shareQr(context) },
                onSwitchModeClick = model::switchMode,
                onDeviceClick = model::selectDevice,
                onNextClick = model::next,
                onDismissQrHelpModal = model::dismissQrHelpModal,
                onResendNotification = {},
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
    onSwitchModeClick: () -> Unit,
    onDeviceClick: (ParticipantName) -> Unit,
    onResendNotification: () -> Unit,
    onNextClick: () -> Unit,
    onDismissQrHelpModal: () -> Unit,
    showHelp: Boolean = true,
    showShare: Boolean = true,
    showNetworkSwitch: Boolean = true,
    showNext: Boolean = true,
) {
    val selectedDevicesSize = state.selectedDevices.size + 1 // we always have our device
    val hasEnoughDevices = selectedDevicesSize >= state.minimumDevices

    // Per-card badge cap: "∞" when the initiator may add more than the threshold (4+ vaults),
    // otherwise the fixed device count this vault is being created with.
    val deviceBadgeMax =
        if (state.allowsMoreDevices) UNBOUNDED_DEVICES_LABEL
        else state.minimumDevicesDisplayed.toString()

    var isExpanded by remember { mutableStateOf(false) }
    V3Scaffold(
        title = stringResource(R.string.peer_discovery_topbar_title),
        applyGradientBackground = true,
        onBackClick = onBackClick,
        actions = {
            if (showHelp) {
                VsTopAppBarAction(icon = R.drawable.ic_question_mark, onClick = onHelpClick)

                UiSpacer(size = 8.dp)
            }

            if (showShare) {
                VsTopAppBarAction(icon = R.drawable.ic_share, onClick = onShareQrClick)
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Theme.v2.colors.variables.bordersExtraLight,
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    // 2/2 and 3/3 vaults auto-start keygen once the peer threshold is met (see
                    // KeygenPeerDiscoveryViewModel.observeAutoStartKeygen), so the Continue button
                    // is
                    // hidden entirely. For 4+ device vaults the initiator still picks which peers
                    // to
                    // commit, so the button stays visible. showNext = false hides it regardless,
                    // for
                    // flows that always auto-advance (QBTC claim co-sign).
                    if (showNext && (state.deviceCount == null || state.deviceCount > 3)) {
                        VsButton(
                            // Until the peer threshold is met the m-of-n is not yet known (e.g.
                            // keysign reuses this screen with no selected peers, which would
                            // otherwise misstate the vault as "Continue (1-of-1)"), so keep the
                            // neutral waiting prompt and only show the m-of-n once enabled.
                            label =
                                if (hasEnoughDevices)
                                    stringResource(
                                        R.string.peer_discovery_action_continue_m_of_n,
                                        Utils.getThreshold(selectedDevicesSize),
                                        selectedDevicesSize,
                                    )
                                else
                                    stringResource(
                                        R.string.peer_discovery_waiting_for_devices_action
                                    ),
                            state =
                                if (hasEnoughDevices) VsButtonState.Enabled
                                else VsButtonState.Disabled,
                            onClick = onNextClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (showNetworkSwitch) {
                        Text(
                            text =
                                buildNetworkModeText(
                                    network = state.network,
                                    onSwitchModeClick = onSwitchModeClick,
                                ),
                            color = Theme.v2.colors.text.tertiary,
                            style = Theme.brockmann.supplementary.caption,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
        content = {
            Box {
                if (state.showQrHelpModal) {
                    ShowQrHelperBottomSheet(onDismiss = onDismissQrHelpModal)
                }

                Column(
                    modifier =
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 12.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    QrCodeContainer(
                        qrCode = state.qr,
                        onClick = { if (state.qr != null) isExpanded = true },
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        WaitingForDevicesHeader()

                        if (state.network == NetworkOption.Local) {
                            LocalModeHint()
                        }

                        if (state.enableNotification) {
                            ResendNotificationButton(
                                remainingSeconds = state.resendCooldownSeconds,
                                onClick = onResendNotification,
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DevicePeerCard(
                                name = state.localPartyId.toDeviceDisplayName(),
                                status = stringResource(R.string.peer_discovery_this_device),
                                index = 1,
                                maxLabel = deviceBadgeMax,
                            )

                            state.devices.forEachIndexed { index, device ->
                                val isSelected = device in state.selectedDevices
                                DevicePeerCard(
                                    name = device.toDeviceDisplayName(),
                                    // Selected peers read "Connected"; unselected ones show their
                                    // raw id (iOS PeerCell parity) — the only cue distinguishing
                                    // two devices of the same model.
                                    status =
                                        if (isSelected)
                                            stringResource(R.string.peer_discovery_status_connected)
                                        else device,
                                    index = index + 2,
                                    maxLabel = deviceBadgeMax,
                                    selected = isSelected,
                                    onClick = { onDeviceClick(device) },
                                )
                            }
                        }
                    }
                }

                if (isExpanded && state.qr != null) {
                    ExpandedQrOverlay(qrCode = state.qr, onDismiss = { isExpanded = false })
                }
            }
        },
    )
}

@Composable
private fun ResendNotificationButton(remainingSeconds: Int, onClick: () -> Unit) {
    val isEnabled = remainingSeconds == 0
    val shape = RoundedCornerShape(12.dp)
    val contentColor = if (isEnabled) Theme.v2.colors.alerts.info else Theme.v2.colors.text.tertiary
    val bgColor =
        if (isEnabled) Theme.v2.colors.backgrounds.disabled
        else Theme.v2.colors.backgrounds.tertiary
    val borderColor =
        if (isEnabled) Theme.v2.colors.border.light else Theme.v2.colors.border.extraLight

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.clip(shape = shape)
                .then(if (isEnabled) Modifier.clickOnce(onClick = onClick) else Modifier)
                .background(color = bgColor, shape = shape)
                .border(color = borderColor, width = 1.dp, shape = shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        UiIcon(drawableResId = R.drawable.ic_bell, tint = contentColor, size = 16.dp)

        UiSpacer(size = 6.dp)

        Text(
            text =
                if (isEnabled) stringResource(R.string.resend_notification)
                else
                    pluralStringResource(
                        R.plurals.resend_notification_in_seconds,
                        remainingSeconds,
                        remainingSeconds,
                    ),
            style = Theme.brockmann.supplementary.caption,
            color = contentColor,
        )
    }
}

@Composable
private fun buildNetworkModeText(network: NetworkOption, onSwitchModeClick: () -> Unit) =
    when (network) {
        NetworkOption.Internet ->
            buildAnnotatedString {
                append(
                    stringResource(
                        R.string.peer_discovery_switch_network_mode_want_to_sign_privately
                    )
                )
                append(" ")
                withLink(
                    link =
                        LinkAnnotation.Clickable(
                            tag = "Switch to local mode",
                            linkInteractionListener = { onSwitchModeClick() },
                            styles =
                                TextLinkStyles(
                                    style =
                                        SpanStyle(
                                            color = Theme.v2.colors.text.secondary,
                                            textDecoration = TextDecoration.Underline,
                                        )
                                ),
                        )
                ) {
                    append(
                        stringResource(R.string.peer_discovery_switch_network_mode_switch_to_local)
                    )
                }
            }

        NetworkOption.Local ->
            buildAnnotatedString {
                withLink(
                    link =
                        LinkAnnotation.Clickable(
                            tag = "Switch to internet mode",
                            linkInteractionListener = { onSwitchModeClick() },
                            styles =
                                TextLinkStyles(
                                    style =
                                        SpanStyle(
                                            color = Theme.v2.colors.text.primary,
                                            textDecoration = TextDecoration.Underline,
                                        )
                                ),
                        )
                ) {
                    append(
                        stringResource(
                            R.string.peer_discovery_switch_network_mode_switch_to_internet
                        )
                    )
                }
            }
    }

@Composable
private fun QrCodeContainer(
    qrCode: BitmapPainter?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outerShape = RoundedCornerShape(24.75.dp)
    val innerShape = RoundedCornerShape(18.56.dp)

    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier.clip(outerShape)
                    .background(brush = QrFrameGradient, shape = outerShape)
                    .clickable(onClick = onClick)
                    .padding(6.19.dp)
        ) {
            Box(
                modifier =
                    Modifier.background(
                            color = Theme.v2.colors.backgrounds.surface1,
                            shape = innerShape,
                        )
                        .border(
                            width = 0.77.dp,
                            color = Theme.v2.colors.border.normal,
                            shape = innerShape,
                        )
                        .padding(12.38.dp)
            ) {
                // Reserve the QR footprint so the framed card keeps its size while the bitmap is
                // still being generated, then fade the QR in once it is ready.
                QrCodeImage(qrCode = qrCode)
            }
        }

        // Anchor the expand affordance to the card's top-right corner so it reads as part of the
        // QR instead of a stray, detached button. Centering it on the corner keeps the control
        // within the card's frame padding and clear of the top-right finder pattern (the bitmap is
        // generated with a zero-module margin), so the code stays reliably scannable.
        if (qrCode != null) {
            ExpandQrButton(
                onClick = onClick,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 24.dp, y = (-24).dp),
            )
        }
    }
}

/**
 * Reserves the QR footprint and fades the generated QR bitmap in once it is ready.
 *
 * Kept as a standalone composable so [AnimatedVisibility] resolves to the non-scoped overload
 * instead of the enclosing [Column]'s [ColumnScope] extension.
 *
 * @param qrCode the QR painter, or null while it is still being generated.
 */
@Composable
private fun QrCodeImage(qrCode: BitmapPainter?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(185.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = qrCode != null, enter = fadeIn()) {
            if (qrCode != null) {
                Image(
                    painter = qrCode,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Circular icon button anchored to the QR card's top-right corner that opens the full-screen QR
 * overlay.
 *
 * Exposes a labelled [Role.Button] semantics node and reserves at least a 48.dp touch target so it
 * stays usable with TalkBack and easy to hit.
 *
 * @param onClick invoked when the expand affordance is tapped.
 */
@Composable
private fun ExpandQrButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(color = Theme.v2.colors.backgrounds.surface1, shape = CircleShape)
                .border(width = 1.dp, color = Theme.v2.colors.border.normal, shape = CircleShape)
                .clickable(onClick = onClick, role = Role.Button)
                .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        UiIcon(
            drawableResId = R.drawable.ic_expand,
            size = 16.dp,
            tint = Theme.v2.colors.text.primary,
            contentDescription = stringResource(R.string.peer_discovery_expand_qr),
        )
    }
}

@Composable
private fun WaitingForDevicesHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.peer_discovery_waiting_title),
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
        )

        AnimatedWaitingDots()
    }
}

@Composable
private fun AnimatedWaitingDots() {
    val transition = rememberInfiniteTransition(label = "waitingDots")
    // Baseline opacities from Figma (75% / 50% / 25%); each dot pulses between a dim floor and its
    // baseline, staggered so the row reads as a left-to-right wave.
    val baseAlphas = listOf(0.75f, 0.5f, 0.25f)

    Row {
        baseAlphas.forEachIndexed { index, baseAlpha ->
            val alpha by
                transition.animateFloat(
                    initialValue = baseAlpha * 0.3f,
                    targetValue = baseAlpha,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 600, delayMillis = index * 200),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "waitingDot$index",
                )

            Text(
                text = ".",
                style = Theme.brockmann.headings.title3,
                color = Theme.v2.colors.text.primary.copy(alpha = alpha),
            )
        }
    }
}

@Composable
private fun DevicePeerCard(
    name: String,
    status: String,
    index: Int,
    maxLabel: String,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    val accentColor = if (selected) Theme.v2.colors.alerts.success else Theme.v2.colors.border.light

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = Theme.v2.colors.backgrounds.state.neutral, shape = shape)
                .border(width = 1.dp, color = accentColor, shape = shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(16.dp),
    ) {
        DeviceTypeIcon(active = selected)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )

            Text(
                text = status,
                style = Theme.brockmann.supplementary.caption,
                color =
                    if (selected) Theme.v2.colors.alerts.success
                    else Theme.v2.colors.text.secondary,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }

        DeviceCountBadge(index = index, maxLabel = maxLabel)
    }
}

@Composable
private fun DeviceTypeIcon(active: Boolean) {
    val color = if (active) Theme.v2.colors.alerts.success else Theme.v2.colors.text.secondary

    Box(
        modifier =
            Modifier.size(32.dp)
                .clip(CircleShape)
                .background(Theme.v2.colors.backgrounds.background)
                .border(width = 1.5.dp, color = color, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        UiIcon(drawableResId = R.drawable.device_backup, size = 16.dp, tint = color)
    }
}

@Composable
private fun DeviceCountBadge(index: Int, maxLabel: String) {
    Box(
        modifier =
            Modifier.background(
                    color = Theme.v2.colors.backgrounds.tertiary_2,
                    shape = RoundedCornerShape(99.dp),
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text =
                stringResource(
                    R.string.peer_discovery_device_count_badge,
                    index.toString(),
                    maxLabel,
                ),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.secondary,
            maxLines = 1,
        )
    }
}

// Vultisig device names are "<model>-<id-suffix>" (see Utils.deviceName). Strip the trailing id so
// the card shows a clean, human-readable device name as in the design.
private fun String.toDeviceDisplayName(): String = substringBeforeLast("-").ifBlank { this }

@Composable
private fun ExpandedQrOverlay(qrCode: BitmapPainter, onDismiss: () -> Unit) {
    val scale = remember { Animatable(0.8f) }
    val alpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, tween(300)) }
        launch { alpha.animateTo(1f, tween(300)) }
    }

    suspend fun close() {
        coroutineScope {
            launch { scale.animateTo(0.8f, tween(300)) }
            launch { alpha.animateTo(0f, tween(300)) }
        }
        onDismiss()
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = alpha.value * 0.9f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    scope.launch { close() }
                },
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = { scope.launch { close() } },
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .padding(16.dp)
                    .graphicsLayer(scaleX = scale.value, scaleY = scale.value, alpha = alpha.value),
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }

        Image(
            painter = qrCode,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier.fillMaxSize()
                    .graphicsLayer(scaleX = scale.value, scaleY = scale.value, alpha = alpha.value),
        )
    }
}

@Composable
private fun LocalModeHint() {
    val shape = RoundedCornerShape(12.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier.fillMaxWidth()
                .background(color = Theme.v2.colors.backgrounds.secondary, shape = shape)
                .border(width = 1.dp, color = Theme.v2.colors.primary.accent4, shape = shape)
                .padding(all = 16.dp),
    ) {
        UiIcon(
            drawableResId = R.drawable.ic_cloud_off,
            size = 16.dp,
            tint = Theme.v2.colors.primary.accent4,
        )

        Text(
            text = stringResource(R.string.peer_discovery_local_mode_hint),
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.secondary,
        )
    }
}

@Composable
internal fun ConnectingToServer(isSuccess: Boolean) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier.fillMaxSize()
                .background(Theme.v2.colors.backgrounds.primary)
                .padding(all = 24.dp),
    ) {
        RiveAnimation(
            animation = R.raw.riv_connecting_with_server,
            modifier = Modifier.size(24.dp),
            onInit = {
                if (isSuccess) {
                    it.fireState("State Machine 1", "Succes")
                }
            },
        )

        UiSpacer(24.dp)

        Text(
            text = stringResource(R.string.keygen_connecting_with_server),
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
        )

        UiSpacer(12.dp)

        Text(
            text = stringResource(R.string.keygen_connecting_with_server_take_a_second),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Error(state: ErrorUiModel, onTryAgainClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier.fillMaxSize()
                .background(Theme.v2.colors.backgrounds.primary)
                .padding(all = 24.dp),
    ) {
        ErrorView(
            title = state.title.asString(),
            description = state.description.asString(),
            errorState = state.errorState,
            rawError = state.rawError,
            modifier = Modifier.fillMaxWidth(),
            buttonUiModel =
                ErrorViewButtonUiModel(
                    text = stringResource(R.string.try_again),
                    onClick = onTryAgainClick,
                ),
        )
    }
}

@Preview
@Composable
private fun PeerDiscoveryScreenPreview() {
    PeerDiscoveryScreen(
        state =
            PeerDiscoveryUiModel(
                localPartyId = "iPhone-A1B",
                network = NetworkOption.Internet,
                devices = listOf("MacBook-C2D", "iPhone-E3F"),
                selectedDevices = listOf("MacBook-C2D", "iPhone-E3F"),
                minimumDevices = 4,
                minimumDevicesDisplayed = 4,
                deviceCount = 4,
                allowsMoreDevices = true,
                enableNotification = false,
            ),
        onResendNotification = {},
        onBackClick = {},
        onHelpClick = {},
        onShareQrClick = {},
        onSwitchModeClick = {},
        onDeviceClick = {},
        onNextClick = {},
        onDismissQrHelpModal = {},
    )
}
