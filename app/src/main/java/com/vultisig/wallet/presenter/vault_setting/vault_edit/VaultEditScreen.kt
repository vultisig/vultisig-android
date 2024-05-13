package com.vultisig.wallet.presenter.vault_setting.vault_edit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.common.asString
import com.vultisig.wallet.presenter.vault_setting.vault_edit.VaultEditEvent.OnNameChange
import com.vultisig.wallet.presenter.vault_setting.vault_edit.VaultEditEvent.OnSave
import com.vultisig.wallet.presenter.vault_setting.vault_edit.VaultEditUiEvent.ShowSnackBar
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VaultEditScreen(navHostController: NavHostController) {
    val viewmodel = hiltViewModel<VaultEditViewModel>()
    val uiModel by viewmodel.uiModel.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(key1 = Unit) {
        viewmodel.loadData()
        viewmodel.channelFlow.collect { event ->
            when (event) {
                is ShowSnackBar -> snackBarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }


    val textColor = MaterialTheme.colorScheme.onBackground
    val appColor = Theme.colors
    val dimens = MaterialTheme.dimens

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackBarHostState)
        },
        bottomBar = {
            Box(Modifier.imePadding()) {
                MultiColorButton(
                    minHeight = dimens.minHeightButton,
                    backgroundColor = appColor.turquoise800,
                    textColor = appColor.oxfordBlue800,
                    iconColor = appColor.turquoise800,
                    textStyle = Theme.montserrat.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = dimens.marginMedium,
                            end = dimens.marginMedium,
                            bottom = dimens.marginMedium,
                        ),
                    text = stringResource(id = R.string.save)
                ) {
                    viewmodel.onEvent(OnSave)
                }
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.vault_settings_rename_title),
                        style = Theme.montserrat.titleLarge,
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
                        navHostController.popBackStack()
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
                .fillMaxSize()
                .padding(it)
                .background(Theme.colors.oxfordBlue800),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(id = R.string.vault_settings_rename_title),
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
                    onValueChange = { newName ->
                        viewmodel.onEvent(OnNameChange(newName))
                    },
                    modifier = Modifier
                        .padding(12.dp)
                        .imePadding(),
                    textStyle = Theme.montserrat.body2.copy(color = Theme.colors.neutral100),
                    )
            }
        }
    }
}
