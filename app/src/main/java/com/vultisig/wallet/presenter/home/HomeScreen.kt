package com.vultisig.wallet.presenter.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.asFlow
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.theme.appColor
import com.vultisig.wallet.ui.theme.dimens
import com.vultisig.wallet.ui.theme.montserratFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val vaults = viewModel.vaults.asFlow().collectAsState(initial = emptyList()).value
    val textColor = MaterialTheme.colorScheme.onBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColor.oxfordBlue800)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Vaults",
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
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "settings", tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_edit_square_24),
                                contentDescription = "search",
                                tint = Color.White
                            )
                        }
                    }
                )
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
            LazyColumn(modifier = Modifier.padding(it)) {
                items(vaults) { vault ->
                    VaultCeil(navController, vault = vault)
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