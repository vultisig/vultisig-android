package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.ui.components.CopyIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.DeviceMeta
import com.vultisig.wallet.ui.models.VaultDetailUiModel
import com.vultisig.wallet.ui.models.VaultDetailViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VaultDetailScreen(
    navHostController: NavHostController,
    model: VaultDetailViewModel = hiltViewModel()
) {
    val state by model.uiModel.collectAsState()

    VaultDetailScreen(
        state = state,
        onBackClick = {
            navHostController.popBackStack()
        }
    )
}

@Composable
private fun VaultDetailScreen(
    state: VaultDetailUiModel,
    onBackClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.vault_settings_details_title),
                iconLeft = R.drawable.ic_caret_left,
                onIconLeftClick = onBackClick
            )
        }
    ) {
        Column(
            Modifier
                .padding(it)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
                .verticalScroll(rememberScrollState())
        ) {
            VaultDetailGroup(
                title = "Vault Info"
            ) {
                InfoItem(
                    key = stringResource(R.string.vault_detail_screen_vault_name),
                    value = state.name
                )
                InfoItem(
                    key = stringResource(R.string.vault_details_screen_vault_part),
                    value = stringResource(
                        R.string.vault_details_screen_vault_part_desc,
                        state.vaultPart,
                        state.vaultSize
                    ),
                )
                InfoItem(
                    key = stringResource(R.string.vault_details_screen_vault_type),
                    value = state.libType ?: "error"
                )
            }

            UiSpacer(24.dp)

            VaultDetailGroup(title = "keys") {
                KeyItem(
                    type = "ECDSA",
                    value = state.pubKeyECDSA
                )
                KeyItem(
                    type = "EdDSA",
                    value = state.pubKeyEDDSA
                )
            }

            UiSpacer(24.dp)

            VaultDetailGroup(
                title = String.format(
                    stringResource(id = R.string.s_of_s_vault),
                    Utils.getThreshold(state.deviceList.size),
                    state.deviceList.size.toString(),
                ),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.deviceList.forEachIndexed { index, it ->
                        DeviceItem(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(0.5f),
                            order = "Signer ${index + 1}",
                            name = it.name,
                            isThisDevice = it.isThisDevice
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun VaultDetailGroupPreview() {
    VaultDetailGroup(title = "Vault Info") {
        Column {
            InfoItem(
                key = "Vault Name",
                value = "Main Vault"
            )
        }
    }
}

@Composable
fun VaultDetailGroup(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.colors.text.extraLight
        )
        content()
    }
}

@Preview
@Composable
private fun VaultDetailScreenPreview() {
    VaultDetailScreen(
        state = VaultDetailUiModel(
            name = "Vault Name",
            vaultPart = "2",
            vaultSize = "2",
            pubKeyECDSA = "asdjhfaksdjhfkajsdhflkajshflkasdjflkajsdflk",
            pubKeyEDDSA = "asdjhfaksdjhfkajsdhflkajshflkasdjflkajsdflk",
            libType = "type",
            deviceList = listOf(
                DeviceMeta(
                    name = "Samsung",
                    isThisDevice = true,
                ),
                DeviceMeta(
                    name = "MacBook",
                    isThisDevice = false
                )
            )
        ),
        onBackClick = {}
    )
}

@Preview
@Composable
private fun InfoItemPreview() {
    InfoItem(key = "Vault Name", value = "Main Vault")
}

@Composable
private fun InfoItem(
    key: String,
    value: String,
) {
    SettingInfoHorizontalItem(
        key = key,
        value = value,
    )
}

@Preview
@Composable
private fun KeyItemPrev() {
    KeyItem(
        type = "ECDSA",
        value = "asdjhfaksdjhfkajsdhflkajshflkasdjflkajsdflk"
    )
}

@Composable
internal fun Modifier.itemModifier(): Modifier = border(
    width = 1.dp,
    color = Theme.colors.borders.light,
    shape = RoundedCornerShape(
        size = 12.dp
    )
)
    .background(
        shape = RoundedCornerShape(
            size = 12.dp
        ),
        color = Theme.colors.backgrounds.disabled
    )
    .padding(
        vertical = 24.dp,
        horizontal = 20.dp
    )

@Composable
private fun KeyItem(type: String, value: String) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .itemModifier()
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)

        ) {
            Text(
                text = type,
                style = Theme.brockmann.headings.subtitle,
                color = Theme.colors.text.primary
            )

            Text(
                text = value,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.colors.text.extraLight
            )
        }
        UiSpacer(16.dp)
        CopyIcon(textToCopy = value)
    }
}

@Composable
internal fun SettingInfoHorizontalItem(
    modifier: Modifier = Modifier,
    key: String,
    value: String?,
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .itemModifier(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = key,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.primary
        )

        if (value == null)
            UiPlaceholderLoader(
                modifier = Modifier
                    .width(24.dp)
            ) else
            Text(
                text = value,
                style = Theme.brockmann.body.s.medium,
                color = Theme.colors.text.primary
            )
    }

}


@Composable
internal fun SettingInfoItemVertical(
    modifier: Modifier = Modifier,
    key: String,
    value: String,
    content: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .itemModifier(),
        verticalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.CenterVertically)
    ) {
        Text(
            text = key,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.light,
        )

        Text(
            text = value,
            style = Theme.brockmann.button.medium,
            color = Theme.colors.neutral0
        )

        content?.let {
            it()
        }
    }
}

@Composable
private fun DeviceItem(
    modifier: Modifier = Modifier,
    order: String,
    name: String,
    isThisDevice: Boolean
) {

    SettingInfoItemVertical(
        modifier = modifier,
        key = order,
        value = name,
        content = if (isThisDevice) {
            {
                Text(
                    text = "This device",
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.colors.text.light
                )
            }
        } else null
    )
}


@Preview
@Composable
private fun DeviceItemPreview() {
    DeviceItem(
        order = "Signer 1",
        name = "MacBook (Web)",
        isThisDevice = true
    )
}