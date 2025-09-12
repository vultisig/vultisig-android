package com.vultisig.wallet.ui.screens.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.containers.V2Scaffold
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

    V2Scaffold(
        title = stringResource(R.string.currency_unit_setting_screen_title),
        onBackClick = onBackClick
    ){

        V2Container(
            type = ContainerType.SECONDARY,
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
                CurrencyUnit(fullName = "US Dollar (\$)"),
                CurrencyUnit(fullName = "Australian Dollar (A\$)"),
                CurrencyUnit(fullName = "Euro (€)"),
                CurrencyUnit(fullName = "Russian Ruble (₽)"),
                CurrencyUnit(fullName = "British Pound (£)"),
                CurrencyUnit(fullName = "Japanese Yen (JP¥)"),
                CurrencyUnit(fullName = "Chinese Yuan (CN¥)"),
                CurrencyUnit(fullName = "Canadian Dollar (CA\$)"),
                CurrencyUnit(fullName = "Singapore Dollar (SGD)"),
                CurrencyUnit(fullName = "Swedish Krona (SEK)"),
            ),
            selectedCurrency = CurrencyUnit(fullName = "US Dollar (\$)"),
        ),
        onCurrencyClick = {},
        onBackClick = {}
    )
}