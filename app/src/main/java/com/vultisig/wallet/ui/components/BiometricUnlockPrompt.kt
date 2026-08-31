package com.vultisig.wallet.ui.components

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.utils.closestActivityOrNull
import javax.crypto.Cipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Whether this device can hold a biometry-guarded copy of the passcode's data key right now.
 *
 * The reason is worth carrying rather than collapsing to a boolean, because the two failures need
 * opposite things from the user: nothing enrolled is fixed in device settings in ten seconds, no
 * hardware is not fixable at all, and a switch that can only ever refuse is worse than no switch.
 */
internal enum class BiometricUnlockAvailability {
    Available,
    NotEnrolled,
    Unavailable,
}

/**
 * Asks about strong biometrics only.
 *
 * The device credential is deliberately not in the set. The passcode exists to keep someone who
 * knows the device PIN out of the wallet, so accepting that PIN as a shortcut past it would hand
 * back exactly what the feature was for. The keystore key is minted with the same restriction, so
 * this is the question that matches what the hardware will actually accept.
 */
internal fun Context.biometricUnlockAvailability(): BiometricUnlockAvailability =
    when (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG)) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricUnlockAvailability.Available
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricUnlockAvailability.NotEnrolled
        else -> BiometricUnlockAvailability.Unavailable
    }

/**
 * Runs the system biometric prompt over a cipher the keystore will only release on a match.
 *
 * A seam rather than a direct call so the view models that sequence enable and unlock stay free of
 * an activity, and can be tested with a launcher that answers without any hardware.
 */
internal fun interface BiometricUnlockLauncher {
    /**
     * Shows the prompt for [cipher] and returns the authorised cipher once the hardware releases
     * the key, or null when the user dismissed it or the prompt could not be shown.
     */
    suspend fun authenticate(cipher: Cipher): Cipher?
}

/** A launcher bound to the current activity, titled for unlocking the app. */
@Composable
internal fun rememberBiometricUnlockLauncher(): BiometricUnlockLauncher {
    val context = LocalContext.current
    val title = stringResource(R.string.passcode_biometric_prompt_title)
    val negativeButton = stringResource(R.string.passcode_biometric_prompt_cancel)
    return remember(context, title, negativeButton) {
        BiometricUnlockLauncher { cipher ->
            context.authenticateForUnlock(
                title = title,
                negativeButton = negativeButton,
                cipher = cipher,
            )
        }
    }
}

private suspend fun Context.authenticateForUnlock(
    title: String,
    negativeButton: String,
    cipher: Cipher,
): Cipher? {
    val activity = closestActivityOrNull() as? FragmentActivity
    if (activity == null) {
        // Reported rather than thrown: the passcode field is on screen either way, so a prompt
        // that cannot be shown costs the shortcut, not the unlock.
        Timber.e("Context is not a FragmentActivity; cannot show the biometric prompt")
        return null
    }

    // BiometricPrompt must be built and started on the main thread, and the caller is a view model
    // coroutine that may be anywhere.
    return withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val prompt =
                BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult
                        ) {
                            // The cipher the result carries, not the one handed in: only that
                            // instance holds the authorisation the keystore has just granted.
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.success(result.cryptoObject?.cipher))
                            }
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            // Covers the cancel button, a back press, and a hardware lockout
                            // alike. All of them mean the same thing here — no key — and the user
                            // has just watched it happen, so none of them is worth a message.
                            Timber.d("Biometric unlock ended: %s", errString)
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.success(null))
                            }
                        }

                        // onAuthenticationFailed is a finger that did not match. The prompt stays
                        // up for another try, so there is nothing to resume with yet.
                    },
                )

            continuation.invokeOnCancellation { prompt.cancelAuthentication() }

            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setNegativeButtonText(negativeButton)
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .setConfirmationRequired(false)
                    .build(),
                BiometricPrompt.CryptoObject(cipher),
            )
        }
    }
}
