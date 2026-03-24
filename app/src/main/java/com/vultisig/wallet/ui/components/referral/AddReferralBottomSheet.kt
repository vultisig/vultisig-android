package com.vultisig.wallet.ui.components.referral

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.v2.loading.V2Loading
import com.vultisig.wallet.ui.models.keygen.AddReferralUiModel
import com.vultisig.wallet.ui.models.keygen.AddReferralViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun AddReferralBottomSheet(
    onApply: (String) -> Unit,
    onDismissRequest: () -> Unit,
    viewModel: AddReferralViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    VsModalBottomSheet(
        onDismissRequest = {
            viewModel.cancelApplyReferral()
            onDismissRequest()
        },
        showDragHandle = false,
    ) {
        AddReferralBottomSheetContent(
            state = state,
            textFieldState = viewModel.textFieldState,
            onApplyClick = { viewModel.applyReferral(onApply) },
            onClearClick = {
                if (!state.isLoading) {
                    viewModel.clearInput()
                }
            },
        )
    }
}

@Composable
private fun AddReferralBottomSheetContent(
    state: AddReferralUiModel,
    textFieldState: TextFieldState,
    onApplyClick: () -> Unit,
    onClearClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier.padding(horizontal = 24.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 24.dp, bottom = 32.dp)
    ) {
        Text(
            text = stringResource(R.string.add_referral_sheet_title),
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
        )

        UiSpacer(8.dp)

        Text(
            text = stringResource(R.string.add_referral_sheet_description),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
        )

        UiSpacer(24.dp)

        VsTextInputField(
            textFieldState = textFieldState,
            hint = stringResource(R.string.add_referral_sheet_placeholder),
            innerState = state.innerState,
            footNote = state.errorMessage?.asString(),
            enabled = !state.isLoading,
            trailingIcon = R.drawable.close_circle,
            onTrailingIconClick = onClearClick,
            modifier = Modifier.fillMaxWidth(),
        )

        UiSpacer(24.dp)

        VsButton(
            state =
                if (state.isLoading || textFieldState.text.isEmpty()) VsButtonState.Disabled
                else VsButtonState.Enabled,
            modifier = Modifier.fillMaxWidth(),
            onClick = onApplyClick,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isLoading) {
                    V2Loading(modifier = Modifier.size(16.dp))
                }

                Text(
                    text = stringResource(R.string.add_referral_sheet_apply),
                    style = Theme.brockmann.button.semibold.regular,
                    color =
                        if (state.isLoading || textFieldState.text.isEmpty())
                            Theme.v2.colors.text.button.disabled
                        else Theme.v2.colors.text.button.primary,
                )
            }
        }
    }
}
