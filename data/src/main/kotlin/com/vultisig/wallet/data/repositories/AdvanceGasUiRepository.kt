package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdvanceGasUiRepository @Inject constructor() {

    private val blockChainSpecific = MutableStateFlow<BlockChainSpecific?>(null)
    private val tokenStandard = MutableStateFlow<TokenStandard?>(null)
    private val isEnabled = MutableStateFlow(true)

    val showSettings = MutableStateFlow(false)

    val shouldShowAdvanceGasSettingsIcon = combine(
        blockChainSpecific.filterNotNull(),
        tokenStandard.filterNotNull(),
        isEnabled
    ) { blockChainSpecific, tokenStandard, isEnabled ->
        isEnabled &&
                blockChainSpecific is BlockChainSpecific.Ethereum &&
                tokenStandard == TokenStandard.EVM
    }

    fun updateBlockChainSpecific(blockChainSpecific: BlockChainSpecific) {
        this.blockChainSpecific.value = blockChainSpecific
    }

    fun updateTokenStandard(tokenStandard: TokenStandard) {
        this.tokenStandard.value = tokenStandard
    }

    fun showSettings() {
        showSettings.value = true
    }

    fun hideSettings() {
        showSettings.value = false
    }

    fun showIcon(){
        isEnabled.value = true
    }

    fun hideIcon() {
        isEnabled.value = false
    }

}
