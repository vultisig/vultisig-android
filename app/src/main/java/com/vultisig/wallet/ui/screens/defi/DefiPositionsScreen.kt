package com.vultisig.wallet.ui.screens.defi

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.defi.DefiPositionsViewModel
import com.vultisig.wallet.ui.screens.v2.home.components.BondedTabs
import com.vultisig.wallet.ui.screens.v2.home.components.NotEnabledContainer
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun DefiPositionsScreen(
    navController: NavHostController,
    viewModel: DefiPositionsViewModel = hiltViewModel<DefiPositionsViewModel>(),
) {
    DefiPositionScreenContent(
        onBackClick = navController::popBackStack,
    )
}

@Composable
internal fun DefiPositionScreenContent(onBackClick: () -> Unit) {
    val tabs = listOf("Bonded", "Staked", "LPs")
    var selectedTab by remember { mutableStateOf(tabs.first()) }

    V2Scaffold(
        onBackClick = onBackClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Theme.colors.backgrounds.primary),
            horizontalAlignment = CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReferralRewardsBanner(isLoading = false)

            BondedTabs(
                tabs = listOf("Bonded", "Staked", "LPs"),
                onTabSelected = { selectedTab = it },
                selectedTab = selectedTab,
                content = {
                    V2Container(
                        type = ContainerType.SECONDARY,
                        cornerType = CornerType.Circular,
                        modifier = Modifier
                            .clickOnce(onClick = {} )
                    ) {
                        UiIcon(
                            drawableResId = R.drawable.edit_chain,
                            size = 16.dp,
                            modifier = Modifier.padding(all = 12.dp),
                            tint = Theme.colors.primary.accent4,
                        )
                    }
                }
            )

            NotEnabledContainer(
                title = "No positions selected",
                content = "You’ve disabled all positions for this chain. Enable at least one position to view balances and manage actions.",
                action = {

                }
            )
        }
    }
}

@Composable
private fun ReferralRewardsBanner(
    isLoading: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.referral_data_banner),
            contentDescription = "Provider Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp)
        ) {
            Text(
                text = Chain.ThorChain.name,
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.l.medium,
            )

            UiSpacer(16.dp)

            Text(
                text = "Balance",
                color = Theme.colors.text.primary,
                style = Theme.brockmann.supplementary.caption,
            )

            UiSpacer(12.dp)

            Text(
                text = "\$3,010.77",
                color = Theme.colors.text.primary,
                style = Theme.satoshi.price.title1,
            )
        }
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
private fun DefiPositionsScreenPreview() {
    DefiPositionScreenContent(
        onBackClick = {}
    )
}