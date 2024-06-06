package com.vultisig.wallet.presenter.vault_setting.vault_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun VaultDetailScreen(navHostController: NavHostController) {
    val viewmodel = hiltViewModel<VaultDetailViewmodel>()
    val uiModel by viewmodel.uiModel.collectAsState()

    LaunchedEffect(key1 = Unit) {
        viewmodel.loadData()
    }

    Column(
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .fillMaxSize(),
    ) {
        TopBar(
            navController = navHostController,
            startIcon = R.drawable.caret_left,
            centerText = stringResource(R.string.vault_settings_details_title)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VaultDetailScreenItem(stringResource(R.string.vault_detail_screen_vault_name), uiModel.name)
            VaultDetailScreenItem(stringResource(R.string.vault_detail_screen_ecdsa), uiModel.pubKeyECDSA)
            VaultDetailScreenItem(stringResource(R.string.vault_detail_screen_eddsa), uiModel.pubKeyEDDSA)
            Text(text = "2 of ${uiModel.deviceList.count()} Vault",

                color = Theme.colors.neutral100,
                modifier = Modifier.fillMaxWidth(),
                style = Theme.montserrat.subtitle2.copy(textAlign = TextAlign.Center),)

            uiModel.deviceList.map {
                VaultDetailScreenItem(it)
            }
        }
    }

}

@Composable
private fun VaultDetailScreenItem(propName: String, propValue: String? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Theme.colors.oxfordBlue600Main
        ),
    ) {

        Column(
            modifier = Modifier.padding(all = if (propValue != null) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            Text(
                text = propName,
                color = Theme.colors.neutral100,
                style = Theme.montserrat.subtitle2,
            )
            propValue?.let {
                Text(
                    text = propValue,
                    color = Theme.colors.neutral100,
                    style = Theme.menlo.overline2,
                )
            }
        }
    }
}

@Preview
@Composable
private fun VaultDetailSettingItemPreview() {
    Column {
        VaultDetailScreenItem("prop", "value")
        VaultDetailScreenItem("prop", null)
    }
}