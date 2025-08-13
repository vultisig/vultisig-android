package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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

@Composable
internal fun ReferralEditExternalScreen(
    navController: NavController,
    model: EditExternalReferralViewModel = hiltViewModel(),
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = "Edit Referred Code",
                onBackClick = {
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
                    text = "Use referred code",
                    textAlign = TextAlign.Start,
                    color = Theme.colors.text.primary,
                    style = Theme.brockmann.body.s.medium,
                )

                UiSpacer(8.dp)

                VsTextInputField(
                    textFieldState = model.referralCodeTextFieldState,
                    innerState = VsTextInputFieldInnerState.Default,
                    hint = stringResource(R.string.referral_screen_code_hint),
                    trailingIcon = R.drawable.clipboard_paste,
                    onTrailingIconClick = {

                    },
                    footNote = "",
                    focusRequester = null,
                    imeAction = ImeAction.Go,
                    keyboardType = KeyboardType.Text,
                )

                UiSpacer(1f)

                VsButton(
                    label = "Save referred code",
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