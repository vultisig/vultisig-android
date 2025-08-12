package com.vultisig.wallet.ui.screens.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Tokens
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.isLayer2
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.models.swapAssetName
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsCheckField
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsHoldableButton
import com.vultisig.wallet.ui.components.launchBiometricPrompt
import com.vultisig.wallet.ui.components.securityscanner.SecurityScannerBadget
import com.vultisig.wallet.ui.components.securityscanner.SecurityScannerBottomSheet
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.TransactionScanStatus
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.models.swap.VerifySwapUiModel
import com.vultisig.wallet.ui.models.swap.VerifySwapViewModel
import com.vultisig.wallet.ui.screens.send.EstimatedNetworkFee
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun VerifySwapScreen(
    viewModel: VerifySwapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val promptTitle = stringResource(R.string.biometry_keysign_login_button)

    val authorize: () -> Unit = remember(context) {
        {
            context.launchBiometricPrompt(
                promptTitle = promptTitle,
                onAuthorizationSuccess = viewModel::authFastSign,
            )
        }
    }

    val errorText = state.errorText
    if (errorText != null) {
        UiAlertDialog(
            title = stringResource(id = R.string.dialog_default_error_title),
            text = errorText.asString(),
            onDismiss = viewModel::dismissError,
        )
    }

    LaunchedEffect(Unit) {
        viewModel.fastSignFlow.collect { shouldShowPrompt ->
            if (shouldShowPrompt) {
                authorize()
            }
        }
    }

    VerifySwapScreen(
        state = state,
        showToolbar = true,
        confirmTitle = stringResource(R.string.verify_swap_sign_button),
        onConsentReceiveAmount = viewModel::consentReceiveAmount,
        onConsentAmount = viewModel::consentAmount,
        onConfirm = viewModel::joinKeySign,
        onConsentAllowance = viewModel::consentAllowance,
        onBackClick = viewModel::back,
        onFastSignClick = viewModel::fastSign,
        onContinueAnyway = viewModel::onConfirmScanning,
        onDismissRequest = viewModel::onDismissSecurityScanner,
    )
}

@Composable
internal fun VerifySwapScreen(
    state: VerifySwapUiModel,
    showToolbar: Boolean,
    confirmTitle: String,
    isConsentsEnabled: Boolean = true,
    onConsentReceiveAmount: (Boolean) -> Unit = {},
    onConsentAmount: (Boolean) -> Unit = {},
    onConsentAllowance: (Boolean) -> Unit = {},
    onFastSignClick: () -> Unit,
    onConfirm: () -> Unit,
    onBackClick: () -> Unit,
    onContinueAnyway: () -> Unit = {},
    onDismissRequest: () -> Unit = {},
) {
    VerifySwapScreen(
        showToolbar = showToolbar,
        tx = state.tx,
        scanStatus = state.txScanStatus,
        hasToShowWarningScanning = state.showScanningWarning,
        hasAllConsents = state.hasAllConsents,
        consentAmount = state.consentAmount,
        consentReceiveAmount = state.consentReceiveAmount,
        consentAllowance = state.consentAllowance,
        confirmTitle = confirmTitle,
        isConsentsEnabled = isConsentsEnabled,
        hasFastSign = state.hasFastSign,
        vaultName = state.vaultName,
        onConsentReceiveAmount = onConsentReceiveAmount,
        onConsentAmount = onConsentAmount,
        onConsentAllowance = onConsentAllowance,
        onFastSignClick = onFastSignClick,
        onConfirm = onConfirm,
        onBackClick = onBackClick,
        onContinueAnyway = onContinueAnyway,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun VerifySwapScreen(
    showToolbar: Boolean,
    tx: SwapTransactionUiModel,
    scanStatus: TransactionScanStatus,
    hasToShowWarningScanning: Boolean,
    hasAllConsents: Boolean,
    consentAmount: Boolean,
    consentReceiveAmount: Boolean,
    consentAllowance: Boolean,
    confirmTitle: String,
    isConsentsEnabled: Boolean = true,
    hasFastSign: Boolean,
    vaultName: String,
    onConsentReceiveAmount: (Boolean) -> Unit,
    onConsentAmount: (Boolean) -> Unit,
    onConsentAllowance: (Boolean) -> Unit,
    onFastSignClick: () -> Unit,
    onConfirm: () -> Unit,
    onBackClick: () -> Unit,
    onContinueAnyway: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            if (showToolbar) {
                VsTopAppBar(
                    title = "Swap overview",
                    onBackClick = onBackClick,
                )
            }
        },
        content = { contentPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .padding(contentPadding)
                    .padding(all = 16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(modifier = Modifier.align(alignment = Alignment.CenterHorizontally)){
                    SecurityScannerBadget(scanStatus)
                }

                Column(
                    modifier = Modifier
                        .background(
                            color = Theme.colors.backgrounds.secondary,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(
                            all = 24.dp,
                        )
                ) {
                    Text(
                        text = stringResource(R.string.verify_swap_youre_swapping_title),
                        style = Theme.brockmann.headings.subtitle,
                        color = Theme.colors.text.light,
                    )

                    UiSpacer(24.dp)

                    SwapToken(
                        valuedToken = tx.src,
                        isSwap = true,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            VerticalDivider(
                                thickness = 1.dp,
                                color = Theme.colors.borders.light,
                                modifier = Modifier
                                    .height(16.dp)
                            )

                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_down),
                                contentDescription = null,
                                tint = Theme.colors.primary.accent4,
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        color = Theme.colors.backgrounds.tertiary,
                                        shape = CircleShape,
                                    )
                                    .padding(6.dp)
                            )

                            VerticalDivider(
                                thickness = 1.dp,
                                color = Theme.colors.borders.light,
                                modifier = Modifier
                                    .height(16.dp)
                            )
                        }

                        Text(
                            "To",
                            style = Theme.brockmann.supplementary.captionSmall,
                            color = Theme.colors.text.extraLight,
                        )

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Theme.colors.borders.light,
                        )
                    }


                    SwapToken(
                        valuedToken = tx.dst,
                        isSwap = true,
                        isDestinationToken = true,
                    )

                    VerifyCardDivider(
                        size = 20.dp,
                    )

                    VerifyVaultDetails(
                        title = stringResource(R.string.swap_form_vault),
                        subtitle = vaultName,
                        metadata = tx.src.token.address,
                    )

                    VerifyCardDivider(
                        size = 20.dp,
                    )

                    EstimatedNetworkFee(
                        tokenGas = tx.networkFeeFormatted,
                        fiatGas = tx.networkFee.fiatValue,
                    )

                    VerifyCardDetails(
                        title = stringResource(R.string.swap_form_estimated_fees_title),
                        subtitle = tx.providerFee.fiatValue,
                    )

                    VerifyCardDivider(
                        size = 10.dp,
                    )

                    VerifyCardDetails(
                        title = stringResource(R.string.verify_swap_screen_total_fees),
                        subtitle = tx.totalFee,
                    )
                }

                if (isConsentsEnabled) {
                    Column {
                        VsCheckField(
                            title = stringResource(R.string.verify_swap_consent_amount),
                            isChecked = consentAmount,
                            onCheckedChange = onConsentAmount,
                        )

                        VsCheckField(
                            title = stringResource(R.string.verify_swap_agree_receive_amount),
                            isChecked = consentReceiveAmount,
                            onCheckedChange = onConsentReceiveAmount,
                        )

                        if (tx.hasConsentAllowance) {
                            VsCheckField(
                                title = stringResource(R.string.verify_swap_agree_allowance),
                                isChecked = consentAllowance,
                                onCheckedChange = onConsentAllowance,
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(
                        horizontal = 24.dp,
                        vertical = 12.dp,
                    )
            ) {

                if (hasToShowWarningScanning &&
                    scanStatus is TransactionScanStatus.Scanned) {
                    SecurityScannerBottomSheet(
                        securityScannerModel = scanStatus.result,
                        onContinueAnyway = onContinueAnyway,
                        onDismissRequest = onDismissRequest,
                    )
                }

                if (hasFastSign) {
                    Text(
                        text = "Hold for paired sign",
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.colors.text.extraLight,
                        textAlign = TextAlign.Center,
                    )

                    VsHoldableButton(
                        label = stringResource(R.string.verify_transaction_fast_sign_btn_title),
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = onFastSignClick,
                        onLongClick = onConfirm,
                    )
                } else {
                    VsButton(
                        label = confirmTitle,
                        modifier = Modifier
                            .fillMaxWidth(),
                        state = if (isConsentsEnabled && !hasAllConsents)
                            VsButtonState.Disabled else VsButtonState.Enabled,
                        onClick = onConfirm,
                    )
                }
            }
        }
    )
}

@Composable
internal fun SwapToken(
    valuedToken: ValuedToken,
    isSwap: Boolean = false,
    isDestinationToken: Boolean = false,
) {
    val token = valuedToken.token
    val value = valuedToken.value
    val shouldShowOnChainLogo = isSwap
            && (!token.isNativeToken || token.chain.isLayer2)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TokenLogo(
            logo = Tokens.getCoinLogo(token.logo),
            title = token.ticker,
            errorLogoModifier = Modifier
                .size(24.dp),
            modifier = Modifier
                .size(24.dp)
                .border(
                    width = 1.dp,
                    color = Theme.colors.borders.light,
                    shape = CircleShape,
                ),
        )

        val text = buildAnnotatedString {
            append(value)
            append(" ")
            withStyle(SpanStyle(color = Theme.colors.text.extraLight)) {
                append(token.ticker)
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isDestinationToken) {
                Text(
                    text = stringResource(R.string.swap_form_min_pay),
                    style = Theme.brockmann.supplementary.captionSmall,
                    color = Theme.colors.text.extraLight,
                )
            }

            Text(
                text = text,
                style = Theme.brockmann.headings.title3,
                color = Theme.colors.text.primary,
            )

            Text(
                text = valuedToken.fiatValue,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.colors.text.extraLight,
            )
        }

        if (shouldShowOnChainLogo) {
            UiSpacer(1f)

            Row(verticalAlignment = Alignment.CenterVertically) {
                TokenLogo(
                    logo = token.chain.logo,
                    title = token.ticker,
                    errorLogoModifier = Modifier
                        .size(16.dp),
                    modifier = Modifier
                        .size(16.dp)
                        .border(
                            width = 1.dp,
                            color = Theme.colors.borders.light,
                            shape = CircleShape,
                        )
                )

                UiSpacer(8.dp)

                Text(
                    text = stringResource(R.string.swap_form_on_chain) + " ${token.chain.swapAssetName()}",
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.colors.text.extraLight,
                )
            }
        }
    }
}

@Composable
internal fun VerifyCardDivider(
    size: Dp,
) {
    HorizontalDivider(
        thickness = 1.dp,
        color = Theme.colors.borders.light,
        modifier = Modifier
            .padding(
                vertical = size,
            )
    )
}

@Composable
internal fun VerifyCardDetails(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(
                vertical = 12.dp,
            )
    ) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.extraLight,
            maxLines = 1,
        )

        Text(
            text = subtitle,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
}

@Composable
internal fun VerifyVaultDetails(
    title: String,
    subtitle: String,
    metadata: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.extraLight,
            maxLines = 1,
        )

        Spacer(Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.widthIn(max = 200.dp)
        ) {
            Text(
                text = subtitle,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.colors.text.primary,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )

            if (metadata.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))

                val display = when {
                    metadata.length > 8 -> "(${metadata.take(4)}...${metadata.takeLast(4)})"
                    metadata.isNotEmpty() -> "($metadata)"
                    else -> ""
                }

                Text(
                    text = display,
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.colors.text.extraLight,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun VerifyCardJsonDetails(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(
                vertical = 12.dp,
            )
    ) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.extraLight,
            maxLines = 1,
        )

        Text(
            text = subtitle,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.primary,
            textAlign = TextAlign.Start,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

@Preview
@Composable
private fun VerifySwapScreenPreview() {
    VerifySwapScreen(
        showToolbar = true,
        onBackClick = {},
        hasAllConsents = false,
        hasToShowWarningScanning = false,
        scanStatus = TransactionScanStatus.NotStarted,
        consentAmount = true,
        consentReceiveAmount = false,
        tx = SwapTransactionUiModel(
            totalFee = "1.00$",
            hasConsentAllowance = true,
        ),
        consentAllowance = true,
        confirmTitle = "Sign",
        hasFastSign = false,
        vaultName = "Main Vault",
        onConsentReceiveAmount = {},
        onConsentAmount = {},
        onConsentAllowance = {},
        onFastSignClick = {},
        onConfirm = {},
        onContinueAnyway = {},
        onDismissRequest = {},
    )
}