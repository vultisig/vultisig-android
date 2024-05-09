package com.vultisig.wallet.presenter.keygen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.appColor
import com.vultisig.wallet.ui.theme.dimens
import com.vultisig.wallet.ui.theme.menloFamily

@Composable
fun DeviceInfoItem(info: String) {
    val textColor = MaterialTheme.appColor.neutral0
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(MaterialTheme.appColor.oxfordBlue600Main)
            .padding(
                horizontal = MaterialTheme.dimens.small2, vertical = MaterialTheme.dimens.medium1
            )
    ) {
        Text(
            text = info, color = textColor, style = MaterialTheme.menloFamily.bodyMedium
        )
    }
}