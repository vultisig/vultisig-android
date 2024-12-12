package com.vultisig.wallet.ui.screens.sign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.components.BoxWithSwipeRefresh
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.models.sign.SignMessageFormUiModel
import com.vultisig.wallet.ui.models.sign.SignMessageFormViewModel
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun SignMessageFormScreen(
    vaultId: VaultId,
    model: SignMessageFormViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    LaunchedEffect(vaultId) {
        model.setData(vaultId)
    }

    SignMessageFormScreen(
        state = state,
        methodFieldState = model.methodFieldState,
        messageFieldState = model.messageFieldState,
        onSign = model::sign,
    )
}


@Composable
internal fun SignMessageFormScreen(
    state: SignMessageFormUiModel,
    methodFieldState: TextFieldState,
    messageFieldState: TextFieldState,
    onSign: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current

    BoxWithSwipeRefresh(
        modifier = Modifier.fillMaxSize(),
        isRefreshing = false,
        onSwipe = { },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(all = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            FormTextFieldCard(
                title = stringResource(R.string.sign_message_form_method_field_title),
                hint = stringResource(R.string.sign_message_form_method_field_hint),
                keyboardType = KeyboardType.Text,
                textFieldState = methodFieldState,
                onLostFocus = { },
                error = null
            )

            FormTextFieldCard(
                title = stringResource(R.string.sign_message_for_message_field_title),
                hint = stringResource(R.string.sign_message_form_message_field_hint),
                keyboardType = KeyboardType.Text,
                textFieldState = messageFieldState,
                onLostFocus = { },
                error = null
            )
        }


        MultiColorButton(
            text = stringResource(R.string.send_continue_button),
            textColor = Theme.colors.oxfordBlue800,
            minHeight = 44.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(all = 16.dp),
            isLoading = state.isLoading,
            onClick = {
                focusManager.clearFocus()
                onSign()
            },
        )
    }

}


@Preview
@Composable
private fun SendFormScreenPreview() {
    SignMessageFormScreen(
        state = SignMessageFormUiModel(),
        methodFieldState = TextFieldState(),
        messageFieldState = TextFieldState(),
    )
}