@file:JvmName("KeygenPeerDiscoveryKt")

package com.voltix.wallet.presenter.keygen

import MultiColorButton
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.asFlow
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.voltix.wallet.R
import com.voltix.wallet.app.ui.theme.appColor
import com.voltix.wallet.app.ui.theme.dimens
import com.voltix.wallet.app.ui.theme.menloFamily
import com.voltix.wallet.app.ui.theme.montserratFamily
import com.voltix.wallet.common.Utils
import com.voltix.wallet.mediator.MediatorService
import com.voltix.wallet.models.TssAction
import com.voltix.wallet.models.Vault
import com.voltix.wallet.presenter.common.QRCodeKeyGenImage
import com.voltix.wallet.presenter.common.TopBar
import com.voltix.wallet.presenter.keygen.components.DeviceInfo
import com.voltix.wallet.presenter.navigation.Screen

@Composable
fun KeygenPeerDiscovery(navController: NavHostController, vault: Vault) {
    val viewModel: KeygenDiscoveryViewModel = hiltViewModel()
    val selectionState = viewModel.selection.asFlow().collectAsState(initial = emptyList()).value
    val context = LocalContext.current
    LaunchedEffect(key1 = viewModel) {
        // start mediator server
        val intent = Intent(context, MediatorService::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("serverName", viewModel.serviceName)
        context.startService(intent)
        viewModel.setData(TssAction.KEYGEN, vault)

    }
    val textColor = MaterialTheme.appColor.neutral0
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
            centerText = "Keygen", startIcon = R.drawable.caret_left,
            navController = navController
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
        if (!selectionState.isNullOrEmpty() && selectionState.count() > 1) {
            Text(
                text = "${Utils.getThreshold(selectionState.count())} of ${selectionState.count()} Vault",
                color = textColor,
                style = MaterialTheme.montserratFamily.bodyLarge
            )
        }
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))

        Text(
            text = "Pair with other devices:",
            color = textColor,
            style = MaterialTheme.montserratFamily.bodyMedium
        )
        Spacer(modifier = Modifier.weight(1.0f))
        if (viewModel.keygenPayloadState.value.isNotEmpty()) {
            QRCodeKeyGenImage(viewModel.keygenPayloadState.value)
        }

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.marginExtraLarge))

        if (!viewModel.participants.value.isNullOrEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(viewModel.participants.value!!.size) { index ->
                    val participant = viewModel.participants.value!![index]
                    val isSelected = selectionState.contains(participant)
                    DeviceInfo(R.drawable.ipad, participant, isSelected = isSelected) { isChecked ->
                        when (isChecked) {
                            true -> viewModel.addParticipant(participant)
                            false -> viewModel.removeParticipant(participant)
                        }

                    }
                }
            }
        } else {
            Text(
                text = "Waiting for other devices to connect...",
                color = textColor,
                style = MaterialTheme.montserratFamily.bodyMedium
            )
        }

        Spacer(modifier = Modifier.weight(1.0f))

        Image(painter = painterResource(id = R.drawable.wifi), contentDescription = null)
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.marginSmall))
        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.marginExtraLarge),
            text = "Keep all devices on same WiFi with Voltix App open. (May not work on hotel/airport WiFi)",
            color = textColor,
            style = MaterialTheme.menloFamily.headlineSmall.copy(
                textAlign = TextAlign.Center, fontSize = 13.sp
            ),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))

        MultiColorButton(
            text = "Start",
            backgroundColor = MaterialTheme.appColor.turquoise600Main,
            textColor = MaterialTheme.appColor.oxfordBlue600Main,
            minHeight = MaterialTheme.dimens.minHeightButton,
            textStyle = MaterialTheme.montserratFamily.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.dimens.marginMedium,
                    end = MaterialTheme.dimens.marginMedium,
                    bottom = MaterialTheme.dimens.buttonMargin,
                )
        ) {
            navController.navigate(
                Screen.DeviceList.route.replace(
                    oldValue = "{count}", newValue = "2"
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun KeygenQrPreview() {
    val navController = rememberNavController()
    KeygenPeerDiscovery(navController, Vault("new vault"))

}
