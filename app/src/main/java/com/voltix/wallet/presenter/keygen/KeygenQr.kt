package com.voltix.wallet.presenter.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.voltix.wallet.R
import com.voltix.wallet.app.ui.theme.appColor
import com.voltix.wallet.app.ui.theme.dimens
import com.voltix.wallet.app.ui.theme.menloFamily
import com.voltix.wallet.app.ui.theme.montserratFamily
import com.voltix.wallet.presenter.common.TopBar
import com.voltix.wallet.presenter.keygen.components.DeviceInfo
import com.voltix.wallet.presenter.navigation.Screen

@Composable
fun KeygenQr(navController: NavHostController) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(MaterialTheme.dimens.medium1)
    ) {
        TopBar(
            centerText = "Keygen", startIcon = R.drawable.caret_left
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
        Text(
            text = "2 of 3 Vault",
            color = textColor,
            style = MaterialTheme.montserratFamily.bodyLarge
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))

        Text(
            text = "Pair with other devices:",
            color = textColor,
            style = MaterialTheme.montserratFamily.bodyMedium
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))

        Image(painter = painterResource(id = R.drawable.qr),
            contentScale = ContentScale.FillBounds,
            contentDescription = "devices",
            modifier = Modifier
                .width(150.dp)
                .height(150.dp)
                .drawBehind {
                    drawRoundRect(
                        color = Color("#33e6bf".toColorInt()), style = Stroke(
                            width = 8f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(50f, 50f), 0.0f)
                        ), cornerRadius = CornerRadius(16.dp.toPx())
                    )
                }
                .padding(20.dp))

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Spacer(
            modifier = Modifier.height(MaterialTheme.dimens.small1)
        )
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            DeviceInfo(R.drawable.ipad, "iPad", "1234h2i34h")
            Spacer(modifier = Modifier.width(MaterialTheme.dimens.large))
            DeviceInfo(R.drawable.iphone, "iPhone", "623654ghdsg")
        }

        Spacer(modifier = Modifier.weight(1.0f))

        Image(painter = painterResource(id = R.drawable.wifi), contentDescription = null)
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.large),
            text = "Keep devices on the same WiFi Network with VOLTIX open.",
            color = textColor,
            style = MaterialTheme.menloFamily.headlineSmall.copy(
                textAlign = TextAlign.Center, fontSize = 13.sp
            ),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))
        Button(onClick = {
            navController.navigate(
                Screen.DeviceList.route.replace(
                    oldValue = "{count}", newValue = "2"
                )
            )
        }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Start")
        }
    }
}
@Preview(showBackground = true, name = "KeygenQr Preview")
@Composable
fun PreviewKeygenQr() {
    val navController = rememberNavController()
    KeygenQr(navController = navController)
}