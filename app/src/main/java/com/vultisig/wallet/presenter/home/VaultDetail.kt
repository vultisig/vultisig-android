package com.vultisig.wallet.presenter.home

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.asFlow
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.ui.theme.appColor
import com.vultisig.wallet.app.ui.theme.dimens
import com.vultisig.wallet.app.ui.theme.montserratFamily
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.models.getBalance
import com.vultisig.wallet.models.logo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDetail(navHostController: NavHostController, vault: Vault) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val context = LocalContext.current
    val viewModel: VaultDetailViewModel = hiltViewModel()
    val coins: List<Coin> = viewModel.coins.asFlow().collectAsState(initial = emptyList()).value

    LaunchedEffect(key1 = Unit) {
        viewModel.setData(vault)
    }
    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = vault.name,
                    style = MaterialTheme.montserratFamily.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier
                        .padding(
                            start = MaterialTheme.dimens.marginMedium,
                            end = MaterialTheme.dimens.marginMedium,
                        )
                        .wrapContentHeight(align = Alignment.CenterVertically)
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.appColor.oxfordBlue800,
                titleContentColor = textColor
            ),
            navigationIcon = {
                IconButton(onClick = {
                    navHostController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "settings", tint = Color.White
                    )
                }
            },
            actions = {
                IconButton(onClick = {
                    Toast.makeText(context, "edit vault", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_edit_square_24),
                        contentDescription = "search",
                        tint = Color.White
                    )
                }
            }
        )
    }, bottomBar = {}) {
        LazyColumn(modifier = Modifier.padding(it)) {
            items(coins) { coin ->
                ChainCeil(navHostController, coin = coin)
            }
        }
    }
}

@Composable
fun ChainCeil(navHostController: NavHostController, coin: Coin) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
            .background(MaterialTheme.appColor.oxfordBlue400)
    ) {
        Row(Modifier.background(MaterialTheme.appColor.oxfordBlue400)) {
            Image(
                painter = painterResource(id = coin.chain.logo),
                contentDescription = null,
                modifier = Modifier
                    .padding(10.dp)
                    .width(32.dp)
                    .height(32.dp)
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = coin.chain.raw,
                        style = MaterialTheme.montserratFamily.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(6.dp)
                            .weight(1f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = coin.getBalance().toString(),
                        style = MaterialTheme.montserratFamily.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.CenterVertically)
                    )
                }
                Text(
                    text = coin.address,
                    style = MaterialTheme.montserratFamily.titleSmall,
                    color = MaterialTheme.appColor.turquoise800,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun PreviewChainCeil() {
    val navHostController = rememberNavController()
    ChainCeil(navHostController, Coins.SupportedCoins[0])
}