package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.CryptoConnectionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

interface CryptoConnectionTypeRepository {
    val activeCryptoConnectionFlow: StateFlow<CryptoConnectionType>
    fun setActiveCryptoConnection(cryptoConnectionType: CryptoConnectionType)
    fun isDefi(chain: Chain) : Boolean
}

internal class CryptoConnectionTypeRepositoryImpl @Inject constructor() : CryptoConnectionTypeRepository {
    private val connection = MutableStateFlow(CryptoConnectionType.Wallet)

    override val activeCryptoConnectionFlow: StateFlow<CryptoConnectionType> =
        connection.asStateFlow()

    override fun setActiveCryptoConnection(cryptoConnectionType: CryptoConnectionType) {
        connection.value = cryptoConnectionType
    }

    override fun isDefi(chain: Chain) = when (chain) {
        Chain.Ethereum,
        Chain.Arbitrum,
        Chain.Base,
        Chain.Optimism,
        Chain.Polygon,
        Chain.Avalanche,
        Chain.ThorChain,
        Chain.BscChain -> true
        else -> false
    }
}


