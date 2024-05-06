package com.vultisig.wallet.presenter.keygen

import com.vultisig.wallet.presenter.base_components.MultiColorButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.R.drawable
import com.vultisig.wallet.app.ui.theme.appColor
import com.vultisig.wallet.app.ui.theme.dimens
import com.vultisig.wallet.app.ui.theme.menloFamily
import com.vultisig.wallet.app.ui.theme.montserratFamily
//import com.vultisig.wallet.presenter.common.TopBar
import com.vultisig.wallet.presenter.navigation.Screen


@Composable
fun Setup(navController: NavHostController) {
    val textColor = MaterialTheme.appColor.neutral0
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(MaterialTheme.dimens.marginMedium)
    ) {
//        TopBar(
//            navController = navController,
//            centerText = "Setup",
//            startIcon = drawable.caret_left,
//            endIcon = drawable.question,
//        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            text = "2 of 3 Vault",
            color = textColor,
            style = MaterialTheme.montserratFamily.bodyLarge
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))

        Text(
            text = "(Any 3 Devices)",
            color = textColor,
            style = MaterialTheme.montserratFamily.bodyMedium
        )
        Spacer(modifier = Modifier.weight(0.5f))

        Image(
            painter = painterResource(id = drawable.devices),
            contentDescription = "devices",
            modifier = Modifier.weight(3.5f)
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.marginSmall))

        Text(
            style = MaterialTheme.montserratFamily.bodyMedium,
            text = "3 Devices to create a vault; ",
            color = textColor
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            style = MaterialTheme.montserratFamily.bodyMedium,
            text = "2 devices to sign a transaction.",
            color = textColor
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        Text(
            style = MaterialTheme.montserratFamily.bodyMedium,
            text = "Automatically backed-up",
            color = textColor
        )

        Spacer(modifier = Modifier.weight(0.5f))

        Image(painter = painterResource(id = drawable.wifi), contentDescription = null)
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.marginSmall))
        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.large),
            text = "Keep devices on the same WiFi Network with vultisig open.",
            color = textColor,
            style = MaterialTheme.menloFamily.titleLarge.copy(
                textAlign = TextAlign.Center
            ),
        )
        Spacer(modifier = Modifier.weight(1.0f))
        MultiColorButton(
            text = stringResource(id = R.string.start),
            backgroundColor = MaterialTheme.appColor.turquoise600Main,
            textColor = MaterialTheme.appColor.oxfordBlue600Main,
            minHeight = MaterialTheme.dimens.minHeightButton,
            textStyle = MaterialTheme.montserratFamily.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.dimens.buttonMargin,
                    end = MaterialTheme.dimens.buttonMargin
                )
        ) {
            navController.navigate(Screen.KeygenFlow.route)
        }

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.marginSmall))

        MultiColorButton(
            text = stringResource(id = R.string.join),
            backgroundColor = MaterialTheme.appColor.oxfordBlue600Main,
            textColor = MaterialTheme.appColor.turquoise600Main,
            iconColor = MaterialTheme.appColor.oxfordBlue600Main,
            borderSize = 1.dp,
            minHeight = MaterialTheme.dimens.minHeightButton,
            textStyle = MaterialTheme.montserratFamily.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.dimens.buttonMargin,
                    end = MaterialTheme.dimens.buttonMargin
                )
        ) {
//            navController.navigate(Screen.Pair.route)
        }
        Spacer(
            modifier = Modifier
                .height(MaterialTheme.dimens.marginMedium)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SetupPreview() {
    val navController = rememberNavController()
    Setup(navController)

}