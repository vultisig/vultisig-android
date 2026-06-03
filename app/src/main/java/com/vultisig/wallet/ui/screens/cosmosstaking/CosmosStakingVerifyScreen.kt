package com.vultisig.wallet.ui.screens.cosmosstaking

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiGradientHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.launchBiometricPrompt
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosStakingVerifyUiState
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosStakingVerifyValidatorRow
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosStakingVerifyViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

/**
 * Staking-specific verify summary for LUNA / LUNC — mirrors iOS `CosmosStakingVerifySummaryView`: a
 * headline that changes per op ("You're staking / unstaking / moving / claiming"), the staked
 * amount, From (vault + address), validator row(s) with resolved moniker + commission, Network, and
 * Est. network fee, then a Sign / Fast-sign CTA.
 */
@Composable
internal fun CosmosStakingVerifyScreen(viewModel: CosmosStakingVerifyViewModel = hiltViewModel()) {
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
            title = stringResource(R.string.dialog_default_error_title),
            text = errorText.asString(),
            onDismiss = viewModel::dismissError,
        )
    }

    V2Scaffold(
        title = stringResource(R.string.verify_deposit_function_overview),
        // Figma "Overview" uses a close (✕) button top-right rather than a back caret — it still
        // pops the verify entry, matching the design while keeping the screen exitable.
        onBackClick = null,
        rightIcon = R.drawable.big_close,
        onRightIconClick = viewModel::back,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                        .padding(bottom = 96.dp)
            ) {
                SummaryCard(state = state)
            }

            VsButton(
                label = stringResource(R.string.cosmos_staking_verify_sign),
                variant = VsButtonVariant.CTA,
                state = if (state.isLoading) VsButtonState.Disabled else VsButtonState.Enabled,
                onClick = {
                    if (state.hasFastSign) {
                        if (!viewModel.tryToFastSignWithPassword()) authorize()
                    } else {
                        viewModel.confirm()
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Composable
private fun SummaryCard(state: CosmosStakingVerifyUiState) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.normal,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero: headline + amount.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(state.headlineRes),
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.secondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = getCoinLogo(state.coinLogo),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).clip(CircleShape),
                )
                UiSpacer(size = 8.dp)
                Text(
                    text = state.amount,
                    style = Theme.brockmann.body.l.medium,
                    color = Theme.v2.colors.text.primary,
                )
                UiSpacer(size = 4.dp)
                Text(
                    text = state.ticker,
                    style = Theme.brockmann.body.l.medium,
                    color = Theme.v2.colors.text.tertiary,
                )
            }
        }

        UiGradientHorizontalDivider()

        SummaryRow(
            label = stringResource(R.string.cosmos_staking_verify_from),
            value = state.vaultName,
            secondary = truncatedMiddle(state.fromAddress),
        )

        state.validatorRows.forEach { row ->
            UiGradientHorizontalDivider()
            SummaryRow(label = stringResource(row.labelRes), value = row.value)
        }

        UiGradientHorizontalDivider()
        SummaryRow(
            label = stringResource(R.string.cosmos_staking_verify_network),
            value = state.networkName,
        )

        UiGradientHorizontalDivider()
        SummaryRow(
            label = stringResource(R.string.cosmos_staking_verify_est_fee),
            value = state.feeCrypto,
            secondary = state.feeFiat.takeIf { it.isNotEmpty() },
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, secondary: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                )
            }
        }
    }
}

private fun truncatedMiddle(value: String): String =
    if (value.length > 14) "${value.substring(0, 8)}…${value.substring(value.length - 4)}"
    else value

@Preview
@Composable
private fun CosmosStakingVerifyScreenPreview() {
    V2Scaffold(
        title = "Overview",
        onBackClick = null,
        rightIcon = R.drawable.big_close,
        onRightIconClick = {},
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                        .padding(bottom = 96.dp)
            ) {
                SummaryCard(
                    state =
                        CosmosStakingVerifyUiState(
                            headlineRes = R.string.cosmos_staking_youre_claiming,
                            amount = "0.000001",
                            ticker = "LUNA",
                            vaultName = "Main Vault",
                            fromAddress = "terra1delegatorxxxxxxxxxxxxxxxxxxxxxxxxxx78wk",
                            validatorRows =
                                listOf(
                                    CosmosStakingVerifyValidatorRow(
                                        labelRes = R.string.cosmos_staking_validator_picker,
                                        value = "Allnodes (5% commission)",
                                    )
                                ),
                            networkName = "Terra",
                            feeCrypto = "0.01 LUNA",
                            isLoading = false,
                        )
                )
            }
            VsButton(
                label = stringResource(R.string.cosmos_staking_verify_sign),
                variant = VsButtonVariant.CTA,
                state = VsButtonState.Enabled,
                onClick = {},
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            )
        }
    }
}
