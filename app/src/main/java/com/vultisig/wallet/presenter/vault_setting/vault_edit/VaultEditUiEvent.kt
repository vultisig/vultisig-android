package com.vultisig.wallet.presenter.vault_setting.vault_edit

import com.vultisig.wallet.common.UiText


sealed class VaultEditUiEvent {
    data class ShowSnackBar(val message: UiText) : VaultEditUiEvent()
}