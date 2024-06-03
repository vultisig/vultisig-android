package com.vultisig.wallet.ui.screens.vault_settings.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun ConfirmDeleteScreen(navHostController: NavHostController) {
    val viewModel = hiltViewModel<ConfirmDeleteViewModel>()
    val uiModel by viewModel.uiModel.collectAsState()
    ConfirmDeleteScreen(
        cautions = uiModel.cautionsBeforeDelete,
        checkedCautionIndexes = uiModel.checkedCautionIndexes,
        isDeleteButtonActive = uiModel.isDeleteButtonEnabled,
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
    onDismissClick: () -> Unit,
    onItemCheckChangeClick: (Int, Boolean) -> Unit,
    onConfirmClick: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val appColor = Theme.colors


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
                        .clickable(onClick = {
                            onDismissClick()
                        }),
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
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .background(Theme.colors.oxfordBlue800)
                .fillMaxSize(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.danger_delete_file),
                        contentDescription = "danger delete file"
                    )
                    Text(
                        text = stringResource(R.string.confirm_delete_permanent_delete_message),
                        color = textColor,
                        style = Theme.menlo.subtitle1,
                        lineHeight = 32.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(250.dp)
                    )
                }
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                cautions.forEachIndexed { index, resId ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checkedCautionIndexes.contains(index),
                                onValueChange = { checked ->
                                    onItemCheckChangeClick(index, checked)
                                },
                            ), verticalAlignment = Alignment.CenterVertically
                    ) {
                        val checkboxDefaultColors = CheckboxDefaults.colors()
                        Checkbox(
                            checked = checkedCautionIndexes.contains(index),
                            onCheckedChange = { checked ->
                                onItemCheckChangeClick(index, checked)
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

                        Text(stringResource(id = resId), style = Theme.menlo.body2)
                    }
                }

                UiSpacer(size = 32.dp)

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
                    onClick = {
                        onConfirmClick()
                    },
                    disabled = isDeleteButtonActive.not()
                )
            }
        }
    }
}



