package com.vultisig.wallet.presenter.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.reorderable.VerticalReorderList
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val vaults by viewModel.vaults.collectAsState()
    val textColor = MaterialTheme.colorScheme.onBackground

    val appColor = Theme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColor.oxfordBlue800)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.home_screen_title),
                            style = Theme.montserrat.subtitle1,
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
                        containerColor = appColor.oxfordBlue800,
                        titleContentColor = textColor
                    ),
                    navigationIcon = {
                        IconButton(onClick = viewModel::navigateToSettingsScreen) {
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
                    text = stringResource(R.string.home_screen_add_new_vault),
                    minHeight = MaterialTheme.dimens.minHeightButton,
                    backgroundColor = appColor.turquoise800,
                    textColor = appColor.oxfordBlue800,
                    iconColor = appColor.turquoise800,
                    textStyle = Theme.montserrat.subtitle1,
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
            VerticalReorderList(
                modifier = Modifier.padding(it),
                onMove = viewModel::onMove,
                data = vaults,
            ) { vault ->
                VaultCeil(navController, vault = vault)
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