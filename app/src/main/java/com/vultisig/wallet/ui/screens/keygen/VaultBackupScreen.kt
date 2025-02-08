package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsCircularLoading
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.keygen.CharHandler
import com.vultisig.wallet.ui.models.keygen.FocusManagerEvent
import com.vultisig.wallet.ui.models.keygen.VaultBackupState
import com.vultisig.wallet.ui.models.keygen.VaultBackupViewModel
import com.vultisig.wallet.ui.models.keygen.VerifyPinState
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.components.rememberClipboardText

@Composable
internal fun VaultBackupScreen(
    model: VaultBackupViewModel = hiltViewModel(),
) {
    val focusManager = LocalFocusManager.current
    val state by model.state.collectAsState()
    LaunchedEffect(Unit) {
        model.textFocusHandler.collect {
            when (it) {
                FocusManagerEvent.MOVE_NEXT -> focusManager.moveFocus(FocusDirection.Next)
                FocusManagerEvent.MOVE_PREVIOUS -> focusManager.moveFocus(FocusDirection.Previous)
                FocusManagerEvent.CLEAR_FOCUS -> focusManager.clearFocus()
                null -> Unit
            }
        }
    }
    VaultBackupScreen(
        state = state,
        onBackClick = model::back,
        charHandlers = model.charHandlers,
        onChangeEmailClick = model::changeEmail,
        onPasteClick = model::paste,
        onBackSpaceClick = model::onBackSpacePressed,
        onRestartKeygenClick = model::restartKeygen
    )
}

@Composable
private fun VaultBackupScreen(
    state: VaultBackupState,
    onBackClick: () -> Unit,
    charHandlers: List<CharHandler>,
    onChangeEmailClick: () -> Unit,
    onPasteClick: (String) -> Unit,
    onBackSpaceClick: (Int) -> Unit,
    onRestartKeygenClick: () -> Unit,
) {
    val textToPaste by rememberClipboardText()
    val focusRequester = remember {
        FocusRequester()
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                onBackClick = onBackClick
            )
        },
        bottomBar = {
            AnimatedContent(
                targetState = state.verifyPinState,
                label = "bottomBar"
            ) {
                when (it) {
                    VerifyPinState.Idle -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.vault_backup_screen_email_sent_to,
                                    state.sentEmailTo
                                ),
                                color = Theme.colors.text.extraLight,
                                style = Theme.brockmann.supplementary.footnote,
                            )
                            Text(
                                text = stringResource(
                                    R.string.vault_backup_screen_change_email,
                                ),
                                modifier = Modifier
                                    .clickable(onClick = onChangeEmailClick),
                                color = Theme.colors.text.light,
                                textDecoration = TextDecoration.Underline,
                                style = Theme.brockmann.supplementary.footnote,
                            )
                        }
                    }

                    VerifyPinState.Loading -> Unit
                    VerifyPinState.SUCCESS -> Unit
                    VerifyPinState.ERROR -> {
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
                                .padding(24.dp)
                                .clickable(onClick = onRestartKeygenClick),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }) {
        Column(
            Modifier
                .padding(it)
                .padding(
                    top = 12.dp,
                    start = 24.dp,
                    end = 24.dp,
                )
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
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .wrapContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .height(intrinsicSize = IntrinsicSize.Min)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    charHandlers.onEachIndexed { index, charHandler ->
                        VsTextInputField(
                            textFieldState = charHandler.textFieldState,
                            innerState = when (state.verifyPinState) {
                                VerifyPinState.Idle -> VsTextInputFieldInnerState.Default
                                VerifyPinState.Loading -> VsTextInputFieldInnerState.Default
                                VerifyPinState.SUCCESS -> VsTextInputFieldInnerState.Success
                                VerifyPinState.ERROR -> VsTextInputFieldInnerState.Error
                            },
                            type = VsTextInputFieldType.Number,
                            focusRequester = focusRequester.takeIf { index == 0 },
                            onKeyEvent = { event ->
                                if (event.key == Key.Backspace) {
                                    onBackSpaceClick(index)
                                }
                                false
                            },
                        )
                    }

                    Text(
                        text = stringResource(R.string.vault_backup_screen_paste),
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(
                                color = Theme.colors.backgrounds.secondary,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .wrapContentWidth()
                            .clickable(
                                enabled = textToPaste != null
                            ) {
                                textToPaste?.text?.let(onPasteClick)
                            },
                        style = Theme.brockmann.body.s.medium,
                        color = if (textToPaste == null)
                            Theme.colors.text.button.disabled else Theme.colors.text.primary,
                    )
                }
                AnimatedContent(
                    targetState = state.verifyPinState,
                    label = "verifying state"
                ) { verifyPinState ->
                    when (verifyPinState) {
                        VerifyPinState.Idle -> Unit
                        VerifyPinState.Loading -> Row(
                            verticalAlignment = Alignment.CenterVertically,
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

                        VerifyPinState.SUCCESS -> Unit
                        VerifyPinState.ERROR -> {
                            Text(
                                text = stringResource(R.string.vault_backup_error_pin),
                                color = Theme.colors.alerts.error,
                                style = Theme.brockmann.supplementary.footnote
                            )
                        }
                    }
                }
            }
        }
    }
}


@Preview
@Composable
private fun VaultBackupScreenPreview() {
    VaultBackupScreen(
        state = VaultBackupState(
            VerifyPinState.Idle,
            sentEmailTo = "test@email.com"
        ),
        onBackClick = {},
        charHandlers = List(4) {
            CharHandler(
                textFieldState = TextFieldState(),
                enteredChar = null
            )
        },
        onPasteClick = {},
        onBackSpaceClick = {},
        onRestartKeygenClick = {},
        onChangeEmailClick = {}
    )
}
