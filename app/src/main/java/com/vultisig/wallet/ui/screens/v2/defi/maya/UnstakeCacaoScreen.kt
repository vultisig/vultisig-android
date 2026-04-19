package com.vultisig.wallet.ui.screens.v2.defi.maya

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.PercentageChip
import com.vultisig.wallet.ui.components.UiGradientHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositFormViewModel
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun UnstakeCacaoScreen(
    vaultId: String,
    chainId: String,
    viewModel: DepositFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(vaultId, chainId) {
        viewModel.loadData(
            vaultId = vaultId,
            chainId = chainId,
            depositType = DeFiNavActions.UNSTAKE_CACAO.type,
            bondAddress = null,
            poolId = null,
        )
    }

    UnstakeCacaoContent(
        state = state,
        tokenAmountFieldState = viewModel.tokenAmountFieldState,
        onDeposit = viewModel::validateAndDeposit,
    )
}

@Composable
private fun UnstakeCacaoContent(
    state: DepositFormUiModel = DepositFormUiModel(),
    tokenAmountFieldState: TextFieldState = TextFieldState(),
    onDeposit: () -> Unit = {},
) {
    val percentages = listOf(25, 50, 75, 100)
    val percentageLabels = listOf("25%", "50%", "75%", stringResource(R.string.max))
    var selectedPercentageIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(state.unstakableAmount) { selectedPercentageIndex = -1 }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(Theme.v2.colors.backgrounds.primary)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val amountCardShape = RoundedCornerShape(12.dp)
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .clip(amountCardShape)
                    .border(
                        width = 1.dp,
                        color = Theme.v2.colors.border.normal,
                        shape = amountCardShape,
                    )
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.remove_pool_amount_label),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                )
            }

            UiSpacer(12.dp)

            UiGradientHorizontalDivider()

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val percentText = tokenAmountFieldState.text.toString()
                    val displayPercent = percentText.toIntOrNull() ?: 0

                    Text(
                        text = stringResource(R.string.remove_pool_percent_format, displayPercent),
                        style = Theme.brockmann.headings.largeTitle,
                        color = Theme.v2.colors.text.primary,
                    )

                    if (state.unstakableAmount != null) {
                        Text(
                            text =
                                stringResource(
                                    R.string.unstake_cacao_available_format,
                                    state.unstakableAmount,
                                ),
                            style = Theme.brockmann.body.m.medium,
                            color = Theme.v2.colors.text.tertiary,
                        )
                    } else {
                        UiPlaceholderLoader(modifier = Modifier.height(20.dp).width(120.dp))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                percentageLabels.forEachIndexed { index, label ->
                    PercentageChip(
                        title = label,
                        isSelected = selectedPercentageIndex == index,
                        onClick = {
                            selectedPercentageIndex = index
                            val percent = percentages[index]
                            tokenAmountFieldState.setTextAndPlaceCursorAtEnd(percent.toString())
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            UiSpacer(size = 12.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.send_form_balance_available),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.secondary,
                )

                Text(
                    text = state.balance.asString(),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.secondary,
                )
            }
        }

        val tokenAmountError = state.tokenAmountError?.asString()
        if (tokenAmountError != null) {
            Text(
                text = tokenAmountError,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.alerts.error,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        VsButton(
            label = stringResource(R.string.send_continue_button),
            variant = VsButtonVariant.CTA,
            modifier = Modifier.fillMaxWidth(),
            onClick = onDeposit,
            state =
                if (
                    (tokenAmountFieldState.text.toString().toIntOrNull() ?: 0) > 0 &&
                        !state.isLoading
                )
                    VsButtonState.Enabled
                else VsButtonState.Disabled,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UnstakeCacaoContentPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary)) {
        UnstakeCacaoContent(
            state =
                DepositFormUiModel(
                    unstakableAmount = "5,000 CACAO",
                    balance = UiText.DynamicString("24,000 CACAO"),
                ),
            tokenAmountFieldState = TextFieldState("50"),
        )
    }
}
