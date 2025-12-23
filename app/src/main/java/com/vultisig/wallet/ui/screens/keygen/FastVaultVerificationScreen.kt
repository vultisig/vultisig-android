package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
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
import com.vultisig.wallet.ui.components.scaffold.VsScaffold
import com.vultisig.wallet.ui.models.keygen.FastVaultVerificationViewModel
import com.vultisig.wallet.ui.models.keygen.FastVaultVerificationViewModel.Companion.FAST_VAULT_VERIFICATION_CODE_LENGTH
import com.vultisig.wallet.ui.models.keygen.VaultBackupState
import com.vultisig.wallet.ui.models.keygen.VerifyPinState
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.launch

@Composable
internal fun FastVaultVerificationScreen(
    model: FastVaultVerificationViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    FastVaultVerificationScreen(
        state = state,
        codeFieldState = model.codeFieldState,
        onBackClick = model::back,
        onCodeChanged = model::processCode,
        onPasteClick = model::paste,
        onChangeEmailClick = model::changeEmail,
    )
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

    VsScaffold(
        title = null,
        onBackClick = onBackClick,
        bottomBar = {
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
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.vault_backup_screen_email_sent_to, state.sentEmailTo
                                ),
                                color = Theme.colors.text.extraLight,
                                style = Theme.brockmann.supplementary.footnote,
                            )

                            Text(
                                text = stringResource(
                                    R.string.vault_backup_screen_change_email,
                                ),
                                color = Theme.colors.text.light,
                                textDecoration = TextDecoration.Underline,
                                style = Theme.brockmann.supplementary.footnote,
                                modifier = Modifier.clickOnce(
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
                                    color = Theme.colors.text.light,
                                    textDecoration = TextDecoration.Underline
                                )
                            ) {
                                append(stringResource(R.string.vault_backup_screen_restart_keygen))
                            }
                        }

                        Text(
                            text = annotatedString,
                            style = Theme.brockmann.supplementary.footnote,
                            color = Theme.colors.text.extraLight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 24.dp)
                                .clickable(onClick = onChangeEmailClick),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
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
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = stringResource(R.string.enter_backup_screen_title),
                    style = Theme.brockmann.headings.largeTitle,
                    color = Theme.colors.text.primary,
                )
                UiSpacer(16.dp)
                Text(
                    text = stringResource(R.string.enter_backup_screen_desc),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.extraLight
                )
                UiSpacer(1f)

                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = CenterVertically
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
                                color = Theme.colors.backgrounds.secondary,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(all = 12.dp)
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
                            color = if (hasClipContent) Theme.colors.text.primary
                            else Theme.colors.text.button.disabled,
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
                                color = Theme.colors.text.primary,
                                style = Theme.brockmann.supplementary.footnote
                            )
                        }

                        VerifyPinState.Success -> Unit

                        VerifyPinState.Error -> {
                            Text(
                                text = stringResource(R.string.vault_backup_error_pin),
                                color = Theme.colors.alerts.error,
                                style = Theme.brockmann.supplementary.footnote
                            )
                        }
                    }
                }

                UiSpacer(1f)
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
