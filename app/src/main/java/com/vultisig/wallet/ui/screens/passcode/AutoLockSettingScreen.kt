package com.vultisig.wallet.ui.screens.passcode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.passcode.AutoLockTimeout
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.passcode.AutoLockSettingUiModel
import com.vultisig.wallet.ui.models.passcode.AutoLockSettingViewModel
import com.vultisig.wallet.ui.models.settings.SettingsItemUiModel
import com.vultisig.wallet.ui.screens.settings.SettingItem
import com.vultisig.wallet.ui.screens.settings.SettingItemLayout
import com.vultisig.wallet.ui.theme.Theme
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = HEADER_KEY) {
                Text(
                    text = stringResource(R.string.auto_lock_setting_header),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.secondary,
                )
            }
            items(state.options, key = { timeout -> timeout.name }) { timeout ->
                Column(
                    Modifier.clip(Theme.v2.radius.lg)
                        .background(Theme.v2.colors.variables.inputFieldsBackground)
                        .border(
                            width = 1.dp,
                            color = Theme.v2.colors.variables.bordersExtraLight,
                            shape = Theme.v2.radius.lg,
                        )
                ) {
                    SettingItem(
                        item =
                            SettingsItemUiModel(
                                title = stringResource(timeout.labelRes()).asUiText(),
                                trailingIcon =
                                    if (timeout == state.selected) R.drawable.check_2 else null,
                            ),
                        onClick = { onTimeoutClick(timeout) },
                        isLastItem = true,
                        tint = Theme.v2.colors.text.tertiary,
                        titleStyle = Theme.brockmann.body.s.medium,
                        layout = SettingItemLayout.Field,
                    )
                }
            }
        }
    }
}

private const val HEADER_KEY = "header"

@Preview
@Composable
private fun AutoLockSettingScreenPreview() {
    AutoLockSettingScreen(
        state = AutoLockSettingUiModel(selected = AutoLockTimeout.FiveMinutes),
        onBackClick = {},
        onTimeoutClick = {},
    )
}
