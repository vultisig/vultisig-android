package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.StyledText
import com.vultisig.wallet.ui.components.StyledTextPart
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.ReferralUiState
import com.vultisig.wallet.ui.models.referral.ReferralViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsClipboardService

@Composable
internal fun ReferralScreen(
    navController: NavController,
    model: ReferralViewModel = hiltViewModel(),
) {
    val clipboardData = VsClipboardService.getClipboardData()
    val state by model.state.collectAsState()

    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    LaunchedEffect(savedStateHandle?.get<String>(NEW_EXTERNAL_REFERRAL_CODE)) {
        val code = savedStateHandle?.get<String>(NEW_EXTERNAL_REFERRAL_CODE).orEmpty()
        model.onNewEditedReferral(code)
        savedStateHandle?.remove<String>(NEW_EXTERNAL_REFERRAL_CODE)
    }

    ReferralScreen(
        onBackPressed = navController::popBackStack,
        onPasteIcon = model::onPasteIconClick,
        onSavedOrEditExternalReferral = model::onSaveOrEditExternalReferral,
        onCreateOrEditReferral = model::onCreateOrEditReferral,
        state = state,
        clipboardData = clipboardData,
        referralState = model.referralCodeTextFieldState,
    )
}

@Composable
private fun ReferralScreen(
    onBackPressed: () -> Unit,
    onPasteIcon: (String) -> Unit,
    onSavedOrEditExternalReferral: () -> Unit,
    onCreateOrEditReferral: () -> Unit,
    state: ReferralUiState,
    clipboardData: MutableState<String?>,
    referralState: TextFieldState,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.referral_screen_title),
                onBackClick = {
                    onBackPressed()
                },
            )
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
                    .imePadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.crypto_natives_v2),
                        contentDescription = "ReferralImage",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                UiSpacer(1f)

                StyledText(
                    parts = listOf(
                        StyledTextPart(stringResource(R.string.referral_save)),
                        StyledTextPart("10%", Theme.colors.primary.accent4),
                        StyledTextPart(stringResource(R.string.referral_add_referral))
                    ),
                    fontSize = 16.sp,
                    fontFamily = Theme.brockmann.body.m.medium.fontFamily,
                    fontWeight = Theme.brockmann.body.m.medium.fontWeight,
                )

                UiSpacer(16.dp)

                VsTextInputField(
                    textFieldState = referralState,
                    innerState = state.referralMessageState,
                    hint = stringResource(R.string.referral_screen_code_hint),
                    trailingIcon = if (state.isSaveEnabled) R.drawable.clipboard_paste else null,
                    onTrailingIconClick = {
                        val content = clipboardData.value
                        if (content.isNullOrEmpty()) return@VsTextInputField
                        onPasteIcon(content)
                    },
                    footNote = state.referralMessage,
                    focusRequester = null, //focusRequester,
                    imeAction = ImeAction.Go,
                    keyboardType = KeyboardType.Text,
                    enabled = state.isSaveEnabled,
                )

                UiSpacer(16.dp)

                VsButton(
                    label = if (state.isSaveEnabled) {
                        stringResource(R.string.referral_save_referral_code)
                    } else {
                        stringResource(R.string.referral_edit_referred)
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    variant = VsButtonVariant.Secondary,
                    state = VsButtonState.Enabled,
                    onClick = onSavedOrEditExternalReferral,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Theme.colors.borders.light,
                    )

                    Text(
                        text = stringResource(R.string.referral_or),
                        modifier = Modifier.padding(16.dp),
                        color = Theme.colors.text.primary,
                        style = Theme.brockmann.supplementary.caption,
                        textAlign = TextAlign.Center,
                    )

                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Theme.colors.borders.light,
                    )
                }

                StyledText(
                    parts = listOf(
                        StyledTextPart(stringResource(R.string.referral_create_code_and_earn)),
                        StyledTextPart("20%", Theme.colors.primary.accent4),
                        StyledTextPart(stringResource(R.string.referral_on_referred_swaps))
                    ),
                    fontSize = 14.sp,
                    fontFamily = Theme.brockmann.body.m.regular.fontFamily,
                    fontWeight = Theme.brockmann.body.m.regular.fontWeight
                )

                UiSpacer(16.dp)

                VsButton(
                    label = if (state.isCreateEnabled) {
                        stringResource(R.string.referral_create_referral)
                    } else {
                        stringResource(R.string.referral_edit_referral)
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    variant = VsButtonVariant.Primary,
                    state = VsButtonState.Enabled,
                    onClick = onCreateOrEditReferral,
                )
            }
        },
    )
}
