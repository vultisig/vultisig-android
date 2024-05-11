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
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.presenter.base_components.MultiColorButton
import com.vultisig.wallet.presenter.common.TopBar
import com.vultisig.wallet.presenter.keygen.components.DeviceInfoItem
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun DeviceList(navController: NavHostController, viewModel: KeygenFlowViewModel) {
    val textColor = Theme.colors.neutral0
    val items = viewModel.selection.value!!
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
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
            style = Theme.montserrat.bodyLarge
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))

        Text(
            text = "With these devices",
            color = textColor,
            style = Theme.montserrat.bodyMedium
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

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))

        Text(
            style = Theme.menlo.bodyMedium,
            text = "You can only send transactions with these ${Utils.getThreshold(items.count())} devices present.",
            color = textColor
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small3))

        Text(
            style = Theme.menlo.bodyMedium,
            text = if (items.count() < 3) "You do not have a 3rd backup device - so you should backup one vault share securely later." else "Your backup device is not needed unless you lose one of your main devices.",
            color = textColor
        )


        Spacer(modifier = Modifier.weight(1.0f))

        MultiColorButton(
            text = "Continue",
            backgroundColor = Theme.colors.turquoise600Main,
            textColor = Theme.colors.oxfordBlue600Main,
            minHeight = MaterialTheme.dimens.minHeightButton,
            textStyle = Theme.montserrat.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.dimens.marginMedium,
                    end = MaterialTheme.dimens.marginMedium,
                    bottom = MaterialTheme.dimens.marginMedium,
                )
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                viewModel.startKeygen()
                viewModel.moveToState(KeygenFlowState.KEYGEN)
            }
        }
    }
}
