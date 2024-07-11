package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.common.decodeContractDecimal
import com.vultisig.wallet.common.decodeContractString
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

internal interface FindCustomTokenRepository {
    val searchedToken: StateFlow<Coin?>
    suspend operator fun invoke(chain: Chain, contractAddress: String): Coin?
}

internal class FindCustomTokenRepositoryImpl @Inject constructor(
    private val evmApiFactory: EvmApiFactory,
) : FindCustomTokenRepository {

    private val _searchedToken = MutableStateFlow<Coin?>(null)
    override val searchedToken: StateFlow<Coin?> = _searchedToken.asStateFlow()

    override suspend operator fun invoke(
        chain: Chain,
        contractAddress: String
    ): Coin? {
        val rpcResponses = evmApiFactory.createEvmApi(chain)
            .findCustomToken(contractAddress)
        var priceProviderID = ""
        var ticker = ""
        var decimal = 0
        (rpcResponses.takeIf { it.isNotEmpty() } ?: return null).forEach {
            if (it.error != null) {
                return null
            }
            when (it.id) {
                1 -> priceProviderID = it.result.decodeContractString() ?: return null
                2 -> ticker = it.result.decodeContractString() ?: return null
                else -> decimal =
                    it.result.decodeContractDecimal().takeIf { dec -> dec != 0 } ?: return null
            }
        }
        val coin = Coin(
            chain = chain,
            ticker = ticker.uppercase(),
            logo = "",
            address = "",
            decimal = decimal,
            hexPublicKey = "",
            priceProviderID = priceProviderID,
            contractAddress = contractAddress,
            isNativeToken = false,
        )
        _searchedToken.update { coin }
        return coin
    }

}