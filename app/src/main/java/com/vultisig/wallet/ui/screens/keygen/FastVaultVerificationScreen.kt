package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.KeyboardDetector
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsCircularLoading
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.inputs.VsCodeInputField
import com.vultisig.wallet.ui.components.inputs.VsCodeInputFieldState
import com.vultisig.wallet.ui.components.rememberClipboardText
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v3.V3Icon
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.keygen.FastVaultVerificationViewModel
import com.vultisig.wallet.ui.models.keygen.FastVaultVerificationViewModel.Companion.FAST_VAULT_VERIFICATION_CODE_LENGTH
import com.vultisig.wallet.ui.models.keygen.VaultBackupState
import com.vultisig.wallet.ui.models.keygen.VerifyPinState
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FastVaultVerificationScreen(
    model: FastVaultVerificationViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    V2BottomSheet(
        onDismissRequest = model::back,
    ) {
        FastVaultVerificationScreen(
            state = state,
            codeFieldState = model.codeFieldState,
            onBackClick = model::back,
            onCodeChanged = model::processCode,
            onPasteClick = model::paste,
            onChangeEmailClick = model::changeEmail,
        )
    }

}

@Composable
private fun FastVaultVerificationScreen(
    state: VaultBackupState,
    codeFieldState: TextFieldState,
    onBackClick: () -> Unit,
    onCodeChanged: (String) -> Unit,
    onPasteClick: (String) -> Unit,
    onChangeEmailClick: () -> Unit,
) {
    val textToPaste by rememberClipboardText {
        // isDigitsOnly return true for empty string! ("".isDigitsOnly == true)
        it?.isNotEmpty() == true && it.isDigitsOnly()
    }
    val hasClipContent = textToPaste != null

    V3Scaffold(
        title = null,
        onBackClick = onBackClick,
        content = {
            val scrollState = rememberScrollState()
            val coroutineScope = rememberCoroutineScope()

            KeyboardDetector(
                onKeyboardIsOpen = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                }
            )

            Column(
                Modifier
                    .imePadding()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

                UiSpacer(
                    size = 32.dp
                )



                V3Icon(
                    logo = R.drawable.icon_shield_solid,
                    shinedBottom = Theme.v2.colors.alerts.info,
                    borderWidth = 1.5.dp,
                    borderColor = Theme.v2.colors.neutrals.n50.copy(alpha = 0.15f)
                )

                UiSpacer(
                    size = 32.dp
                )

                Text(
                    text = stringResource(R.string.backup_4_digit_code_received_via_email),
                    style = Theme.brockmann.headings.title2,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.Center,
                )
                UiSpacer(16.dp)
                Text(
                    text = stringResource(R.string.backup_this_will_activate_the_co_signer),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary
                )
                UiSpacer(32.dp)

                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        6.dp,
                        alignment = Alignment.CenterHorizontally
                    ),
                    verticalAlignment = CenterVertically,
                ) {
                    VsCodeInputField(
                        textFieldState = codeFieldState,
                        onChangeInput = onCodeChanged,
                        maxCharacters = FAST_VAULT_VERIFICATION_CODE_LENGTH,
                        state = when (state.verifyPinState) {
                            VerifyPinState.Success -> VsCodeInputFieldState.Success
                            VerifyPinState.Error -> VsCodeInputFieldState.Error
                            else -> VsCodeInputFieldState.Default
                        },
                        modifier = Modifier
                            .testTag("FastVaultVerificationScreen.codeField")
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(
                                color = Theme.v2.colors.backgrounds.secondary,
                                shape = CircleShape
                            )
                            .padding(
                                horizontal = 20.dp,
                                vertical = 14.dp
                            )
                            .clickable(
                                enabled = hasClipContent,
                                onClick = {
                                    if (hasClipContent) {
                                        onPasteClick(textToPaste.toString())
                                    }
                                },
                            )
                    ) {
                        Text(
                            text = stringResource(R.string.vault_backup_screen_paste),
                            style = Theme.brockmann.body.s.medium,
                            color = if (hasClipContent) Theme.v2.colors.text.button.primary
                            else Theme.v2.colors.text.button.disabled,
                        )
                    }
                }

                UiSpacer(12.dp)
                AnimatedContent(
                    targetState = state.verifyPinState, label = "verifying state"
                ) { verifyPinState ->
                    when (verifyPinState) {
                        VerifyPinState.Idle -> Unit

                        VerifyPinState.Loading -> Row(
                            verticalAlignment = CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VsCircularLoading(
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.vault_backup_verifying_pin),
                                color = Theme.v2.colors.text.primary,
                                style = Theme.brockmann.supplementary.footnote
                            )
                        }

                        VerifyPinState.Success -> Unit

                        VerifyPinState.Error -> {
                            Text(
                                text = stringResource(R.string.vault_backup_error_pin),
                                color = Theme.v2.colors.alerts.error,
                                style = Theme.brockmann.supplementary.footnote
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = state.verifyPinState,
                    label = "bottomBar"
                ) { verifyPinState ->
                    when (verifyPinState) {
                        VerifyPinState.Idle -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.vault_backup_screen_email_sent_to, state.sentEmailTo
                                    ),
                                    color = Theme.v2.colors.text.tertiary,
                                    style = Theme.brockmann.supplementary.footnote,
                                )
                                UiSpacer(
                                    size = 12.dp
                                )

                                Text(
                                    text = stringResource(R.string.backup_use_a_different_email),
                                    color = Theme.v2.colors.text.secondary,
                                    style = Theme.brockmann.body.s.medium,
                                    modifier = Modifier
                                        .clip(
                                            shape = CircleShape
                                        )
                                        .background(
                                            color = Theme.v2.colors.buttons.ctaDisabled
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = Theme.v2.colors.border.extraLight
                                        )
                                        .padding(
                                            horizontal = 12.dp,
                                            vertical = 8.dp,
                                        )
                                        .clickOnce(
                                            onClick = onChangeEmailClick,
                                            coolDownPeriod = 1500L
                                        ),
                                )
                            }
                        }

                        VerifyPinState.Loading -> Unit
                        VerifyPinState.Success -> Unit
                        VerifyPinState.Error -> {
                            val annotatedString = buildAnnotatedString {
                                append(stringResource(R.string.vault_backup_screen_donot_recieve_email))
                                append(" ")
                                withStyle(
                                    style = SpanStyle(
                                        color = Theme.v2.colors.text.secondary,
                                        textDecoration = TextDecoration.Underline
                                    )
                                ) {
                                    append(stringResource(R.string.vault_backup_screen_restart_keygen))
                                }
                            }

                            Text(
                                text = annotatedString,
                                style = Theme.brockmann.supplementary.footnote,
                                color = Theme.v2.colors.text.tertiary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 24.dp)
                                    .clickable(onClick = onChangeEmailClick),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    )
}


@Preview
@Composable
private fun VaultBackupScreenPreview() {
    FastVaultVerificationScreen(
        state = VaultBackupState(
            verifyPinState = VerifyPinState.Idle,
            sentEmailTo = "test@email.com"
        ),
        codeFieldState = TextFieldState(),
        onBackClick = {},
        onCodeChanged = {},
        onPasteClick = {},
        onChangeEmailClick = {}
    )
}
