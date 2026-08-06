package com.vultisig.wallet.ui.screens.passcode

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.passcode.AutoLockTimeout
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.passcode.AutoLockSettingUiModel
import com.vultisig.wallet.ui.models.passcode.AutoLockSettingViewModel
import com.vultisig.wallet.ui.models.settings.SettingsItemUiModel
import com.vultisig.wallet.ui.screens.settings.SettingItem
import com.vultisig.wallet.ui.utils.asUiText

@Composable
internal fun AutoLockSettingScreen(navController: NavHostController) {
    val viewModel = hiltViewModel<AutoLockSettingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    AutoLockSettingScreen(
        state = state,
        onBackClick = { navController.popBackStack() },
        onTimeoutClick = viewModel::onTimeoutClick,
    )
}

@Composable
internal fun AutoLockSettingScreen(
    state: AutoLockSettingUiModel,
    onBackClick: () -> Unit,
    onTimeoutClick: (AutoLockTimeout) -> Unit,
) {
    V2Scaffold(
        title = stringResource(R.string.auto_lock_setting_title),
        onBackClick = onBackClick,
    ) {
        V2Container(type = ContainerType.SECONDARY) {
            LazyColumn {
                itemsIndexed(state.options, key = { _, timeout -> timeout.name }) { index, timeout
                    ->
                    SettingItem(
                        item =
                            SettingsItemUiModel(
                                title = stringResource(timeout.labelRes()).asUiText(),
                                trailingIcon =
                                    if (timeout == state.selected) R.drawable.check_2 else null,
                            ),
                        onClick = { onTimeoutClick(timeout) },
                        isLastItem = index == state.options.lastIndex,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun AutoLockSettingScreenPreview() {
    AutoLockSettingScreen(
        state = AutoLockSettingUiModel(selected = AutoLockTimeout.FiveMinutes),
        onBackClick = {},
        onTimeoutClick = {},
    )
}
