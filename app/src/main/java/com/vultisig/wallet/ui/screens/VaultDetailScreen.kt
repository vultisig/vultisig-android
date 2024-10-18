package com.vultisig.wallet.ui.screens

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
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.models.VaultDetailViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultDetailScreen(
    navHostController: NavHostController,
    model: VaultDetailViewModel = hiltViewModel<VaultDetailViewModel>()
) {
    val state by model.uiModel.collectAsState()

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
            VaultDetailScreenItem(
                stringResource(R.string.vault_detail_screen_vault_name),
                state.name
            )
            VaultDetailScreenItem(
                stringResource(R.string.vault_detail_screen_ecdsa),
                state.pubKeyECDSA
            )
            VaultDetailScreenItem(
                stringResource(R.string.vault_detail_screen_eddsa),
                state.pubKeyEDDSA
            )
            Text(
                text = String.format(
                    stringResource(id = R.string.s_of_s_vault),
                    Utils.getThreshold(state.deviceList.size),
                    state.deviceList.size.toString()
                ),
                color = Theme.colors.neutral100,
                modifier = Modifier.fillMaxWidth(),
                style = Theme.montserrat.subtitle2.copy(textAlign = TextAlign.Center),
            )

            state.deviceList.map {
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
        VaultDetailScreenItem("prop")
    }
}