package com.vultisig.wallet.ui.screens.vault_settings.components.delete

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.library.form.VsUiCheckbox
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.screens.SettingInfoHorizontalItem
import com.vultisig.wallet.ui.screens.itemModifier
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.V2.colors

private val CAUTION_ROW_MIN_HEIGHT = 40.dp
private val BANNER_TEXT_MAX_WIDTH = 283.dp
private val GLOW_CENTER_Y = 331.dp
private val GLOW_RADIUS = 400.dp
private const val GLOW_HEIGHT_RATIO = 1.15f
private val GLOW_COLOR = Color(0xFFE9413F)
private val GLOW_STOPS =
    arrayOf(
        0f to GLOW_COLOR.copy(alpha = 0.1f),
        0.2f to GLOW_COLOR.copy(alpha = 0.086f),
        0.4f to GLOW_COLOR.copy(alpha = 0.056f),
        0.6f to GLOW_COLOR.copy(alpha = 0.028f),
        0.8f to GLOW_COLOR.copy(alpha = 0.01f),
        1f to Color.Transparent,
    )

@Composable
internal fun ConfirmDeleteScreen(navHostController: NavHostController) {
    val viewModel = hiltViewModel<ConfirmDeleteViewModel>()
    val uiModel by viewModel.uiModel.collectAsState()
    ConfirmDeleteScreen(
        cautions = uiModel.cautionsBeforeDelete,
        checkedCautionIndexes = uiModel.checkedCautionIndexes,
        isDeleteButtonActive = uiModel.isDeleteButtonEnabled,
        vaultDeleteUiModel = uiModel.vaultDeleteUiModel,
        onBackClick = { navHostController.popBackStack() },
        onItemCheckChangeClick = viewModel::changeCheckCaution,
        onConfirmClick = viewModel::delete,
    )
}

@Composable
private fun ConfirmDeleteScreen(
    cautions: List<Int>,
    checkedCautionIndexes: List<Int>,
    isDeleteButtonActive: Boolean,
    vaultDeleteUiModel: VaultDeleteUiModel,
    onItemCheckChangeClick: (Int, Boolean) -> Unit,
    onConfirmClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    V2Scaffold(
        title = stringResource(R.string.confirm_delete_delete_vault),
        onBackClick = onBackClick,
        bottomBar = {
            VsButton(
                onClick = onConfirmClick,
                state =
                    if (isDeleteButtonActive.not()) VsButtonState.Disabled
                    else VsButtonState.Enabled,
                label = stringResource(R.string.confirm_delete_delete_vault),
                variant = VsButtonVariant.Error,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 22.dp),
            )
        },
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .settingsDeleteGlow()
                    .verticalScroll(rememberScrollState())
        ) {
            DeleteVaultBanner()
            UiSpacer(14.dp)
            SettingInfoHorizontalItem(
                key = stringResource(R.string.vault_settings_delete_vault_name),
                value = vaultDeleteUiModel.name,
                itemShape = Theme.v2.radius.lg,
                itemVerticalPadding = 20.dp,
            )
            UiSpacer(12.dp)
            SettingInfoHorizontalItem(
                key = stringResource(R.string.vault_settings_delete_vault_value),
                value = vaultDeleteUiModel.totalFiatValue,
                itemShape = Theme.v2.radius.lg,
                itemVerticalPadding = 20.dp,
            )
            UiSpacer(12.dp)

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(intrinsicSize = IntrinsicSize.Min),
            ) {
                VerticalVaultInfo(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    key = stringResource(R.string.vault_settings_delete_vault_part),
                    value =
                        stringResource(
                            id = R.string.vault_part_n_of_t,
                            vaultDeleteUiModel.vaultPart,
                            vaultDeleteUiModel.deviceList.size,
                        ),
                )

                VerticalVaultInfo(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    key = stringResource(R.string.vault_settings_delete_vault_id),
                    value = vaultDeleteUiModel.localPartyId,
                )
            }

            UiSpacer(12.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(intrinsicSize = IntrinsicSize.Min),
            ) {
                VerticalVaultInfo2(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    key = stringResource(R.string.vault_settings_delete_vault_ecdsa_key),
                    value = vaultDeleteUiModel.pubKeyECDSA,
                )

                VerticalVaultInfo2(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    key = stringResource(R.string.vault_settings_delete_vault_eddsa_key),
                    value = vaultDeleteUiModel.pubKeyEDDSA,
                )
            }

            UiSpacer(size = 22.dp)

            cautions.forEachIndexed { index, resId ->
                if (index > 0) UiSpacer(size = 2.dp)
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .then(
                                if (index == 0) Modifier
                                else Modifier.heightIn(min = CAUTION_ROW_MIN_HEIGHT)
                            )
                            .toggleable(
                                value = checkedCautionIndexes.contains(index),
                                onValueChange = { checked ->
                                    onItemCheckChangeClick(index, checked)
                                },
                            )
                            .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VsUiCheckbox(
                        checked = checkedCautionIndexes.contains(index),
                        checkIcon = R.drawable.ic_caution_check,
                        checkIconSize = 24.dp,
                    ) { checked ->
                        onItemCheckChangeClick(index, checked)
                    }

                    Text(
                        text = stringResource(resId),
                        style = Theme.brockmann.supplementary.caption,
                        color = colors.text.secondary,
                    )
                }
            }

            UiSpacer(size = 8.dp)
        }
    }
}

@Composable
private fun VerticalVaultInfo(modifier: Modifier = Modifier, key: String, value: String) {
    Column(
        modifier = modifier.itemModifier(shape = Theme.v2.radius.lg),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = key,
            style = Theme.brockmann.supplementary.footnote,
            color = colors.text.secondary,
        )
        Text(
            text = value,
            style = Theme.brockmann.supplementary.footnote,
            color = colors.text.primary,
        )
    }
}

@Composable
private fun VerticalVaultInfo2(modifier: Modifier = Modifier, key: String, value: String) {
    Column(
        modifier = modifier.itemModifier(shape = Theme.v2.radius.lg),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = key,
            style = Theme.brockmann.supplementary.footnote,
            color = colors.text.primary,
        )
        Text(
            text = value,
            style = Theme.brockmann.supplementary.caption,
            color = colors.text.tertiary,
        )
    }
}

@Composable
private fun DeleteVaultBanner() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        UiSpacer(size = 17.dp)
        UiIcon(
            drawableResId = R.drawable.ic_triangle_warning_filled,
            contentDescription = "warning",
            size = 24.dp,
            tint = colors.alerts.error,
        )

        UiSpacer(size = 14.dp)

        Text(
            text = stringResource(R.string.confirm_delete_delete_vault),
            style = Theme.brockmann.headings.title2,
            color = colors.alerts.error,
        )

        UiSpacer(size = 8.dp)

        Text(
            text = stringResource(R.string.confirm_delete_permanent_delete_message),
            style = Theme.brockmann.supplementary.footnote,
            color = colors.text.tertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = BANNER_TEXT_MAX_WIDTH),
        )
    }
}

/** The red wash Figma lays over the screen's content, a blurred ellipse. */
private fun Modifier.settingsDeleteGlow(): Modifier = drawWithContent {
    drawContent()
    val glowCenter = Offset(center.x, GLOW_CENTER_Y.toPx())
    val radius = GLOW_RADIUS.toPx()
    scale(scaleX = 1f, scaleY = GLOW_HEIGHT_RATIO, pivot = glowCenter) {
        drawCircle(
            brush =
                Brush.radialGradient(
                    colorStops = GLOW_STOPS,
                    center = glowCenter,
                    radius = radius,
                ),
            radius = radius,
            center = glowCenter,
        )
    }
}

@Preview
@Composable
private fun ConfirmDeleteScreenPreview() {
    ConfirmDeleteScreen(
        cautions =
            listOf(
                R.string.vault_settings_delete_vault_caution1,
                R.string.vault_settings_delete_vault_caution2,
                R.string.vault_settings_delete_vault_caution3,
            ),
        checkedCautionIndexes = listOf(0, 1),
        isDeleteButtonActive = false,
        vaultDeleteUiModel =
            VaultDeleteUiModel(
                name = "My Vault",
                totalFiatValue = "$12,345.67",
                vaultPart = 2,
                deviceList = listOf("Device 1", "Device 2", "Device 3"),
                localPartyId = "123e4567-e89b-12d3-a456-426614174000",
                pubKeyECDSA = "04bfcabf...9c3d1e5f",
                pubKeyEDDSA = "ed25519:3b6a27bc...",
            ),
        onItemCheckChangeClick = { _, _ -> },
        onConfirmClick = {},
        onBackClick = {},
    )
}
