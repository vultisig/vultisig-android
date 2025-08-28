package com.vultisig.wallet.ui.screens.referral

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiGradientDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.EditVaultReferralUiState
import com.vultisig.wallet.ui.models.referral.EditVaultReferralViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralEditVaultScreen(
    navController: NavController,
    model: EditVaultReferralViewModel = hiltViewModel(),
) {
    val clipboardManager =
        LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val state by model.state.collectAsStateWithLifecycle()

    ReferralEditVaultScreen(
        state = state,
        onBackPressed = navController::popBackStack,
        onSavedReferral = model::onSavedReferral,
        referralTextFieldState = model.referralTexFieldState,
        onDecrementCounter = model::onDecrementCounter,
        onIncrementCounter = model::onIncrementCounter,
        onCopyReferralCode = {
            val clip = ClipData.newPlainText("ReferralCode", it)
            clipboardManager?.setPrimaryClip(clip)
        },
    )
}

@Composable
private fun ReferralEditVaultScreen(
    state: EditVaultReferralUiState,
    onBackPressed: () -> Unit,
    onCopyReferralCode: (String) -> Unit,
    onSavedReferral: () -> Unit,
    onIncrementCounter: () -> Unit,
    onDecrementCounter: () -> Unit,
    referralTextFieldState: TextFieldState,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.referral_edit_referral),
                onBackClick = onBackPressed,
            )
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                Text(
                    text = stringResource(R.string.referral_view_your_referral_code),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.primary,
                )

                UiSpacer(8.dp)

                VsTextInputField(
                    textFieldState = referralTextFieldState,
                    innerState = VsTextInputFieldInnerState.Default,
                    focusRequester = null,
                    trailingIcon = R.drawable.ic_copy,
                    onTrailingIconClick = {
                        val text = referralTextFieldState.text.toString()
                        onCopyReferralCode(text)
                    },
                    imeAction = ImeAction.Go,
                    keyboardType = KeyboardType.Text,
                    enabled = false,
                )

                UiSpacer(16.dp)

                UiGradientDivider(
                    initialColor = Theme.colors.backgrounds.primary,
                    endColor = Theme.colors.backgrounds.primary,
                )

                UiSpacer(16.dp)

                CounterYearExpiration(
                    count = state.referralCounter,
                    defaultInitCounter = 0,
                    onIncrement = onIncrementCounter,
                    onDecrement = onDecrementCounter,
                )

                UiSpacer(16.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        color = Theme.colors.text.extraLight,
                        style = Theme.brockmann.body.s.medium,
                        text = stringResource(R.string.referral_create_expiration_date),
                        textAlign = TextAlign.Start,
                    )

                    UiSpacer(1f)

                    Text(
                        color = Theme.colors.text.primary,
                        style = Theme.brockmann.body.s.medium,
                        text = state.referralExpiration,
                        textAlign = TextAlign.Start,
                    )
                }

                UiSpacer(16.dp)

                UiGradientDivider(
                    initialColor = Theme.colors.backgrounds.primary,
                    endColor = Theme.colors.backgrounds.primary,
                )

                UiSpacer(16.dp)

                EstimatedNetworkFee(
                    title = stringResource(R.string.referral_create_cost),
                    tokenGas = state.referralCostAmount,
                    fiatGas = state.referralCostFiat,
                )
            }
        },
        bottomBar = {
            VsButton(
                label = "Save Changes",
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 32.dp)
                    .fillMaxWidth(),
                variant = VsButtonVariant.Primary,
                state = if (state.referralCounter != 0) {
                    VsButtonState.Enabled
                } else {
                    VsButtonState.Disabled
                },
                onClick = onSavedReferral,
            )
        }
    )
}