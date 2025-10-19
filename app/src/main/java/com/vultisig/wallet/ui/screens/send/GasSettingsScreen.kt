@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.screens.send

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.ui.components.GradientButton
import com.vultisig.wallet.ui.components.TopBarWithoutNav
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.library.form.FormEntry
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.components.library.form.FormTitleContainer
import com.vultisig.wallet.ui.models.send.GasSettings
import com.vultisig.wallet.ui.models.send.GasSettingsUiModel
import com.vultisig.wallet.ui.models.send.GasSettingsViewModel
import com.vultisig.wallet.ui.models.send.PriorityFee
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun GasSettingsScreen(
    chain: Chain,
    specific: BlockChainSpecificAndUtxo,
    onDismissGasSettings: () -> Unit,
    onSaveGasSettings: (GasSettings) -> Unit,
    model: GasSettingsViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    LaunchedEffect(Unit) {
        model.loadData(chain, specific)
    }

    BasicAlertDialog(
        onDismissRequest = onDismissGasSettings,
    ) {
        GasSettingsScreen(
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
private fun GasSettingsScreen(
    state: GasSettingsUiModel,

    gasLimitState: TextFieldState,
    baseFeeState: TextFieldState,
    priorityFeeState: TextFieldState,

    byteFeeState: TextFieldState,

    onCloseClick: () -> Unit,
    onSaveClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .background(
                color = Theme.colors.backgrounds.primary,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(all = 24.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(
                drawableResId = R.drawable.ic_caret_left,
                size = 16.dp,
                tint = Theme.colors.text.extraLight,
                onClick = onCloseClick,
            )

            Text(
                text = stringResource(R.string.gas_settings_advanced_gas_fee),
                style = Theme.brockmann.headings.title3,
                color = Theme.colors.text.primary
            )
        }

        UiSpacer(14.dp)

        FadingHorizontalDivider()

        UiSpacer(14.dp)

        when (state.chainSpecific) {
            is BlockChainSpecific.Ethereum -> {
                EthGasSettings(
                    state = state,
                    gasLimitState = gasLimitState,
                    baseFeeState = baseFeeState,
                    priorityFeeState = priorityFeeState,
                )
            }

            is BlockChainSpecific.UTXO -> {
                UTXOSettings(
                    state = state,
                    byteFeeState = byteFeeState,
                )
            }

            else -> Unit
        }

        UiSpacer(14.dp)

        VsButton(
            label = stringResource(R.string.gas_settings_save),
            onClick = onSaveClick,
            modifier = Modifier
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun UTXOSettings(
    state: GasSettingsUiModel,
    byteFeeState: TextFieldState,
) {
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
    state: GasSettingsUiModel,
    gasLimitState: TextFieldState,
    baseFeeState: TextFieldState,
    priorityFeeState: TextFieldState,
) {
    Text(
        text = stringResource(R.string.gas_settings_max_base_fee_gwei),
        style = Theme.brockmann.body.s.medium,
        color = Theme.colors.text.primary,
    )

    UiSpacer(8.dp)

    VsTextInputField(
        textFieldState = baseFeeState,
        keyboardType = KeyboardType.Number,
    )

    UiSpacer(14.dp)

    Text(
        text = stringResource(R.string.gas_settings_priority_fee_gwei),
        style = Theme.brockmann.body.s.medium,
        color = Theme.colors.text.primary,
    )

    UiSpacer(8.dp)

    VsTextInputField(
        textFieldState = priorityFeeState,
        keyboardType = KeyboardType.Number,
    )

    UiSpacer(14.dp)

    Text(
        text = stringResource(R.string.eth_gas_settings_gas_limit_title),
        style = Theme.brockmann.body.s.medium,
        color = Theme.colors.text.primary,
    )

    UiSpacer(8.dp)

    VsTextInputField(
        textFieldState = gasLimitState,
        keyboardType = KeyboardType.Number,
    )
}

@Preview
@Composable
private fun EthGasSettingsScreenPreview() {
    GasSettingsScreen(
        state = GasSettingsUiModel(),
        gasLimitState = TextFieldState(),
        byteFeeState = TextFieldState(),
        baseFeeState = TextFieldState(),
        priorityFeeState = TextFieldState(),
        onSaveClick = {},
        onCloseClick = {},
    )
}