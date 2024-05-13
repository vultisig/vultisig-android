package com.vultisig.wallet.presenter.settings.default_chains_setting

import com.vultisig.wallet.ui.models.ChainUiModel

internal sealed class DefaultChainsSettingEvent {
    data object Initialize : DefaultChainsSettingEvent()
    data class UpdateItem(val chain: ChainUiModel,val checked: Boolean) : DefaultChainsSettingEvent()
}