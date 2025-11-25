package com.vultisig.wallet.data.blockchain

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

interface TierRemoteNFTService {
    suspend fun checkNFTBalance(address: String): Boolean
}

class TierRemoteNFTServiceImpl @Inject constructor(
    private val evmApiFactory: EvmApiFactory,
) : TierRemoteNFTService {

    companion object {
        private const val NFT_CONTRACT_ADDRESS = "0xa98b29a8f5a247802149c268ecf860b8308b7291"
    }

    override suspend fun checkNFTBalance(address: String): Boolean {
        return try {
            val factory = evmApiFactory.createEvmApi(Chain.Ethereum)
            val totalNFTs =
                factory.getERC20Balance(address, NFT_CONTRACT_ADDRESS)
            totalNFTs > BigInteger.ZERO
        } catch (e: Exception) {
            Timber.e(e, "Failed to check NFT balance")
            false
        }
    }
}