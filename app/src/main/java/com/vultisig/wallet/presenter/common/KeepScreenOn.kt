package com.vultisig.wallet.presenter.common

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val observer = remember {
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                Lifecycle.Event.ON_PAUSE -> activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else -> Unit
            }
        }
    }

    LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> lifecycleOwner.lifecycle.addObserver(observer)
            Lifecycle.Event.ON_STOP -> lifecycleOwner.lifecycle.removeObserver(observer)
            else -> Unit
        }
    }
}