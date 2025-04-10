package com.vultisig.wallet.ui.utils.file

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow

/**
 * @param createDocumentRequestFlow should emit file name to launch document creation dialog
 */
@Composable
internal fun RequestCreateDocument(
    mimeType: String,
    onDocumentCreated: (uri: Uri) -> Unit,
    createDocumentRequestFlow: Flow<String>,
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            mimeType = mimeType,
        )
    ) { result ->
        result?.let { uri ->
            onDocumentCreated(uri)
        }
    }

    LaunchedEffect(Unit) {
        createDocumentRequestFlow.collect { fileName ->
            filePickerLauncher.launch(fileName)
        }
    }
}