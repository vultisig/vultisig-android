package com.vultisig.wallet.ui.screens.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.getProviderLogo
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.models.payload.DAppMetadata
import com.vultisig.wallet.data.models.swapAssetName
import com.vultisig.wallet.data.usecases.getTierType
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsCheckField
import com.vultisig.wallet.ui.components.buttons.FastSignPairedButtons
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.dapp.DappRequestBanner
import com.vultisig.wallet.ui.components.launchBiometricPrompt
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.securityscanner.SecurityScannerBadget
import com.vultisig.wallet.ui.components.securityscanner.SecurityScannerBottomSheet
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.TransactionScanStatus
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.models.swap.VerifySwapUiModel
import com.vultisig.wallet.ui.models.swap.VerifySwapViewModel
import com.vultisig.wallet.ui.screens.send.EstimatedNetworkFee
import com.vultisig.wallet.ui.screens.swap.components.ReferralDiscountRow
import com.vultisig.wallet.ui.screens.swap.components.VultDiscountRow
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun VerifySwapScreen(viewModel: VerifySwapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val promptTitle = stringResource(R.string.biometry_keysign_login_button)

    val authorize: () -> Unit =
        remember(context) {
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
        hasToolbar = true,
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
    hasToolbar: Boolean,
    confirmTitle: String,
    isConsentsEnabled: Boolean = true,
    dappMetadata: DAppMetadata? = null,
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
        hasToolbar = hasToolbar,
        tx = state.tx,
        scanStatus = state.txScanStatus,
        hasToShowWarningScanning = state.showScanningWarning,
        hasAllConsents = state.hasAllConsents,
        isSigning = state.isSigning,
        consentAmount = state.consentAmount,
        consentReceiveAmount = state.consentReceiveAmount,
        consentAllowance = state.consentAllowance,
        confirmTitle = confirmTitle,
        isConsentsEnabled = isConsentsEnabled,
        hasFastSign = state.hasFastSign,
        vaultName = state.vaultName,
        dappMetadata = dappMetadata,
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
    hasToolbar: Boolean,
    tx: SwapTransactionUiModel,
    scanStatus: TransactionScanStatus,
    hasToShowWarningScanning: Boolean,
    hasAllConsents: Boolean,
    isSigning: Boolean,
    consentAmount: Boolean,
    consentReceiveAmount: Boolean,
    consentAllowance: Boolean,
    confirmTitle: String,
    isConsentsEnabled: Boolean = true,
    hasFastSign: Boolean,
    vaultName: String,
    dappMetadata: DAppMetadata?,
    onConsentReceiveAmount: (Boolean) -> Unit,
    onConsentAmount: (Boolean) -> Unit,
    onConsentAllowance: (Boolean) -> Unit,
    onFastSignClick: () -> Unit,
    onConfirm: () -> Unit,
    onBackClick: () -> Unit,
    onContinueAnyway: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val isSignEnabled = (!isConsentsEnabled || hasAllConsents) && !isSigning

    V2Scaffold(
        title = stringResource(R.string.verify_swap_swap_overview).takeIf { hasToolbar },
        onBackClick = onBackClick.takeIf { hasToolbar },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                dappMetadata?.takeUnless { it.isEmpty }?.let { DappRequestBanner(metadata = it) }

                Column(modifier = Modifier.align(alignment = Alignment.CenterHorizontally)) {
                    SecurityScannerBadget(scanStatus)
                }

                Column(
                    modifier =
                        Modifier.background(
                                color = Theme.v2.colors.backgrounds.secondary,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .padding(all = 24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.verify_swap_youre_swapping_title),
                        style = Theme.brockmann.headings.subtitle,
                        color = Theme.v2.colors.text.secondary,
                    )

                    UiSpacer(24.dp)

                    SwapToken(valuedToken = tx.src, isSwap = true)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            VerticalDivider(
                                thickness = 1.dp,
                                color = Theme.v2.colors.border.light,
                                modifier = Modifier.height(16.dp),
                            )

                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_down),
                                contentDescription = null,
                                tint = Theme.v2.colors.primary.accent4,
                                modifier =
                                    Modifier.size(24.dp)
                                        .background(
                                            color = Theme.v2.colors.backgrounds.tertiary_2,
                                            shape = CircleShape,
                                        )
                                        .padding(6.dp),
                            )

                            VerticalDivider(
                                thickness = 1.dp,
                                color = Theme.v2.colors.border.light,
                                modifier = Modifier.height(16.dp),
                            )
                        }

                        Text(
                            stringResource(R.string.swap_form_dst_token_title),
                            style = Theme.brockmann.supplementary.captionSmall,
                            color = Theme.v2.colors.text.tertiary,
                        )

                        HorizontalDivider(thickness = 1.dp, color = Theme.v2.colors.border.light)
                    }

                    SwapToken(valuedToken = tx.dst, isSwap = true, isDestinationToken = true)

                    VerifyCardDivider(size = 20.dp)

                    VerifyVaultDetails(
                        title = stringResource(R.string.swap_form_vault),
                        subtitle = vaultName,
                        metadata = tx.src.token.address,
                    )

                    // External recipient must be visible before signing — never a silent default
                    // (#4858). Only shown when the user routed the output to a custom address, and
                    // given warning emphasis so it reads as a deliberate deviation, not a fee row.
                    tx.externalRecipient
                        ?.takeIf { it.isNotBlank() }
                        ?.let { recipient ->
                            VerifyCardDivider(size = 20.dp)
                            VerifyExternalRecipientRow(address = recipient)
                        }

                    VerifyCardDivider(size = 20.dp)

                    if (tx.provider.isNotBlank()) {
                        VerifyProviderRow(provider = tx.providerLabel.ifBlank { tx.provider })
                    }

                    EstimatedNetworkFee(
                        tokenGas = tx.networkFeeFormatted,
                        fiatGas = tx.networkFee.fiatValue,
                    )

                    // Swap Fee row mirrors the form (#5358): percentage in the title when known,
                    // and "included in quoted rate" instead of a fiat amount for providers (1inch)
                    // that bake the affiliate fee into the quoted destination amount. Hidden
                    // entirely for a SwapKit UTXO deposit, whose cost is already the Network Fee.
                    if (!tx.swapFeeHidden) {
                        VerifyCardDetails(
                            title =
                                tx.swapFeePercent?.let {
                                    stringResource(
                                        R.string.swap_form_estimated_fees_with_percent_title,
                                        it,
                                    )
                                } ?: stringResource(R.string.swap_form_estimated_fees_title),
                            subtitle =
                                if (tx.swapFeeIncludedInRate)
                                    stringResource(
                                        R.string.swap_form_estimated_fees_included_in_rate
                                    )
                                else tx.providerFee.fiatValue,
                        )
                    }

                    // Shown only for THORChain / MayaChain, which report an outbound fee distinct
                    // from the affiliate swap fee. Mirrors the swap form's breakdown so the rows
                    // reconcile to the total (#5061).
                    tx.outboundFee?.let { outboundFee ->
                        VerifyCardDetails(
                            title = stringResource(R.string.swap_form_outbound_fee_title),
                            subtitle = outboundFee,
                        )
                    }

                    // VULT-tier and referral discount rows, matching the swap form so the user can
                    // confirm their tier was applied at the moment of approval (#5358). Each row is
                    // a no-op when its data is absent (co-signer / no discount).
                    VultDiscountRow(
                        vultBpsDiscount = tx.vultBpsDiscount,
                        tierType = tx.vultBpsDiscount?.getTierType(),
                        fiatValue = tx.vultBpsDiscountFiatValue,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )

                    ReferralDiscountRow(
                        referralBpsDiscount = tx.referralBpsDiscount,
                        fiatValue = tx.referralBpsDiscountFiatValue,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )

                    VerifyCardDivider(size = 10.dp)

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
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                if (hasToShowWarningScanning && scanStatus is TransactionScanStatus.Scanned) {
                    SecurityScannerBottomSheet(
                        securityScannerModel = scanStatus.result,
                        onContinueAnyway = onContinueAnyway,
                        onDismissRequest = onDismissRequest,
                    )
                }

                if (hasFastSign) {
                    FastSignPairedButtons(
                        onFastSignClick = onFastSignClick,
                        onPairedSignClick = onConfirm,
                        state =
                            if (!isSignEnabled) {
                                VsButtonState.Disabled
                            } else {
                                VsButtonState.Enabled
                            },
                        isLoading = isSigning,
                    )
                } else {
                    VsButton(
                        label = confirmTitle,
                        modifier = Modifier.fillMaxWidth(),
                        state =
                            if (!isSignEnabled) VsButtonState.Disabled else VsButtonState.Enabled,
                        isLoading = isSigning,
                        onClick = onConfirm,
                    )
                }
            }
        },
    )
}

@Composable
internal fun SwapToken(
    valuedToken: ValuedToken,
    isSwap: Boolean = false,
    isDestinationToken: Boolean = false,
    isLoading: Boolean = false,
) {
    val token = valuedToken.token
    val value = valuedToken.value
    // Show the "on <chain>" indicator on both swap rows so the From and To sides communicate
    // their chain consistently — including native assets on their own L1.
    val shouldShowOnChainLogo = isSwap

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TokenLogo(
            logo = getCoinLogo(token.logo),
            title = token.ticker,
            errorLogoModifier =
                Modifier.size(24.dp).clip(CircleShape).background(Theme.v2.colors.neutrals.n200),
            modifier =
                Modifier.size(24.dp)
                    .border(width = 1.dp, color = Theme.v2.colors.border.light, shape = CircleShape),
        )

        val text = buildAnnotatedString {
            append(value)
            append(" ")
            withStyle(SpanStyle(color = Theme.v2.colors.text.tertiary)) { append(token.ticker) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isDestinationToken) {
                Text(
                    text = stringResource(R.string.swap_form_min_pay),
                    style = Theme.brockmann.supplementary.captionSmall,
                    color = Theme.v2.colors.text.tertiary,
                )
            }

            if (isLoading) {
                UiPlaceholderLoader(modifier = Modifier.height(20.dp).width(150.dp))

                UiPlaceholderLoader(modifier = Modifier.height(20.dp).width(150.dp))
            } else {
                Text(
                    text = text,
                    style = Theme.brockmann.headings.title3,
                    color = Theme.v2.colors.text.primary,
                )

                Text(
                    text = valuedToken.fiatValue,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                )
            }
        }

        if (shouldShowOnChainLogo) {
            UiSpacer(1f)

            Row(verticalAlignment = Alignment.CenterVertically) {
                TokenLogo(
                    logo = token.chain.logo,
                    title = token.ticker,
                    errorLogoModifier = Modifier.size(16.dp),
                    modifier =
                        Modifier.size(16.dp)
                            .border(
                                width = 1.dp,
                                color = Theme.v2.colors.border.light,
                                shape = CircleShape,
                            ),
                )

                UiSpacer(8.dp)

                if (isLoading) {
                    UiPlaceholderLoader(modifier = Modifier.height(20.dp).width(150.dp))
                } else {
                    Text(
                        text =
                            stringResource(R.string.swap_form_on_chain) +
                                " ${token.chain.swapAssetName()}",
                        style = Theme.brockmann.supplementary.footnote,
                        color = Theme.v2.colors.text.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun VerifyCardDivider(size: Dp) {
    HorizontalDivider(
        thickness = 1.dp,
        color = Theme.v2.colors.border.light,
        modifier = Modifier.padding(vertical = size),
    )
}

@Composable
internal fun VerifyCardDetails(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    bracketValue: String? = null,
    showAllContent: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.tertiary,
            maxLines = 1,
            modifier = Modifier.defaultMinSize(minWidth = 52.dp),
        )

        if (bracketValue != null) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = subtitle,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(
                    text = "($bracketValue)",
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            UiSpacer(weight = 1f)

            Text(
                text = subtitle,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.End,
                modifier = if (showAllContent) Modifier.fillMaxWidth() else Modifier,
                maxLines = if (showAllContent) 5 else 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
    }
}

@Composable
internal fun VerifyVaultDetails(
    title: String,
    subtitle: String,
    metadata: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.tertiary,
            maxLines = 1,
        )

        Spacer(Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.widthIn(max = 200.dp),
        ) {
            Text(
                text = subtitle,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )

            if (metadata.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))

                val display =
                    when {
                        metadata.length > 8 -> "(${metadata.take(4)}...${metadata.takeLast(4)})"
                        metadata.isNotEmpty() -> "($metadata)"
                        else -> ""
                    }

                Text(
                    text = display,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * External-recipient row on the swap verify screen (#4858). Warning-styled so the user registers
 * that the output is routed to an address other than their own vault — the most security-sensitive
 * of the advanced overrides — before signing.
 */
@Composable
internal fun VerifyExternalRecipientRow(address: String, modifier: Modifier = Modifier) {
    val display = if (address.length > 8) "${address.take(6)}...${address.takeLast(6)}" else address
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.verify_swap_recipient),
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.alerts.warning,
            maxLines = 1,
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = display,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.alerts.warning,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
internal fun VerifyProviderRow(provider: String, modifier: Modifier = Modifier) {
    val logo = remember(provider) { getProviderLogo(provider) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.swap_screen_provider_title),
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.tertiary,
            maxLines = 1,
            modifier = Modifier.defaultMinSize(minWidth = 52.dp),
        )

        UiSpacer(weight = 1f)

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (logo != null) {
                TokenLogo(
                    logo = logo,
                    title = provider,
                    errorLogoModifier = Modifier.size(16.dp).clip(CircleShape),
                    modifier =
                        Modifier.size(16.dp)
                            .border(
                                width = 1.dp,
                                color = Theme.v2.colors.border.light,
                                shape = CircleShape,
                            ),
                )
            }
            Text(
                text = provider,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
                maxLines = 1,
            )
        }
    }
}

@Preview
@Composable
private fun VerifySwapScreenPreview() {
    VerifySwapScreen(
        hasToolbar = true,
        onBackClick = {},
        hasAllConsents = false,
        isSigning = false,
        hasToShowWarningScanning = false,
        scanStatus = TransactionScanStatus.NotStarted,
        consentAmount = true,
        consentReceiveAmount = false,
        tx = SwapTransactionUiModel(totalFee = "1.00$", hasConsentAllowance = true),
        consentAllowance = true,
        confirmTitle = "Sign",
        hasFastSign = false,
        vaultName = "Main Vault",
        dappMetadata = null,
        onConsentReceiveAmount = {},
        onConsentAmount = {},
        onConsentAllowance = {},
        onFastSignClick = {},
        onConfirm = {},
        onContinueAnyway = {},
        onDismissRequest = {},
    )
}

@Preview
@Composable
private fun JoinKeysignSwapVerifyPreview() {
    VerifySwapScreen(
        state =
            VerifySwapUiModel(
                tx = SwapTransactionUiModel(totalFee = "1.00$", hasConsentAllowance = true),
                vaultName = "Main Vault",
            ),
        hasToolbar = true,
        onBackClick = {},
        confirmTitle = stringResource(R.string.verify_swap_sign_button),
        isConsentsEnabled = false,
        onFastSignClick = {},
        onConfirm = {},
    )
}
