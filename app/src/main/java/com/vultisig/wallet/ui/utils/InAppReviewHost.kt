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
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import com.vultisig.wallet.data.repositories.InAppReviewRepository
import com.vultisig.wallet.data.utils.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
        viewModelScope.launch {
            runCatchingCancellable { inAppReviewRepository.onPromptRequested() }
        }
    }
}

/**
 * Hosts the Play in-app review card above the navigation graph.
 *
 * The moments worth asking at — a vault created, a transaction gone through — only record that they
 * happened; the card is presented from here. Each of them routes on within a couple of seconds, so
 * an effect owned by the screen that earned the moment would be cancelled before the card was due.
 *
 * Whether the card then actually appears is entirely Play's decision: its quota is opaque and the
 * flow no-ops silently when spent or when Play services are missing, so the request is
 * fire-and-forget and the throttling lives in [InAppReviewRepository].
 *
 * Composed only while the passcode gate is open — see the call site in `MainActivityContent`.
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

        // Asks Play for the token only. Nothing is drawn and nothing is spent until it is launched.
        val reviewInfo = reviewManager.requestReviewFlow().awaitOrNull() ?: return@LaunchedEffect

        // An ask is spent whether or not Play shows anything, so it must never be spent while the
        // app is in the background. Suspending rather than bailing out means a prompt that came due
        // behind a backgrounded app is presented when the user returns, not on some later launch.
        lifecycle.withResumed {
            viewModel.onPromptRequested()
            reviewManager.launchReviewFlow(activity, reviewInfo)
        }
    }
}

/**
 * Suspends for this task's result, or null if the request failed.
 *
 * Suspending rather than listening keeps what follows inside the effect's scope: a card that comes
 * due just as the app locks or leaves composition is then never launched, where a completion
 * listener would fire and launch it whatever became of the coroutine that registered it.
 */
private suspend fun Task<ReviewInfo>.awaitOrNull(): ReviewInfo? =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                @ReviewErrorCode val errorCode = (task.exception as? ReviewException)?.errorCode
                Timber.e("In-app review: request failed with %s", errorCode)
                continuation.resume(null)
                return@addOnCompleteListener
            }
            continuation.resume(task.result)
        }
    }
