package com.vultisig.wallet.ui.screens.cosmosstaking

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosUndelegateViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun CosmosUndelegateScreen(viewModel: CosmosUndelegateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    V2Scaffold(title = "Unstake ${state.ticker.ifEmpty { "Token" }}") {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Validator",
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                )
                Text(
                    text = state.validatorAddress,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                )

                Text(
                    text = "Amount (${state.ticker.ifEmpty { "Token" }})",
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                )
                BasicTextField(
                    value = viewModel.amountFieldState.text.toString(),
                    onValueChange = { newText ->
                        viewModel.amountFieldState.edit { replace(0, length, newText) }
                    },
                    singleLine = true,
                    textStyle =
                        TextStyle(
                            color = Theme.v2.colors.text.primary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = Theme.v2.colors.border.normal,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                )

                val errorMessage = state.errorMessage
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = Theme.v2.colors.alerts.error,
                        style = Theme.brockmann.supplementary.caption,
                    )
                }
            }

            VsButton(
                label = "Continue",
                variant = VsButtonVariant.CTA,
                state = if (state.isSubmitting) VsButtonState.Disabled else VsButtonState.Enabled,
                onClick = viewModel::submit,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            )
        }
    }
}
