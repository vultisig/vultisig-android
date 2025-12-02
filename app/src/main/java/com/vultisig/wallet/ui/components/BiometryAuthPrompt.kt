package com.vultisig.wallet.ui.components

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.closestActivityOrNull
import timber.log.Timber

private val allowedAuthenticatorTypes
    get() = BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL

@Composable
internal fun BiometryAuthScreen(
) {
    // skip authorization in debug mode
    var isAuthorized by rememberSaveable { mutableStateOf(BuildConfig.DEBUG) }

    if (!isAuthorized) {
        Timber.d("Unauthorized, checking biometric availability")

        val context = LocalContext.current

        if (context.canAuthenticateBiometric()) {
            Timber.d("Biometric auth available, launching prompt")

            val promptTitle = stringResource(R.string.biometry_auth_login_button)

            val authorize: () -> Unit = remember(context) {
                {
                    context.launchBiometricPrompt(
                        promptTitle = promptTitle,
                        onAuthorizationSuccess = { isAuthorized = true },
                    )
                }
            }

            LaunchedEffect(Unit) {
                authorize()
            }

            BiometryAuthView(
                onRequestLogin = authorize,
            )
        } else {
            isAuthorized = true
        }
    }
}

@Composable
private fun BiometryAuthView(
    onRequestLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.v2.colors.backgrounds.secondary)
            .padding(all = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UiSpacer(weight = 1f)

        Image(
            painter = painterResource(id = R.drawable.vultisig),
            contentDescription = stringResource(id = R.string.app_name)
        )

        UiSpacer(size = 16.dp)

        Text(
            text = stringResource(id = R.string.app_name),
            color = Theme.v2.colors.neutrals.n50,
            textAlign = TextAlign.Center,
            style = Theme.montserrat.heading2,
        )

        UiSpacer(size = 32.dp)

        Text(
            text = stringResource(R.string.create_new_vault_screen_secure_crypto_vault),
            color = Theme.v2.colors.neutrals.n50,
            textAlign = TextAlign.Center,
            style = Theme.montserrat.subtitle1,
        )

        UiSpacer(weight = 1.5f)

        VsButton(
            label = stringResource(R.string.biometry_auth_login_button),
            onClick = onRequestLogin,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun Context.canAuthenticateBiometric(): Boolean = BiometricManager.from(this)
    .canAuthenticate(allowedAuthenticatorTypes) == BIOMETRIC_SUCCESS

internal fun Context.launchBiometricPrompt(
    promptTitle: String,
    onAuthorizationSuccess: () -> Unit,
) {
    Timber.d("launchBiometricPrompt")

    val activity = (closestActivityOrNull() as? FragmentActivity)
        ?: error("Context is not a FragmentActivity. Can't launch biometric prompt")

    activity.launchBiometricPrompt(
        title = promptTitle,
        onAuthorizationSuccess = onAuthorizationSuccess,
    )
}

private fun FragmentActivity.launchBiometricPrompt(
    title: String,
    onAuthorizationSuccess: () -> Unit,
) {
    val biometricPrompt = BiometricPrompt(this,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                super.onAuthenticationSucceeded(result)
                Timber.d("onAuthenticationSucceeded")
                onAuthorizationSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Timber.d("onAuthenticationError: $errString")
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Timber.d("onAuthenticationFailed")
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setAllowedAuthenticators(allowedAuthenticatorTypes)
        .setConfirmationRequired(false)
        .build()

    Timber.d("biometricPrompt::authenticate")
    biometricPrompt.authenticate(promptInfo)
}

@Preview
@Composable
private fun BiometryAuthScreenPreview() {
    BiometryAuthView(
        onRequestLogin = {}
    )
}