@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.logo
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.components.library.form.TokenCard
import com.vultisig.wallet.ui.models.transaction.AddAddressEntryUiModel
import com.vultisig.wallet.ui.models.transaction.AddAddressEntryViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun AddAddressEntryScreen(
    navController: NavController,
    model: AddAddressEntryViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    AddAddressEntryScreen(
        navController = navController,
        state = state,
        titleTextFieldState = model.titleTextFieldState,
        addressTextFieldState = model.addressTextFieldState,
        onSelectChainClick = model::selectChain,
        onSaveAddressClick = model::saveAddress,
        onAddressFieldLostFocus = model::validateAddress,
    )
}

@Composable
internal fun AddAddressEntryScreen(
    navController: NavController,
    state: AddAddressEntryUiModel,
    titleTextFieldState: TextFieldState,
    addressTextFieldState: TextFieldState,
    onSelectChainClick: (Chain) -> Unit = {},
    onSaveAddressClick: () -> Unit = {},
    onAddressFieldLostFocus: () -> Unit = {},
) {
    Scaffold(
        containerColor = Theme.colors.oxfordBlue800,
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(R.string.add_address_title),
                startIcon = R.drawable.caret_left,
            )
        },
        content = { scaffoldPadding ->
            Column(
                modifier = Modifier
                    .padding(scaffoldPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val selectedChain = state.selectedChain

                FormCard {
                    var isChainsExpanded by remember { mutableStateOf(false) }
                    TokenCard(
                        title = selectedChain.raw,
                        availableToken = "",
                        tokenLogo = selectedChain.logo,
                        chainLogo = null,
                        onClick = { isChainsExpanded = !isChainsExpanded },
                        actionIcon = R.drawable.caret_down,
                    )

                    if (isChainsExpanded) {
                        state.chains.forEach { chain ->
                            TokenCard(
                                title = chain.raw,
                                availableToken = "",
                                tokenLogo = chain.logo,
                                onClick = {
                                    isChainsExpanded = false
                                    onSelectChainClick(chain)
                                },
                                actionIcon = if (selectedChain == chain)
                                    R.drawable.check
                                else null
                            )
                        }
                    }
                }

                FormTextFieldCard(
                    title = stringResource(R.string.add_address_title_title),
                    hint = stringResource(R.string.add_address_type_hint),
                    error = null,
                    keyboardType = KeyboardType.Text,
                    textFieldState = titleTextFieldState,
                )

                FormTextFieldCard(
                    title = stringResource(R.string.add_address_address_title),
                    hint = stringResource(R.string.add_address_type_hint),
                    error = state.addressError,
                    keyboardType = KeyboardType.Text,
                    textFieldState = addressTextFieldState,
                    onLostFocus = onAddressFieldLostFocus,
                )
            }
        },
        bottomBar = {
            MultiColorButton(
                text = stringResource(R.string.add_address_save_address_button),
                textColor = Theme.colors.oxfordBlue800,
                minHeight = 44.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 16.dp,
                        horizontal = 16.dp,
                    ),
                onClick = onSaveAddressClick,
            )
        }
    )

}

@Preview
@Composable
private fun AddAddressEntryScreenPreview() {
    AddAddressEntryScreen(
        navController = rememberNavController()
    )
}