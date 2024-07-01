package com.vultisig.wallet.ui.screens.keygen

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiScrollableColumn
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@Composable
internal fun AddVaultScreen(
    navController: NavHostController,
) {
    val uriHandler = LocalUriHandler.current
    val link = stringResource(id = R.string.link_docs_create_vault)

    AddVaultScreen(
        navController = navController,
        onOpenHelp = {
            uriHandler.openUri(link)
        }
    )
}

@Composable
private fun AddVaultScreen(
    navController: NavHostController,
    onOpenHelp: () -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val isFirstEntry = navController.previousBackStackEntry == null
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.oxfordBlue800)
    ) {

        UiScrollableColumn(
            modifier = Modifier
                .align(Center)
                .fillMaxHeight(),
            horizontalAlignment = CenterHorizontally
        ) {
            TopBar(
                navController = navController,
                centerText = "",
                startIcon = R.drawable.caret_left.takeIf { !isFirstEntry },
                endIcon = R.drawable.question,
                onEndIconClick = onOpenHelp,
            )
            Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium2))
            Image(
                painter = painterResource(id = R.drawable.vultisig), contentDescription = "vultisig"
            )
            Text(
                text = stringResource(R.string.create_new_vault_screen_vultisig),
                color = textColor,
                style = Theme.montserrat.heading3.copy(fontSize = 50.sp)
            )
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = stringResource(R.string.create_new_vault_screen_secure_crypto_vault),
                color = textColor,
                style = Theme.montserrat.body3.copy(
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
                text = stringResource(R.string.create_new_vault_screen_create_a_new_vault),
                minHeight = 44.dp,
                backgroundColor = Theme.colors.turquoise800,
                textColor = Theme.colors.oxfordBlue800,
                iconColor = Theme.colors.turquoise800,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.dimens.marginMedium,
                        end = MaterialTheme.dimens.marginMedium,
                        bottom = MaterialTheme.dimens.marginMedium,
                    )
            ) {
                navController.navigate(route = Screen.Setup.createRoute(Destination.KeygenFlow.DEFAULT_NEW_VAULT))
            }
            Spacer(modifier = Modifier.size(MaterialTheme.dimens.extraSmall))
            MultiColorButton(
                text = stringResource(R.string.create_new_vault_screen_import_an_existing_vault),
                backgroundColor = Theme.colors.oxfordBlue800,
                textColor = Theme.colors.turquoise800,
                iconColor = Theme.colors.oxfordBlue800,
                borderSize = 1.dp,
                textStyle = Theme.montserrat.subtitle1,
                minHeight = 44.dp,
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
    AddVaultScreen(navController)
}