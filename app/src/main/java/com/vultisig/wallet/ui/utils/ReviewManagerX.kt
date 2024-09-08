package com.vultisig.wallet.ui.utils

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.model.ReviewErrorCode
import timber.log.Timber

internal fun ReviewManager.showReviewPopUp(context: Context) {
    val request = requestReviewFlow()
    request.addOnCompleteListener { task ->
        if (task.isSuccessful) {
            // We got the ReviewInfo object
            val reviewInfo = task.result
            launchReviewFlow(context as Activity, reviewInfo)
        } else {
            // There was some problem, log or handle the error code.
            @ReviewErrorCode val reviewErrorCode = (task.getException() as ReviewException).errorCode
            Timber.e("Error: $reviewErrorCode")
        }
    }
}