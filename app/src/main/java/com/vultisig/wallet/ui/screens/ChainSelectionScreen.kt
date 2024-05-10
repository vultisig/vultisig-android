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
import com.vultisig.wallet.models.logo
import com.vultisig.wallet.ui.components.TokenSelectionItem
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.models.ChainSelectionViewModel
import com.vultisig.wallet.ui.theme.appColor

@Composable
internal fun ChainSelectionScreen(
    navController: NavHostController,
    viewModel: ChainSelectionViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsState().value

    Column(
        modifier = Modifier
            .background(MaterialTheme.appColor.oxfordBlue800)
            .fillMaxSize(),
    ) {
        TopBar(
            centerText = stringResource(R.string.chains), startIcon = R.drawable.caret_left,
            navController = navController
        )

        LazyColumn(
            contentPadding = PaddingValues(all = 16.dp),
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
                        if (checked) {
                            viewModel.enableAccount(token)
                        } else {
                            viewModel.disableAccount(token)
                        }
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun ChainSelectionViewPreview() {
    ChainSelectionScreen(
        navController = rememberNavController()
    )
}
