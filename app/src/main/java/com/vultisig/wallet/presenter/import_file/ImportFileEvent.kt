package com.vultisig.wallet.presenter.import_file

import android.net.Uri

sealed class ImportFileEvent {
    data class FileSelected(val uri: Uri?) : ImportFileEvent()
    data class FileNameFetched(val fileName: String) : ImportFileEvent()
    data class FileContentFetched(val fileContent: String?) : ImportFileEvent()
    data object OnContinueClick : ImportFileEvent()
    data object RemoveSelectedFile : ImportFileEvent()
}