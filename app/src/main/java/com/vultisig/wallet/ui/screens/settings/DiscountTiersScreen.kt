package com.vultisig.wallet.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun DiscountTiersScreen(navController: NavHostController) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.backgrounds.secondary),
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(R.string.vault_settings_discounts),
                startIcon = R.drawable.ic_caret_left
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = Theme.colors.borders.light,
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.tiers_header),
                    contentDescription = "Provider Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            UiSpacer(size = 24.dp)
            
            Text(
                text = "Hold VULT to unlock lower trading fees.",
                style = Theme.brockmann.body.s.regular,
                textAlign = TextAlign.Start,
            )

            UiSpacer(size = 16.dp)

            TierCard(TierType.BRONZE)
        }
    }
}

@Composable
private fun TierCard(
    tierType: TierType,
) {
    val borderGradient = Brush.verticalGradient(
        colors = listOf(
            Theme.colors.error.copy(alpha = 0.6f),    // bright orange at the top
            Color.Transparent     // fade to transparent bottom
        ),
        startY = 0f,
        endY = 400f // controls where it fades out; adjust based on card height
    )

    Box(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = borderGradient,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.colors.backgrounds.neutral)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.tier_bronze),
                        contentDescription = null,
                        modifier = Modifier
                            .size(50.dp)
                            .padding(8.dp)
                    )

                    Text(
                        text = "Bronze",
                        style = Theme.brockmann.headings.title1,
                        color = Theme.colors.text.primary,
                    )
                }

                // Discount badge example
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Theme.colors.backgrounds.secondary)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Discount: 10bps",
                        style = Theme.brockmann.body.s.regular,
                    )
                }
            }

            UiSpacer(size = 8.dp)

            Text(
                text = "Stake 1,000 \$VULT (~\$1,000)",
                style = Theme.brockmann.body.m.regular,
                color = Theme.colors.text.primary
            )

            UiSpacer(size = 16.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Theme.colors.backgrounds.secondary)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Unlock Tier",
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.colors.text.primary
                )
            }
        }
    }
}


internal enum class TierType { BRONZE, SILVER, GOLD, PLATINIUM }