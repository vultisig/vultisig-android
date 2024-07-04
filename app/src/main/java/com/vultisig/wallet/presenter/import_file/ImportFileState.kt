package com.vultisig.wallet.presenter.import_file

import android.net.Uri
import com.vultisig.wallet.common.UiText

internal data class ImportFileState(
    val fileUri: Uri? = null,
    val fileName: String? = null,
    val fileContent: String? = null,
    val showPasswordPrompt: Boolean = false,
    val password: String? = null,
    val isPasswordObfuscated: Boolean = true,
    val passwordErrorHint: UiText? = null,
)