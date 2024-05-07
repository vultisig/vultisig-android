package com.vultisig.wallet.presenter.signing_error

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.ui.theme.appColor
import com.vultisig.wallet.app.ui.theme.dimens
import com.vultisig.wallet.app.ui.theme.menloFamily
import com.vultisig.wallet.presenter.base_components.MultiColorButton
//import com.vultisig.wallet.presenter.common.TopBar

@Composable
fun SigningError(navController: NavHostController) {
    val textColor = MaterialTheme.appColor.neutral0
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(MaterialTheme.dimens.marginMedium)
    ) {
//        TopBar(centerText = "Keygen", navController = navController)

        Spacer(modifier = Modifier.weight(1.0f))
        Image(
            painterResource(id = R.drawable.danger),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))
        Text(
            text = "Signing Error. Please try again.", color = textColor,
            style = MaterialTheme.menloFamily.bodyMedium.copy(
                textAlign = TextAlign.Center, lineHeight = 25.sp
            ),
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.large),

            )

        Spacer(modifier = Modifier.weight(1.0f))

        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.small1),
            text = "Keep devices on the same WiFi Network, correct vault and pair devices. \n" + "Make sure no other devices are \n" + "running vultisig.",
            color = textColor,
            style = MaterialTheme.menloFamily.titleSmall.copy(
                textAlign = TextAlign.Center
            ),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        MultiColorButton(
            text = stringResource(id = R.string.try_again),
            backgroundColor = MaterialTheme.appColor.turquoise600Main,
            textColor = MaterialTheme.appColor.oxfordBlue600Main,
            minHeight = MaterialTheme.dimens.minHeightButton,
            modifier = Modifier.fillMaxWidth()
        ) {

        }
        Spacer(
            modifier = Modifier
                .height(MaterialTheme.dimens.marginMedium)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SigningErrorPreview() {
    val navController = rememberNavController()
    SigningError(navController)

}