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
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.presenter.keygen.components.DeviceInfoItem
import com.vultisig.wallet.ui.components.digitStringToWords
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun DeviceList(navController: NavHostController, viewModel: KeygenFlowViewModel) {
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
            centerText = stringResource(R.string.device_list_screen_keygen),
            startIcon = R.drawable.caret_left,
            navController = navController
        )

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))

        Text(
            text = stringResource(
                R.string.device_list_of_vault,
                Utils.getThreshold(items.count()),
                items.count()
            ),
            color = textColor,
            style = Theme.montserrat.body1
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))

        Text(
            text = stringResource(R.string.device_list_screen_with_these_devices),
            color = textColor,
            style = Theme.montserrat.bodyMedium
        )

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))

        LazyColumn {
            val thresholds = Utils.getThreshold(items.count())
            items.forEachIndexed() { index, item ->
                item {
                    if (item == viewModel.localPartyID) {
                        DeviceInfoItem("${index+1}. $item ${stringResource(R.string.this_device)}")
                    } else {
                        if (index < thresholds)
                            DeviceInfoItem("${index+1}. $item ${stringResource(R.string.pair_device)}")
                        else
                            DeviceInfoItem("${index+1}. $item ${stringResource(R.string.backup_device)}")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))

        Text(
            style = Theme.menlo.bodyMedium,
            text = digitStringToWords(
                R.string.device_list_desc1,
                Utils.getThreshold(items.count())
            ),
            color = textColor
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small3))

        Text(
            style = Theme.menlo.bodyMedium,
            text = if (items.count() < 3) stringResource(R.string.device_list_desc2) else stringResource(
                R.string.device_list_desc3
            ),
            color = textColor
        )


        Spacer(modifier = Modifier.weight(1.0f))

        MultiColorButton(
            text = stringResource(R.string.device_list_screen_continue),
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
