package com.vultisig.wallet.ui.utils

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun WriteFilePermissionHandler(
    permissionFlow: Flow<Boolean>,
    onPermissionResult: (Boolean) -> Unit
) {
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onPermissionResult
    )

    LaunchedEffect(Unit) {
        permissionFlow.collectLatest {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}


@Composable
internal fun RequestWriteFilePermissionOnceIfNotGranted(
    onRequestPermissionResult: (Boolean) -> Unit
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        RequestPermissionOnceIfNotGranted(
            permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
            onRequestPermissionResult = onRequestPermissionResult,
        )
    }
}

@Composable
internal fun RequestPermissionOnceIfNotGranted(
    permission: String,
    onRequestPermissionResult: (Boolean) -> Unit,
) {
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onRequestPermissionResult
    )

    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(permission)
    }
}