package com.vultisig.wallet.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.rememberTextFieldState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NamingComponent(
    title: String = "",
    saveButtonText: String = "",
    textFieldState: TextFieldState = rememberTextFieldState(),
    navHostController: NavHostController = rememberNavController(),
    inputTitle: String = "",
    errorText: UiText? = null,
    snackBarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onSave: () -> Unit = {}
) {

    val focusManager = LocalFocusManager.current
    Scaffold(
        snackbarHost = {
            SnackbarHost(snackBarHostState)
        },
        bottomBar = {
            Box(Modifier.imePadding()) {
                MultiColorButton(
                    minHeight = 44.dp,
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
                )
            }
        },
        topBar = {
            TopBar(
                navController = navHostController,
                centerText = title,
                startIcon = R.drawable.caret_left,
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
                hint = "",
                error = errorText,
                keyboardType = KeyboardType.Text,
                textFieldState = textFieldState,
                onLostFocus = onSave
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
private fun NamingComponentPreView() {
    NamingComponent()
}