package com.vultisig.wallet.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.models.settings.CurrencyUnitSettingViewModel
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun CurrencyUnitSettingScreen(navController: NavHostController) {
    val colors = Theme.colors
    val viewModel = hiltViewModel<CurrencyUnitSettingViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(key1 = Unit) {
        viewModel.initScreenUnit()
    }


    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.oxfordBlue800),
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(R.string.currency_unit_setting_screen_title),
                startIcon = R.drawable.ic_caret_left
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .padding(it),
        ) {
            items(state.currencyUnits) { currencyUnit ->
                CurrencyUnitSettingItem(
                    name = currencyUnit.name,
                    isSelected = currencyUnit == state.selectedCurrency,
                    onClick = {
                        viewModel.changeCurrencyUnit(currencyUnit)
                    }
                )
            }
        }
    }
}

@Composable
private fun CurrencyUnitSettingItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = Theme.colors
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clickOnce(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.oxfordBlue600Main
        )
    ) {
        Row(
            modifier = Modifier.padding(all = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                color = colors.neutral0,
                style = Theme.montserrat.body2,
            )
            UiSpacer(weight = 1.0f)


            Icon(
                modifier = Modifier.alpha(if (isSelected) 1f else 0f),
                painter = painterResource(id = R.drawable.check),
                contentDescription = null,
                tint = colors.neutral0,
            )
        }
    }
}