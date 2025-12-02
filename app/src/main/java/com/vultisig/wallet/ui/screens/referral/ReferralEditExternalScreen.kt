package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.EditExternalReferralViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsClipboardService
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun ReferralEditExternalScreen(
    navController: NavController,
    model: EditExternalReferralViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()
    val clipboardData = VsClipboardService.getClipboardData()

    Scaffold(
        containerColor = Theme.v2.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.referral_edit_external_title),
                onBackClick = {
                    val code = model.referralCodeTextFieldState.text.toString()
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(NEW_EXTERNAL_REFERRAL_CODE, code)
                    navController.popBackStack()
                },
            )
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.referral_use_referred_code),
                    textAlign = TextAlign.Start,
                    color = Theme.v2.colors.text.primary,
                    style = Theme.brockmann.body.s.medium,
                )

                UiSpacer(8.dp)

                VsTextInputField(
                    textFieldState = model.referralCodeTextFieldState,
                    innerState = state.referralMessageState,
                    hint = stringResource(R.string.referral_screen_code_hint),
                    trailingIcon = R.drawable.clipboard_paste,
                    onTrailingIconClick = {
                        val content = clipboardData.value
                        if (content.isNullOrEmpty()) return@VsTextInputField
                        model.onPasteIconClick(content)
                    },
                    footNote = state.referralMessage?.asString(),
                    focusRequester = null,
                    imeAction = ImeAction.Go,
                    keyboardType = KeyboardType.Text,
                )

                UiSpacer(1f)

                VsButton(
                    label = stringResource(R.string.referral_save_referred_code),
                    modifier = Modifier.fillMaxWidth(),
                    variant = VsButtonVariant.Primary,
                    state = VsButtonState.Enabled,
                    onClick = model::onSaveReferral,
                )

                UiSpacer(32.dp)
            }
        }
    )
}

internal const val NEW_EXTERNAL_REFERRAL_CODE = "NEW_EXTERNAL_REFERRAL_CODE"

@Preview(showBackground = true)
@Composable
private fun ReferralEditExternalScreenPreview() {
    val textFieldState = TextFieldState("FRIEND-CODE-123")
    
    Scaffold(
        containerColor = Theme.v2.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.referral_edit_external_title),
                onBackClick = {},
            )
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.referral_use_referred_code),
                    textAlign = TextAlign.Start,
                    color = Theme.v2.colors.text.primary,
                    style = Theme.brockmann.body.s.medium,
                )

                UiSpacer(8.dp)

                VsTextInputField(
                    textFieldState = textFieldState,
                    innerState = VsTextInputFieldInnerState.Default,
                    hint = stringResource(R.string.referral_screen_code_hint),
                    trailingIcon = R.drawable.clipboard_paste,
                    onTrailingIconClick = {},
                    footNote = null,
                    focusRequester = null,
                    imeAction = ImeAction.Go,
                    keyboardType = KeyboardType.Text,
                )

                UiSpacer(1f)

                VsButton(
                    label = stringResource(R.string.referral_save_referred_code),
                    modifier = Modifier.fillMaxWidth(),
                    variant = VsButtonVariant.Primary,
                    state = VsButtonState.Enabled,
                    onClick = {},
                )
            }
        },
    )
}