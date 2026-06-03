package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.CryptoConnectionType
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface CryptoConnectionTypeRepository {
    val activeCryptoConnectionFlow: StateFlow<CryptoConnectionType>

    fun setActiveCryptoConnection(cryptoConnectionType: CryptoConnectionType)

    fun hasDeFiPositionsScreen(chain: Chain): Boolean
}

internal class CryptoConnectionTypeRepositoryImpl @Inject constructor() :
    CryptoConnectionTypeRepository {
    private val connection = MutableStateFlow(CryptoConnectionType.Wallet)

    override val activeCryptoConnectionFlow: StateFlow<CryptoConnectionType> =
        connection.asStateFlow()

    override fun setActiveCryptoConnection(cryptoConnectionType: CryptoConnectionType) {
        connection.value = cryptoConnectionType
    }

    override fun hasDeFiPositionsScreen(chain: Chain) =
        when (chain) {
            Chain.ThorChain,
            Chain.MayaChain,
            Chain.Tron,
            // Terra / TerraClassic surface a staking positions screen (delegate / undelegate /
            // redelegate / claim). Without these branches they're selectable in the "Select DeFi
            // chains" sheet (isDeFiSupported) but get filtered out of the DeFi portfolio list, so
            // the user never reaches the LUNA/LUNC staking view.
            Chain.Terra,
            Chain.TerraClassic -> true
            else -> false
        }
}
