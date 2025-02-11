package com.vultisig.wallet.ui.components.rive

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Fit

@Composable
fun RiveAnimation(
    modifier: Modifier = Modifier,
    @RawRes animation: Int,
    stateMachineName: String? = null,
    alignment: Alignment = Alignment.CENTER,
    fit: Fit = Fit.CONTAIN,
    autoPlay: Boolean = true,
    onInit: (RiveAnimationView) -> Unit = {},
) {
    if (LocalInspectionMode.current) {
        // rive doesn't work in preview
        Spacer(modifier)
    } else {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                RiveAnimationView(context).also {
                    it.setRiveResource(
                        resId = animation,
                        stateMachineName = stateMachineName,
                        alignment = alignment,
                        autoplay = autoPlay,
                        fit = fit,
                    )
                }
            },
            update = { view -> onInit(view) }
        )
    }
}