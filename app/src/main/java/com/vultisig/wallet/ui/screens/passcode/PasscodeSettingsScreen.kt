package com.vultisig.wallet.ui.screens.passcode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.passcode.AutoLockTimeout
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.passcode.PasscodeSettingsUiModel
import com.vultisig.wallet.ui.models.passcode.PasscodeSettingsViewModel
import com.vultisig.wallet.ui.models.settings.SettingsItemUiModel
import com.vultisig.wallet.ui.screens.settings.SettingItem
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText

@Composable
internal fun PasscodeSettingsScreen(navController: NavHostController) {
    val viewModel = hiltViewModel<PasscodeSettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    PasscodeSettingsScreen(
        state = state,
        onBackClick = { navController.popBackStack() },
        onPasscodeEnabledChange = viewModel::onPasscodeEnabledChange,
        onChangePasscodeClick = viewModel::onChangePasscodeClick,
        onAutoLockClick = viewModel::onAutoLockClick,
    )
}

@Composable
internal fun PasscodeSettingsScreen(
    state: PasscodeSettingsUiModel,
    onBackClick: () -> Unit,
    onPasscodeEnabledChange: (Boolean) -> Unit,
    onChangePasscodeClick: () -> Unit,
    onAutoLockClick: () -> Unit,
) {
    V2Scaffold(
        title = stringResource(R.string.passcode_settings_title),
        onBackClick = onBackClick,
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            V2Container(type = ContainerType.SECONDARY) {
                Column {
                    SettingItem(
                        item =
                            SettingsItemUiModel(
                                title =
                                    UiText.StringResource(
                                        R.string.passcode_settings_encryption_title
                                    ),
                                subTitle =
                                    UiText.StringResource(
                                        R.string.passcode_settings_encryption_subtitle
                                    ),
                                leadingIcon = R.drawable.security,
                                trailingSwitch = state.isPasscodeEnabled,
                            ),
                        onClick = {
                            if (state.isReady) onPasscodeEnabledChange(!state.isPasscodeEnabled)
                        },
                        // Change and auto-lock only exist once a passcode does, so with the
                        // switch off this row is both first and last.
                        isLastItem = !state.isPasscodeEnabled,
                    )

                    if (state.isPasscodeEnabled) {
                        SettingItem(
                            item =
                                SettingsItemUiModel(
                                    title =
                                        UiText.StringResource(R.string.passcode_settings_change),
                                    leadingIcon = R.drawable.lock,
                                    trailingIcon = R.drawable.ic_small_caret_right,
                                ),
                            onClick = onChangePasscodeClick,
                            isLastItem = false,
                        )

                        SettingItem(
                            item =
                                SettingsItemUiModel(
                                    title =
                                        UiText.StringResource(R.string.passcode_settings_auto_lock),
                                    value = stringResource(state.autoLockTimeout.labelRes()),
                                    leadingIcon = R.drawable.ic_clock_filled,
                                    trailingIcon = R.drawable.ic_small_caret_right,
                                ),
                            onClick = onAutoLockClick,
                            isLastItem = true,
                        )
                    }
                }
            }

            // Outside the switch's branch: the warning is worth most before a passcode is set.
            UiSpacer(size = 12.dp)
            Text(
                text = stringResource(R.string.passcode_settings_encryption_explanation),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary,
            )
        }
    }
}

/** The picker's own label for [this], reused here so the row and the picker cannot disagree. */
internal fun AutoLockTimeout.labelRes(): Int =
    when (this) {
        AutoLockTimeout.Never -> R.string.auto_lock_never
        AutoLockTimeout.OneMinute -> R.string.auto_lock_one_minute
        AutoLockTimeout.FiveMinutes -> R.string.auto_lock_five_minutes
        AutoLockTimeout.TenMinutes -> R.string.auto_lock_ten_minutes
        AutoLockTimeout.FifteenMinutes -> R.string.auto_lock_fifteen_minutes
        AutoLockTimeout.ThirtyMinutes -> R.string.auto_lock_thirty_minutes
    }

@Preview
@Composable
private fun PasscodeSettingsScreenPreview() {
    PasscodeSettingsScreen(
        state =
            PasscodeSettingsUiModel(
                isPasscodeEnabled = true,
                autoLockTimeout = AutoLockTimeout.FiveMinutes,
                isReady = true,
            ),
        onBackClick = {},
        onPasscodeEnabledChange = {},
        onChangePasscodeClick = {},
        onAutoLockClick = {},
    )
}
