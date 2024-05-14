package com.vultisig.wallet.presenter.settings.settings_main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun SettingsScreen(navController: NavHostController) {
    val colors = Theme.colors
    val viewModel = hiltViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(key1 = Unit) {
        viewModel.loadSettings()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.oxfordBlue800),
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(R.string.settings_screen_title),
                startIcon = R.drawable.caret_left
            )
        }
    ) {
        Column(modifier = Modifier.padding(it),
            horizontalAlignment = Alignment.CenterHorizontally
            ) {
            AppSettingItem(
                R.drawable.settings_globe,
                stringResource(R.string.settings_screen_language), state.selectedLocal.mainName
            ){
                viewModel.navigateTo(Destination.LanguageSetting)
            }


            AppSettingItem(
                R.drawable.settings_dollar,
                stringResource(R.string.settings_screen_currency), state.selectedCurrency.name
            ){
                viewModel.navigateTo(Destination.CurrencyUnitSetting)
            }

            AppSettingItem(
                R.drawable.settings_coin,
                stringResource(R.string.settings_screen_default_chains)
            ){
                viewModel.navigateTo(Destination.DefaultChainSetting)
            }

            AppSettingItem(
                R.drawable.settings_question,
                stringResource(R.string.settings_screen_faq)
            ){
                viewModel.navigateTo(Destination.FAQSetting)
            }

            Text(
                text = stringResource(R.string.settings_screen_others),
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                style = Theme.montserrat.body2,
                textAlign = TextAlign.Start
            )

            AppSettingItem(
                R.drawable.settings_logo,
                stringResource(R.string.settings_screen_vtx_token)
            ){
                viewModel.navigateTo(Destination.VultisigToken)
            }

            AppSettingItem(
                R.drawable.share,
                stringResource(R.string.settings_screen_share_the_app)
            )

            UiSpacer(weight = 1f)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.settings_github),
                    contentDescription = "github",
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://github.com/vultisig")
                    }
                )
                Image(
                    painter = painterResource(id = R.drawable.settings_twitter),
                    contentDescription = "twitter",
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://twitter.com/vultisig")
                    }
                )
                Image(
                    painter = painterResource(id = R.drawable.settings_discord),
                    contentDescription = "discord",
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://discord.com/vultisig")
                    }
                )
            }

            UiSpacer(size = 48.dp)

            Text(text = stringResource(R.string.vultisig_app_version, "1.23")
                , style = Theme.menlo.titleSmall,
                color = Theme.colors.turquoise600Main
                )
            UiSpacer(size = 24.dp)
        }

    }
}

@Composable
private fun AppSettingItem(@DrawableRes logo: Int, title: String, currentValue: String? = null, onClick: ()->Unit = {}) {
    val colors = Theme.colors
    Card(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.oxfordBlue600Main
        )
    ) {
        Row(
            modifier = Modifier.padding(all = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                modifier = Modifier
                    .padding(
                        end = 12.dp,
                    )
                    .size(20.dp)
                    .clip(CircleShape),
                painter = painterResource(id = logo),
                contentDescription = stringResource(R.string.token_logo),
            )
            Text(
                text = title,
                color = colors.neutral0,
                style = Theme.montserrat.body2,
            )

            Spacer(modifier = Modifier.weight(1f))

            currentValue?.let {
                Text(
                    text = it,
                    color = colors.neutral0,
                    style = Theme.montserrat.body2,
                )
            }

            Icon(
                painter = painterResource(id = R.drawable.caret_right),
                contentDescription = null,
                tint = colors.neutral0,
            )
        }
    }
}