package com.vultisig.wallet.presenter.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.appColor
import com.vultisig.wallet.ui.theme.dimens
import com.vultisig.wallet.ui.theme.montserratFamily

@Composable
fun VaultCeil(navHostController: NavHostController, vault: Vault) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Card(
        colors = CardDefaults.cardColors(containerColor = Theme.colors.oxfordBlue400),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MaterialTheme.dimens.marginMedium,
                end = MaterialTheme.dimens.marginMedium,
                top = MaterialTheme.dimens.marginMedium,
            ), onClick = {
            navHostController.navigate(Screen.VaultDetail.createRoute(vault.name))
        }

    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(id = R.drawable.hamburger_menu), contentDescription = "draggable item",modifier = Modifier.width(16.dp))
            Text(
                text = vault.name.uppercase(),
                style = Theme.montserrat.subtitle2,
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
            UiSpacer(weight = 1f)
            Image(
                painter = painterResource(R.drawable.caret_right),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = MaterialTheme.dimens.marginMedium)

            )
        }
    }
}