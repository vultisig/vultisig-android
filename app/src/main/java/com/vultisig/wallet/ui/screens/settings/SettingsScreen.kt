package com.vultisig.wallet.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.AppVersionText
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.referral.ReferralCodeBottomSheet
import com.vultisig.wallet.ui.models.settings.SettingsViewModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks
import com.vultisig.wallet.ui.utils.VsUriHandler

@Composable
fun SettingsScreen(navController: NavHostController) {
    val colors = Theme.colors
    val viewModel = hiltViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsState()
    val uriHandler = VsUriHandler()
    val context: Context = LocalContext.current

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
                startIcon = R.drawable.ic_caret_left
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(it)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppSettingItem(
                R.drawable.gear,
                stringResource(R.string.settings_screen_vault_settings)
            ) {
                viewModel.navigateTo(Destination.VaultSettings(viewModel.vaultId))
            }

            AppSettingItem(
                R.drawable.settings_globe,
                stringResource(R.string.settings_screen_language),
                state.selectedLocal.mainName
            ) {
                viewModel.navigateTo(Destination.LanguageSetting)
            }

            AppSettingItem(
                R.drawable.settings_dollar,
                stringResource(R.string.settings_screen_currency),
                state.selectedCurrency.name
            ) {
                viewModel.navigateTo(Destination.CurrencyUnitSetting)
            }

            AppSettingItem(
                logo = R.drawable.ic_bookmark,
                title = stringResource(R.string.address_book_settings_title),
            ) {
                viewModel.navigateTo(Destination.AddressBook())
            }

            if (REFERRAL_FEATURE_FLAG) {
                AppSettingItem(
                    logo = R.drawable.handshake,
                    title = stringResource(R.string.referral_code_settings_title),
                ) {
                    viewModel.onClickReferralCode()
                }
            }

            AppSettingItem(
                R.drawable.settings_coin,
                stringResource(R.string.settings_screen_default_chains)
            ) {
                viewModel.navigateTo(Destination.DefaultChainSetting)
            }

            AppSettingItem(
                R.drawable.settings_question,
                stringResource(R.string.settings_screen_faq)
            ) {
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
                stringResource(R.string.settings_screen_register_your_vaults),
                backgroundColor = colors.turquoise600Main,
                textColor = colors.oxfordBlue600Main,
                style = Theme.montserrat.caption
            ) {
                viewModel.navigateTo(Destination.RegisterVault(viewModel.vaultId))
            }

            AppSettingItem(
                R.drawable.settings_logo,
                stringResource(R.string.settings_screen_vtx_token)
            ) {
                viewModel.navigateTo(Destination.VultisigToken)
            }

            AppSettingItem(
                R.drawable.share,
                stringResource(R.string.settings_screen_share_the_app)
            ) {
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "https://play.google.com/store/apps/details?id=com.vultisig.wallet"
                    )
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(
                    sendIntent,
                    null
                )
                context.startActivity(shareIntent)
            }

            Text(
                text = stringResource(R.string.settings_screen_legal),
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                style = Theme.montserrat.body2,
                textAlign = TextAlign.Start
            )

            AppSettingItem(
                R.drawable.shield_check,
                stringResource(R.string.settings_screen_privacy_policy)
            ) {
                uriHandler.openUri(VsAuxiliaryLinks.PRIVACY)
            }

            AppSettingItem(
                R.drawable.note,
                stringResource(R.string.settings_screen_tos)
            ) {
                uriHandler.openUri(VsAuxiliaryLinks.TERMS_OF_SERVICE)
            }

            UiSpacer(weight = 1f)

            UiSpacer(size = 26.dp)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.settings_github),
                    contentDescription = "github",
                    modifier = Modifier.clickable {
                        uriHandler.openUri(VsAuxiliaryLinks.GITHUB)
                    }
                )
                Image(
                    painter = painterResource(id = R.drawable.settings_twitter),
                    contentDescription = "twitter",
                    modifier = Modifier.clickable {
                        uriHandler.openUri(VsAuxiliaryLinks.TWITTER)
                    }
                )
                Image(
                    painter = painterResource(id = R.drawable.settings_discord),
                    contentDescription = "discord",
                    modifier = Modifier.clickable {
                        uriHandler.openUri(VsAuxiliaryLinks.DISCORD)
                    }
                )
            }

            AppVersionText(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 24.dp)
                    .clickable(onClick = viewModel::clickSecret)
            )

            if (state.hasToShowReferralCodeSheet && REFERRAL_FEATURE_FLAG) {
                ReferralCodeBottomSheet(
                    onContinue = { viewModel.onContinueReferralBottomSheet() },
                    onDismissRequest = { viewModel.onDismissReferralBottomSheet() },
                )
             }
        }
    }
}

@Composable
private fun AppSettingItem(
    @DrawableRes logo: Int,
    title: String,
    currentValue: String? = null,
    backgroundColor: Color = Theme.colors.oxfordBlue600Main,
    textColor: Color = Theme.colors.neutral0,
    style: TextStyle = Theme.montserrat.body2,
    onClick: () -> Unit = {},
) {
    val colors = Theme.colors
    Card(
        modifier = Modifier
            .clickOnce(
                onClick = onClick
            )
            .padding(
                horizontal = 12.dp, vertical = 8.dp
            )
            .fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
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
                color = textColor,
                style = style,
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
                painter = painterResource(id = R.drawable.ic_small_caret_right),
                contentDescription = null,
                tint = colors.neutral0,
            )
        }
    }
}

private const val REFERRAL_FEATURE_FLAG = false