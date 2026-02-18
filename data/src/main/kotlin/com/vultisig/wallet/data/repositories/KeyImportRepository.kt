package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import javax.inject.Inject
import javax.inject.Singleton

enum class DerivationPath {
    Default, Phantom
}

data class ChainImportSetting(
    val chain: Chain,
    val derivationPath: DerivationPath = DerivationPath.Default,
)

data class KeyImportData(
    val mnemonic: String,
    val chainSettings: List<ChainImportSetting> = emptyList(),
)

interface KeyImportRepository {
    fun get(): KeyImportData?
    fun setMnemonic(mnemonic: String)
    fun setChainSettings(settings: List<ChainImportSetting>)
    fun clear()
}

@Singleton
internal class KeyImportRepositoryImpl @Inject constructor() : KeyImportRepository {

    private var data: KeyImportData? = null

    override fun get(): KeyImportData? = data

    override fun setMnemonic(mnemonic: String) {
        data = KeyImportData(mnemonic = mnemonic)
    }

    override fun setChainSettings(settings: List<ChainImportSetting>) {
        data = data?.copy(chainSettings = settings)
    }

    override fun clear() {
        data = null
    }
}
