package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.chains.helpers.LegacyRawTransaction
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.payload.UtxoInfo
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import wallet.core.jni.BitcoinScript

/**
 * Checks each input of a DOGE or DASH spend against the transaction that created it.
 *
 * Both chains sign with legacy `SIGHASH_ALL`, which does not commit to input amounts, so a UTXO
 * whose amount is understated — by the UTXO provider, or by the initiating device's payload — still
 * signs: the wallet plans change from the smaller figure, and the difference goes to the miner as
 * fee. (BTC/LTC's BIP143, BCH's FORKID and ZEC's ZIP-243 commit to amounts, so a wrong figure
 * there just yields an invalid signature.) Each outpoint's previous transaction is fetched and
 * hashed; its txid must equal the outpoint's, and the output at that index must carry the claimed
 * amount and pay the vault's own address.
 *
 * Throws on any mismatch, and lets a failed fetch propagate: an input that can't be checked is not
 * signed.
 */
class VerifyUtxoInputAmountsUseCase @Inject constructor(private val blockChairApi: BlockChairApi) {

    suspend operator fun invoke(coin: Coin, utxos: List<UtxoInfo>) {
        if (coin.chain !in AMOUNT_UNCOMMITTED_CHAINS || utxos.isEmpty()) return
        val vaultScript = BitcoinScript.lockScriptForAddress(coin.address, coin.chain.coinType).data()
        val limit = Semaphore(MAX_CONCURRENT_FETCHES)
        coroutineScope {
            utxos
                .groupBy { it.hash.lowercase() }
                .map { (hash, spent) ->
                    async { limit.withPermit { checkTransaction(coin.chain, hash, spent, vaultScript) } }
                }
                .awaitAll()
        }
    }

    private suspend fun checkTransaction(
        chain: Chain,
        hash: String,
        spent: List<UtxoInfo>,
        vaultScript: ByteArray,
    ) {
        val raw =
            checkNotNull(blockChairApi.getRawTransaction(chain, hash)) {
                "Couldn't fetch the transaction behind input $hash"
            }
        val transaction =
            checkNotNull(LegacyRawTransaction.parseOrNull(raw)) {
                "Couldn't read the transaction behind input $hash"
            }
        check(transaction.txid.equals(hash, ignoreCase = true)) {
            "The transaction served for input $hash doesn't hash to it"
        }
        for (utxo in spent) {
            val output =
                checkNotNull(transaction.outputs.getOrNull(utxo.index.toInt())) {
                    "Input $hash:${utxo.index} doesn't exist"
                }
            check(output.value == utxo.amount) {
                "Input $hash:${utxo.index} holds ${output.value}, not the ${utxo.amount} claimed"
            }
            check(output.script.contentEquals(vaultScript)) {
                "Input $hash:${utxo.index} doesn't belong to this vault"
            }
        }
    }

    private companion object {
        val AMOUNT_UNCOMMITTED_CHAINS = setOf(Chain.Dogecoin, Chain.Dash)
        const val MAX_CONCURRENT_FETCHES = 4
    }
}
