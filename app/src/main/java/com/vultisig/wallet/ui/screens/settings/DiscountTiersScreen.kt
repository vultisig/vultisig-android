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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

            UiSpacer(size = 32.dp)
        }
    }
}

@Composable
private fun TierCard(
    tierName: String,
    requirement: String,
    discount: String,
    isActive: Boolean
) {
    val colors = Theme.colors
    
    Card(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) colors.turquoise600Main.copy(alpha = 0.1f) else colors.oxfordBlue600Main
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tierName,
                        style = Theme.montserrat.body1.copy(fontWeight = FontWeight.Bold),
                        color = if (isActive) colors.turquoise600Main else colors.neutral0
                    )
                    if (isActive) {
                        UiSpacer(size = 8.dp)
                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = colors.turquoise600Main
                            )
                        ) {
                            Text(
                                text = "ACTIVE",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = Theme.montserrat.caption.copy(fontWeight = FontWeight.Bold),
                                color = colors.oxfordBlue800
                            )
                        }
                    }
                }
                UiSpacer(size = 4.dp)
                Text(
                    text = requirement,
                    style = Theme.montserrat.body3,
                    color = colors.neutral200
                )
            }
            
            Text(
                text = discount,
                style = Theme.montserrat.body1.copy(fontWeight = FontWeight.Bold),
                color = colors.turquoise600Main
            )
        }
    }
}