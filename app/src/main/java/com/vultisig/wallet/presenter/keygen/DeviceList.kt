package com.vultisig.wallet.presenter.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.digitStringToWords
import com.vultisig.wallet.ui.components.vultiGradient
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        onContinueClick = {
            CoroutineScope(Dispatchers.IO).launch {
                viewModel.startKeygen()
                viewModel.moveToState(KeygenFlowState.KEYGEN)
            }
        }
    )
}

@Composable
private fun DeviceList(
    navController: NavController,
    localPartyId: String,
    items: List<String>,
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
                Hint(
                    text = digitStringToWords(
                        R.string.device_list_desc1,
                        Utils.getThreshold(items.count())
                    )
                )

                Hint(
                    text = if (items.count() < 3)
                        stringResource(R.string.device_list_desc2)
                    else stringResource(
                        R.string.device_list_desc3
                    )
                )

                MultiColorButton(
                    text = stringResource(R.string.device_list_screen_continue),
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
                centerText = stringResource(R.string.device_list_screen_keygen),
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
                    UiSpacer(size = 8.dp)

                    Text(
                        text = stringResource(
                            R.string.device_list_of_vault,
                            Utils.getThreshold(items.count()),
                            items.count()
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

                    UiSpacer(size = 32.dp)
                }
            }

            val thresholds = Utils.getThreshold(items.count())
            itemsIndexed(items) { index, item ->
                if (item == localPartyId) {
                    DeviceInfoItem("${index + 1}. $item ${stringResource(R.string.this_device)}")
                } else {
                    if (index < thresholds)
                        DeviceInfoItem("${index + 1}. $item ${stringResource(R.string.pair_device)}")
                    else
                        DeviceInfoItem("${index + 1}. $item ${stringResource(R.string.backup_device)}")
                }

                UiSpacer(size = 16.dp)
            }

        }
    }
}

@Composable
private fun DeviceInfoItem(
    info: String,
) {
    val textColor = Theme.colors.neutral0

    Text(
        text = info,
        color = textColor,
        style = Theme.menlo.overline2,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Theme.colors.oxfordBlue600Main,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(
                vertical = 24.dp,
                horizontal = 20.dp,
            )
    )
}

@Composable
private fun Hint(
    text: String,
) {
    val brushGradient = Brush.vultiGradient()

    Row(
        modifier = Modifier
            .border(
                width = 1.dp,
                brush = brushGradient,
                shape = RoundedCornerShape(10.dp)
            )
            .fillMaxWidth()
            .padding(all = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = android.R.drawable.ic_menu_info_details),
            contentDescription = null,
            modifier = Modifier
                .graphicsLayer(alpha = 0.99f)
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRect(brushGradient, blendMode = BlendMode.SrcAtop)
                    }
                }
                .size(20.dp)
        )

        UiSpacer(size = 10.dp)

        Text(
            style = Theme.menlo.body1,
            text = text,
            color = Theme.colors.neutral0
        )
    }
}

@Preview
@Composable
private fun DeviceListPreview() {
    DeviceList(
        navController = rememberNavController(),
        localPartyId = "localPartyId",
        items = listOf("device1", "device2", "device3"),
        onContinueClick = {}
    )
}