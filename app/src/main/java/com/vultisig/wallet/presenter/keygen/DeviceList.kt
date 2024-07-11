package com.vultisig.wallet.presenter.keygen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.presenter.keygen.components.DeviceInfoItem
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.digitStringToWords
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun DeviceList(navController: NavHostController, viewModel: KeygenFlowViewModel) {
    val textColor = Theme.colors.neutral0
    val items = viewModel.uiState.collectAsState().value.selection
    Scaffold(
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .padding(
                vertical = 12.dp,
                horizontal = 8.dp
            ),
        bottomBar = {
            MultiColorButton(
                text = stringResource(R.string.device_list_screen_continue),
                backgroundColor = Theme.colors.turquoise600Main,
                textColor = Theme.colors.oxfordBlue600Main,
                minHeight = 44.dp,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 12.dp,
                    )
            ) {
                CoroutineScope(Dispatchers.IO).launch {
                    viewModel.startKeygen()
                    viewModel.moveToState(KeygenFlowState.KEYGEN)
                }
            }
        },
        topBar = {
            TopBar(
                centerText = stringResource(R.string.device_list_screen_keygen),
                startIcon = R.drawable.caret_left,
                navController = navController
            )
        }
    ) {
        LazyColumn(modifier = Modifier.padding(it)) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(
                            R.string.device_list_of_vault,
                            Utils.getThreshold(items.count()),
                            items.count()
                        ),
                        color = textColor,
                        style = Theme.montserrat.body1
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.device_list_screen_with_these_devices),
                        color = textColor,
                        style = Theme.montserrat.body2
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            val thresholds = Utils.getThreshold(items.count())
            items.forEachIndexed { index, item ->
                item {
                    if (item == viewModel.localPartyID) {
                        DeviceInfoItem("${index + 1}. $item ${stringResource(R.string.this_device)}")
                    } else {
                        if (index < thresholds)
                            DeviceInfoItem("${index + 1}. $item ${stringResource(R.string.pair_device)}")
                        else
                            DeviceInfoItem("${index + 1}. $item ${stringResource(R.string.backup_device)}")
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        style = Theme.menlo.body2,
                        text = digitStringToWords(
                            R.string.device_list_desc1,
                            Utils.getThreshold(items.count())
                        ),
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        style = Theme.menlo.body2,
                        text = if (items.count() < 3) stringResource(R.string.device_list_desc2) else stringResource(
                            R.string.device_list_desc3
                        ),
                        color = textColor
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
