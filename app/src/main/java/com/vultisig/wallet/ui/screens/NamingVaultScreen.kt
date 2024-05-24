package com.vultisig.wallet.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text2.BasicTextField2
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.models.NamingVaultViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun NamingVaultScreen(
    navController: NavHostController,
) {
    val viewModel = hiltViewModel<NamingVaultViewModel>()
    val uiModel by viewModel.uiModel.collectAsState()
    val textColor = MaterialTheme.colorScheme.onBackground
    val appColor = Theme.colors
    val dimens = MaterialTheme.dimens

    Scaffold(
        bottomBar = {
            MultiColorButton(
                minHeight = dimens.minHeightButton,
                backgroundColor = appColor.turquoise800,
                textColor = appColor.oxfordBlue800,
                iconColor = appColor.turquoise800,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .imePadding()
                    .fillMaxWidth()
                    .padding(
                        start = dimens.marginMedium,
                        end = dimens.marginMedium,
                        bottom = dimens.marginMedium,
                    ),
                text = stringResource(id = R.string.naming_vault_screen_continue),
                onClick = viewModel::onContinueClick,
            )
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.naming_vault_screen_name_your_vault),
                        style = Theme.montserrat.subtitle1,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier
                            .padding(
                                start = dimens.marginMedium,
                                end = dimens.marginMedium,
                            )
                            .wrapContentHeight(align = Alignment.CenterVertically)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appColor.oxfordBlue800,
                    titleContentColor = textColor
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "back", tint = Color.White
                        )
                    }
                },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .background(Theme.colors.oxfordBlue800)
                .fillMaxSize()
                .padding(
                    horizontal = 12.dp,
                    vertical = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(id = R.string.naming_vault_screen_vault_name),
                color = Theme.colors.neutral100,
                style = Theme.montserrat.body2,
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Theme.colors.oxfordBlue600Main
                ),
            ) {
                BasicTextField2(
                    value = uiModel.name,
                    onValueChange = viewModel::onNameChanged,
                    modifier = Modifier
                        .padding(12.dp)
                        .imePadding(),
                    textStyle = Theme.montserrat.body2.copy(color = Theme.colors.neutral100),
                )
            }
        }
    }
}




