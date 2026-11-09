package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldStyle
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.referral.EditVaultReferralUiState
import com.vultisig.wallet.ui.models.referral.EditVaultReferralViewModel
import com.vultisig.wallet.ui.models.referral.PayoutAssetUiModel
import com.vultisig.wallet.ui.models.referral.ReferralError
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsClipboardService

@Composable
internal fun ReferralEditVaultScreen(
    navController: NavController,
    model: EditVaultReferralViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by model.state.collectAsStateWithLifecycle()

    ReferralEditVaultScreen(
        state = state,
        onBackPressed = navController::popBackStack,
        onSavedReferral = model::onSavedReferral,
        referralTextFieldState = model.referralTextFieldState,
        onDecrementCounter = model::onDecrementCounter,
        onIncrementCounter = model::onIncrementCounter,
        onSelectPayoutAsset = model::onSelectPayoutAsset,
        onDismissError = model::onDismissError,
        onCopyReferralCode = { VsClipboardService.copy(context, it) },
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
    onSelectPayoutAsset: () -> Unit,
    onDismissError: () -> Unit,
    referralTextFieldState: TextFieldState,
) {
    if (state.error != null) {
        val message =
            when (state.error) {
                ReferralError.BALANCE_ERROR ->
                    stringResource(R.string.referral_create_not_enough_balance)
                else -> stringResource(R.string.referral_create_unknown_error)
            }

        UiAlertDialog(
            title = stringResource(R.string.dialog_default_error_title),
            text = message,
            confirmTitle = stringResource(R.string.try_again),
            onDismiss = onDismissError,
        )
    }

    V2Scaffold(
        title = stringResource(R.string.referral_edit_referral),
        onBackClick = onBackPressed,
        content = {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(REFERRAL_FORM_GAP),
            ) {
                EditReferralForm(
                    state = state,
                    referralTextFieldState = referralTextFieldState,
                    onCopyReferralCode = onCopyReferralCode,
                    onIncrementCounter = onIncrementCounter,
                    onDecrementCounter = onDecrementCounter,
                    onSelectPayoutAsset = onSelectPayoutAsset,
                )
            }
        },
        bottomBar = {
            VsButton(
                label = stringResource(R.string.save_changes),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                variant = VsButtonVariant.Primary,
                state =
                    if (state.isSaveEnabled) {
                        VsButtonState.Enabled
                    } else {
                        VsButtonState.Disabled
                    },
                onClick = onSavedReferral,
            )
        },
    )
}

@Composable
private fun EditReferralForm(
    state: EditVaultReferralUiState,
    referralTextFieldState: TextFieldState,
    onCopyReferralCode: (String) -> Unit,
    onIncrementCounter: () -> Unit,
    onDecrementCounter: () -> Unit,
    onSelectPayoutAsset: () -> Unit,
) {
    VsTextInputField(
        textFieldState = referralTextFieldState,
        label = stringResource(R.string.referral_view_your_referral_code),
        innerState = VsTextInputFieldInnerState.Default,
        focusRequester = null,
        trailingIcon = R.drawable.copy2,
        onTrailingIconClick = {
            val text = referralTextFieldState.text.toString()
            onCopyReferralCode(text)
        },
        imeAction = ImeAction.Go,
        keyboardType = KeyboardType.Text,
        enabled = false,
        style = VsTextInputFieldStyle.Settings,
    )

    UiHorizontalDivider()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.referral_edit_extend_expiration),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )

        CounterYearExpiration(
            count = state.referralCounter,
            defaultInitCounter = 0,
            onIncrement = onIncrementCounter,
            onDecrement = onDecrementCounter,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            color = Theme.v2.colors.text.primary,
            style = Theme.brockmann.supplementary.caption,
            text =
                stringResource(
                    if (state.referralCounter > 0) {
                        R.string.referral_edit_new_expiration
                    } else {
                        R.string.referral_create_expiration_date
                    }
                ),
            textAlign = TextAlign.Start,
        )

        UiSpacer(1f)

        Text(
            color = Theme.v2.colors.text.primary,
            style = Theme.brockmann.supplementary.caption,
            text = state.referralExpiration,
            textAlign = TextAlign.Start,
        )
    }

    UiHorizontalDivider()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.referral_choose_payout_asset),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )

        PayoutAssetSelection(asset = state.payoutAsset, onClick = onSelectPayoutAsset)
    }

    UiHorizontalDivider()

    EstimatedNetworkFee(
        title = stringResource(R.string.referral_create_cost),
        tokenGas = state.referralCostAmountFormatted,
        fiatGas = state.referralCostFiatFormatted,
    )
}

@Composable
private fun PayoutAssetSelection(asset: PayoutAssetUiModel?, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(Theme.v2.radius.md)
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = Theme.v2.radius.md,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (asset != null) {
            TokenLogo(
                errorLogoModifier = Modifier.size(32.dp).background(Theme.v2.colors.neutrals.n100),
                logo = asset.logo,
                title = asset.ticker,
                modifier = Modifier.size(32.dp),
            )

            UiSpacer(6.dp)

            Text(
                text = asset.ticker,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
            )
        }

        UiSpacer(1f)

        Icon(
            painter = painterResource(id = R.drawable.ic_caret_right),
            contentDescription = null,
            tint = Theme.v2.colors.text.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReferralEditVaultScreenPreview() {
    val referralTextFieldState = TextFieldState("VULTISIG-REF-2024")

    ReferralEditVaultScreen(
        state =
            EditVaultReferralUiState(
                referralCounter = 2,
                referralExpiration = "December 31, 2025",
                referralCostAmountFormatted = "0.02 RUNE",
                referralCostFiatFormatted = "$1.50",
                payoutAsset =
                    PayoutAssetUiModel(
                        asset = "THOR.RUNE",
                        logo = "rune",
                        ticker = "RUNE",
                        chain = "THORChain",
                    ),
                isSaveEnabled = true,
                error = null,
            ),
        onBackPressed = {},
        onCopyReferralCode = {},
        onSavedReferral = {},
        onIncrementCounter = {},
        onDecrementCounter = {},
        onSelectPayoutAsset = {},
        onDismissError = {},
        referralTextFieldState = referralTextFieldState,
    )
}
