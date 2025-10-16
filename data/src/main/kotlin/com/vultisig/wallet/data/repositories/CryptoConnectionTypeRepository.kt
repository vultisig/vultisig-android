package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.CryptoConnectionType
import javax.inject.Inject

interface CryptoConnectionTypeRepository {
    val activeCryptoConnection: CryptoConnectionType
    fun setActiveCryptoConnection(cryptoConnectionType: CryptoConnectionType)
    fun isDefi(chain: Chain) : Boolean
}

internal class CryptoConnectionTypeRepositoryImpl @Inject constructor() : CryptoConnectionTypeRepository {

    private var cryptoConnection: CryptoConnectionType = CryptoConnectionType.Wallet
    override val activeCryptoConnection: CryptoConnectionType
        get() = cryptoConnection

    override fun setActiveCryptoConnection(cryptoConnectionType: CryptoConnectionType) {
        cryptoConnection = cryptoConnectionType
    }

    override fun isDefi(chain: Chain) = when (chain) {
        Chain.Ethereum,
        Chain.Arbitrum,
        Chain.Base,
        Chain.Optimism,
        Chain.Polygon,
        Chain.Avalanche,
        Chain.BscChain -> true
        else -> false
    }
}


