package com.vultisig.wallet.ui.screens.keygen

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.theme.Theme

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
    val isFirstEntry = navController.previousBackStackEntry == null
    Scaffold(
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800),
        topBar = {
            TopBar(
                navController = navController,
                centerText = "",
                startIcon = R.drawable.ic_caret_left.takeIf { !isFirstEntry },
                endIcon = R.drawable.question,
                onEndIconClick = onOpenHelp,
            )
        },
        bottomBar = {
            Column(
                horizontalAlignment = CenterHorizontally
            ) {
                MultiColorButton(
                    text = stringResource(R.string.create_new_vault_screen_create_a_new_vault),
                    backgroundColor = Theme.colors.turquoise800,
                    textColor = Theme.colors.oxfordBlue800,
                    iconColor = Theme.colors.turquoise800,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 12.dp,
                        ),
                    onClick = { navController.navigate(route = Destination.SelectVaultType.route) }
                )
                MultiColorButton(
                    text = stringResource(R.string.home_screen_import_vault),
                    backgroundColor = Theme.colors.oxfordBlue800,
                    textColor = Theme.colors.turquoise800,
                    iconColor = Theme.colors.oxfordBlue800,
                    borderSize = 1.dp,
                    textStyle = Theme.montserrat.subtitle1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                    onClick = { navController.navigate(Destination.ImportVault.route) }
                )
            }
        }
    ) { padding ->
        val configuration = LocalConfiguration.current
        when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE ->
                Row(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = CenterVertically
                ) {
                    MainContent()
                }

            else ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    MainContent()
                }
        }
    }
}

@Composable
private fun MainContent() {
    Image(
        painter = painterResource(id = R.drawable.vultisig),
        contentDescription = "vultisig"
    )
    Column {
        Text(
            text = stringResource(R.string.create_new_vault_screen_vultisig),
            color = Theme.colors.neutral100,
            style = Theme.montserrat.heading3.copy(fontSize = 50.sp)
        )
        UiSpacer(size = 40.dp)
        Text(
            text = stringResource(R.string.create_new_vault_screen_secure_crypto_vault),
            color = Theme.colors.neutral100,
            style = Theme.montserrat.body3.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            ),
        )
    }
}

@Preview
@Composable
fun CreateNewVaultPreview() {
    val navController = rememberNavController()
    AddVaultScreen(navController)
}