package com.voltix.wallet.presenter.device_list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.voltix.wallet.app.ui.theme.appColor
import com.voltix.wallet.app.ui.theme.dimens
import com.voltix.wallet.app.ui.theme.menloFamily

@Composable
fun DeviceInfoItem(info: String) {
    val textColor = MaterialTheme.colorScheme.onBackground
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
@Preview(showBackground = true)
@Composable
fun PreviewDeviceInfoItem() {
    DeviceInfoItem("Device Name: Voltix")
}