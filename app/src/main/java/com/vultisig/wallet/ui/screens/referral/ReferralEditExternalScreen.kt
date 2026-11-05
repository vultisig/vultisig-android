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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.referral.EditExternalReferralUiState
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

    ReferralEditExternalScreen(
        onBackClick = {
            val code = model.referralCodeTextFieldState.text.toString()
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set(NEW_EXTERNAL_REFERRAL_CODE, code)
            navController.popBackStack()
        },
        state = state,
        referralCodeTextFieldState = model.referralCodeTextFieldState,
        onSaveReferral = model::onSaveReferral,
        onPasteClick = {
            val content = clipboardData.value
            model.onPasteIconClick(content)
        },
    )
}

@Composable
private fun ReferralEditExternalScreen(
    state: EditExternalReferralUiState,
    referralCodeTextFieldState: TextFieldState,
    onBackClick: () -> Unit,
    onSaveReferral: () -> Unit,
    onPasteClick: () -> Unit,
) {
    V2Scaffold(
        title = stringResource(R.string.referral_save_referred_code),
        onBackClick = onBackClick,
        bottomBar = {
            VsButton(
                label = stringResource(R.string.referral_save_referred_code),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                variant = VsButtonVariant.Primary,
                state = VsButtonState.Enabled,
                onClick = onSaveReferral,
            )
        },
        content = {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .imePadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = saveReferralHeader(),
                    textAlign = TextAlign.Start,
                    color = Theme.v2.colors.text.primary,
                    style = Theme.brockmann.headings.subtitle,
                )

                UiSpacer(8.dp)

                VsTextInputField(
                    textFieldState = referralCodeTextFieldState,
                    innerState = state.referralMessageState,
                    hint = stringResource(R.string.referral_screen_code_hint),
                    trailingIcon = R.drawable.clipboard_paste,
                    onTrailingIconClick = onPasteClick,
                    footNote = state.referralMessage?.asString(),
                    focusRequester = null,
                    imeAction = ImeAction.Go,
                    keyboardType = KeyboardType.Text,
                )
            }
        },
    )
}

@Composable
private fun saveReferralHeader(): AnnotatedString {
    val text = stringResource(R.string.referral_save_header, FRIEND_DISCOUNT_LABEL)
    val start = text.indexOf(FRIEND_DISCOUNT_LABEL)
    return AnnotatedString(
        text = text,
        spanStyles =
            listOf(
                AnnotatedString.Range(
                    SpanStyle(color = Theme.v2.colors.primary.accent4),
                    start,
                    start + FRIEND_DISCOUNT_LABEL.length,
                )
            ),
    )
}

private const val FRIEND_DISCOUNT_LABEL = "10%"

internal const val NEW_EXTERNAL_REFERRAL_CODE = "NEW_EXTERNAL_REFERRAL_CODE"

@Preview(showBackground = true)
@Composable
private fun ReferralEditExternalScreenPreview() {
    val textFieldState = TextFieldState("FRIEND-CODE-123")
    ReferralEditExternalScreen(
        state = EditExternalReferralUiState(),
        referralCodeTextFieldState = textFieldState,
        onBackClick = {},
        onSaveReferral = {},
        onPasteClick = {},
    )
}
