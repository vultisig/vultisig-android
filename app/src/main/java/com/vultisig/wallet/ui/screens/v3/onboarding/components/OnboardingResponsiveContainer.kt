package com.vultisig.wallet.ui.screens.v3.onboarding.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.utils.rememberWindowWidthSizeClass

@Composable
internal fun OnboardingResponsiveContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = OnboardingDefaults.MaxContentWidth,
    maxHeight: Dp = OnboardingDefaults.MaxContentHeight,
    content: @Composable BoxScope.() -> Unit,
) {
    if (isCompactWidth()) {
        Box(modifier = modifier.fillMaxSize(), content = content)
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.widthIn(max = maxWidth).heightIn(max = maxHeight).fillMaxSize(),
                content = content,
            )
        }
    }
}

@Composable
internal fun OnboardingResponsiveBottomBar(
    modifier: Modifier = Modifier,
    maxWidth: Dp = OnboardingDefaults.MaxContentWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    if (isCompactWidth()) {
        Box(modifier = modifier.fillMaxWidth(), content = content)
    } else {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun isCompactWidth(): Boolean =
    rememberWindowWidthSizeClass() == WindowWidthSizeClass.Compact

internal object OnboardingDefaults {
    val MaxContentWidth = 480.dp
    val MaxContentHeight = 900.dp
}
