package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v2.snackbar.VSSnackbarState
import com.vultisig.wallet.ui.components.v2.snackbar.VsSnackBar
import com.vultisig.wallet.ui.components.v2.snackbar.rememberVsSnackbarState
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
    val snackbarState = rememberVsSnackbarState()
    val graphicsLayer = rememberGraphicsLayer()
    val context = LocalContext.current

    VaultDetailScreen(
        state = state,
        snackBarState = snackbarState,
        graphicsLayer = graphicsLayer,
        onShareClick = {
            model.takeScreenShot(
                graphicsLayer = graphicsLayer,
                context = context,
            )
        },
        onBackClick = {
            navHostController.popBackStack()
        },
    )
}

@Composable
private fun VaultDetailScreen(
    state: VaultDetailUiModel,
    snackBarState: VSSnackbarState,
    onBackClick: () -> Unit,
    graphicsLayer: GraphicsLayer,
    onShareClick: () -> Unit,
) {
    val ecdsaKeyCopiedMessage = stringResource(R.string.vault_detail_screen_ecdsa_key_copied)
    val eddsaKeyCopiedMessage = stringResource(R.string.vault_detail_screen_eddsa_key_copied)
    
    Box(modifier = Modifier.fillMaxSize()) {
        V2Scaffold(
            title = stringResource(R.string.vault_settings_details_title),
            onBackClick = onBackClick,
            rightIcon = R.drawable.ic_share,
            onRightIconClick = onShareClick,
        ){
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .drawWithContent {
                        graphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(graphicsLayer)
                    }
            ) {
                VaultDetailGroup(
                    title = stringResource(R.string.vault_detail_vault_info)
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

                VaultDetailGroup(title = stringResource(R.string.vault_details_keys)) {
                    KeyItem(
                        type = "ECDSA Key",
                        value = state.pubKeyECDSA,
                        onCopyCompleted = {
                            snackBarState.show(ecdsaKeyCopiedMessage)
                        },
                    )
                    KeyItem(
                        type = "EdDSA Key",
                        value = state.pubKeyEDDSA,
                        onCopyCompleted = {
                            snackBarState.show(eddsaKeyCopiedMessage)
                        },
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
        
        VsSnackBar(
            snackbarState = snackBarState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
            color = Theme.v2.colors.text.tertiary
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
        snackBarState = rememberVsSnackbarState(),
        graphicsLayer = rememberGraphicsLayer(),
        onShareClick = {},
        onBackClick = {},
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
    color = Theme.v2.colors.border.light,
    shape = RoundedCornerShape(
        size = 12.dp
    )
)
    .background(
        shape = RoundedCornerShape(
            size = 12.dp
        ),
        color = Theme.v2.colors.backgrounds.disabled
    )
    .padding(
        vertical = 24.dp,
        horizontal = 20.dp
    )

@Composable
private fun KeyItem(type: String, value: String, onCopyCompleted: (String) -> Unit = {}) {

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
                color = Theme.v2.colors.text.primary
            )

            Text(
                text = value,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary
            )
        }
        UiSpacer(16.dp)
        CopyIcon(textToCopy = value, onCopyCompleted = onCopyCompleted)
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
            color = Theme.v2.colors.text.primary
        )

        if (value == null)
            UiPlaceholderLoader(
                modifier = Modifier
                    .width(24.dp)
            ) else
            Text(
                text = value,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary
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
            color = Theme.v2.colors.text.secondary,
        )

        Text(
            text = value,
            style = Theme.brockmann.button.medium.medium,
            color = Theme.v2.colors.neutrals.n50
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
                    text = stringResource(R.string.peer_discovery_this_device),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.secondary
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