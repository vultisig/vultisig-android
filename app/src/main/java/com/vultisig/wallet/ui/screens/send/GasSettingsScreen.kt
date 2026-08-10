@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.screens.send

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.supportsLegacyGas
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.models.send.GasSettings
import com.vultisig.wallet.ui.models.send.GasSettingsUiModel
import com.vultisig.wallet.ui.models.send.GasSettingsViewModel
import com.vultisig.wallet.ui.theme.Theme
import java.math.BigInteger

@Composable
internal fun GasSettingsScreen(
    chain: Chain,
    specific: BlockChainSpecificAndUtxo,
    onDismissGasSettings: () -> Unit,
    onSaveGasSettings: (GasSettings) -> Unit,
    model: GasSettingsViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    LaunchedEffect(Unit) { model.loadData(chain, specific) }

    BasicAlertDialog(onDismissRequest = onDismissGasSettings) {
        GasSettingsScreen(
            isLegacyGas = chain.supportsLegacyGas,
            state = state,
            gasLimitState = model.gasLimitState,
            baseFeeState = model.baseFeeState,
            priorityFeeState = model.priorityFeeState,
            byteFeeState = model.byteFeeState,
            onSaveClick = {
                onSaveGasSettings(model.save())
                onDismissGasSettings()
            },
            onCloseClick = onDismissGasSettings,
        )
    }
}

@Composable
internal fun GasSettingsScreen(
    isLegacyGas: Boolean,
    state: GasSettingsUiModel,
    gasLimitState: TextFieldState,
    baseFeeState: TextFieldState,
    priorityFeeState: TextFieldState,
    byteFeeState: TextFieldState,
    onCloseClick: () -> Unit,
    onSaveClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier.background(
                    color = Theme.v2.colors.backgrounds.primary,
                    shape = Theme.v2.radius.xl,
                )
                .padding(all = 24.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(
                drawableResId = R.drawable.ic_caret_left,
                size = 16.dp,
                tint = Theme.v2.colors.text.tertiary,
                onClick = onCloseClick,
            )

            Text(
                text = stringResource(R.string.gas_settings_advanced_gas_fee),
                style = Theme.brockmann.headings.title3,
                color = Theme.v2.colors.text.primary,
            )
        }

        UiSpacer(14.dp)

        FadingHorizontalDivider()

        UiSpacer(14.dp)

        when (state.chainSpecific) {
            is BlockChainSpecific.Ethereum -> {
                EthGasSettings(
                    isLegacyGas = isLegacyGas,
                    state = state,
                    gasLimitState = gasLimitState,
                    baseFeeState = baseFeeState,
                    priorityFeeState = priorityFeeState,
                )
            }

            is BlockChainSpecific.UTXO -> {
                UTXOSettings(state = state, byteFeeState = byteFeeState)
            }

            else -> Unit
        }

        UiSpacer(14.dp)

        VsButton(
            label = stringResource(R.string.add_vault_save),
            onClick = onSaveClick,
            // Blocks saving while the eth_gasPrice/base fee fetch is in flight, and keeps
            // blocking on failure too — the fields stay blank either way, and blank would
            // otherwise save as a zero fee (issue #5397).
            isLoading = state.isLoadingEthFee,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun UTXOSettings(state: GasSettingsUiModel, byteFeeState: TextFieldState) {
    FormTextFieldCard(
        title = stringResource(R.string.utxo_settings_byte_fee_title),
        hint = "",
        error = state.byteFeeError,
        keyboardType = KeyboardType.Number,
        textFieldState = byteFeeState,
    )
}

@Composable
private fun EthGasSettings(
    isLegacyGas: Boolean,
    state: GasSettingsUiModel,
    gasLimitState: TextFieldState,
    baseFeeState: TextFieldState,
    priorityFeeState: TextFieldState,
) {
    Text(
        text =
            stringResource(
                if (isLegacyGas) R.string.gas_settings_gas_price_gwei
                else R.string.gas_settings_max_base_fee_gwei
            ),
        style = Theme.brockmann.body.s.medium,
        color = Theme.v2.colors.text.primary,
    )

    UiSpacer(8.dp)

    VsTextInputField(textFieldState = baseFeeState, keyboardType = KeyboardType.Number)

    UiSpacer(14.dp)

    // Legacy-gas chains (BSC) have no priority fee: their gasPrice is the single field above.
    if (!isLegacyGas) {
        Text(
            text = stringResource(R.string.gas_settings_priority_fee_gwei),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(8.dp)

        VsTextInputField(textFieldState = priorityFeeState, keyboardType = KeyboardType.Number)

        UiSpacer(14.dp)
    }

    Text(
        text = stringResource(R.string.eth_gas_settings_gas_limit_title),
        style = Theme.brockmann.body.s.medium,
        color = Theme.v2.colors.text.primary,
    )

    UiSpacer(8.dp)

    VsTextInputField(textFieldState = gasLimitState, keyboardType = KeyboardType.Number)
}

private val previewEthSpec =
    BlockChainSpecific.Ethereum(
        maxFeePerGasWei = BigInteger.valueOf(3_000_000_000L),
        priorityFeeWei = BigInteger.valueOf(1_000_000_000L),
        nonce = BigInteger.ZERO,
        gasLimit = BigInteger.valueOf(21_000L),
    )

@Preview
@Composable
private fun EthGasSettingsScreenPreview() {
    GasSettingsScreen(
        isLegacyGas = false,
        state = GasSettingsUiModel(chainSpecific = previewEthSpec),
        gasLimitState = TextFieldState("21000"),
        byteFeeState = TextFieldState(),
        baseFeeState = TextFieldState("3"),
        priorityFeeState = TextFieldState("1"),
        onSaveClick = {},
        onCloseClick = {},
    )
}

@Preview
@Composable
private fun LegacyGasSettingsScreenPreview() {
    GasSettingsScreen(
        isLegacyGas = true,
        state = GasSettingsUiModel(chainSpecific = previewEthSpec),
        gasLimitState = TextFieldState("21000"),
        byteFeeState = TextFieldState(),
        baseFeeState = TextFieldState("3"),
        priorityFeeState = TextFieldState("0"),
        onSaveClick = {},
        onCloseClick = {},
    )
}
