package com.voltix.wallet.presenter.create_new_vault

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.voltix.wallet.R
import com.voltix.wallet.app.ui.theme.appColor
import com.voltix.wallet.app.ui.theme.montserratFamily
import com.voltix.wallet.presenter.navigation.Screen

@Composable
fun CreateNewVault(navController: NavHostController) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColor.oxfordBlue800)
    ) {
        Image(
            painter = painterResource(id = R.drawable.question),
            contentDescription = "question",
            modifier = Modifier
                .align(TopEnd)
                .padding(19.dp)
        )
        Column(
            modifier = Modifier.align(Center), horizontalAlignment = CenterHorizontally
        ) {
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
                .align(BottomCenter)
                .padding(20.dp),
            horizontalAlignment = CenterHorizontally
        ) {
            Button(onClick = {
                navController.navigate(route = Screen.Setup.route)
            }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Create a New Vault")
            }
            Button(onClick = {
                navController.navigate(Screen.ImportFile.route)
            }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Import an Existing Vault")
            }

        }
    }
}
