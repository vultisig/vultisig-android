package com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.models.Chain
import javax.inject.Inject

/**
 * Fetches the Bitcoin UTXOs for an address via the shared Blockchair client and maps them to
 * [ClaimableUtxo]. Malformed rows (empty txid, negative index, or negative value) are dropped.
 * Bitcoin doesn't know about QBTC claims, so these are raw candidates — cross-check them with
 * [QbtcClaimChainService.filterClaimable] before presenting them as claimable.
 */
internal interface QbtcClaimableUtxosService {
    suspend fun fetchClaimableCandidates(btcAddress: String): List<ClaimableUtxo>
}

internal class QbtcClaimableUtxosServiceImpl
@Inject
constructor(private val blockChairApi: BlockChairApi) : QbtcClaimableUtxosService {

    override suspend fun fetchClaimableCandidates(btcAddress: String): List<ClaimableUtxo> {
        val info = blockChairApi.getAddressInfo(Chain.Bitcoin, btcAddress)
        val currentBlockHeight = info?.currentBlockHeight
        return info
            ?.utxos
            .orEmpty()
            .filter { it.transactionHash.isNotEmpty() && it.index >= 0 && it.value >= 0 }
            .map { utxo ->
                val blockId = utxo.blockId.takeIf { it > 0 }?.toLong()
                ClaimableUtxo(
                    txid = utxo.transactionHash,
                    vout = utxo.index,
                    amount = utxo.value,
                    confirmations =
                        if (blockId != null && currentBlockHeight != null) {
                            (currentBlockHeight - blockId + 1).coerceAtLeast(0)
                        } else {
                            null
                        },
                )
            }
    }
}
