package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.digitStringToWords
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.DeviceInfoItem
import com.vultisig.wallet.ui.utils.Hint

@Composable
internal fun DeviceList(
    navController: NavHostController,
    viewModel: KeygenFlowViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val items = uiState.selection

    DeviceList(
        navController = navController,
        localPartyId = viewModel.localPartyID,
        items = items,
        isReshare = uiState.isReshareMode,
        onContinueClick = {
            viewModel.moveToKeygen()
        }
    )
}

@Composable
private fun DeviceList(
    navController: NavController,
    localPartyId: String,
    items: List<String>,
    isReshare: Boolean,
    onContinueClick: () -> Unit,
) {
    val textColor = Theme.colors.neutral0

    Scaffold(
        containerColor = Theme.colors.oxfordBlue800,
        bottomBar = {
            Column(
                modifier = Modifier
                    .padding(
                        horizontal = 16.dp,
                        vertical = 12.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isReshare) {
                    Hint(
                        text = stringResource(R.string.reshare_device_list_screen_desc1),
                    )
                } else {
                    Hint(
                        text = digitStringToWords(
                            R.string.device_list_desc1,
                            Utils.getThreshold(items.size)
                        )
                    )

                    Hint(
                        text = stringResource(R.string.device_list_desc2),
                    )
                }

                MultiColorButton(
                    text = stringResource(
                        if (isReshare) R.string.reshare_start else
                            R.string.device_list_screen_continue
                    ),
                    backgroundColor = Theme.colors.turquoise600Main,
                    textColor = Theme.colors.oxfordBlue600Main,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = onContinueClick,
                )
            }
        },
        topBar = {
            TopBar(
                centerText =
                stringResource(
                    if (isReshare) R.string.reshare_device_screen_changes_in_setup
                    else R.string.device_list_screen_keygen
                ),
                startIcon = R.drawable.caret_left,
                navController = navController
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .padding(it)
                .padding(
                    horizontal = 16.dp,
                )
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = CenterHorizontally,
                ) {
                    if (isReshare) {
                        UiSpacer(size = 12.dp)

                        Text(
                            text = stringResource(R.string.reshare_device_list_screen_new_vault_setup),
                            color = textColor,
                            style = Theme.montserrat.body3
                        )

                        UiSpacer(size = 16.dp)

                        Text(
                            text = stringResource(
                                id = R.string.reshare_device_list_of_vault,
                                Utils.getThreshold(items.size),
                                items.size
                            ),
                            color = textColor,
                            style = Theme.montserrat.subtitle3,
                            modifier = Modifier
                                .size(
                                    width = 71.dp,
                                    height = 30.dp
                                )
                                .background(
                                    color = Theme.colors.transparentOxfordBlue,
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(
                                    vertical = 4.dp,
                                    horizontal = 16.dp,
                                ),
                            textAlign = TextAlign.Center,
                        )


                    } else {
                        UiSpacer(size = 8.dp)

                        Text(
                            text = stringResource(
                                R.string.device_list_of_vault,
                                Utils.getThreshold(items.size),
                                items.size
                            ),
                            color = textColor,
                            style = Theme.montserrat.subtitle2
                        )

                        UiSpacer(size = 12.dp)

                        Text(
                            text = stringResource(R.string.device_list_screen_with_these_devices),
                            color = textColor,
                            style = Theme.montserrat.body3
                        )
                    }
                    UiSpacer(size = 32.dp)
                }
            }

            val thresholds = Utils.getThreshold(items.size)
            itemsIndexed(items) { index, item ->
                if (isReshare) {
                    if (item == localPartyId) {
                        DeviceInfoItem(
                            "${index + 1}. $item ${stringResource(R.string.this_device)}",
                            Theme.colors.trasnparentTurquoise
                        )
                    } else {
                        if (index < thresholds)
                            DeviceInfoItem(
                                "${index + 1}. $item ${stringResource(R.string.pair_device)}",
                                Theme.colors.trasnparentTurquoise
                            )
                        else
                            DeviceInfoItem(
                                "${index + 1}. $item ${stringResource(R.string.backup_device)}",
                                Theme.colors.transparentRed
                            )
                    }
                } else {
                    if (item == localPartyId) {
                        DeviceInfoItem("${index + 1}. $item ${stringResource(R.string.this_device)}")
                    } else {
                        if (index < thresholds)
                            DeviceInfoItem("${index + 1}. $item ${stringResource(R.string.pair_device)}")
                        else
                            DeviceInfoItem("${index + 1}. $item ${stringResource(R.string.backup_device)}")
                    }
                }

                UiSpacer(size = 16.dp)
            }

        }
    }
}


@Preview
@Composable
private fun DeviceListPreview() {
    DeviceList(
        navController = rememberNavController(),
        localPartyId = "localPartyId",
        isReshare = false,
        items = listOf("device1", "device2", "device3"),
        onContinueClick = {}
    )
}