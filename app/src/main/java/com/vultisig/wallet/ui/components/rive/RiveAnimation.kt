package com.vultisig.wallet.ui.components.rive

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.Artboard
import app.rive.ExperimentalRiveComposeAPI
import app.rive.Result
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.RiveUI
import app.rive.ViewModelInstance
import app.rive.rememberCommandQueueOrNull
import app.rive.rememberRiveFile
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
            update = { view -> onInit(view) })
    }
}

@OptIn(ExperimentalRiveComposeAPI::class)
@Composable
fun RiveAnimation(
    file: RiveFile,
    modifier: Modifier = Modifier,
    artboard: Artboard? = null,
    stateMachineName: String? = null,
    viewModelInstance: ViewModelInstance? = null,
    fit: Fit = Fit.CONTAIN,
    alignment: Alignment = Alignment.CENTER,
) {
    if (LocalInspectionMode.current) {
        // rive doesn't work in preview
        Spacer(modifier)
    } else {
        RiveUI(
            file = file,
            modifier = modifier,
            artboard = artboard,
            stateMachineName = stateMachineName,
            viewModelInstance = viewModelInstance,
            fit = fit,
            alignment = alignment,
        )
    }
}

@OptIn(ExperimentalRiveComposeAPI::class)
@Composable
fun rememberRiveResourceFile(@RawRes resId: Int): State<RiveFile?> {
    val commandQueue = rememberCommandQueueOrNull(remember { mutableStateOf(null) })

    val riveFileResult = commandQueue?.let {
        rememberRiveFile(
            RiveFileSource.RawRes(resId),
            commandQueue = it
        )
    }

    return remember(riveFileResult) {
        derivedStateOf {
            (riveFileResult?.value as? Result.Success)?.value
        }
    }
}