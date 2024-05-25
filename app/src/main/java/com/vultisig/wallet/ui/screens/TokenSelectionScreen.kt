package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.ui.components.TokenSelectionItem
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.models.TokenSelectionViewModel
import com.vultisig.wallet.ui.theme.appColor

@Composable
internal fun TokenSelectionScreen(
    navController: NavHostController,
    viewModel: TokenSelectionViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsState().value

    Column(
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .fillMaxSize(),
    ) {
        TopBar(
            centerText = stringResource(R.string.tokens),
            startIcon = R.drawable.caret_left,
            navController = navController
        )

        LazyColumn(
            contentPadding = PaddingValues(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.tokens) { token ->
                val coin = token.coin
                TokenSelectionItem(
                    title = coin.ticker,
                    subtitle = Coins.capitalizeTokenSubtitle(coin.priceProviderID),
                    logo = Coins.getCoinLogo(logoName = coin.logo),
                    isChecked = token.isEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            viewModel.enableToken(coin)
                        } else {
                            viewModel.disableToken(coin)
                        }
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun TokenSelectionPreview() {
    TokenSelectionScreen(
        navController = rememberNavController()
    )
}
