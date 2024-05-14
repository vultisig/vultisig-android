package com.vultisig.wallet.presenter.settings.default_chains_setting

internal sealed class DefaultChainsSettingEvent {
    data object Initialize : DefaultChainsSettingEvent()
    data class ChangeChaneState(val chain: DefaultChain, val checked: Boolean) : DefaultChainsSettingEvent()
}