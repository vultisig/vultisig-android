package com.vultisig.wallet.ui.utils

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.withResumed
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import com.vultisig.wallet.data.repositories.InAppReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Gap between the moment that earned the card and the card itself, so it lands on a settled screen
 * rather than on top of the success animation the user is still watching.
 */
private val PROMPT_SETTLE_DELAY = 2.seconds

@HiltViewModel
internal class InAppReviewViewModel
@Inject
constructor(private val inAppReviewRepository: InAppReviewRepository) : ViewModel() {

    val isPromptPending: Flow<Boolean> = inAppReviewRepository.isPromptPending

    fun onPromptRequested() {
        viewModelScope.launch { inAppReviewRepository.onPromptRequested() }
    }
}

/**
 * Hosts the Play in-app review card at the app root.
 *
 * The moments worth asking at — a vault created, a transaction gone through — either navigate away
 * immediately or own a screen the card should not cover, so they only record the moment and this
 * host presents it. Whether the card actually appears is entirely Play's decision: its quota is
 * opaque and the flow no-ops silently when spent or when Play services are missing, so the request
 * is fire-and-forget and the throttling lives in [InAppReviewRepository].
 */
@Composable
internal fun InAppReviewHost(viewModel: InAppReviewViewModel = hiltViewModel()) {
    val activity = LocalActivity.current ?: return
    val reviewManager = remember(activity) { ReviewManagerFactory.create(activity) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isPromptPending by viewModel.isPromptPending.collectAsStateWithLifecycle(false)

    LaunchedEffect(isPromptPending, reviewManager) {
        if (!isPromptPending) return@LaunchedEffect

        delay(PROMPT_SETTLE_DELAY)

        // An ask is spent whether or not Play shows anything, so it must never be spent while the
        // app is in the background. Suspending rather than bailing out means a prompt that came due
        // behind a backgrounded app is presented when the user returns, not on some later launch.
        lifecycle
            .withResumed { reviewManager.requestReviewFlow() }
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    @ReviewErrorCode val errorCode = (task.exception as? ReviewException)?.errorCode
                    Timber.e("In-app review: request failed with %s", errorCode)
                    return@addOnCompleteListener
                }
                viewModel.onPromptRequested()
                reviewManager.launchReviewFlow(activity, task.result)
            }
    }
}
