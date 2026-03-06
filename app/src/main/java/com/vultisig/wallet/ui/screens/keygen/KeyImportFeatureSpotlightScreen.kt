package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rive.runtime.kotlin.core.Fit
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.keygen.KeyImportFeatureSpotlightViewModel
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.delay

private val RiveHeight = 300.dp
private val RevealedTopPadding = 32.dp

@Composable
internal fun KeyImportFeatureSpotlightScreen(
    model: KeyImportFeatureSpotlightViewModel = hiltViewModel()
) {
    var revealed by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(3000)
        revealed = true
        delay(700)
        contentVisible = true
    }

    val density = LocalDensity.current
    var availableHeightPx by remember { mutableIntStateOf(0) }

    val centerTop = with(density) { (availableHeightPx.toDp() - RiveHeight) / 2 }
    val riveTop by
        animateDpAsState(
            targetValue = if (revealed) RevealedTopPadding else centerTop,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "riveTop",
        )
    val contentAlpha by
        animateFloatAsState(
            targetValue = if (contentVisible) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "contentAlpha",
        )

    V2Scaffold(onBackClick = model::back) {
        Column(modifier = Modifier.fillMaxSize().onSizeChanged { availableHeightPx = it.height }) {
            Spacer(modifier = Modifier.height(riveTop))

            RiveAnimation(
                animation = R.raw.riv_import_seedphrase,
                modifier = Modifier.fillMaxWidth().height(RiveHeight),
                fit = Fit.CONTAIN,
            )

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).alpha(contentAlpha)
            ) {
                Text(
                    text = stringResource(R.string.key_import_spotlight_before_you_start),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                )

                UiSpacer(12.dp)

                val headingText = buildAnnotatedString {
                    append(stringResource(R.string.key_import_spotlight_heading_part1))
                    withStyle(SpanStyle(brush = Theme.v2.colors.gradients.primary)) {
                        append(stringResource(R.string.key_import_spotlight_heading_highlight))
                    }
                    append(stringResource(R.string.key_import_spotlight_heading_part2))
                }

                Text(
                    text = headingText,
                    style = Theme.brockmann.headings.title2.copy(lineHeight = 30.sp),
                    color = Theme.v2.colors.text.primary,
                )

                UiSpacer(32.dp)

                BulletItem(
                    icon = R.drawable.ic_seedphrase,
                    title = stringResource(R.string.key_import_spotlight_seedphrase_title),
                    description = stringResource(R.string.key_import_spotlight_seedphrase_desc),
                )

                UiSpacer(24.dp)

                BulletItem(
                    icon = R.drawable.ic_devices,
                    title = stringResource(R.string.key_import_spotlight_device_title),
                    description = stringResource(R.string.key_import_spotlight_device_desc),
                )

                UiSpacer(65.dp)

                VsButton(
                    label = stringResource(R.string.key_import_spotlight_get_started),
                    onClick = model::getStarted,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BulletItem(icon: Int, title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        UiSpacer(16.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = Theme.brockmann.headings.subtitle,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(8.dp)
            Text(
                text = description,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary,
            )
        }
    }
}
