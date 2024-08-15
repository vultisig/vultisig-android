package com.vultisig.wallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun BlowfishMessage(isShow: Boolean, warnings: List<String>) {
    if (isShow) {
        val isWarning = warnings.isNotEmpty()
        val blowfishMainColor =
            if (isWarning) Theme.colors.miamiMarmalade
            else Theme.colors.approval
        val blowfishBackgroundColor =
            if (isWarning) Theme.colors.miamiMarmaladeFaded
            else Theme.colors.approvalFaded
        val blowfishDrawableResId =
            if (isWarning) R.drawable.ic_warning
            else R.drawable.ic_blowfish_approve
        Card(
            modifier = Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp
            ),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(2.dp, blowfishMainColor),
            colors = CardDefaults.cardColors(
                containerColor = blowfishBackgroundColor,
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.Absolute.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiIcon(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        top = 16.dp,
                        bottom = 16.dp
                    ),
                    drawableResId = blowfishDrawableResId,
                    size = 16.dp,
                    tint = blowfishMainColor,
                )
                if (isWarning) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        warnings.forEach {
                            Text(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                text = it,
                                style = Theme.montserrat.body2.copy(
                                    fontSize = 12.sp,
                                ),
                                color = Theme.colors.neutral100,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = stringResource(id = R.string.blowfish_approve),
                        style = Theme.montserrat.body2.copy(
                            fontSize = 12.sp,
                        ),
                        color = Theme.colors.neutral100,
                    )
                }
            }
        }
    }
}