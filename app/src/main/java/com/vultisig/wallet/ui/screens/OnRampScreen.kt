package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.scaffold.VsScaffold
import com.vultisig.wallet.ui.models.OnRampViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsUriHandler

@Composable
fun OnRampScreen(
    navController: NavController,
    viewModel: OnRampViewModel = hiltViewModel(),
) {
    val uriHandler = VsUriHandler()
    val banxaUrl by viewModel.banxaUrl.collectAsState()
    
    LaunchedEffect(banxaUrl) {
        banxaUrl?.let { url ->
            viewModel.onUrlOpened()
            uriHandler.openUri(url)
            navController.popBackStack()
        }
    }
    
    VsScaffold(
        title = stringResource(R.string.transaction_buy),
        onBackClick = { navController.popBackStack() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Theme.colors.backgrounds.primary)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.banxa_logo),
                contentDescription = "Banxa Logo",
                modifier = Modifier.size(120.dp)
            )

            UiSpacer(size = 16.dp)

            Text(
                text = stringResource(R.string.banxa_buy_or_transfer),
                color = Theme.colors.text.primary,
                style = Theme.brockmann.headings.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            UiSpacer(size = 16.dp)

            VsButton(
                label = stringResource(R.string.banxa_continue),
                onClick = { viewModel.openBanxaWebsite() },
                modifier = Modifier.fillMaxWidth(),
                state = VsButtonState.Enabled
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BanxaScreenPreview() {
    OnRampScreen(
        navController = rememberNavController()
    )
}