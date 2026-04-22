@file:OptIn(ExperimentalAnimationApi::class)

package com.vultisig.wallet.app.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.vultisig.wallet.app.activity.components.AnimatedSplash
import com.vultisig.wallet.app.activity.components.CheckDeeplink
import com.vultisig.wallet.app.activity.components.MainActivityContent
import com.vultisig.wallet.data.repositories.PreventScreenshotsRepository
import com.vultisig.wallet.data.services.VultisigFirebaseMessagingService
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.theme.v2.V2.colors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels<MainViewModel>()

    @Inject lateinit var appUpdateManager: AppUpdateManager

    @Inject lateinit var preventScreenshotsRepository: PreventScreenshotsRepository

    private val pushNotificationReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val qrCodeData =
                    intent.getStringExtra(VultisigFirebaseMessagingService.QR_CODE_DATA) ?: return
                mainViewModel.onForegroundPushReceived(qrCodeData)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Workaround for Android 8.0 (API 26) crash: "Only fullscreen opaque activities can
        // request orientation". Theme.SplashScreen sets windowIsTranslucent=true on API 26,
        // which conflicts with screenOrientation in the manifest. Setting it programmatically
        // here avoids the conflict; API 26 is skipped as it would still crash.
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { false }
        super.onCreate(savedInstanceState)

        // Handle notification tap when app was killed — ViewModel awaits navigation readiness.
        intent?.getStringExtra(VultisigFirebaseMessagingService.QR_CODE_DATA)?.let {
            mainViewModel.onPushNotificationReceived(it)
        }

        val systemBarStyle =
            SystemBarStyle.auto(
                colors.backgrounds.primary.toArgb(),
                colors.backgrounds.primary.toArgb(),
            ) {
                true
            }

        enableEdgeToEdge(statusBarStyle = systemBarStyle, navigationBarStyle = systemBarStyle)

        observePreventScreenshots()

        setContent {
            OnBoardingComposeTheme {
                val screen by mainViewModel.startDestination.collectAsStateWithLifecycle()

                val navController = rememberNavController()

                val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    AnimatedSplash(isLoading = isLoading, onSplashComplete = { showSplash = false })
                } else {
                    var isNavigationReady by remember { mutableStateOf(false) }

                    if (isNavigationReady) {
                        CheckDeeplink(mainViewModel::openUri)
                    }

                    CheckUpdates()

                    MainActivityContent(
                        navController = navController,
                        mainViewModel = mainViewModel,
                        startDestination = screen,
                        onNavigationReady = { isNavigationReady = true },
                    )
                }
            }
        }
    }

    @Composable
    private fun CheckUpdates() {
        val lifecycle = LocalLifecycleOwner.current.lifecycle

        LaunchedEffect(Unit) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.startUpdateEvent.collect { startImmediateUpdate() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val qrCodeData =
            intent.getStringExtra(VultisigFirebaseMessagingService.QR_CODE_DATA) ?: return
        mainViewModel.onPushNotificationReceived(qrCodeData)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(VultisigFirebaseMessagingService.PUSH_NOTIFICATION_ACTION)
        ContextCompat.registerReceiver(
            this,
            pushNotificationReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(pushNotificationReceiver)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Receiver was not registered")
        }
    }

    private fun observePreventScreenshots() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                preventScreenshotsRepository.isEnabled.collect { isEnabled ->
                    if (isEnabled) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
    }

    private fun startImmediateUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            try {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    this,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE)
                        .setAllowAssetPackDeletion(true)
                        .build(),
                    0,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to start update flow")
            }
        }
    }
}
