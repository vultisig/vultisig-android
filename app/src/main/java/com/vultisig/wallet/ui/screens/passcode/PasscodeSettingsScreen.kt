package com.vultisig.wallet.ui.screens.passcode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.passcode.AutoLockTimeout
import com.vultisig.wallet.ui.components.BiometricUnlockAvailability
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.biometricUnlockAvailability
import com.vultisig.wallet.ui.components.rememberBiometricUnlockLauncher
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.passcode.PasscodeSettingsUiModel
import com.vultisig.wallet.ui.models.passcode.PasscodeSettingsViewModel
import com.vultisig.wallet.ui.models.settings.SettingsItemUiModel
import com.vultisig.wallet.ui.screens.settings.SettingItem
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun PasscodeSettingsScreen(navController: NavHostController) {
    val viewModel = hiltViewModel<PasscodeSettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberBiometricUnlockLauncher()
    var biometricAvailability by
        remember(context) { mutableStateOf(context.biometricUnlockAvailability()) }

    // Enrolling a biometric is done in device settings, which is exactly where the
    // not-enrolled note sends the user. Re-asking on the way back keeps the row from staying
    // dead until the screen is left and re-entered.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        biometricAvailability = context.biometricUnlockAvailability()
    }

    PasscodeSettingsScreen(
        state = state,
        biometricAvailability = biometricAvailability,
        onBackClick = { navController.popBackStack() },
        onPasscodeEnabledChange = viewModel::onPasscodeEnabledChange,
        onChangePasscodeClick = viewModel::onChangePasscodeClick,
        onAutoLockClick = viewModel::onAutoLockClick,
        onBiometricUnlockChange = { viewModel.onBiometricUnlockChange(it, launcher) },
    )
}

@Composable
internal fun PasscodeSettingsScreen(
    state: PasscodeSettingsUiModel,
    biometricAvailability: BiometricUnlockAvailability,
    onBackClick: () -> Unit,
    onPasscodeEnabledChange: (Boolean) -> Unit,
    onChangePasscodeClick: () -> Unit,
    onAutoLockClick: () -> Unit,
    onBiometricUnlockChange: (Boolean) -> Unit,
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
                            isLastItem = false,
                        )

                        SettingItem(
                            item =
                                SettingsItemUiModel(
                                    title =
                                        UiText.StringResource(
                                            R.string.passcode_settings_biometrics
                                        ),
                                    leadingIcon = R.drawable.ic_biometrics,
                                    trailingSwitch = state.isBiometricUnlockEnabled,
                                ),
                            // The row stays visible on a device that cannot do this, with the note
                            // below saying why — a capability that silently is not there reads as
                            // one the app forgot to build.
                            onClick = {
                                if (
                                    biometricAvailability == BiometricUnlockAvailability.Available
                                ) {
                                    onBiometricUnlockChange(!state.isBiometricUnlockEnabled)
                                }
                            },
                            isLastItem = true,
                        )
                    }
                }
            }

            biometricNote(state, biometricAvailability)?.let { note ->
                UiSpacer(size = 12.dp)
                Text(
                    text = note,
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.tertiary,
                )
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

/**
 * What the biometric row has to say for itself, if anything.
 *
 * A failed attempt outranks the device state: the user has just tried something and is owed the
 * outcome, not a description of the hardware.
 */
@Composable
private fun biometricNote(
    state: PasscodeSettingsUiModel,
    availability: BiometricUnlockAvailability,
): String? {
    if (!state.isPasscodeEnabled) return null
    state.biometricError?.let {
        return it.asString()
    }
    return when (availability) {
        BiometricUnlockAvailability.Available -> null
        BiometricUnlockAvailability.NotEnrolled ->
            stringResource(R.string.passcode_biometric_not_enrolled)
        BiometricUnlockAvailability.Unavailable ->
            stringResource(R.string.passcode_biometric_not_available)
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
                isBiometricUnlockEnabled = true,
            ),
        biometricAvailability = BiometricUnlockAvailability.Available,
        onBackClick = {},
        onPasscodeEnabledChange = {},
        onChangePasscodeClick = {},
        onAutoLockClick = {},
        onBiometricUnlockChange = {},
    )
}
