package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.vultiGradient
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun AddVaultScreen(
    navController: NavHostController,
) {
    Scaffold(
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800),
    ) { padding ->
        AddVaultScreen(
            modifier = Modifier.padding(padding),
            onCreateNewVaultClick = {
                navController.navigate(Destination.SelectVaultType.route)
            },
            onScanQrCodeClick = {
                navController.navigate(Destination.JoinThroughQr(null).route)
            },
            onImportVaultClick = {
                navController.navigate(Destination.ImportVault.route)
            }
        )
    }
}

@Composable
private fun AddVaultScreen(
    modifier: Modifier,
    onCreateNewVaultClick: () -> Unit,
    onScanQrCodeClick: () -> Unit,
    onImportVaultClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize(),
        horizontalAlignment = CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.vultisig),
                contentDescription = "vultisig",
                modifier = Modifier
                    .width(190.dp)
                    .height(160.dp)
            )
            UiSpacer(16.dp)
            Text(
                text = stringResource(R.string.create_new_vault_screen_vultisig),
                color = Theme.colors.neutral100,
                style = Theme.montserrat.heading3.copy(fontSize = 50.sp)
            )
            UiSpacer(16.dp)
            Text(
                text = stringResource(R.string.create_new_vault_screen_secure_crypto_vault),
                style = Theme.montserrat.subtitle1.copy(
                    brush = Brush.vultiGradient()
                ),
            )
        }
        Column(
            horizontalAlignment = CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(
                vertical = 24.dp,
            )
        ) {
            MultiColorButton(
                text = stringResource(R.string.create_new_vault_screen_create_new_vault),
                minHeight = 44.dp,
                backgroundColor = Theme.colors.turquoise800,
                textColor = Theme.colors.oxfordBlue800,
                iconColor = Theme.colors.turquoise800,
                textStyle = Theme.montserrat.subtitle1,
                onClick = onCreateNewVaultClick,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
            )

            Text(
                modifier = Modifier.padding(4.dp),
                text = stringResource(R.string.create_new_vault_screen_or),
                color = Theme.colors.neutral100,
                style = Theme.menlo.subtitle1,
            )

            MultiColorButton(
                text = stringResource(R.string.home_screen_scan_qr_code),
                backgroundColor = Theme.colors.oxfordBlue600Main,
                textColor = Color.White,
                iconColor = Theme.colors.oxfordBlue800,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                    ),
                onClick = onScanQrCodeClick
            )

            MultiColorButton(
                text = stringResource(R.string.home_screen_import_vault),
                backgroundColor = Theme.colors.oxfordBlue600Main,
                textColor = Color.White,
                iconColor = Theme.colors.oxfordBlue800,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
                onClick = onImportVaultClick
            )
        }
    }
}


@Preview
@Composable
fun AddVaultScreenPreview() {
    OnBoardingComposeTheme {
        AddVaultScreen(
            modifier = Modifier,
            onCreateNewVaultClick = {},
            onScanQrCodeClick = {},
            onImportVaultClick = {})
    }
}