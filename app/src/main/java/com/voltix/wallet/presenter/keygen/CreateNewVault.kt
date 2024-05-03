package com.voltix.wallet.presenter.keygen

import MultiColorButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.voltix.wallet.R
import com.voltix.wallet.app.ui.theme.appColor
import com.voltix.wallet.app.ui.theme.dimens
import com.voltix.wallet.app.ui.theme.montserratFamily
import com.voltix.wallet.presenter.common.TopBar
import com.voltix.wallet.presenter.navigation.Screen

@Composable
fun CreateNewVault(navController: NavHostController) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColor.oxfordBlue800)
    ) {

        Column(
            modifier = Modifier.align(Center).fillMaxHeight(),
            horizontalAlignment = CenterHorizontally
        ) {
            TopBar(navController = navController,
                centerText = "",
                startIcon = R.drawable.caret_left,
                endIcon = R.drawable.question)
            Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
            Image(
                painter = painterResource(id = R.drawable.voltix), contentDescription = "voltix"
            )
            Text(
                text = "Voltix",
                color = textColor,
                style = MaterialTheme.montserratFamily.headlineLarge.copy(fontSize = 50.sp)
            )
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "SECURE CRYPTO VAULT", color = textColor,
                style = MaterialTheme.montserratFamily.bodySmall.copy(
                    letterSpacing = 2.sp, fontWeight = FontWeight.Bold
                ),
            )
        }
        Column(
            modifier = Modifier
                .align(BottomCenter),
            horizontalAlignment = CenterHorizontally
        ) {

            MultiColorButton(
                text = "Create a New Vault",
                minHeight = MaterialTheme.dimens.minHeightButton,
                backgroundColor = MaterialTheme.appColor.turquoise800,
                textColor = MaterialTheme.appColor.oxfordBlue800,
                iconColor = MaterialTheme.appColor.turquoise800,
                textStyle = MaterialTheme.montserratFamily.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.dimens.marginMedium,
                        end = MaterialTheme.dimens.marginMedium,
                        bottom = MaterialTheme.dimens.marginMedium,
                    )
            ) {
                navController.navigate(route = Screen.Setup.route)
            }
            Spacer(modifier = Modifier.size(MaterialTheme.dimens.extraSmall))
            MultiColorButton(
                text = "Import an Existing Vault",
                backgroundColor = MaterialTheme.appColor.oxfordBlue800,
                textColor = MaterialTheme.appColor.turquoise800,
                iconColor = MaterialTheme.appColor.oxfordBlue800,
                borderSize = 1.dp,
                textStyle = MaterialTheme.montserratFamily.titleLarge,
                minHeight = MaterialTheme.dimens.minHeightButton,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.dimens.marginMedium,
                        end = MaterialTheme.dimens.marginMedium,
                        bottom = MaterialTheme.dimens.buttonMargin,
                    )
            ) {
                navController.navigate(Screen.ImportFile.route)
            }


        }
    }
}

@Preview(showBackground = true)
@Composable
fun CreateNewVaultPreview() {
    val navController = rememberNavController()
    CreateNewVault(navController)
}