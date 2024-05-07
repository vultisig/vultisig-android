package com.vultisig.wallet.presenter.keygen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.ui.theme.appColor
import com.vultisig.wallet.app.ui.theme.dimens
import com.vultisig.wallet.app.ui.theme.montserratFamily
import com.vultisig.wallet.presenter.base_components.MultiColorButton
//import com.vultisig.wallet.presenter.common.TopBar
import com.vultisig.wallet.presenter.navigation.Screen

@Composable
fun CreateNewVault(navController: NavHostController) {
    var isNewVaultVisible by remember {
        mutableStateOf(true)
    }


    val centerText = if(isNewVaultVisible) "Voltix" else "Setup"
    val startIcon = if(isNewVaultVisible) null else R.drawable.caret_left
    val endIcon = if(isNewVaultVisible) null else R.drawable.question

    val animationSpec = tween<IntOffset>(300)
    BackHandler {
        isNewVaultVisible = true
    }


    Column(
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(MaterialTheme.dimens.marginMedium)
    ) {

       /* AnimatedTopBar(
            navController = navController,
            centerText = centerText,
            startIcon = startIcon,
            endIcon = endIcon,
            onCenterTextClick = {
                navController.navigate(Screen.VaultListAndDetailsList.route)
            },
            onBackClick = {
                isNewVaultVisible = true
            }
        )*/

        Box {

            NewVault(navController = navController) {
                isNewVaultVisible = false
            }


            this@Column.AnimatedVisibility(
                visible = isNewVaultVisible.not(),
                enter = slideInVertically(
                    animationSpec = animationSpec,
                )
            ) {
                Setup(navController = navController)
            }
        }
    }


}

@Composable
fun NewVault(navController: NavHostController, onNavigate: () -> Unit) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColor.oxfordBlue800)
            .padding(MaterialTheme.dimens.marginMedium)
    ) {

        Column(
            modifier = Modifier
                .align(Center)
                .fillMaxHeight(),
            horizontalAlignment = CenterHorizontally
        ) {
            /*TopBar(
                navController = navController,
                centerText = "",
                startIcon = R.drawable.caret_left,
                endIcon = R.drawable.question
            )*/
            Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
            Image(
                painter = painterResource(id = R.drawable.vultisig), contentDescription = "vultisig"
            )
            Text(
                text = "vultisig",
                color = textColor,
                style = MaterialTheme.montserratFamily.headlineLarge.copy(fontSize = 50.sp)
            )
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "SECURE CRYPTO VAULT", color = textColor,
                style = MaterialTheme.montserratFamily.titleLarge,
            )
        }

        Column(
            modifier = Modifier
                .align(BottomCenter),
            horizontalAlignment = CenterHorizontally
        ) {

            MultiColorButton(
                text = stringResource(R.string.create_a_new_vault),
                minHeight = MaterialTheme.dimens.minHeightButton,
                backgroundColor = MaterialTheme.appColor.turquoise800,
                textColor = MaterialTheme.appColor.oxfordBlue800,
                iconColor = MaterialTheme.appColor.turquoise800,
                textStyle = MaterialTheme.montserratFamily.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.dimens.buttonMargin,
                        end = MaterialTheme.dimens.buttonMargin
                    )
            ) {
                onNavigate()
            }
            Spacer(modifier = Modifier.height(MaterialTheme.dimens.marginSmall))
            MultiColorButton(
                text = stringResource(R.string.import_an_existing_vault),
                backgroundColor = MaterialTheme.appColor.oxfordBlue800,
                textColor = MaterialTheme.appColor.turquoise800,
                iconColor = MaterialTheme.appColor.oxfordBlue800,
                borderSize = 1.dp,
                textStyle = MaterialTheme.montserratFamily.titleLarge,
                minHeight = MaterialTheme.dimens.minHeightButton,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.dimens.buttonMargin,
                        end = MaterialTheme.dimens.buttonMargin
                    )

            ) {
                navController.navigate(Screen.ImportFile.route)
            }


            Spacer(
                modifier = Modifier
                    .height(MaterialTheme.dimens.marginMedium)
            )
        }
    }
}
@Preview(showBackground = true)
@Composable
fun CreateNewVaultPreview() {
    val navController = rememberNavController()
    CreateNewVault(navController)
}