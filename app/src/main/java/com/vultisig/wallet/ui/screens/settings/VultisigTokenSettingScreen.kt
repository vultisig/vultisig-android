package com.vultisig.wallet.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks
import com.vultisig.wallet.ui.utils.VsUriHandler

@Composable
fun VultisigTokenScreen(navController: NavHostController) {
    val colors = Theme.colors
    val uriHandler = VsUriHandler()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.oxfordBlue800),
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(R.string.vultisig_token_setting_screen_title),
                startIcon = R.drawable.ic_caret_left
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "\$1", style = Theme.montserrat.heading5, color = colors.neutral0)
            UiSpacer(size = 32.dp)
            Image(painter = painterResource(id = R.drawable.vultisig), contentDescription = "logo")
            UiSpacer(size = 32.dp)
            val supply = "100,000,000"
            FeatureItem(stringResource(R.string.token_settings_supply, supply))
            FeatureItem(stringResource(R.string.feature_item_vtx_tokens_are_burnt_from_fees),R.drawable.ic_small_caret_right)
            FeatureItem(text = stringResource(R.string.feature_item_vtx_tokens_are_airdropped_to_users), icon = R.drawable.ic_small_caret_right )
            FeatureItem(
                text = stringResource(R.string.feature_item_learn_more),
                icon = R.drawable.share
            ) {
                uriHandler.openUri(VsAuxiliaryLinks.VULT_TOKEN)
            }
        }

    }
}

@Composable
fun FeatureItem(text: String, @DrawableRes icon: Int? = null, onClick: () -> Unit = {}) {
    val colors = Theme.colors
    Card(
        modifier = Modifier
            .padding(
                horizontal = 12.dp,
                vertical = 8.dp
            )
            .clickable(onClick = onClick)
            .fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.oxfordBlue600Main
        )
    ) {
        Row(
            modifier = Modifier.padding(all = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                text = text,
                color = colors.neutral0,
                style = Theme.montserrat.body2,
                lineHeight = 25.sp,
                modifier = Modifier.weight(0.8f)
            )

            if (icon != null)
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    tint = colors.neutral0,
                )
        }
    }
}
