package com.vultisig.wallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
internal fun BackupWarning(onWarningClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, Theme.colors.errorBorder),
        colors = CardDefaults.cardColors(
            containerColor = Theme.colors.errorBackground
        ),
        onClick = { onWarningClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Absolute.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(
                drawableResId = R.drawable.ic_warning,
                size = 24.dp,
                tint = Theme.colors.errorBorder,
                modifier = Modifier.padding(16.dp),
            )
            Text(
                text = stringResource(id = R.string.backup_now),
                style = Theme.montserrat.body2.copy(
                    fontSize = 16.sp,
                ),
                color = Theme.colors.neutral100,
            )
            UiIcon(
                drawableResId = R.drawable.caret_right,
                size = 18.dp,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}