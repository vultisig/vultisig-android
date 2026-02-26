package com.vultisig.wallet.ui.screens.v3.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.v3.DeviceList
import com.vultisig.wallet.ui.models.v3.ReviewVaultDevicesEvent
import com.vultisig.wallet.ui.models.v3.ReviewVaultDevicesUiState
import com.vultisig.wallet.ui.models.v3.ReviewVaultDevicesViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReviewVaultDevicesScreen(
    viewModel: ReviewVaultDevicesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    ReviewVaultDevicesScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun ReviewVaultDevicesScreen(
    uiState: ReviewVaultDevicesUiState,
    onEvent: (ReviewVaultDevicesEvent) -> Unit,
) {
    V3Scaffold(
        onBackClick = { onEvent(ReviewVaultDevicesEvent.Back) },
        bottomBar = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = V3Scaffold.PADDING_HORIZONTAL),
            ) {
                VsButton(
                    label = stringResource(R.string.review_vault_devices_looks_good),
                    variant = VsButtonVariant.CTA,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onEvent(ReviewVaultDevicesEvent.LooksGood) },
                )

                UiSpacer(size = 20.dp)

                Text(
                    text = stringResource(R.string.review_vault_devices_something_wrong),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.secondary,
                    modifier = Modifier.clickable {
                        onEvent(ReviewVaultDevicesEvent.SomethingsWrong)
                    },
                )

                UiSpacer(size = 12.dp)
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,

            ) {

            Image(
                painter = painterResource(id = R.drawable.vault_details),
                contentDescription = "vault devices",
                modifier = Modifier.width(244.dp)
            )

            UiSpacer(size = 30.dp)

            Text(
                text = stringResource(R.string.review_vault_devices_title),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.Center,
            )

            UiSpacer(size = 12.dp)

            Text(
                text = stringResource(R.string.review_vault_devices_description),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = V3Scaffold.PADDING_HORIZONTAL),
            )

            UiSpacer(size = 32.dp)

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(uiState.devices) { index, device ->
                    VaultDeviceItem(
                        name = if (device.name == uiState.localPartyId) {
                            stringResource(
                                R.string.review_vault_devices_this_device_label,
                                device.name,
                            )
                        } else {
                            device.name
                        },
                        label = stringResource(
                            R.string.review_vault_devices_device_index,
                            index + 1,
                        ),
                        device_image = device.device_image,
                        id = device.id
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultDeviceItem(
    name: String,
    label: String,
    id: String,
    modifier: Modifier = Modifier,
    @DrawableRes device_image: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Theme.v2.colors.backgrounds.secondary,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(
                horizontal = 16.dp,
                vertical = 14.dp
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = Theme.v2.colors.backgrounds.primary,
                    shape = CircleShape,
                ),
        ) {
            UiIcon(
                drawableResId = device_image,
                size = 20.dp,
                tint = Theme.v2.colors.alerts.info,
            )
        }

        Column {
            Text(
                text = name,
                style = Theme.brockmann.headings.subtitle,
                color = Theme.v2.colors.neutrals.n50,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${label} - ${id}",
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun ReviewVaultDevicesScreenPreview() {
    ReviewVaultDevicesScreen(
        uiState = ReviewVaultDevicesUiState(
            localPartyId = "iPhone",
            devices = listOf(
                DeviceList(
                    name = "iPhone",
                    device_image = R.drawable.iphone,
                    id = "ABC123"
                ),
                DeviceList(
                    name = "Extension",
                    device_image = R.drawable.extension,
                    id = "ABC123"
                ),
                DeviceList(
                    name = "MacBook",
                    device_image = R.drawable.mac_book,
                    id = "ABC123"
                )
            )
        ),
        onEvent = {},

        )
}
