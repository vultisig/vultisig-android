package com.vultisig.wallet.presenter.settings.default_chains_setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.models.logo
import com.vultisig.wallet.presenter.settings.default_chains_setting.DefaultChainsSettingEvent.*
import com.vultisig.wallet.ui.components.TokenSelectionItem
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun DefaultChainSetting(navController: NavHostController) {

    val colors = Theme.colors
    val viewModel = hiltViewModel<DefaultChainsSettingViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(key1 = Unit) {
        viewModel.onEvent(Initialize)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.oxfordBlue800),
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(R.string.default_chain_screen_title),
                startIcon = R.drawable.caret_left
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .padding(it)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            items(state.chains) { chain ->
                val token = chain.coin
                TokenSelectionItem(
                    title = token.ticker,
                    subtitle = token.chain.raw,
                    logo = token.chain.logo,
                    isChecked = chain.isEnabled,
                    onCheckedChange = { checked ->
                        viewModel.onEvent(UpdateItem(chain,checked))
                    }
                )
            }
        }
    }
}