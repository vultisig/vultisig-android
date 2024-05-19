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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
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
    val dimens = MaterialTheme.dimens
    val coroutineScope = rememberCoroutineScope()
    val modalBottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        containerColor = Color.Transparent,
        sheetState = modalBottomSheetState,
        onDismissRequest = {
            coroutineScope.launch {
                modalBottomSheetState.hide()
                onDismissClick()
            }
        }) {
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
                                coroutineScope.launch {
                                    modalBottomSheetState.hide()
                                    onDismissClick()
                                }
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
                        minHeight = dimens.minHeightButton,
                        backgroundColor = appColor.red,
                        textColor = appColor.oxfordBlue800,
                        iconColor = appColor.turquoise800,
                        textStyle = Theme.montserrat.subtitle1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = dimens.marginMedium,
                                end = dimens.marginMedium,
                                bottom = dimens.marginMedium,
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
}



