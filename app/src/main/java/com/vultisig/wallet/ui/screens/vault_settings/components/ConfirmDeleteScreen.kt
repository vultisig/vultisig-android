package com.vultisig.wallet.ui.screens.vault_settings.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.presenter.common.ClickOnce
import com.vultisig.wallet.presenter.common.clickOnce
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.Theme.colors


@Composable
internal fun ConfirmDeleteScreen(navHostController: NavHostController) {
    val viewModel = hiltViewModel<ConfirmDeleteViewModel>()
    val uiModel by viewModel.uiModel.collectAsState()
    ConfirmDeleteScreen(
        cautions = uiModel.cautionsBeforeDelete,
        checkedCautionIndexes = uiModel.checkedCautionIndexes,
        isDeleteButtonActive = uiModel.isDeleteButtonEnabled,
        vaultDeleteUiModel = uiModel.vaultDeleteUiModel,
        onDismissClick = {
            navHostController.popBackStack()
        },
        onItemCheckChangeClick = viewModel::changeCheckCaution,
        onConfirmClick = viewModel::delete
    )
}

@Composable
internal fun ConfirmDeleteScreen(
    cautions: List<Int>,
    checkedCautionIndexes: List<Int>,
    isDeleteButtonActive: Boolean,
    vaultDeleteUiModel: VaultDeleteUiModel,
    onDismissClick: () -> Unit,
    onItemCheckChangeClick: (Int, Boolean) -> Unit,
    onConfirmClick: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val appColor = colors

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Image(
                    painter = painterResource(id = R.drawable.caret_left),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clickOnce {
                            onDismissClick()
                        },
                )

                UiSpacer(weight = 1f)

                Text(
                    text = stringResource(R.string.confirm_delete_delete_vault),
                    color = textColor,
                    style = Theme.montserrat.heading5,
                )

                UiSpacer(weight = 1f)
                Spacer(modifier = Modifier.size(24.dp))
            }
        },
        bottomBar = {
            MultiColorButton(
                minHeight = 48.dp,
                backgroundColor = appColor.red,
                textColor = appColor.oxfordBlue800,
                iconColor = appColor.turquoise800,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
                text = stringResource(R.string.confirm_delete_delete_vault),
                onClick = onConfirmClick,
                disabled = isDeleteButtonActive.not()
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .background(colors.oxfordBlue800)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UiSpacer(size = 20.dp)
            Image(
                painter = painterResource(id = R.drawable.danger_delete_file),
                contentDescription = "danger delete file"
            )
            UiSpacer(size = 20.dp)
            Text(
                text = stringResource(R.string.confirm_delete_permanent_delete_message),
                color = textColor,
                style = Theme.menlo.subtitle1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.5f)
            )

            UiSpacer(size = 12.dp)

            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.oxfordBlue600Main)
                    .padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.vault_settings_delete_vault_details),
                        color = colors.neutral0,
                        style = Theme.montserrat.heading5,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.vault_settings_delete_vault_name),
                        color = colors.neutral0,
                        style = Theme.menlo.body2,
                    )
                    UiSpacer(size = 12.dp)
                    Text(
                        text = vaultDeleteUiModel.name,
                        color = colors.neutral0,
                        style = Theme.menlo.overline2,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.vault_settings_delete_vault_value),
                        color = colors.neutral0,
                        style = Theme.menlo.body2,
                    )
                    UiSpacer(size = 12.dp)
                    if (vaultDeleteUiModel.totalFiatValue != null){
                        Text(
                            text = vaultDeleteUiModel.totalFiatValue,
                            color = colors.neutral0,
                            style = Theme.menlo.overline2,
                        )
                    } else{
                        UiPlaceholderLoader(
                            modifier = Modifier
                                .width(24.dp)
                        )
                    }

                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.vault_settings_delete_vault_type),
                        color = colors.neutral0,
                        style = Theme.menlo.body2,
                    )
                    UiSpacer(size = 12.dp)
                    Text(
                        text = String.format(
                            stringResource(id = R.string.s_of_s_vault),
                            Utils.getThreshold(vaultDeleteUiModel.deviceList.size),
                            vaultDeleteUiModel.deviceList.size.toString()
                        ),
                        color = colors.neutral0,
                        style = Theme.menlo.overline2,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.vault_settings_delete_vault_ecdsa_key),
                        color = colors.neutral0,
                        style = Theme.menlo.body2,
                    )
                    UiSpacer(size = 12.dp)
                    Text(
                        text = vaultDeleteUiModel.pubKeyECDSA,
                        color = colors.neutral0,
                        style = Theme.montserrat.overline,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.vault_settings_delete_vault_eddsa_key),
                        color = colors.neutral0,
                        style = Theme.menlo.body2,
                    )
                    UiSpacer(size = 12.dp)
                    Text(
                        text = vaultDeleteUiModel.pubKeyEDDSA,
                        color = colors.neutral0,
                        style = Theme.montserrat.overline,
                    )
                }
            }

            UiSpacer(weight = 1f)

            cautions.forEachIndexed { index, resId ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checkedCautionIndexes.contains(index),
                            onValueChange = { checked ->
                                onItemCheckChangeClick(
                                    index,
                                    checked
                                )
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val checkboxDefaultColors = CheckboxDefaults.colors()
                    Checkbox(
                        checked = checkedCautionIndexes.contains(index),
                        onCheckedChange = { checked ->
                            onItemCheckChangeClick(
                                index,
                                checked
                            )
                        },
                        colors = CheckboxColors(
                            checkedCheckmarkColor = appColor.neutral0,
                            checkedBorderColor = appColor.turquoise800,
                            checkedBoxColor = appColor.turquoise800,
                            uncheckedCheckmarkColor = appColor.oxfordBlue200,
                            uncheckedBorderColor = appColor.oxfordBlue200,
                            uncheckedBoxColor = appColor.oxfordBlue200,
                            disabledBorderColor = checkboxDefaultColors.disabledBorderColor,
                            disabledCheckedBoxColor = checkboxDefaultColors.disabledCheckedBoxColor,
                            disabledUncheckedBoxColor = checkboxDefaultColors.disabledUncheckedBoxColor,
                            disabledIndeterminateBorderColor = checkboxDefaultColors.disabledIndeterminateBorderColor,
                            disabledUncheckedBorderColor = checkboxDefaultColors.disabledUncheckedBorderColor,
                            disabledIndeterminateBoxColor = checkboxDefaultColors.disabledIndeterminateBoxColor,
                        )
                    )

                    Text(
                        stringResource(id = resId),
                        style = Theme.menlo.body2
                    )
                }
            }

            UiSpacer(size = 16.dp)
        }
    }
}


