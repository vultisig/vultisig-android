package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.ReferralViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralScreen(
    navController: NavController,
    model: ReferralViewModel = hiltViewModel(),
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.referral_screen_title),
                onBackClick = {
                    navController.popBackStack()
                },
            )
        },
        content = { contentPadding ->
            ReferralContent(contentPadding)
        },
    )
}

@Composable
internal fun ReferralContent(
    paddingValues: PaddingValues,
    onConfirmEditOrSaveExternalReferral: () -> Unit = {},
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(paddingValues)
            .verticalScroll(scrollState)
            .imePadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            contentAlignment = Alignment.Center
        ) {

            Image(
                painter = painterResource(id = R.drawable.crypto_natives),
                contentDescription = "ReferralImage",
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        UiSpacer(16.dp)

        // TODO: This can be shown or hide
        Text(
            text = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        color = Theme.colors.text.primary,
                        fontSize = 16.sp,
                        fontFamily = Theme.brockmann.body.m.medium.fontFamily,
                        fontWeight = Theme.brockmann.body.m.medium.fontWeight,
                    )
                ) {
                    append("Save ")
                }
                withStyle(
                    style = SpanStyle(
                        color = Theme.colors.primary.accent4,
                        fontSize = 16.sp,
                        fontFamily = Theme.brockmann.body.m.medium.fontFamily,
                        fontWeight = Theme.brockmann.body.m.medium.fontWeight,
                    )
                ) {
                    append("10%")
                }
                withStyle(
                    style = SpanStyle(
                        color = Theme.colors.text.primary,
                        fontSize = 16.sp,
                        fontFamily = Theme.brockmann.body.m.medium.fontFamily,
                        fontWeight = Theme.brockmann.body.m.medium.fontWeight,
                    )
                ) {
                    append(" on swaps - Add a Referral")
                }
            },
            color = Theme.colors.text.primary,
            textAlign = TextAlign.Center,
        )

        UiSpacer(16.dp)

        VsTextInputField(
            textFieldState = TextFieldState(),
            innerState = VsTextInputFieldInnerState.Default,
            hint = stringResource(R.string.referral_screen_code_hint),
            trailingIcon = R.drawable.clipboard_paste,
            onTrailingIconClick = { },
            footNote = null, // state.errorMessage?.asString(),
            focusRequester = null, //focusRequester,
            imeAction = ImeAction.Done,
            onKeyboardAction = {

            },
        )

        UiSpacer(16.dp)

        VsButton(
            label = "Save referral code",
            modifier = Modifier
                .fillMaxWidth(),
            variant = VsButtonVariant.Secondary,
            state = VsButtonState.Enabled,
            onClick = onConfirmEditOrSaveExternalReferral,
        )

        UiSpacer(16.dp)

        Text(
            text = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        color = Theme.colors.text.primary,
                        fontSize = 14.sp,
                        fontFamily = Theme.brockmann.body.m.regular.fontFamily,
                        fontWeight = Theme.brockmann.body.m.regular.fontWeight,
                    )
                ) {
                    append("Create your own code and earn  ")
                }
                withStyle(
                    style = SpanStyle(
                        color = Theme.colors.primary.accent4,
                        fontSize = 14.sp,
                        fontFamily = Theme.brockmann.body.m.regular.fontFamily,
                        fontWeight = Theme.brockmann.body.m.regular.fontWeight,
                    )
                ) {
                    append("20%")
                }
                withStyle(
                    style = SpanStyle(
                        color = Theme.colors.text.primary,
                        fontSize = 14.sp,
                        fontFamily = Theme.brockmann.body.m.regular.fontFamily,
                        fontWeight = Theme.brockmann.body.m.regular.fontWeight,
                    )
                ) {
                    append(" on referred swaps")
                }
            },
            color = Theme.colors.text.primary,
            textAlign = TextAlign.Center,
        )

        UiSpacer(16.dp)

        VsButton(
            label = "Create referral",
            modifier = Modifier
                .fillMaxWidth(),
            variant = VsButtonVariant.Primary,
            state = VsButtonState.Enabled,
            onClick = onConfirmEditOrSaveExternalReferral,
        )
    }
}