package com.vultisig.wallet.presenter.import_file

import android.net.Uri
import com.vultisig.wallet.common.UiText

sealed class ImportFileEvent {
    data class FileSelected(val uri: Uri?) : ImportFileEvent()
    data object OnContinueClick : ImportFileEvent()
    data object RemoveSelectedFile : ImportFileEvent()
}