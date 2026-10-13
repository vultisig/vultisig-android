package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.chains.helpers.LegacyRawTransaction
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
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
 * amount and pay the address this device derives from its own vault.
 *
 * Throws on any mismatch, and lets a failed fetch propagate: an input that can't be checked is not
 * signed.
 */
@Singleton
class VerifyUtxoInputAmountsUseCase
@Inject
constructor(
    private val blockChairApi: BlockChairApi,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) {

    // A transaction whose bytes hash to its txid can't change, so verified ones are kept.
    private val verified = ConcurrentHashMap<String, LegacyRawTransaction>()

    /** Throws unless every input in [utxos] is an output of [vault]'s own address on [chain]. */
    suspend operator fun invoke(vault: Vault, chain: Chain, utxos: List<UtxoInfo>) {
        if (chain !in AMOUNT_UNCOMMITTED_CHAINS || utxos.isEmpty()) return
        val (address, _) = chainAccountAddressRepository.getAddress(chain, vault)
        val vaultScript = BitcoinScript.lockScriptForAddress(address, chain.coinType).data()
        val limit = Semaphore(MAX_CONCURRENT_FETCHES)
        coroutineScope {
            utxos
                .groupBy { it.hash.lowercase().removePrefix("0x") }
                .map { (hash, spent) ->
                    async {
                        val transaction = limit.withPermit { verifiedTransaction(chain, hash) }
                        checkOutputs(transaction, hash, spent, vaultScript)
                    }
                }
                .awaitAll()
        }
    }

    private suspend fun verifiedTransaction(chain: Chain, hash: String): LegacyRawTransaction {
        val key = "$chain:$hash"
        verified[key]?.let {
            return it
        }
        val raw =
            checkNotNull(fetchWithRetry(chain, hash)) {
                "Couldn't fetch the transaction behind input $hash"
            }
        val transaction =
            checkNotNull(LegacyRawTransaction.parseOrNull(raw)) {
                "Couldn't read the transaction behind input $hash"
            }
        check(transaction.txid.equals(hash, ignoreCase = true)) {
            "The transaction served for input $hash doesn't hash to it"
        }
        if (verified.size >= MAX_CACHED_TRANSACTIONS) verified.clear()
        verified[key] = transaction
        return transaction
    }

    private suspend fun fetchWithRetry(chain: Chain, hash: String): String? {
        repeat(FETCH_ATTEMPTS - 1) { attempt ->
            try {
                return blockChairApi.getRawTransaction(chain, hash)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.d(e, "Raw transaction fetch failed, retrying")
                delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        return blockChairApi.getRawTransaction(chain, hash)
    }

    private fun checkOutputs(
        transaction: LegacyRawTransaction,
        hash: String,
        spent: List<UtxoInfo>,
        vaultScript: ByteArray,
    ) {
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
        const val MAX_CACHED_TRANSACTIONS = 500
        const val FETCH_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1_000L
    }
}
