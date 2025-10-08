package com.vultisig.wallet.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.VsSwitch
import com.vultisig.wallet.ui.models.settings.DefaultChainsSettingViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun DefaultChainSetting(navController: NavHostController) {

    val colors = Theme.colors
    val viewModel = hiltViewModel<DefaultChainsSettingViewModel>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(key1 = Unit) {
        viewModel.initialize()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.oxfordBlue800),
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(R.string.default_chain_screen_title),
                startIcon = R.drawable.ic_caret_left
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .padding(it)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.chains) { chain ->
                TokenSelection(
                    title = chain.title,
                    subtitle = chain.subtitle,
                    logo = chain.logo,
                    isChecked = chain in state.selectedDefaultChains,
                    onCheckedChange = { checked ->
                        viewModel.changeChaneState(chain,checked)
                    }
                )
            }
        }
    }
}

@Composable
internal fun TokenSelection(
    title: String,
    subtitle: String,
    @DrawableRes logo: Int,
    isChecked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit),
) {
    val appColor = Theme.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = isChecked,
                onValueChange = onCheckedChange,
                role = androidx.compose.ui.semantics.Role.Switch
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = appColor.oxfordBlue600Main
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
                    .size(32.dp)
                    .clip(CircleShape),
                painter = painterResource(id = logo),
                contentDescription = stringResource(R.string.token_logo),
                contentScale = ContentScale.Crop
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = appColor.neutral100,
                    style = Theme.montserrat.subtitle1,
                )
                Text(
                    text = subtitle,
                    color = appColor.neutral100,
                    style = Theme.montserrat.body3,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            VsSwitch(
                colors = SwitchDefaults.colors(
                    checkedThumbColor = appColor.neutral0,
                    checkedBorderColor = appColor.turquoise800,
                    checkedTrackColor = appColor.turquoise800,
                    uncheckedThumbColor = appColor.neutral0,
                    uncheckedBorderColor = appColor.oxfordBlue400,
                    uncheckedTrackColor = appColor.oxfordBlue400
                ),
                checked = isChecked,
                onCheckedChange = null,
            )
        }
    }
}