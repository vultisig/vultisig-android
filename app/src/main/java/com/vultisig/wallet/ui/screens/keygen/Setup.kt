package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.R.drawable
import com.vultisig.wallet.common.asString
import com.vultisig.wallet.ui.components.DevicesOnSameNetworkHint
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiBarContainer
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.keygen.KeygenSetupViewModel
import com.vultisig.wallet.ui.models.keygen.VaultSetupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun Setup(
    navController: NavHostController,
    vaultId: String,
    viewModel: KeygenSetupViewModel = hiltViewModel(),
) {
    val uriHandler = LocalUriHandler.current
    val helpLink = stringResource(R.string.link_docs_create_vault)

    val textColor = Theme.colors.neutral0

    val state by viewModel.uiModel.collectAsState()

    UiBarContainer(
        navController = navController,
        title = stringResource(R.string.setup_title),
        endIcon = drawable.question,
        onEndIconClick = {
            uriHandler.openUri(helpLink)
        },
    ) {
        Column(
            horizontalAlignment = CenterHorizontally,
        ) {
            TabRow(
                selectedTabIndex = state.tabIndex,
                contentColor = Theme.colors.neutral0,
                containerColor = Theme.colors.oxfordBlue800,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        height = 1.dp,
                        color = Theme.colors.neutral0,
                        modifier = Modifier.tabIndicatorOffset(tabPositions[state.tabIndex]),
                    )
                },
                divider = { /* removed divider */ },
            ) {
                state.tabs.forEachIndexed { index, tab ->
                    Tab(
                        text = {
                            Text(
                                text = tab.title.asString(),
                                color = Theme.colors.neutral0,
                                style = Theme.montserrat.body2,
                            )
                        },
                        selected = state.tabIndex == index,
                        onClick = { viewModel.selectTab(index) }
                    )
                }
            }

            UiSpacer(size = 24.dp)

            Text(
                text = state.tabs[state.tabIndex].content.asString(),
                color = textColor,
                style = Theme.montserrat.body3,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )

            Image(
                painter = painterResource(id = state.tabs[state.tabIndex].drawableResId),
                contentDescription = "devices",
                contentScale = ContentScale.Fit,
                modifier = Modifier.weight(1f).
                fillMaxWidth()
            )

            DevicesOnSameNetworkHint(
                title = stringResource(R.string.setup_keep_devices_on_the_same_wifi_network_with_vultisig_open)
            )

            UiSpacer(size = 24.dp)

            MultiColorButton(
                text = stringResource(R.string.setup_start),
                backgroundColor = Theme.colors.turquoise600Main,
                textColor = Theme.colors.oxfordBlue600Main,
                minHeight = 44.dp,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 12.dp,
                    )
            ) {
                if (vaultId == Screen.KeygenFlow.DEFAULT_NEW_VAULT || vaultId.isEmpty()) {
                    navController.navigate(Destination.NamingVault(VaultSetupType.fromInt(state.tabIndex)).route)
                } else {
                    // when reshare , we need to set to M_OF_N
                    navController.navigate(
                        Screen.KeygenFlow.createRoute(
                            vaultId,
                            VaultSetupType.M_OF_N
                        )
                    )
                }
            }

            MultiColorButton(
                text = stringResource(R.string.setup_pair),
                backgroundColor = Theme.colors.oxfordBlue600Main,
                textColor = Theme.colors.turquoise600Main,
                iconColor = Theme.colors.oxfordBlue600Main,
                borderSize = 1.dp,
                minHeight = 44.dp,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 12.dp,
                    )
            ) {
                navController.navigate(Screen.JoinKeygen.route)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupPreview() {
    val navController = rememberNavController()
    Setup(navController, "vaultId")
}