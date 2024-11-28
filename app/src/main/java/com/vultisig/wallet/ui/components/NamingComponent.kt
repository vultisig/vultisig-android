package com.vultisig.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText

@Composable
internal fun NamingComponent(
    title: String = "",
    hint: String = "",
    hintColor: Color = Theme.colors.neutral100,
    saveButtonText: String = "",
    textFieldState: TextFieldState = rememberTextFieldState(),
    navHostController: NavHostController = rememberNavController(),
    inputTitle: String = "",
    errorText: UiText? = null,
    isLoading: Boolean = false,
    onLostFocus: () -> Unit = {},
    onSave: () -> Unit = {}
) {

    val focusManager = LocalFocusManager.current
    Scaffold(
        bottomBar = {
            Box(Modifier.imePadding()) {
                MultiColorButton(
                    backgroundColor = Theme.colors.turquoise800,
                    textColor = Theme.colors.oxfordBlue800,
                    iconColor = Theme.colors.turquoise800,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                    text = saveButtonText,
                    onClick = {
                        focusManager.clearFocus()
                        onSave()
                    },
                    isLoading = isLoading,
                )
            }
        },
        topBar = {
            TopBar(
                navController = navHostController,
                centerText = title,
                startIcon = R.drawable.ic_caret_left,
            )
        },
    ) {
        Box(
            modifier = Modifier
                .padding(it)
                .padding(12.dp)
                .background(Theme.colors.oxfordBlue800),
        ) {
            FormTextFieldCard(
                title = inputTitle,
                hint = hint,
                hintColor = hintColor,
                error = errorText,
                keyboardType = KeyboardType.Text,
                textFieldState = textFieldState,
                onLostFocus = onLostFocus
            )
        }
    }
}


@Preview
@Composable
private fun NamingComponentPreView() {
    NamingComponent()
}