package com.vultisig.wallet.presenter.list_of_vault_and_details_list.components


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.data.static_data.getSampleVaultData
import com.vultisig.wallet.presenter.list_of_vault_and_details_list.VaultListAndDetailsViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.app.ui.theme.appColor
import com.vultisig.wallet.app.ui.theme.dimens
import com.vultisig.wallet.app.ui.theme.montserratFamily
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.presenter.base_components.MultiColorButton


@Composable
fun VaultList(navController: NavHostController,vaultListItems: List<Vault>) {

    val viewModel = hiltViewModel<VaultListAndDetailsViewModel>()
    val textColor = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColor.oxfordBlue800)
    ) {


        LazyColumn(
            Modifier.weight(7.0f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.dimens.small1)
        ) {
//            itemsIndexed(vaultListItems) { index, item ->
//                VaultListItem(item.name, index) { viewModel.onVaultItemClick(it) }
//            }
        }

        Column(
            modifier = Modifier
                .weight(1.0f)
                .align(CenterHorizontally),
            horizontalAlignment = CenterHorizontally
        ) {

            MultiColorButton(
                text = stringResource(id = R.string.add_new_vault),
                minHeight = MaterialTheme.dimens.minHeightButton,
                backgroundColor = MaterialTheme.appColor.turquoise800,
                textColor = MaterialTheme.appColor.oxfordBlue800,
                iconColor = MaterialTheme.appColor.oxfordBlue800,
                startIcon = R.drawable.plus,
                textStyle = MaterialTheme.montserratFamily.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.dimens.buttonMargin,
                        end = MaterialTheme.dimens.buttonMargin
                    )
            ) {}
            Spacer(modifier = Modifier.height(MaterialTheme.dimens.marginSmall))
            MultiColorButton(
                text = stringResource(id = R.string.join_airdrop),
                backgroundColor = MaterialTheme.appColor.oxfordBlue800,
                textColor = MaterialTheme.appColor.turquoise800,
                iconColor = MaterialTheme.appColor.oxfordBlue800,
                borderSize = 1.dp,
                textStyle = MaterialTheme.montserratFamily.titleLarge,
                minHeight = MaterialTheme.dimens.minHeightButton,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.dimens.buttonMargin,
                        end = MaterialTheme.dimens.buttonMargin
                    )

            ) {}
            Spacer(
                modifier = Modifier
                    .height(MaterialTheme.dimens.marginMedium)
            )
        }
    }
}
@Preview(showBackground = true)
@Composable
fun VaultListPreview() {
    val navController = rememberNavController()
    VaultList( navController, getSampleVaultData())
}