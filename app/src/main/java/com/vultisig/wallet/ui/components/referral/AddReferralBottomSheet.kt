package com.vultisig.wallet.ui.components.referral

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.models.keygen.AddReferralUiModel
import com.vultisig.wallet.ui.models.keygen.AddReferralViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddReferralBottomSheet(
    onApply: (String) -> Unit,
    onDismissRequest: () -> Unit,
    viewModel: AddReferralViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Theme.v2.colors.backgrounds.secondary,
        shape = RoundedCornerShape(24.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
    ) {
        AddReferralBottomSheetContent(
            state = state,
            textFieldState = viewModel.textFieldState,
            onApplyClick = { viewModel.applyReferral(onApply) },
            onClearClick = viewModel::clearInput,
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
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 24.dp, bottom = 32.dp),
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
            trailingIcon = R.drawable.close_circle,
            onTrailingIconClick = onClearClick,
            modifier = Modifier.fillMaxWidth(),
        )

        UiSpacer(24.dp)

        VsButton(
            label = stringResource(R.string.add_referral_sheet_apply),
            state = if (state.isLoading || textFieldState.text.isEmpty())
                VsButtonState.Disabled else VsButtonState.Enabled,
            modifier = Modifier.fillMaxWidth(),
            onClick = onApplyClick,
        )
    }
}
