package com.vultisig.wallet.ui.screens.qbtc

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimAmountFormatter
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimBlockedReason
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimError
import com.vultisig.wallet.ui.components.CopyIcon
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.loader.VsSigningProgressIndicator
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.qbtc.QbtcClaimUiState
import com.vultisig.wallet.ui.models.qbtc.QbtcClaimUtxoUiModel
import com.vultisig.wallet.ui.models.qbtc.QbtcClaimViewModel
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun QbtcClaimScreen(viewModel: QbtcClaimViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    QbtcClaimScreen(
        state = state,
        isFastVault = viewModel.isFastVault(),
        onBackClick = viewModel::back,
        onToggle = viewModel::toggle,
        onConfirm = viewModel::confirm,
        onStartSecureVault = viewModel::startSecureVaultClaim,
        onRetry = viewModel::retry,
    )
}

@Composable
internal fun QbtcClaimScreen(
    state: QbtcClaimUiState,
    isFastVault: Boolean,
    onBackClick: () -> Unit,
    onToggle: (String) -> Unit,
    onConfirm: (password: String) -> Unit,
    onStartSecureVault: () -> Unit,
    onRetry: () -> Unit,
) {
    var passwordPrompt by remember { mutableStateOf(false) }

    if (passwordPrompt) {
        FastVaultPasswordSheet(
            onDismiss = { passwordPrompt = false },
            onConfirm = {
                passwordPrompt = false
                onConfirm(it)
            },
        )
    }

    V3Scaffold(
        title = null,
        onBackClick = onBackClick,
        applyGradientBackground = false,
        bottomBar = {
            if (state is QbtcClaimUiState.Selecting) {
                VsButton(
                    label = ctaLabel(state),
                    state = if (state.canConfirm) VsButtonState.Enabled else VsButtonState.Disabled,
                    onClick = { if (isFastVault) passwordPrompt = true else onStartSecureVault() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }
        },
    ) {
        when (state) {
            QbtcClaimUiState.Loading ->
                CenteredProgress(stringResource(R.string.qbtc_claim_loading))
            is QbtcClaimUiState.Blocked -> BlockedContent(state.reason)
            is QbtcClaimUiState.Selecting -> SelectingContent(state, onToggle)
            is QbtcClaimUiState.Pairing -> PairingContent(state)
            is QbtcClaimUiState.Signing ->
                ClaimSigningProgress(
                    label = stringResource(R.string.qbtc_claim_proving),
                    logoRes = R.drawable.qbtc,
                )
            is QbtcClaimUiState.Done -> DoneContent(state)
            is QbtcClaimUiState.Failed -> FailedContent(state.error, onRetry)
        }
    }
}

@Composable
private fun SelectingContent(state: QbtcClaimUiState.Selecting, onToggle: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    QbtcClaimHeroCard(state.totalEligibleSats)
                    ClaimTabHeader()
                    QbtcClaimDescription()
                }
                UiSpacer(size = 16.dp)
                Text(
                    text = stringResource(R.string.qbtc_claim_eligible_header),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.tertiary,
                )
            }
        }
        items(state.utxos, key = { it.key }) { utxo ->
            QbtcClaimUtxoRow(
                utxo = utxo,
                isSelected = utxo.key in state.selectedKeys,
                onToggle = { onToggle(utxo.key) },
            )
        }
    }
}

@Composable
private fun QbtcClaimHeroCard(totalEligibleSats: Long) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(QbtcHeroGlow.copy(alpha = 0.09f), QbtcHeroGlow.copy(alpha = 0f))
                    )
                )
                .border(1.dp, QbtcHeroBorder.copy(alpha = 0.17f), RoundedCornerShape(16.dp))
    ) {
        Image(
            painter = painterResource(R.drawable.qbtc_claim_hero),
            contentDescription = null,
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .width(200.78.dp)
                    .height(206.dp)
                    .offset(x = 31.78.dp, y = (-17).dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.qbtc_claim_hero_title),
                style = Theme.brockmann.body.l.medium,
                color = Theme.v2.colors.text.primary,
            )
            Text(
                text = QbtcClaimAmountFormatter.formatQbtc(totalEligibleSats),
                style = Theme.satoshi.price.title1,
                color = Theme.v2.colors.text.primary,
            )
        }
    }
}

@Composable
private fun ClaimTabHeader() {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(IntrinsicSize.Min),
    ) {
        Text(
            text = stringResource(R.string.qbtc_claim_tab),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
        Box(
            modifier =
                Modifier.fillMaxWidth().height(1.5.dp).background(Theme.v2.colors.primary.accent4)
        )
    }
}

@Composable
private fun QbtcClaimDescription() {
    val full = stringResource(R.string.qbtc_claim_description)
    val emphasis = stringResource(R.string.qbtc_claim_description_emphasis)
    val annotated = buildAnnotatedString {
        val idx = full.indexOf(emphasis)
        if (idx >= 0) {
            append(full.substring(0, idx))
            withStyle(SpanStyle(color = Theme.v2.colors.text.primary)) { append(emphasis) }
            append(full.substring(idx + emphasis.length))
        } else {
            append(full)
        }
    }
    Text(
        text = annotated,
        style = Theme.brockmann.supplementary.caption,
        color = Theme.v2.colors.text.secondary,
    )
}

@Composable
private fun QbtcClaimCheckbox(checked: Boolean) {
    val ringColor =
        if (checked) Theme.v2.colors.alerts.success
        else Theme.v2.colors.text.tertiary.copy(alpha = 0.6f)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(24.dp)
                .clip(CircleShape)
                .background(
                    if (checked) Theme.v2.colors.alerts.success.copy(alpha = 0.05f)
                    else Color.Transparent
                )
                .border(1.dp, ringColor, CircleShape),
    ) {
        if (checked) {
            Icon(
                painter = painterResource(R.drawable.qbtc_claim_check),
                contentDescription = null,
                tint = Theme.v2.colors.alerts.success,
                modifier = Modifier.size(16.87.dp),
            )
        }
    }
}

@Composable
private fun QbtcClaimUtxoRow(
    utxo: QbtcClaimUtxoUiModel,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.surface1)
                .clickable(onClick = onToggle)
                .padding(16.dp),
    ) {
        QbtcClaimCheckbox(checked = isSelected)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = utxo.shortId,
                style = Theme.brockmann.button.semibold.medium,
                color = Theme.v2.colors.text.primary,
            )
            Text(
                text =
                    utxo.subtitleConfirmations?.let {
                        stringResource(R.string.qbtc_claim_utxo_block_format, it)
                    } ?: stringResource(R.string.qbtc_claim_utxo_pending),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = utxo.qbtcAmount,
                style = Theme.brockmann.button.semibold.medium,
                color = Theme.v2.colors.text.primary,
            )
            Text(
                text = utxo.btcAmount,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
            )
        }
    }
}

private val QbtcHeroGlow = Color(0xFF5FBFFF)
private val QbtcHeroBorder = Color(0xFFB090F5)

@Composable
private fun DoneContent(state: QbtcClaimUiState.Done) {
    val uriHandler = LocalUriHandler.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().padding(top = 48.dp),
    ) {
        Text(
            text = stringResource(R.string.qbtc_claim_success_title),
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.text.primary,
        )
        Text(
            text = QbtcClaimAmountFormatter.formatQbtc(state.totalSats),
            style = Theme.satoshi.price.title1,
            color = Theme.v2.colors.primary.accent4,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                text = state.txHash,
                style = Theme.brockmann.body.s.regular,
                color = Theme.v2.colors.text.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
            CopyIcon(textToCopy = state.explorerUrl.ifEmpty { state.txHash })
        }
        if (state.explorerUrl.isNotEmpty()) {
            VsButton(
                label = stringResource(R.string.transaction_history_view_on_explorer),
                onClick = { uriHandler.openUri(state.explorerUrl) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun BlockedContent(reason: QbtcClaimBlockedReason) {
    val title =
        when (reason) {
            QbtcClaimBlockedReason.KillSwitchClosed ->
                stringResource(R.string.qbtc_claim_unavailable_title)
            is QbtcClaimBlockedReason.UnsupportedBtcAddress ->
                stringResource(R.string.qbtc_claim_unsupported_address_title)
            is QbtcClaimBlockedReason.UtxoFetchFailed ->
                stringResource(R.string.qbtc_claim_failed_to_load_title)
            QbtcClaimBlockedReason.NoUtxos -> stringResource(R.string.qbtc_claim_no_utxos_title)
        }
    val detail =
        when (reason) {
            QbtcClaimBlockedReason.KillSwitchClosed ->
                stringResource(R.string.qbtc_claim_unavailable_detail)
            is QbtcClaimBlockedReason.UnsupportedBtcAddress -> reason.detail
            is QbtcClaimBlockedReason.UtxoFetchFailed -> reason.message
            QbtcClaimBlockedReason.NoUtxos -> stringResource(R.string.qbtc_claim_no_utxos_detail)
        }
    CenteredMessage(title = title, detail = detail)
}

@Composable
private fun FailedContent(error: QbtcClaimError, onRetry: () -> Unit) {
    val message =
        when (error) {
            QbtcClaimError.INVALID_BTC_PUBLIC_KEY ->
                stringResource(R.string.qbtc_claim_error_invalid_btc_key)
            QbtcClaimError.PROOF_HASH_MISMATCH ->
                stringResource(R.string.qbtc_claim_error_proof_mismatch)
            QbtcClaimError.BROADCAST_UNAVAILABLE ->
                stringResource(R.string.qbtc_claim_error_broadcast_unavailable)
            QbtcClaimError.PAIRING_TIMEOUT ->
                stringResource(R.string.qbtc_claim_error_pairing_timeout)
            QbtcClaimError.GENERIC -> stringResource(R.string.qbtc_claim_failed)
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        UiSpacer(weight = 1f)
        Text(
            text = message,
            style = Theme.brockmann.body.m.medium,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
        )
        UiSpacer(weight = 1f)
        VsButton(
            label = stringResource(R.string.try_again),
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CenteredMessage(title: String, detail: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = title,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = detail,
                style = Theme.brockmann.body.s.regular,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CenteredProgress(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        VsSigningProgressIndicator(text = label)
    }
}

@Composable
private fun ClaimSigningProgress(label: String, @DrawableRes logoRes: Int) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(color = Theme.v2.colors.backgrounds.primary)
                .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UiSpacer(weight = 1f)

        Image(
            painter = painterResource(logoRes),
            contentDescription = null,
            modifier = Modifier.size(72.dp).clip(CircleShape),
        )

        UiSpacer(24.dp)

        RiveAnimation(animation = R.raw.riv_connecting_with_server, modifier = Modifier.size(24.dp))

        UiSpacer(16.dp)

        Text(
            text = label,
            color = Theme.v2.colors.text.primary,
            style = Theme.brockmann.headings.title2,
            textAlign = TextAlign.Center,
        )

        UiSpacer(weight = 1f)
    }
}

@Composable
private fun PairingContent(state: QbtcClaimUiState.Pairing) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.qbtc_claim_pair_instruction),
            style = Theme.brockmann.body.m.medium,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
        )
        val qr = state.qr
        if (qr != null) {
            Image(
                painter = qr,
                contentDescription = null,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Theme.v2.colors.text.primary),
            )
        } else {
            VsSigningProgressIndicator(text = stringResource(R.string.qbtc_claim_title))
        }
        state.joinedDevices.forEach { device ->
            Text(
                text = device,
                style = Theme.brockmann.body.s.regular,
                color = Theme.v2.colors.text.secondary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FastVaultPasswordSheet(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val passwordFieldState = remember { TextFieldState() }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val submit = { onConfirm(passwordFieldState.text.toString()) }

    V2BottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            UiSpacer(size = 32.dp)

            UiIcon(
                drawableResId = R.drawable.focus_lock,
                size = 32.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                tint = Theme.v2.colors.primary.accent4,
            )

            UiSpacer(size = 10.dp)

            Text(
                text = stringResource(R.string.qbtc_claim_password_title),
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.headings.title3,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            UiSpacer(12.dp)
            FadingHorizontalDivider()
            UiSpacer(size = 20.dp)

            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            VsTextInputField(
                textFieldState = passwordFieldState,
                hint = stringResource(R.string.backup_password_screen_enter_password),
                type =
                    VsTextInputFieldType.Password(
                        isVisible = isPasswordVisible,
                        onVisibilityClick = { isPasswordVisible = !isPasswordVisible },
                    ),
                focusRequester = focusRequester,
                imeAction = ImeAction.Go,
                onKeyboardAction = { submit() },
                invisibleIcon = R.drawable.eye_closed,
            )

            UiSpacer(size = 16.dp)

            VsButton(
                label = stringResource(R.string.qbtc_claim_title),
                onClick = submit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ctaLabel(state: QbtcClaimUiState.Selecting): String =
    if (state.isAllSelected) {
        stringResource(R.string.qbtc_claim_cta_all)
    } else {
        stringResource(R.string.qbtc_claim_cta_partial, state.selectedKeys.size, state.utxos.size)
    }
