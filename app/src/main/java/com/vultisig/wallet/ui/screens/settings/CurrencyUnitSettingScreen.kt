package com.vultisig.wallet.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.settings.CurrencyUnit
import com.vultisig.wallet.ui.models.settings.CurrencyUnitSettingUiModel
import com.vultisig.wallet.ui.models.settings.CurrencyUnitSettingViewModel
import com.vultisig.wallet.ui.models.settings.SettingsItemUiModel
import com.vultisig.wallet.ui.utils.asUiText

@Composable
internal fun CurrencyUnitSettingScreen(navController: NavHostController) {
    val viewModel = hiltViewModel<CurrencyUnitSettingViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(key1 = Unit) {
        viewModel.initScreenUnit()
    }


    CurrencyUnitSettingScreen(
        state = state,
        onBackClick = {
            navController.popBackStack()
        },
        onCurrencyClick = {
            viewModel.changeCurrencyUnit(it)
        }
    )
}

@Composable
private fun CurrencyUnitSettingScreen(
    state: CurrencyUnitSettingUiModel,
    onBackClick: () -> Unit,
    onCurrencyClick: (CurrencyUnit) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            VsTopAppBar(
                onIconLeftClick = onBackClick,
                title = stringResource(R.string.currency_unit_setting_screen_title),
                iconLeft = R.drawable.ic_caret_left
            )
        }
    ) {

        SettingsBox(
            modifier = Modifier
                .padding(it)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
        ) {
            LazyColumn {
                itemsIndexed(state.currencyUnits) { index, currencyUnit ->
                    CurrencyUnitSettingItem(
                        name = currencyUnit.fullName,
                        isSelected = currencyUnit == state.selectedCurrency,
                        onClick = {
                            onCurrencyClick(currencyUnit)
                        },
                        isLastItem = index == state.currencyUnits.lastIndex
                    )
                }
            }
        }

    }
}

@Composable
private fun CurrencyUnitSettingItem(
    name: String,
    isSelected: Boolean,
    isLastItem: Boolean,
    onClick: () -> Unit
) {

    SettingItem(
        item = SettingsItemUiModel(
            title = name.asUiText(),
            trailingIcon = if (isSelected) R.drawable.check_2 else null,
        ),
        onClick = onClick,
        isLastItem = isLastItem
    )
}

@Preview
@Composable
private fun CurrencyUnitSettingScreenPreview() {
    CurrencyUnitSettingScreen(
        state = CurrencyUnitSettingUiModel(
            currencyUnits = listOf(
                CurrencyUnit(fullName = "United States Dollar (\$)"),
                CurrencyUnit(fullName = "Australian Dollar (A\$)"),
                CurrencyUnit(fullName = "Euro (€)"),
                CurrencyUnit(fullName = "Russian Ruble (₽)"),
                CurrencyUnit(fullName = "British Pound (£)"),
                CurrencyUnit(fullName = "Japanese Yen (¥)"),
                CurrencyUnit(fullName = "Chinese Yuan (¥)"),
                CurrencyUnit(fullName = "Canadian Dollar (\$)"),
                CurrencyUnit(fullName = "Singapore Dollar (S\$)"),
                CurrencyUnit(fullName = "Swedish Krona (kr)"),
            ),
            selectedCurrency = CurrencyUnit(fullName = "United States Dollar (\$)"),
        ),
        onCurrencyClick = {},
        onBackClick = {}
    )
}