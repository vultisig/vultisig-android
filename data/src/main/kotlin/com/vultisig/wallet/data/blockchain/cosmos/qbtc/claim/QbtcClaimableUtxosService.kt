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

    override suspend fun fetchClaimableCandidates(btcAddress: String): List<ClaimableUtxo> =
        blockChairApi.getAddressInfo(Chain.Bitcoin, btcAddress)?.utxos.orEmpty().mapNotNull { utxo
            ->
            if (utxo.transactionHash.isEmpty() || utxo.index < 0 || utxo.value < 0) {
                null
            } else {
                ClaimableUtxo(
                    txid = utxo.transactionHash,
                    vout = utxo.index,
                    amount = utxo.value,
                    blockHeight = utxo.blockId.takeIf { it > 0 }?.toLong(),
                )
            }
        }
}
