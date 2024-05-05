package com.vultisig.wallet.presenter.home

import com.vultisig.wallet.presenter.base_components.MultiColorButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.app.ui.theme.appColor
import com.vultisig.wallet.app.ui.theme.dimens
import com.vultisig.wallet.app.ui.theme.montserratFamily
import com.vultisig.wallet.presenter.navigation.Screen

@Composable
fun HomeScreen(navController: NavHostController) {
    val viewModel: HomeViewModel = viewModel()
    val vaults = viewModel.vaults.asFlow().collectAsState(initial = emptyList()).value
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onBackground

    LaunchedEffect(key1 = viewModel) {
        viewModel.setData(context)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColor.oxfordBlue800)
    ) {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(MaterialTheme.appColor.oxfordBlue800),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.padding(MaterialTheme.dimens.marginMedium))
                    Text(
                        text = "Vaults",
                        style = MaterialTheme.montserratFamily.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .padding(
                                start = MaterialTheme.dimens.marginMedium,
                                end = MaterialTheme.dimens.marginMedium,
                            )
                            .wrapContentHeight(align = Alignment.CenterVertically)
                    )
                }
            },
            bottomBar = {
                MultiColorButton(
                    text = "+ Add New Vault",
                    minHeight = MaterialTheme.dimens.minHeightButton,
                    backgroundColor = MaterialTheme.appColor.turquoise800,
                    textColor = MaterialTheme.appColor.oxfordBlue800,
                    iconColor = MaterialTheme.appColor.turquoise800,
                    textStyle = MaterialTheme.montserratFamily.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = MaterialTheme.dimens.marginMedium,
                            end = MaterialTheme.dimens.marginMedium,
                            bottom = MaterialTheme.dimens.marginMedium,
                        )
                ) {
                    navController.navigate(route = Screen.CreateNewVault.route)
                }
            }
        ) {
            LazyColumn(modifier= Modifier.padding(it)) {
                items(vaults) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColor.oxfordBlue400),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = MaterialTheme.dimens.marginMedium,
                                end = MaterialTheme.dimens.marginMedium,
                                top = MaterialTheme.dimens.marginMedium,
                            )
                            .height(60.dp)

                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = it.name.uppercase(),
                                style = MaterialTheme.montserratFamily.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .padding(
                                        start = MaterialTheme.dimens.marginMedium,
                                        end = MaterialTheme.dimens.marginMedium,
                                        top = MaterialTheme.dimens.marginMedium,
                                        bottom = MaterialTheme.dimens.marginMedium,
                                    )
                                    .wrapContentHeight(align = Alignment.CenterVertically)

                            )
                            Image(
                                painter = painterResource(R.drawable.caret_right),
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = MaterialTheme.dimens.marginMedium)

                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "HomeScreen")
@Composable
fun HomeScreenPreview() {
    val navController = rememberNavController()
    HomeScreen(navController = navController)
}