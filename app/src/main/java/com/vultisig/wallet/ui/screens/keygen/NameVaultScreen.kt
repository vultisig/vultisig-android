package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.referral.AddReferralBottomSheet
import com.vultisig.wallet.ui.components.referral.AddReferralHeaderButton
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.keygen.NameVaultUiModel
import com.vultisig.wallet.ui.models.keygen.NameVaultViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun NameVaultScreen(model: NameVaultViewModel = hiltViewModel()) {
    val state by model.state.collectAsState()
    var showReferralSheet by rememberSaveable { mutableStateOf(false) }
    val referralCode by model.referralCode.collectAsState()

    NameVaultScreen(
        state = state,
        textFieldState = model.nameFieldState,
        hasReferral = !referralCode.isNullOrEmpty(),
        onReferralClick = { showReferralSheet = true },
        onNextClick = model::navigateToEmail,
        onClearClick = model::clearInput,
        onBackClick = model::back,
    )

    if (state.showServerVaultExistsWarning) {
        AlertDialog(
            containerColor = Theme.v2.colors.backgrounds.secondary,
            onDismissRequest = model::dismissServerVaultWarning,
            title = {
                Text(
                    text = stringResource(R.string.name_vault_server_exists_warning_title),
                    color = Theme.v2.colors.neutrals.n100,
                    style = Theme.montserrat.heading5,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.name_vault_server_exists_warning_message),
                    color = Theme.v2.colors.neutrals.n100,
                    style = Theme.montserrat.body2,
                )
            },
            confirmButton = {
                TextButton(onClick = model::continueWithServerVaultWarning) {
                    Text(
                        text = stringResource(R.string.name_vault_server_exists_warning_continue),
                        color = Theme.v2.colors.neutrals.n100,
                        style = Theme.montserrat.body3,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = model::dismissServerVaultWarning) {
                    Text(
                        text = stringResource(R.string.name_vault_server_exists_warning_back),
                        color = Theme.v2.colors.neutrals.n100,
                        style = Theme.montserrat.body3,
                    )
                }
            },
        )
    }

    if (showReferralSheet) {
        AddReferralBottomSheet(
            onApply = { _ -> showReferralSheet = false },
            onDismissRequest = { showReferralSheet = false },
        )
    }
}

@Composable
private fun NameVaultScreen(
    state: NameVaultUiModel,
    textFieldState: TextFieldState,
    hasReferral: Boolean = false,
    onReferralClick: () -> Unit = {},
    onNextClick: () -> Unit,
    onClearClick: () -> Unit,
    onBackClick: () -> Unit,
) {

    val canProceed = state.isNextButtonEnabled && !state.isLoading

    V2Scaffold(
        title = null,
        onBackClick = onBackClick,
        actions = { AddReferralHeaderButton(hasReferral = hasReferral, onClick = onReferralClick) },
        bottomBar = {
            VsButton(
                label = stringResource(R.string.fast_vault_name_screen_next),
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .testTag("NameVaultScreen.continue"),
                state = if (canProceed) VsButtonState.Enabled else VsButtonState.Disabled,
                onClick = onNextClick,
            )
        },
    ) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Column {
            Text(
                text = stringResource(R.string.fast_vault_name_screen_title),
                style = Theme.brockmann.headings.largeTitle,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(16.dp)
            Text(
                text = stringResource(R.string.fast_vault_name_screen_desc),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
            )
            VsTextInputField(
                textFieldState = textFieldState,
                trailingIcon = R.drawable.close_circle,
                onTrailingIconClick = onClearClick,
                focusRequester = focusRequester,
                footNote = state.errorMessage?.asString(),
                imeAction = ImeAction.Go,
                onKeyboardAction = { if (canProceed) onNextClick() },
                modifier =
                    Modifier.fillMaxSize().wrapContentHeight().testTag("NameVaultScreen.nameField"),
            )
        }
    }
}

@Preview
@Composable
private fun FastVaultNameScreenPreview() {
    NameVaultScreen(
        state = NameVaultUiModel(),
        textFieldState = rememberTextFieldState(),
        onNextClick = {},
        onClearClick = {},
        onBackClick = {},
    )
}
