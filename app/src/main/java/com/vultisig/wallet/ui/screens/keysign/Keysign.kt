package com.vultisig.wallet.ui.screens.keysign

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.createBitmap
import app.rive.Fit
import app.rive.ImageAsset
import app.rive.Result
import app.rive.ViewModelSource
import app.rive.rememberViewModelInstance
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.payload.DAppMetadata
import com.vultisig.wallet.ui.components.KeepScreenOn
import com.vultisig.wallet.ui.components.loader.VsSigningProgressIndicator
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.rive.rememberRiveResourceFile
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import com.vultisig.wallet.ui.models.keysign.KeysignState
import com.vultisig.wallet.ui.models.keysign.TransactionStatus
import com.vultisig.wallet.ui.models.keysign.TransactionTypeUiModel
import com.vultisig.wallet.ui.models.keysign.progress
import com.vultisig.wallet.ui.screens.TransactionDoneView
import com.vultisig.wallet.ui.screens.transaction.SendTxOverviewScreen
import com.vultisig.wallet.ui.screens.transaction.SwapTransactionOverviewScreen
import com.vultisig.wallet.ui.screens.transaction.toUiTransactionInfo
import com.vultisig.wallet.ui.utils.VsUriHandler
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val RIVE_PROGRESS_PROPERTY = "progessPercentage" // typo in riv_keysign.riv
private const val RIVE_TO_TOKEN_IMAGE_PROPERTY = "toToken"

// Coarse phase keys for the top-level AnimatedContent. All in-progress signing sub-states share the
// "progress" key so the Rive progress animation keeps running instead of restarting on each tick;
// crossing into "finished" or "error" is what triggers the fade transition.
private const val KEYSIGN_PHASE_PROGRESS = "progress"
private const val KEYSIGN_PHASE_FINISHED = "finished"
private const val KEYSIGN_PHASE_ERROR = "error"

// The toToken logo only occupies a small slot in the animation, so cap the rasterized size.
// Rive decodes the bytes we hand it via ImageDecoder.decodeToBitmap, which copies the image into
// an IntArray (width * height ints) on the Java heap. A drawable with large/abnormal intrinsic
// bounds would balloon that allocation past the heap limit and throw an OutOfMemoryError — which
// Rive's native decode path does not catch (it only catches Exception), so it aborts the whole
// process. Bounding the size keeps that allocation tiny and well within the heap.
internal const val RIVE_TO_TOKEN_IMAGE_MAX_PX = 256

@Composable
internal fun KeysignView(
    state: KeysignState,
    txHash: String,
    approveTransactionHash: String,
    transactionLink: String,
    approveTransactionLink: String,
    onComplete: () -> Unit,
    onAddToAddressBook: () -> Unit,
    onBack: () -> Unit = {},
    progressLink: String?,
    transactionTypeUiModel: TransactionTypeUiModel?,
    showToolbar: Boolean = false,
    hasBackClick: Boolean,
    showSaveToAddressBook: Boolean,
    dappMetadata: DAppMetadata? = null,
    @DrawableRes coinLogoRes: Int? = null,
) {
    // Block system back while signing/broadcasting is in progress. Popping the nav entry here
    // cancels the ViewModel's coroutine scope mid-broadcast, before a terminal state lands, and a
    // force-retry could double-send. Back is re-enabled once the flow reaches a finished/error
    // state.
    BackHandler(enabled = state.isInProgress) {}
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.isInProgress) {
            KeepScreenOn()
        }
        AnimatedContent(
            targetState = state,
            // Only animate when crossing between the coarse phases (in progress ↔ finished ↔
            // error). Signing sub-states map to the same "progress" key so the Rive progress
            // animation stays mounted and keeps running instead of restarting on each tick.
            contentKey = { current ->
                when (current) {
                    is KeysignState.KeysignFinished -> KEYSIGN_PHASE_FINISHED
                    is KeysignState.Error -> KEYSIGN_PHASE_ERROR
                    else -> KEYSIGN_PHASE_PROGRESS
                }
            },
            transitionSpec = {
                fadeIn(animationSpec = tween(durationMillis = 220)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 150))
            },
            label = "keysign_phase",
        ) { targetState ->
            when (targetState) {
                is KeysignState.KeysignFinished -> {
                    when (transactionTypeUiModel) {
                        is TransactionTypeUiModel.Swap -> {
                            var isTransactionDetailVisible by remember { mutableStateOf(false) }
                            SwapTransactionOverviewScreen(
                                showToolbar = showToolbar,
                                transactionHash = txHash,
                                approveTransactionHash = approveTransactionHash,
                                transactionLink = transactionLink,
                                transactionStatus = targetState.transactionStatus,
                                approveTransactionLink = approveTransactionLink,
                                onComplete = onComplete,
                                progressLink = progressLink,
                                onBack = onBack,
                                transactionTypeUiModel =
                                    transactionTypeUiModel.swapTransactionUiModel,
                                isTransactionDetailVisible = isTransactionDetailVisible,
                                onTransactionDetailVisibleChange = {
                                    isTransactionDetailVisible = it
                                },
                                dappMetadata = dappMetadata,
                            )
                        }
                        is TransactionTypeUiModel.Deposit,
                        is TransactionTypeUiModel.Send -> {
                            var isTransactionDetailVisible by remember { mutableStateOf(false) }
                            SendTxOverviewScreen(
                                transactionHash = txHash,
                                transactionLink = transactionLink,
                                onComplete = onComplete,
                                onBack = onBack,
                                transactionStatus = targetState.transactionStatus,
                                tx = transactionTypeUiModel.toUiTransactionInfo(),
                                showToolbar = showToolbar,
                                onAddToAddressBook = onAddToAddressBook,
                                showSaveToAddressBook = showSaveToAddressBook,
                                isTransactionDetailVisible = isTransactionDetailVisible,
                                onTransactionDetailVisibleChange = {
                                    isTransactionDetailVisible = it
                                },
                                dappMetadata = dappMetadata,
                            )
                        }
                        else -> {

                            val uriHandler = VsUriHandler()
                            TransactionDoneView(
                                transactionHash = txHash,
                                approveTransactionHash = approveTransactionHash,
                                transactionLink = transactionLink,
                                approveTransactionLink = approveTransactionLink,
                                onComplete = onComplete,
                                onBack = onBack,
                                transactionTypeUiModel = transactionTypeUiModel,
                                showToolbar = showToolbar,
                                onUriClick = uriHandler::openUri,
                            )
                        }
                    }
                }

                is KeysignState.Error -> {
                    KeysignErrorScreen(
                        errorMessage = targetState.errorMessage,
                        tryAgain = onBack,
                        onBack = onBack.takeIf { hasBackClick },
                    )
                }

                else -> {
                    KeysignRiveProgress(progress = targetState.progress, coinLogoRes = coinLogoRes)
                }
            }
        }
    }
}

@Composable
private fun KeysignRiveProgress(progress: Float, @DrawableRes coinLogoRes: Int?) {
    val riveFile = rememberRiveResourceFile(resId = R.raw.riv_keysign).value
    if (riveFile == null) {
        VsSigningProgressIndicator(text = stringResource(R.string.keysign_screen_preparing_vault))
        return
    }
    val vmi =
        rememberViewModelInstance(
            file = riveFile,
            source = ViewModelSource.Named("ViewModel").defaultInstance(),
        )

    val animatedValue by
        animateFloatAsState(
            targetValue = progress.times(100),
            animationSpec = tween(durationMillis = 300),
            label = "riv_progress_animation",
        )

    SideEffect { vmi.setNumber(RIVE_PROGRESS_PROPERTY, animatedValue) }

    val context = LocalContext.current
    var toTokenAsset by remember { mutableStateOf<ImageAsset?>(null) }

    LaunchedEffect(coinLogoRes, riveFile, vmi) {
        if (coinLogoRes == null) return@LaunchedEffect
        val bytes =
            withContext(Dispatchers.Default) { encodeDrawableAsPng(context, coinLogoRes) }
                ?: run {
                    Timber.w("Could not encode coin logo res %d for keysign animation", coinLogoRes)
                    return@LaunchedEffect
                }
        // ImageAsset handles are worker-scoped, and vmi.setImage runs on the file's
        // worker — decode on riveFile.riveWorker (the worker that owns the vmi), not a
        // separate rememberRiveWorkerOrNull() instance, or the handle is foreign and the
        // assignment silently no-ops.
        val result = ImageAsset.fromBytes(riveFile.riveWorker, bytes)
        if (result is Result.Success) {
            toTokenAsset = result.value
            vmi.setImage(RIVE_TO_TOKEN_IMAGE_PROPERTY, result.value)
        } else {
            Timber.w("Failed to load toToken image asset for res %d", coinLogoRes)
        }
    }
    DisposableEffect(toTokenAsset) {
        val assetToDispose = toTokenAsset
        onDispose { assetToDispose?.close() }
    }

    RiveAnimation(
        file = riveFile,
        viewModelInstance = vmi,
        modifier = Modifier.fillMaxSize(),
        fit = Fit.Cover(),
    )
}

/**
 * Rasterizes a (vector or raster) drawable resource to PNG bytes for Rive ImageAsset binding.
 *
 * The logo is drawn — aspect ratio preserved — centered onto a square canvas (see
 * [squareLogoLayout]) so Rive's square "toToken" image slot renders every coin at the same
 * proportion. Handing Rive a non-square PNG makes the slot stretch/fill it differently and the logo
 * renders oversized / distorted (issue #4755). The canvas side is bounded by
 * [RIVE_TO_TOKEN_IMAGE_MAX_PX] so the image we hand to Rive can never produce an oversized decode
 * allocation — see the constant for why an unbounded size crashes the process.
 */
private fun encodeDrawableAsPng(context: Context, @DrawableRes resId: Int): ByteArray? {
    val drawable = AppCompatResources.getDrawable(context, resId) ?: return null
    val layout = squareLogoLayout(drawable.intrinsicWidth, drawable.intrinsicHeight)
    return try {
        // Draw the aspect-preserved logo straight onto a transparent square canvas — a single
        // bitmap, no intermediate copy — so Rive's square "toToken" slot renders it without
        // distortion.
        val square = createBitmap(layout.canvasSize, layout.canvasSize)
        try {
            drawable.setBounds(
                layout.left,
                layout.top,
                layout.left + layout.logoWidth,
                layout.top + layout.logoHeight,
            )
            drawable.draw(Canvas(square))
            ByteArrayOutputStream().use { stream ->
                square.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        } finally {
            square.recycle()
        }
    } catch (e: Exception) {
        // Any step here can throw; the caller treats null as "no logo" and logs it. Never let this
        // escape into the LaunchedEffect coroutine and crash the screen.
        Timber.e(e, "Failed to encode coin logo res %d as PNG", resId)
        null
    }
}

/**
 * Placement of an aspect-preserved coin logo on a square canvas. The canvas is square so Rive's
 * square "toToken" slot renders the logo without distortion; the logo is centered within it
 * ([left], [top]) and the canvas side equals the logo's larger dimension, so a square logo gets no
 * padding while a non-square one is letterboxed with transparency.
 */
@VisibleForTesting
internal data class SquareLogoLayout(
    val canvasSize: Int,
    val logoWidth: Int,
    val logoHeight: Int,
    val left: Int,
    val top: Int,
) {
    val isSquare: Boolean
        get() = logoWidth == logoHeight
}

/**
 * Computes the [SquareLogoLayout] for a drawable's intrinsic bounds. The logo dimensions come from
 * [boundedLogoSize] (clamped to [RIVE_TO_TOKEN_IMAGE_MAX_PX], aspect preserved); the canvas side is
 * their max so the logo always fits centered with transparent padding.
 */
@VisibleForTesting
internal fun squareLogoLayout(intrinsicWidth: Int, intrinsicHeight: Int): SquareLogoLayout {
    val (width, height) = boundedLogoSize(intrinsicWidth, intrinsicHeight)
    val side = maxOf(width, height)
    return SquareLogoLayout(
        canvasSize = side,
        logoWidth = width,
        logoHeight = height,
        left = (side - width) / 2,
        top = (side - height) / 2,
    )
}

/**
 * Returns a (width, height) bounded by [RIVE_TO_TOKEN_IMAGE_MAX_PX], preserving aspect ratio. Falls
 * back to a square of the max size when intrinsic bounds are missing (vector drawables can report
 * -1) or already within the bound only the down-scale path applies.
 */
@VisibleForTesting
internal fun boundedLogoSize(intrinsicWidth: Int, intrinsicHeight: Int): Pair<Int, Int> {
    if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
        return RIVE_TO_TOKEN_IMAGE_MAX_PX to RIVE_TO_TOKEN_IMAGE_MAX_PX
    }
    val largest = maxOf(intrinsicWidth, intrinsicHeight)
    if (largest <= RIVE_TO_TOKEN_IMAGE_MAX_PX) {
        return intrinsicWidth to intrinsicHeight
    }
    val scale = RIVE_TO_TOKEN_IMAGE_MAX_PX.toFloat() / largest
    val width = (intrinsicWidth * scale).toInt().coerceAtLeast(1)
    val height = (intrinsicHeight * scale).toInt().coerceAtLeast(1)
    return width to height
}

@Preview
@Composable
private fun KeysignPreview() {
    KeysignView(
        state = KeysignState.KeysignFinished(TransactionStatus.Confirmed),
        progressLink = null,
        txHash = "0x1234567890",
        approveTransactionHash = "0x1234567890",
        transactionLink = "",
        approveTransactionLink = "",
        transactionTypeUiModel =
            TransactionTypeUiModel.Send(
                TransactionDetailsUiModel(
                    srcAddress = "0x1234567890",
                    dstAddress = "0x1234567890",
                    memo = "some memo",
                )
            ),
        onComplete = {},
        onAddToAddressBook = {},
        showSaveToAddressBook = true,
        hasBackClick = true,
    )
}
