package com.vultisig.wallet.presenter.import_file

import android.net.Uri

sealed class ImportFileUiEvent {
    data class FetchFileName(val uri: Uri) : ImportFileUiEvent()
    data class CopyFileToAppDir(val uri: Uri) : ImportFileUiEvent()
}