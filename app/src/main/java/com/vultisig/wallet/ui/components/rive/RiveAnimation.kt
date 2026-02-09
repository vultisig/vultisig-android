package com.vultisig.wallet.ui.components.rive

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.Artboard
import app.rive.Fit
import app.rive.Result
import app.rive.Rive
import app.rive.RiveFile
import app.rive.RiveFileSource
import app.rive.StateMachine
import app.rive.ViewModelInstance
import app.rive.rememberRiveFile
import app.rive.rememberRiveWorkerOrNull
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Alignment

@Composable
fun RiveAnimation(
    modifier: Modifier = Modifier,
    @RawRes animation: Int,
    stateMachineName: String? = null,
    alignment: Alignment = Alignment.CENTER,
    fit: app.rive.runtime.kotlin.core.Fit = app.rive.runtime.kotlin.core.Fit.FIT_WIDTH,
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

@Composable
fun RiveAnimation(
    file: RiveFile,
    modifier: Modifier = Modifier,
    artboard: Artboard? = null,
    stateMachine: StateMachine? = null,
    viewModelInstance: ViewModelInstance? = null,
    fit: Fit = Fit.Contain(),
) {
    if (LocalInspectionMode.current) {
        // rive doesn't work in preview
        Spacer(modifier)
    } else {
        Rive(
            file = file,
            modifier = modifier,
            artboard = artboard,
            stateMachine = stateMachine,
            viewModelInstance = viewModelInstance,
            fit = fit,
        )
    }
}

@Composable
fun rememberRiveResourceFile(@RawRes resId: Int): State<RiveFile?> {
    val riveWorker = rememberRiveWorkerOrNull()

    val riveFileResult = riveWorker?.let {
        rememberRiveFile(
            RiveFileSource.RawRes.from(resId),
            riveWorker
        )
    }

    return remember(riveFileResult) {
        derivedStateOf {
            (riveFileResult as? Result.Success)?.value
        }
    }
}