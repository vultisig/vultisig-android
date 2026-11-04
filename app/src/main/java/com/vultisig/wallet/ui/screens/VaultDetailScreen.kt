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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.ui.components.CopyIcon
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v2.snackbar.VSSnackbarState
import com.vultisig.wallet.ui.components.v2.snackbar.VsSnackBar
import com.vultisig.wallet.ui.components.v2.snackbar.rememberVsSnackbarState
import com.vultisig.wallet.ui.models.vault.DeviceMeta
import com.vultisig.wallet.ui.models.vault.VaultDetailUiModel
import com.vultisig.wallet.ui.models.vault.VaultDetailViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsClipboardService

/** Caps the height of the long MLDSA public key row before it ellipsizes. */
private const val MLDSA_KEY_MAX_LINES = 4

private val DETAIL_ROW_MIN_HEIGHT = 66.dp
private val KEY_ROW_VERTICAL_PADDING = 14.5.dp
private val SIGNER_ICON_SIZE = 23.dp

@Composable
internal fun VaultDetailScreen(
    navHostController: NavHostController,
    model: VaultDetailViewModel = hiltViewModel(),
) {
    val state by model.uiModel.collectAsState()
    val snackbarState = rememberVsSnackbarState()
    val graphicsLayer = rememberGraphicsLayer()

    VaultDetailScreen(
        state = state,
        snackBarState = snackbarState,
        graphicsLayer = graphicsLayer,
        onShareClick = { model.takeScreenShot(graphicsLayer = graphicsLayer) },
        onBackClick = { navHostController.popBackStack() },
    )
}

@Composable
internal fun VaultDetailScreen(
    state: VaultDetailUiModel,
    snackBarState: VSSnackbarState,
    onBackClick: () -> Unit,
    graphicsLayer: GraphicsLayer,
    onShareClick: () -> Unit,
) {
    val ecdsaKeyCopiedMessage = stringResource(R.string.vault_detail_screen_ecdsa_key_copied)
    val eddsaKeyCopiedMessage = stringResource(R.string.vault_detail_screen_eddsa_key_copied)
    val mldsaKeyCopiedMessage = stringResource(R.string.vault_detail_screen_mldsa_key_copied)

    Box(modifier = Modifier.fillMaxSize()) {
        V2Scaffold(
            title = stringResource(R.string.vault_settings_details_title),
            onBackClick = onBackClick,
            rightIcon = R.drawable.ic_download_4,
            onRightIconClick = onShareClick,
        ) {
            Column(
                modifier =
                    Modifier.verticalScroll(rememberScrollState()).drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    }
            ) {
                VaultDetailGroup(title = stringResource(R.string.vault_detail_vault_info)) {
                    InfoItem(
                        key = stringResource(R.string.vault_detail_screen_vault_name),
                        value = state.name,
                    )
                    InfoItem(
                        key = stringResource(R.string.vault_details_screen_vault_part),
                        value =
                            stringResource(
                                R.string.vault_share_position,
                                state.vaultPart,
                                state.vaultSize,
                            ),
                    )
                    InfoItem(
                        key = stringResource(R.string.vault_details_screen_vault_type),
                        value = state.libType ?: "error",
                    )
                }

                UiSpacer(24.dp)

                VaultDetailGroup(title = stringResource(R.string.vault_details_keys)) {
                    KeyItem(
                        type = stringResource(R.string.vault_detail_screen_ecdsa_key),
                        value = state.pubKeyECDSA,
                        onCopyCompleted = {
                            if (VsClipboardService.needsCopyConfirmation())
                                snackBarState.show(ecdsaKeyCopiedMessage)
                        },
                    )
                    KeyItem(
                        type = stringResource(R.string.vault_detail_screen_eddsa_key),
                        value = state.pubKeyEDDSA,
                        onCopyCompleted = {
                            if (VsClipboardService.needsCopyConfirmation())
                                snackBarState.show(eddsaKeyCopiedMessage)
                        },
                    )
                    if (state.pubKeyMLDSA.isNotBlank()) {
                        KeyItem(
                            type = stringResource(R.string.vault_detail_screen_mldsa_key),
                            value = state.pubKeyMLDSA,
                            // The MLDSA public key is far longer than the ECDSA/EdDSA keys; cap its
                            // height and ellipsize so it doesn't blow up the row. The full value is
                            // still copyable via the copy icon.
                            maxValueLines = MLDSA_KEY_MAX_LINES,
                            onCopyCompleted = {
                                if (VsClipboardService.needsCopyConfirmation())
                                    snackBarState.show(mldsaKeyCopiedMessage)
                            },
                        )
                    }
                }

                UiSpacer(24.dp)

                VaultDetailGroup(
                    title =
                        String.format(
                            stringResource(id = R.string.s_of_s_vault),
                            Utils.getThreshold(state.deviceList.size),
                            state.deviceList.size.toString(),
                        )
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.deviceList.forEachIndexed { index, it ->
                            DeviceItem(
                                modifier = Modifier.fillMaxHeight().weight(0.5f),
                                order = stringResource(R.string.vault_detail_signer, index + 1),
                                name = it.name,
                                isThisDevice = it.isThisDevice,
                            )
                        }
                    }
                }
            }
        }

        VsSnackBar(snackbarState = snackBarState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Preview
@Composable
private fun VaultDetailGroupPreview() {
    VaultDetailGroup(title = "Vault Info") {
        Column { InfoItem(key = "Vault Name", value = "Main Vault") }
    }
}

@Composable
fun VaultDetailGroup(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
        )
        content()
    }
}

@Preview
@Composable
private fun VaultDetailScreenPreview() {
    VaultDetailScreen(
        state =
            VaultDetailUiModel(
                name = "Vault Name",
                vaultPart = "2",
                vaultSize = "2",
                pubKeyECDSA = "asdjhfaksdjhfkajsdhflkajshflkasdjflkajsdflk",
                pubKeyEDDSA = "asdjhfaksdjhfkajsdhflkajshflkasdjflkajsdflk",
                libType = "type",
                deviceList =
                    listOf(
                        DeviceMeta(name = "Samsung", isThisDevice = true),
                        DeviceMeta(name = "MacBook", isThisDevice = false),
                    ),
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
private fun InfoItem(key: String, value: String) {
    SettingInfoHorizontalItem(
        modifier = Modifier.heightIn(min = DETAIL_ROW_MIN_HEIGHT),
        key = key,
        value = value,
        keyStyle = Theme.brockmann.body.s.medium,
    )
}

@Preview
@Composable
private fun KeyItemPrev() {
    KeyItem(type = "ECDSA", value = "asdjhfaksdjhfkajsdhflkajshflkasdjflkajsdflk")
}

@Composable
internal fun Modifier.itemModifier(
    shape: Shape = Theme.v2.radius.md,
    verticalPadding: Dp = 16.dp,
    horizontalPadding: Dp = 20.dp,
): Modifier =
    border(width = 1.dp, color = Theme.v2.colors.border.light, shape = shape)
        .background(shape = shape, color = Theme.v2.colors.backgrounds.disabled)
        .padding(vertical = verticalPadding, horizontal = horizontalPadding)

@Composable
private fun KeyItem(
    type: String,
    value: String,
    maxValueLines: Int = Int.MAX_VALUE,
    onCopyCompleted: (String) -> Unit = {},
) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = DETAIL_ROW_MIN_HEIGHT)
                .itemModifier(verticalPadding = KEY_ROW_VERTICAL_PADDING),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = type,
                style = Theme.brockmann.headings.subtitle,
                color = Theme.v2.colors.text.primary,
            )

            Text(
                text = value,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
                maxLines = maxValueLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        UiSpacer(12.dp)
        CopyIcon(
            textToCopy = value,
            onCopyCompleted = onCopyCompleted,
            icon = R.drawable.copy2,
            tint = Color.Unspecified,
        )
    }
}

@Composable
internal fun SettingInfoHorizontalItem(
    modifier: Modifier = Modifier,
    key: String,
    value: String?,
    keyStyle: TextStyle = Theme.brockmann.supplementary.footnote,
    itemShape: Shape = Theme.v2.radius.md,
    itemVerticalPadding: Dp = 16.dp,
) {

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .itemModifier(shape = itemShape, verticalPadding = itemVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = key,
            style = keyStyle,
            color = Theme.v2.colors.text.primary,
        )

        if (value == null) UiPlaceholderLoader(modifier = Modifier.width(24.dp))
        else
            Text(
                text = value,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )
    }
}

@Composable
private fun DeviceItem(
    modifier: Modifier = Modifier,
    order: String,
    name: String,
    isThisDevice: Boolean,
) {
    Row(
        modifier =
            modifier.itemModifier(shape = Theme.v2.radius.lg, horizontalPadding = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = order,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.secondary,
            )
            Text(
                text = name,
                style = Theme.brockmann.button.medium.medium,
                color = Theme.v2.colors.neutrals.n50,
            )
            Text(
                text = if (isThisDevice) stringResource(R.string.vault_detail_this_device) else "",
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.secondary,
            )
        }
        UiIcon(
            drawableResId = R.drawable.ic_laptop_mobile,
            size = SIGNER_ICON_SIZE,
            tint = Color.Unspecified,
        )
    }
}

@Preview
@Composable
private fun DeviceItemPreview() {
    DeviceItem(order = "Signer 1", name = "MacBook (Web)", isThisDevice = true)
}
