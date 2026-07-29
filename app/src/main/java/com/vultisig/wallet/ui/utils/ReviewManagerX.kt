package com.vultisig.wallet.ui.utils

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * Launches the Play in-app review flow whenever [requests] emits.
 *
 * Whether the dialog actually appears is entirely Play's decision — its quota is opaque and the
 * flow no-ops silently when exhausted or when Play services are unavailable. Callers must therefore
 * treat every request as fire-and-forget and do their own throttling upstream.
 */
@Composable
internal fun InAppReviewEffect(requests: Flow<Unit>) {
    val activity = LocalActivity.current ?: return
    val reviewManager = remember(activity) { ReviewManagerFactory.create(activity) }
    LaunchedEffect(requests, reviewManager) {
        requests.collect { reviewManager.showReviewPopUp(activity) }
    }
}

internal fun ReviewManager.showReviewPopUp(activity: Activity) {
    val request = requestReviewFlow()
    request.addOnCompleteListener { task ->
        if (task.isSuccessful) {
            // We got the ReviewInfo object
            val reviewInfo = task.result
            launchReviewFlow(activity, reviewInfo)
        } else {
            // There was some problem, log or handle the error code.
            @ReviewErrorCode val reviewErrorCode = (task.exception as? ReviewException)?.errorCode
            Timber.e("ReviewError: $reviewErrorCode")
        }
    }
}
