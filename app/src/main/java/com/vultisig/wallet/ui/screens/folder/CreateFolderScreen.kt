package com.vultisig.wallet.ui.screens.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.SelectionItem
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.models.folder.CreateFolderViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun CreateFolderScreen(
    navController: NavHostController,
    viewModel: CreateFolderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val textFieldState = viewModel.textFieldState

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
                    text = stringResource(id = R.string.create_folder_create),
                    disabled = !state.isCreateButtonEnabled,
                    onClick = {
                        viewModel.createFolder()
                    },
                )
            }
        },
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(id = R.string.create_folder_title),
                startIcon = R.drawable.ic_caret_left,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .padding(12.dp)
                .background(Theme.colors.oxfordBlue800),
        ) {
            FormTextFieldCard(
                title = stringResource(id = R.string.create_folder_name),
                hint = stringResource(id = R.string.create_folder_placeholder),
                error = state.errorText,
                keyboardType = KeyboardType.Text,
                textFieldState = textFieldState,
            )
            UiSpacer(size = 14.dp)
            Text(
                text = stringResource(id = R.string.create_folder_list_title),
                color = Theme.colors.neutral200,
                style = Theme.montserrat.body1,
            )
            UiSpacer(size = 12.dp)

            LazyColumn(
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.checkedVaults.toList()) { vaultPair ->
                    val vault = vaultPair.first
                    SelectionItem(
                        title = vault.name,
                        isChecked = vaultPair.second,
                        onCheckedChange = { checked ->
                            viewModel.checkVault(vault, checked)
                        }
                    )
                }
            }
        }
    }
}