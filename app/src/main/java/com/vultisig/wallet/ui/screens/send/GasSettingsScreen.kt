@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.screens.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.ui.components.GradientButton
import com.vultisig.wallet.ui.components.TopBarWithoutNav
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.FormEntry
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.components.library.form.FormTitleContainer
import com.vultisig.wallet.ui.models.send.GasSettingsUiModel
import com.vultisig.wallet.ui.models.send.GasSettingsViewModel
import com.vultisig.wallet.ui.models.send.GasSettings
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

    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
        dragHandle = null,
        containerColor = Theme.colors.oxfordBlue800,
        onDismissRequest = onDismissGasSettings,
    ) {
        GasSettingsScreen(
            state = state,
            gasLimitState = model.gasLimitState,
            byteFeeState = model.byteFeeState,
            onSelectPriorityFee = model::selectPriorityFee,
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
    byteFeeState: TextFieldState,
    onSelectPriorityFee: (PriorityFee) -> Unit,
    onCloseClick: () -> Unit,
    onSaveClick: () -> Unit,
) {
    Column {
        TopBarWithoutNav(
            centerText = stringResource(R.string.eth_gas_settings_title),
            startIcon = R.drawable.x,
            onStartIconClick = onCloseClick,
            endIcon = R.drawable.done_check,
            onEndIconClick = onSaveClick,
        )

        Column(
            modifier = Modifier
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FormTitleContainer(
                title = stringResource(R.string.eth_gas_settings_priority_title)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PriorityFee.entries.forEach { fee ->
                        GradientButton(
                            text = fee.name
                                .lowercase()
                                .capitalize(Locale.current),
                            isSelected = fee == state.selectedPriorityFee,
                            onClick = { onSelectPriorityFee(fee) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            when (state.chainSpecific) {
                is BlockChainSpecific.Ethereum -> {
                    EthGasSettings(
                        state = state,
                        gasLimitState = gasLimitState,
                    )
                }
                is BlockChainSpecific.UTXO -> {
                    UTXOSettings(
                        state = state,
                        byteFeeState = byteFeeState,
                    )
                }
                else -> {}
            }

            UiSpacer(size = 64.dp)
        }
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
    gasLimitState: TextFieldState
) {
    FormEntry(
        title = stringResource(R.string.eth_gas_settings_base_fee_title),
    ) {
        Text(
            text = state.currentBaseFee,
            color = Theme.colors.neutral100,
            style = Theme.menlo.body1,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(
                    horizontal = 12.dp,
                    vertical = 16.dp
                ),
        )
    }

    FormTextFieldCard(
        title = stringResource(R.string.eth_gas_settings_gas_limit_title),
        hint = stringResource(R.string.eth_gas_settings_gas_limit_title),
        error = state.gasLimitError,
        keyboardType = KeyboardType.Number,
        textFieldState = gasLimitState,
    )


    FormEntry(
        title = stringResource(R.string.eth_gas_setting_total_fee_title),
    ) {
        Text(
            text = state.totalFee,
            color = Theme.colors.neutral100,
            style = Theme.menlo.body1,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(
                    horizontal = 12.dp,
                    vertical = 16.dp
                ),
        )
    }
}

@Preview
@Composable
private fun EthGasSettingsScreenPreview() {
    GasSettingsScreen(
        state = GasSettingsUiModel(
            currentBaseFee = "25",
        ),
        gasLimitState = TextFieldState(),
        byteFeeState = TextFieldState(),
        onSelectPriorityFee = {},
        onSaveClick = {},
        onCloseClick = {},
    )
}