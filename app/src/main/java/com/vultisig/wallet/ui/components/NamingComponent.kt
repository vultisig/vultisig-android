package com.vultisig.wallet.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text2.BasicTextField2
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.rememberTextFieldState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.ui.components.library.form.TextFieldValidator
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

    var focusState by remember {
        mutableStateOf<FocusState?>(null)
    }
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
                .background(Theme.colors.oxfordBlue800),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = inputTitle,
                    color = Theme.colors.neutral100,
                    style = Theme.montserrat.body2,
                )

                TextFieldValidator(
                    errorText = errorText,
                    focusState = focusState
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Theme.colors.oxfordBlue600Main
                        ),
                    ) {
                        BasicTextField2(
                            state = textFieldState,
                            modifier = Modifier
                                .padding(12.dp)
                                .onFocusChanged {
                                    focusState = it
                                }
                                .imePadding(),
                            textStyle = Theme.montserrat.body2.copy(color = Theme.colors.neutral100),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
private fun NamingComponentPreView() {
    NamingComponent()
}