package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.v2.modifiers.shinedCenter
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.keygen.ImportSeedphraseUiModel
import com.vultisig.wallet.ui.models.keygen.ImportSeedphraseViewModel
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString
import kotlinx.coroutines.delay

@Composable
internal fun ImportSeedphraseScreen(model: ImportSeedphraseViewModel = hiltViewModel()) {
    val state by model.state.collectAsState()

    val keyboardController = LocalSoftwareKeyboardController.current

    ImportSeedphraseContent(
        state = state,
        mnemonicFieldState = model.mnemonicFieldState,
        onBackClick = model::back,
        onImportClick = {
            keyboardController?.hide()
            model.importSeedphrase()
        },
    )
}

@Composable
internal fun ImportSeedphraseContent(
    state: ImportSeedphraseUiModel,
    mnemonicFieldState: TextFieldState,
    onBackClick: () -> Unit,
    onImportClick: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(500)
        focusRequester.requestFocus()
    }

    V3Scaffold(
        onBackClick = onBackClick,
        bottomBar = {
            VsButton(
                label =
                    if (state.isImporting) stringResource(R.string.import_seedphrase_checking)
                    else stringResource(R.string.import_seedphrase_import_button),
                onClick = onImportClick,
                state =
                    if (state.isImportEnabled && !state.isImporting) VsButtonState.Enabled
                    else VsButtonState.Disabled,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(
                            vertical = V3Scaffold.PADDING_VERTICAL,
                            horizontal = V3Scaffold.PADDING_HORIZONTAL,
                        ),
            )
        },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ImportSeedphraseStepIcon()

            UiSpacer(24.dp)

            Text(
                text = stringResource(R.string.import_seedphrase_title),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.Center,
            )

            UiSpacer(8.dp)

            Text(
                text = importSeedphraseSubtitle(),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
            )

            UiSpacer(24.dp)

            Box(modifier = Modifier.fillMaxWidth()) {
                VsTextInputField(
                    textFieldState = mnemonicFieldState,
                    hint = stringResource(R.string.import_seedphrase_hint),
                    type = VsTextInputFieldType.MultiLine(minLines = 5),
                    innerState = state.innerState,
                    focusRequester = focusRequester,
                    autoCorrectEnabled = false,
                    footNote = state.errorMessage?.asString(),
                )

                if (state.wordCount > 0 && state.errorMessage == null) {
                    Text(
                        text = "${state.wordCount}/${state.expectedWordCount}",
                        style = Theme.brockmann.supplementary.footnote,
                        color = Theme.v2.colors.text.tertiary,
                        modifier =
                            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun importSeedphraseSubtitle() = buildAnnotatedString {
    val highlight = stringResource(R.string.import_seedphrase_subtitle_highlight)
    val template = stringResource(R.string.import_seedphrase_subtitle, highlight)
    append(template)
    val highlightStart = template.indexOf(highlight)
    if (highlightStart >= 0) {
        addStyle(
            style = SpanStyle(color = Theme.v2.colors.text.primary),
            start = highlightStart,
            end = highlightStart + highlight.length,
        )
    }
}

@Composable
private fun ImportSeedphraseStepIcon() {
    Box(
        modifier =
            Modifier.size(44.dp)
                .clip(CircleShape)
                .shinedCenter(color = Theme.v2.colors.alerts.success, shineAlpha = 0.15f)
                .border(
                    width = 1.5.dp,
                    color = Theme.v2.colors.neutrals.n50.copy(alpha = 0.2f),
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        UiIcon(
            drawableResId = R.drawable.import_seed,
            tint = Color(0xFF28BBC1),
            contentDescription = null,
            size = 18.dp,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ImportSeedphraseContentPreview() {
    OnBoardingComposeTheme {
        ImportSeedphraseContent(
            state = ImportSeedphraseUiModel(wordCount = 0, expectedWordCount = 12),
            mnemonicFieldState = TextFieldState(),
            onBackClick = {},
            onImportClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ImportSeedphraseContentErrorPreview() {
    OnBoardingComposeTheme {
        ImportSeedphraseContent(
            state =
                ImportSeedphraseUiModel(
                    wordCount = 8,
                    expectedWordCount = 12,
                    innerState = VsTextInputFieldInnerState.Error,
                    errorMessage =
                        com.vultisig.wallet.ui.utils.UiText.DynamicString(
                            "You entered 8 words. Seed phrase must be 12 or 24"
                        ),
                ),
            mnemonicFieldState =
                TextFieldState(initialText = "apple banana cherry dog elephant frog grape horse"),
            onBackClick = {},
            onImportClick = {},
        )
    }
}
