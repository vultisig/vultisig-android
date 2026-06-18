package com.vultisig.wallet.ui.components.rive

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.PlayableInstance
import com.vultisig.wallet.app.isRiveInitialized

@Composable
fun RiveAnimation(
    modifier: Modifier = Modifier,
    @RawRes animation: Int,
    stateMachineName: String? = null,
    alignment: Alignment = Alignment.CENTER,
    fit: app.rive.runtime.kotlin.core.Fit = app.rive.runtime.kotlin.core.Fit.FIT_WIDTH,
    autoPlay: Boolean = true,
    onInit: (RiveAnimationView) -> Unit = {},
    onFirstFrame: () -> Unit = {},
) {
    if (LocalInspectionMode.current || !isRiveInitialized) {
        Spacer(modifier)
    } else {
        // Read the latest callback without re-running the one-shot factory below.
        val currentOnFirstFrame = rememberUpdatedState(onFirstFrame)
        key(animation) {
            AndroidView(
                modifier = modifier,
                factory = { context ->
                    RiveAnimationView(context).also { view ->
                        view.setRiveResource(
                            resId = animation,
                            stateMachineName = stateMachineName,
                            alignment = alignment,
                            autoplay = autoPlay,
                            fit = fit,
                        )
                        // The view inflates and renders its first frame a few hundred ms after it
                        // is composed. Callers that overlay their own content (e.g. a QR code) use
                        // this to reveal it only once the Rive frame is actually on screen, so the
                        // two appear together instead of the overlay flashing in first (#4954).
                        view.registerListener(
                            object : RiveFileController.Listener {
                                override fun notifyPlay(animation: PlayableInstance) {}

                                override fun notifyPause(animation: PlayableInstance) {}

                                override fun notifyStop(animation: PlayableInstance) {}

                                override fun notifyLoop(animation: PlayableInstance) {}

                                override fun notifyStateChanged(
                                    stateMachineName: String,
                                    stateName: String,
                                ) {}

                                override fun notifyAdvance(elapsedTime: Float) {
                                    currentOnFirstFrame.value()
                                    view.unregisterListener(this)
                                }
                            }
                        )
                    }
                },
                update = { view -> onInit(view) },
            )
        }
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
    if (LocalInspectionMode.current || !isRiveInitialized) {
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
    if (!isRiveInitialized) {
        return remember { mutableStateOf(null) }
    }

    val riveWorker = rememberRiveWorkerOrNull()

    val riveFileResult =
        riveWorker?.let { rememberRiveFile(RiveFileSource.RawRes.from(resId), riveWorker) }

    return remember(riveFileResult) {
        derivedStateOf { (riveFileResult as? Result.Success)?.value }
    }
}
