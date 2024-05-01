package com.voltix.wallet.presenter.keygen

import MultiColorButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.voltix.wallet.R
import com.voltix.wallet.app.ui.theme.appColor
import com.voltix.wallet.app.ui.theme.dimens
import com.voltix.wallet.app.ui.theme.menloFamily
import com.voltix.wallet.app.ui.theme.montserratFamily
import com.voltix.wallet.common.Utils
import com.voltix.wallet.presenter.common.TopBar
import com.voltix.wallet.presenter.keygen.components.DeviceInfoItem
import com.voltix.wallet.presenter.navigation.Screen

@Composable
fun DeviceList(navController: NavHostController) {
    val viewModel: KeygenDiscoveryViewModel = hiltViewModel()
    val textColor = MaterialTheme.appColor.neutral0
    val items = viewModel.selection.value!!
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginSmall
            )
    ) {
        TopBar(
            centerText = "Keygen",
            startIcon = R.drawable.caret_left,
            navController = navController
        )

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))

        Text(
            text = "${Utils.getThreshold(items.count())} of ${items.count()} Vault",
            color = textColor,
            style = MaterialTheme.montserratFamily.bodyLarge
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))

        Text(
            text = "With these devices",
            color = textColor,
            style = MaterialTheme.montserratFamily.bodyMedium
        )

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))

        LazyColumn {
            items.forEach {
                item {
                    DeviceInfoItem(it)
                }
            }
        }

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))

        Text(
            style = MaterialTheme.menloFamily.bodyMedium,
            text = "You can only send transactions with these ${Utils.getThreshold(items.count())} devices present.",
            color = textColor
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small3))
        if(items.count() < 3) {
            Text(
                style = MaterialTheme.menloFamily.bodyMedium,
                text = "You do not have a 3rd backup device - so you should backup one vault share securely later.",
                color = textColor
            )
        }

        Spacer(modifier = Modifier.weight(1.0f))

        MultiColorButton(
            text = "Continue",
            backgroundColor = MaterialTheme.appColor.turquoise600Main,
            textColor = MaterialTheme.appColor.oxfordBlue600Main,
            minHeight = MaterialTheme.dimens.minHeightButton,
            textStyle = MaterialTheme.montserratFamily.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.dimens.marginMedium,
                    end = MaterialTheme.dimens.marginMedium,
                    bottom = MaterialTheme.dimens.marginMedium,
                )
        ) {
            navController.navigate(
                Screen.GeneratingKeyGen.route
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DeviceListPreview() {
    val navController = rememberNavController()
    DeviceList(navController)

}