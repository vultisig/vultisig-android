package com.vultisig.wallet.presenter.keygen.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.ui.theme.appColor
import com.vultisig.wallet.app.ui.theme.dimens
import com.vultisig.wallet.app.ui.theme.montserratFamily


@Composable
fun DeviceInfo(@DrawableRes icon: Int, name: String, isSelected: Boolean,onItemSelected:(Boolean)->Unit) {
    val textColor = MaterialTheme.appColor.neutral0
    Column(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = MaterialTheme.dimens.small1))
            .background(MaterialTheme.appColor.oxfordBlue600Main)
            .padding(
                vertical = MaterialTheme.dimens.small1, horizontal = MaterialTheme.dimens.medium1
            ),

        horizontalAlignment = CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.height(100.dp)
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            text = name, color = textColor, style = MaterialTheme.montserratFamily.titleMedium
        )
        Checkbox(checked= isSelected, onCheckedChange = onItemSelected)
    }
}

@Preview(showBackground = true)
@Composable
fun DeviceInfoPreview() {
    val navController = rememberNavController()
    DeviceInfo(R.drawable.ipad, "iPad", true,onItemSelected = {  })

}