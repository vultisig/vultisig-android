package com.vultisig.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun BackupWarning(onWarningClick: () -> Unit) {
    WarningCard(
        onClick = onWarningClick,
    ) {
        Text(
            text = stringResource(id = R.string.backup_now),
            textAlign = TextAlign.Center,
            style = Theme.montserrat.body2.copy(
                fontSize = 16.sp,
            ),
            color = Theme.colors.neutral100,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .weight(1f),
        )
    }

}

@Composable
internal fun WarningCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    @DrawableRes startIcon: Int = R.drawable.ic_warning,
    startIconTint: Color = Theme.colors.alert,
    startIconSize: Dp = 24.dp,
    @DrawableRes endIcon: Int = R.drawable.ic_small_caret_right,
    endIconTint: Color = Theme.colors.neutral100,
    endIconSize: Dp = 18.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, Theme.colors.alert),
        colors = CardDefaults.cardColors(
            containerColor = Theme.colors.alertBackground
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Absolute.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(
                drawableResId = startIcon,
                size = startIconSize,
                tint = startIconTint,
                modifier = Modifier.padding(16.dp),
            )
            content()
            UiIcon(
                drawableResId = endIcon,
                tint = endIconTint,
                size = endIconSize,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview
@Composable
private fun BackupWarningPreview() {
    BackupWarning {}
}