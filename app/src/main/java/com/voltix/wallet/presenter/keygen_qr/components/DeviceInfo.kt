package com.voltix.wallet.presenter.keygen_qr.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.voltix.wallet.app.ui.theme.appColor
import com.voltix.wallet.app.ui.theme.dimens
import com.voltix.wallet.app.ui.theme.montserratFamily


@Composable
fun DeviceInfo(@DrawableRes icon: Int, name: String, info: String) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = MaterialTheme.dimens.small1))
            .background(MaterialTheme.appColor.oxfordBlue600Main)
            .padding(
                vertical = MaterialTheme.dimens.small1, horizontal = MaterialTheme.dimens.medium1
            ),

        horizontalAlignment = CenterHorizontally
    ) {
        Image(painter = painterResource(id = icon), contentDescription = "ipad")
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            text = name, color = textColor, style = MaterialTheme.montserratFamily.titleMedium
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            text = info, color = textColor, style = MaterialTheme.montserratFamily.titleSmall
        )
    }
}