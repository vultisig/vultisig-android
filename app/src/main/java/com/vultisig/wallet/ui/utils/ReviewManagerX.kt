package com.vultisig.wallet.ui.utils

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import com.vultisig.wallet.data.repositories.InAppReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Delay between a success and the store card, so the prompt lands on a settled screen rather than
 * on top of the animation the user is still watching.
 */
private val PROMPT_SETTLE_DELAY = 2.seconds

@HiltViewModel
internal class InAppReviewViewModel
@Inject
constructor(private val inAppReviewRepository: InAppReviewRepository) : ViewModel() {

    val promptOpportunities: Flow<Unit> = inAppReviewRepository.promptOpportunities

    suspend fun claimReviewPrompt(): Boolean = inAppReviewRepository.claimReviewPrompt()
}

/**
 * Hosts the Play in-app review flow at the app root, so every success flow can record its own
 * positive moment without also owning a surface the store card may be presented over.
 *
 * Whether the dialog actually appears is entirely Play's decision — its quota is opaque and the
 * flow no-ops silently when exhausted or when Play services are unavailable — so the request is
 * fire-and-forget and all throttling happens upstream in [InAppReviewRepository].
 */
@Composable
internal fun InAppReviewHost(viewModel: InAppReviewViewModel = hiltViewModel()) {
    val activity = LocalActivity.current ?: return
    val reviewManager = remember(activity) { ReviewManagerFactory.create(activity) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(reviewManager) {
        // collectLatest so a second success arriving inside the settle window restarts the wait
        // instead of queueing a card behind the screen the user has already moved on from.
        viewModel.promptOpportunities.collectLatest {
            delay(PROMPT_SETTLE_DELAY)
            // A claim is spent whether or not Play shows anything, so never spend one while the
            // app is in the background. The events stay counted, and the next success re-asks.
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@collectLatest
            reviewManager.requestReviewFlow().addOnCompleteListener { task ->
                launch {
                    if (
                        task.isSuccessful &&
                            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    ) {
                        // Only claim the prompt if the activity is still resumed after the request
                        if (viewModel.claimReviewPrompt()) {
                            val reviewInfo = task.result
                            reviewManager.launchReviewFlow(activity, reviewInfo)
                        }
                    } else {
                        @ReviewErrorCode
                        val reviewErrorCode = (task.exception as? ReviewException)?.errorCode
                        Timber.e("ReviewError: %s", reviewErrorCode)
                    }
                }
            }
        }
    }
}
