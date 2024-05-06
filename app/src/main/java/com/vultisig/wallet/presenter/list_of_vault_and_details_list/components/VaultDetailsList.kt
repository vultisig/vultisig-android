
package com.vultisig.wallet.presenter.list_of_vault_and_details_list.components


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.presenter.list_of_vault_and_details_list.DetailsItem
import com.vultisig.wallet.R
import com.vultisig.wallet.app.ui.theme.appColor
import com.vultisig.wallet.app.ui.theme.dimens

@Composable
fun VaultDetailsList(navController: NavHostController,vaultDetailsListItems:List<DetailsItem>) {


    val textColor = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(MaterialTheme.dimens.marginMedium)
    ) {


        LazyColumn(
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            /*itemsIndexed(vaultDetailsListItems) { index, item  ->
                if(index<vaultDetailsListItems.count()-1)
                {
                    VaultDetailsListItem(
                        logo = item .logo,
                        coinName = item .coinName,
                        assets = item.assets,
                        isAssets =  item .isAsset,
                        totalValue = item .value,
                        code = item.code,
                    )
                }else
                {
                    CreateItem(value = "Choose Chains")
                }
            }*/
        }

        Box(
            modifier = Modifier
                .padding(MaterialTheme.dimens.small3)
                .align(BottomCenter)
                .size(MaterialTheme.dimens.circularMedium1)
                .clip(CircleShape)
                .background(color = MaterialTheme.appColor.turquoise600Main),
            contentAlignment = Alignment.Center
        ) {
            val painter = painterResource(R.drawable.camera)

            Image(
                painter = painterResource(R.drawable.camera),
                contentDescription = "camera",
                contentScale = ContentScale.Fit,

                modifier = Modifier
                    .aspectRatio(painter.intrinsicSize.width / painter.intrinsicSize.height)
                    .fillMaxWidth()
                    .padding(MaterialTheme.dimens.small1),
                        colorFilter = ColorFilter.tint(color =  MaterialTheme.appColor.oxfordBlue800)


            )
        }

    }
}
@Preview(showBackground = true)
@Composable
fun VaultDetailsListPreview() {
    val navController = rememberNavController()
    VaultDetailsList( navController, emptyList() )
}