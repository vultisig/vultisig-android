package com.vultisig.wallet.presenter.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.activity.MainActivity
import com.vultisig.wallet.app.ui.theme.appColor
import com.vultisig.wallet.app.ui.theme.dimens
import com.vultisig.wallet.app.ui.theme.montserratFamily
import com.vultisig.wallet.chains.thorchainHelper
import com.vultisig.wallet.presenter.keysign.BlockChainSpecific
import com.vultisig.wallet.presenter.keysign.KeysignPayload
import com.vultisig.wallet.presenter.keysign.KeysignShareViewModel
import com.vultisig.wallet.presenter.navigation.Screen
import com.vultisig.wallet.ui.components.UiPlusButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.ChainAccountUiModel
import com.vultisig.wallet.ui.models.VaultDetailViewModel
import java.math.BigInteger

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
internal fun VaultDetailScreen(
    vaultId: String,
    navHostController: NavHostController,
    viewModel: VaultDetailViewModel = hiltViewModel(),
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val context = LocalContext.current
    val keysignShareViewModel: KeysignShareViewModel =
        viewModel(context as MainActivity)

    val state = viewModel.uiState.collectAsState().value

    LaunchedEffect(key1 = viewModel) {
        viewModel.loadData(vaultId)
    }
    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = state.vaultName,
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
                    val vault = viewModel.currentVault.value
                    val coin = thorchainHelper(vault.pubKeyECDSA, vault.hexChainCode).getCoin()
                    coin?.let {
                        keysignShareViewModel.vault = viewModel.currentVault.value
                        keysignShareViewModel.keysignPayload = KeysignPayload(
                            coin = it,
                            toAddress = "thor1f04877jfmm2sxmxyqkj3m9xtak8he0gg7ypuzz",
                            toAmount = BigInteger("10000000"),
                            blockChainSpecific = BlockChainSpecific.THORChain(
                                BigInteger("1024"),
                                BigInteger("0")
                            ),
                            memo = null,
                            swapPayload = null,
                            approvePayload = null,
                            vaultPublicKeyECDSA = viewModel.currentVault.value.pubKeyECDSA
                        )
                        navHostController.navigate(Screen.KeysignFlow.route)
                    }

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
        LazyColumn(
            modifier = Modifier.padding(it),
            contentPadding = PaddingValues(
                all = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.accounts) { account ->
                ChainCeil(
                    account = account
                )
            }
            item {
                UiSpacer(
                    size = 16.dp,
                )
                UiPlusButton(
                    title = stringResource(R.string.vault_choose_chains),
                    onClick = {
                        navHostController.navigate(
                            Screen.VaultDetail.AddChainAccount.createRoute(vaultId)
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun ChainCeil(
    account: ChainAccountUiModel,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Row(Modifier.background(MaterialTheme.appColor.oxfordBlue400)) {
            Image(
                painter = painterResource(id = account.logo),
                contentDescription = null,
                modifier = Modifier
                    .padding(10.dp)
                    .width(32.dp)
                    .height(32.dp)
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = account.chainName,
                        style = MaterialTheme.montserratFamily.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(6.dp)
                            .weight(1f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = account.nativeTokenAmount ?: "",
                        style = MaterialTheme.montserratFamily.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.CenterVertically)
                    )
                    Text(
                        text = account.fiatAmount ?: "",
                        style = MaterialTheme.montserratFamily.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.CenterVertically)
                    )
                }
                Text(
                    text = account.address,
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
    ChainCeil(
        ChainAccountUiModel(
            chainName = "Bitcoin",
            logo = R.drawable.bitcoin,
            address = "123abc456bca123abc456bca123abc456bca",
            nativeTokenAmount = "0.01",
            fiatAmount = "1000$",
        )
    )
}