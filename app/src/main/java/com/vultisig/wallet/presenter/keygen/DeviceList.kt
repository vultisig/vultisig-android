package com.vultisig.wallet.presenter.keygen

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.ui.theme.appColor
import com.vultisig.wallet.app.ui.theme.dimens
import com.vultisig.wallet.app.ui.theme.menloFamily
import com.vultisig.wallet.app.ui.theme.montserratFamily
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.presenter.base_components.MultiColorButton

import com.vultisig.wallet.presenter.keygen.components.DeviceInfoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Composable
fun DeviceList(navController: NavHostController, viewModel: KeygenFlowViewModel) {
    val textColor = MaterialTheme.appColor.neutral0
    val items = viewModel.selection.value!!
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding( MaterialTheme.dimens.marginMedium)
    ) {
//        TopBar(centerText = "Summery", startIcon = R.drawable.caret_left, navController = navController)

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
            val thresholds = Utils.getThreshold(items.count())
            items.forEachIndexed() { index, item ->
                item {
                    if (item == viewModel.localPartyID) {
                        DeviceInfoItem("$index. $item (This Device)")
                    } else {
                        if (index < thresholds)
                            DeviceInfoItem("$index. $item (Pair Device)")
                        else
                            DeviceInfoItem("$index. $item (Backup Device)")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small3))

        Text(
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.menloFamily.titleLarge.copy(textAlign = TextAlign.Start),
            text = "You can only send transactions with these two devices present.",
            color = textColor
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small3))

        Text(
            style = MaterialTheme.menloFamily.bodyMedium,
            text = if (items.count() < 3) "You do not have a 3rd backup device - so you should backup one vault share securely later." else "Your backup device is not needed unless you lose one of your main devices.",
            color = textColor
        )


        Spacer(modifier = Modifier.weight(1.0f))

        MultiColorButton(
            text = stringResource(R.string.continue_res),
            backgroundColor = MaterialTheme.appColor.turquoise600Main,
            textColor = MaterialTheme.appColor.oxfordBlue600Main,
            minHeight = MaterialTheme.dimens.minHeightButton,
            textStyle = MaterialTheme.montserratFamily.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.dimens.buttonMargin,
                    end = MaterialTheme.dimens.buttonMargin
                )
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                viewModel.startKeygen()
                viewModel.moveToState(KeygenFlowState.KEYGEN)
            }
        }
        Spacer(
            modifier = Modifier
                .height(MaterialTheme.dimens.marginMedium)
        )
//        Button(onClick = {
//            if (itemCount + 1 <= 4) {
//                navController.navigate(
//                    Screen.DeviceList.route.replace(
//                        oldValue = "{count}", newValue = "${itemCount + 1}"
//                    )
//                )
//            } else {
//                navController.navigate(
//                    Screen.GeneratingKeyGen.route
//                )
//            }
//        }, modifier = Modifier.fillMaxWidth().padding(MaterialTheme.dimens.marginMedium)) {
//            Text(text = "Continue")
//        }
    }
}
