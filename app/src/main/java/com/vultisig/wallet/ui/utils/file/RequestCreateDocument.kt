package com.vultisig.wallet.ui.utils.file

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * @param createDocumentRequestFlow should emit file name to launch document creation dialog
 */
@Composable
internal fun RequestCreateDocument(
    mimeType: String,
    onDocumentCreated: (uri: Uri, mimeType: String) -> Unit,
    createDocumentRequestFlow: Flow<String>,
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            mimeType = mimeType,
        )
    ) { result ->
        result?.let { uri ->
            onDocumentCreated(uri, mimeType)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(
        createDocumentRequestFlow,
        lifecycleOwner.lifecycle
    ) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            createDocumentRequestFlow.collect { fileName ->
                try {
                    filePickerLauncher.launch(fileName)
                } catch (e: android.content.ActivityNotFoundException) {
                    Timber.w("No activity found to handle CreateDocument ,${e}")
                }
            }
        }
    }
}