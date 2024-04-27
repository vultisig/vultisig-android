package com.voltix.wallet.presenter.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.voltix.wallet.R.drawable
import com.voltix.wallet.app.ui.theme.appColor
import com.voltix.wallet.app.ui.theme.dimens
import com.voltix.wallet.app.ui.theme.menloFamily
import com.voltix.wallet.app.ui.theme.montserratFamily
import com.voltix.wallet.presenter.common.TopBar
import com.voltix.wallet.presenter.navigation.Screen

@Composable
fun Setup(navController: NavHostController) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(MaterialTheme.dimens.medium1)
    ) {
        TopBar(
            centerText = "Setup", startIcon = drawable.caret_left, endIcon = drawable.question
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
        Text(
            text = "2 of 3 Vault",
            color = textColor,
            style = MaterialTheme.montserratFamily.bodyLarge
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))

        Text(
            text = "(Any 3 Devices)",
            color = textColor,
            style = MaterialTheme.montserratFamily.bodyMedium
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))

        Image(
            painter = painterResource(id = drawable.devices),
            contentDescription = "devices",
            modifier = Modifier.width(140.dp)
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))

        Text(
            style = MaterialTheme.montserratFamily.bodySmall,
            text = "3 Devices to create a vault; ",
            color = textColor
        )
        Text(
            style = MaterialTheme.montserratFamily.bodySmall,
            text = "2 devices to sign a transaction.",
            color = textColor
        )
        Text(
            style = MaterialTheme.montserratFamily.bodySmall,
            text = "Automatically backed-up",
            color = textColor
        )

        Spacer(modifier = Modifier.weight(1.0f))

        Image(painter = painterResource(id = drawable.wifi), contentDescription = null)
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
            navController.navigate(Screen.KeygenQr.route)
        }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Start")
        }
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Button(onClick = {
                         navController.navigate(Screen.Pair.route)
        }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Join")
        }
    }
}
@Preview(showBackground = true, name = "Setup Preview")
@Composable
fun PreviewSetup() {
    val navController = rememberNavController()
    Setup(navController)
}