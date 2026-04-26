package com.vultisig.wallet.ui.screens.v3.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rive.Alignment as RiveAlignment
import app.rive.Fit
import app.rive.rememberViewModelInstance
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.VsCircularLoading
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.rive.rememberRiveResourceFile
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.v3.onboarding.ChooseDeviceCountUiEvent
import com.vultisig.wallet.ui.models.v3.onboarding.ChooseDeviceCountViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ChooseDeviceCountScreen(viewModel: ChooseDeviceCountViewModel = hiltViewModel()) {
    ChooseDeviceCountScreen(onEvent = viewModel::handleEvent)
}

@Composable
private fun ChooseDeviceCountScreen(onEvent: (ChooseDeviceCountUiEvent) -> Unit) {
    V3Scaffold(
        applyGradientBackground = true,
        onBackClick = { onEvent(ChooseDeviceCountUiEvent.Back) },
        content = {
            val riveFile = rememberRiveResourceFile(resId = R.raw.riv_devices_component).value

            if (riveFile == null) {
                VsCircularLoading(modifier = Modifier.fillMaxSize().wrapContentSize())
            } else {
                val vmi = rememberViewModelInstance(file = riveFile)
                var deviceIndex by remember { mutableIntStateOf(0) }

                LaunchedEffect(Unit) {
                    vmi.getNumberFlow("Index").collect { index ->
                        deviceIndex = index.toInt().coerceIn(0, 3)
                        onEvent(ChooseDeviceCountUiEvent.IndexChanged(index.toInt()))
                    }
                }

                val deviceCountLabel = if (deviceIndex == 3) "4+" else (deviceIndex + 1).toString()
                val a11yDescription =
                    stringResource(R.string.choose_device_count_a11y_description, deviceCountLabel)

                Box(Modifier.fillMaxSize()) {
                    RiveAnimation(
                        file = riveFile,
                        viewModelInstance = vmi,
                        fit = Fit.Contain(alignment = RiveAlignment.TopCenter),
                        modifier =
                            Modifier.fillMaxWidth().semantics {
                                contentDescription = a11yDescription
                                liveRegion = LiveRegionMode.Polite
                            },
                    )
                    Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                        Tip()
                        UiSpacer(size = 16.dp)
                        VsButton(
                            label = stringResource(R.string.referral_onboarding_get_started),
                            modifier = Modifier.fillMaxWidth(),
                            variant = VsButtonVariant.CTA,
                            onClick = { onEvent(ChooseDeviceCountUiEvent.Next) },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun Tip() {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        UiIcon(drawableResId = R.drawable.tip, size = 12.dp, tint = Theme.v2.colors.alerts.info)
        UiSpacer(size = 8.dp)
        Text(
            text = stringResource(R.string.welcome_tip),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.secondary,
        )
    }
}

@Preview
@Composable
private fun ChooseDeviceCountScreenPreview() {
    ChooseDeviceCountScreen(onEvent = {})
}
