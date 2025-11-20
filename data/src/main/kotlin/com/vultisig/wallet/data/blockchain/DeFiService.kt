package com.vultisig.wallet.data.blockchain

import com.vultisig.wallet.data.blockchain.model.DeFiBalance

interface DeFiService {
    suspend fun getRemoteDeFiBalance(address: String): List<DeFiBalance>

    suspend fun getCacheDeFiBalance(address: String, vaultId: String): List<DeFiBalance>
}