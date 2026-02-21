package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import androidx.compose.foundation.layout.Spacer
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldType
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.keygen.ImportSeedphraseViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString
import kotlinx.coroutines.delay

@Composable
internal fun ImportSeedphraseScreen(
    model: ImportSeedphraseViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(500)
        focusRequester.requestFocus()
    }

    V2Scaffold(
        onBackClick = model::back,
        title = stringResource(R.string.import_seedphrase_title),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Text(
                text = stringResource(R.string.import_seedphrase_subtitle),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
            )

            UiSpacer(24.dp)

            VsTextInputField(
                textFieldState = model.mnemonicFieldState,
                label = stringResource(R.string.import_seedphrase_label),
                hint = stringResource(R.string.import_seedphrase_hint),
                type = VsTextInputFieldType.MultiLine(minLines = 5),
                innerState = state.innerState,
                focusRequester = focusRequester,
                autoCorrectEnabled = false,
                footNote = state.errorMessage?.asString(),
            )

            UiSpacer(8.dp)

            Text(
                text = stringResource(
                    R.string.import_seedphrase_word_count,
                    state.wordCount,
                    state.expectedWordCount
                ),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.primary,
            )

            Spacer(modifier = Modifier.weight(1f))

            VsButton(
                label = if (state.isImporting)
                    stringResource(R.string.import_seedphrase_checking)
                else stringResource(R.string.import_seedphrase_import_button),
                onClick = {
                    keyboardController?.hide()
                    model.importSeedphrase()
                },
                state = if (state.isImportEnabled && !state.isImporting)
                    VsButtonState.Enabled
                else VsButtonState.Disabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
