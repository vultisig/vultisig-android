package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.BittensorApi
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CardanoApi
import com.vultisig.wallet.data.api.CardanoTransactionAlreadyBroadcastException
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.models.BlockChainStatusDeserialized
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Chain.Akash
import com.vultisig.wallet.data.models.Chain.Arbitrum
import com.vultisig.wallet.data.models.Chain.Avalanche
import com.vultisig.wallet.data.models.Chain.Base
import com.vultisig.wallet.data.models.Chain.Bitcoin
import com.vultisig.wallet.data.models.Chain.BitcoinCash
import com.vultisig.wallet.data.models.Chain.Blast
import com.vultisig.wallet.data.models.Chain.BscChain
import com.vultisig.wallet.data.models.Chain.CronosChain
import com.vultisig.wallet.data.models.Chain.Dash
import com.vultisig.wallet.data.models.Chain.Dogecoin
import com.vultisig.wallet.data.models.Chain.Dydx
import com.vultisig.wallet.data.models.Chain.Ethereum
import com.vultisig.wallet.data.models.Chain.GaiaChain
import com.vultisig.wallet.data.models.Chain.Kujira
import com.vultisig.wallet.data.models.Chain.Litecoin
import com.vultisig.wallet.data.models.Chain.Mantle
import com.vultisig.wallet.data.models.Chain.MayaChain
import com.vultisig.wallet.data.models.Chain.Noble
import com.vultisig.wallet.data.models.Chain.Optimism
import com.vultisig.wallet.data.models.Chain.Osmosis
import com.vultisig.wallet.data.models.Chain.Polkadot
import com.vultisig.wallet.data.models.Chain.Polygon
import com.vultisig.wallet.data.models.Chain.Ripple
import com.vultisig.wallet.data.models.Chain.Sei
import com.vultisig.wallet.data.models.Chain.Solana
import com.vultisig.wallet.data.models.Chain.Sui
import com.vultisig.wallet.data.models.Chain.Terra
import com.vultisig.wallet.data.models.Chain.TerraClassic
import com.vultisig.wallet.data.models.Chain.ThorChain
import com.vultisig.wallet.data.models.Chain.Ton
import com.vultisig.wallet.data.models.Chain.ZkSync
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber

fun interface BroadcastTxUseCase {
    suspend operator fun invoke(chain: Chain, tx: SignedTransactionResult): String?
}

internal class BroadcastTxUseCaseImpl
@Inject
constructor(
    private val thorChainApi: ThorChainApi,
    private val evmApiFactory: EvmApiFactory,
    private val blockChairApi: BlockChairApi,
    private val mayaChainApi: MayaChainApi,
    private val cosmosApiFactory: CosmosApiFactory,
    private val solanaApi: SolanaApi,
    private val polkadotApi: PolkadotApi,
    private val bittensorApi: BittensorApi,
    private val suiApi: SuiApi,
    private val tonApi: TonApi,
    private val rippleApi: RippleApi,
    private val tronApi: TronApi,
    private val cardanoApi: CardanoApi,
    private val transactionStatusRepository: TransactionStatusRepository,
) : BroadcastTxUseCase {

    override suspend fun invoke(chain: Chain, tx: SignedTransactionResult) =
        when (chain) {
            // THOR/Maya are account-sequence Cosmos chains: on a joined-device duplicate-broadcast
            // race the loser's broadcast is rejected with a sequence mismatch. Confirm the peer's
            // byte-identical tx actually committed on chain before reporting our local hash as
            // success, mirroring every other chain's recover-if-already-broadcast path.
            ThorChain ->
                recoverIfAlreadyBroadcast(
                    tx = tx,
                    broadcast = {
                        thorChainApi.broadcastTransaction(tx.rawTransaction).orKnownHash(tx)
                    },
                    verify = { hash -> isLandedOnChain(hash, chain) },
                )

            Bitcoin,
            BitcoinCash,
            Litecoin,
            Dogecoin,
            Dash,
            Chain.Zcash ->
                recoverIfAlreadyBroadcast(
                    tx = tx,
                    broadcast = { blockChairApi.broadcastTransaction(chain, tx.rawTransaction) },
                    verify = { hash ->
                        val response = blockChairApi.getTsStatus(chain, hash)
                        (response as? BlockChainStatusDeserialized.Result)
                            ?.data
                            ?.data
                            ?.containsKey(hash) == true
                    },
                )

            Ethereum,
            CronosChain,
            Blast,
            BscChain,
            Avalanche,
            Mantle,
            Base,
            Polygon,
            Optimism,
            Arbitrum,
            ZkSync,
            Sei,
            Chain.Hyperliquid -> {
                val evmApi = evmApiFactory.createEvmApi(chain)
                evmApi.sendTransaction(tx.rawTransaction)
            }

            Solana ->
                recoverIfAlreadyBroadcast(
                    tx = tx,
                    broadcast = { solanaApi.broadcastTransaction(tx.rawTransaction) },
                    verify = { hash ->
                        solanaApi.checkStatus(hash)?.result?.value?.any { it != null } == true
                    },
                )

            GaiaChain,
            Kujira,
            Dydx,
            Osmosis,
            Terra,
            TerraClassic,
            Noble,
            Akash,
            Chain.Qbtc -> {
                val cosmosApi = cosmosApiFactory.createCosmosApi(chain)
                recoverIfAlreadyBroadcast(
                    tx = tx,
                    broadcast = {
                        cosmosApi.broadcastTransaction(tx.rawTransaction).orKnownHash(tx)
                    },
                    // A code=32 sequence mismatch on a joined co-signer means the peer's
                    // byte-identical tx already advanced the account sequence in a committed
                    // block. The LCD returns 404 (null) until our hash is committed. A committed tx
                    // can still have failed execution (non-zero code), so require code==0 — an
                    // included-but-failed tx moved no funds and must not be reported as success.
                    verify = { hash -> cosmosApi.getTxStatus(hash)?.txResponse?.code == 0 },
                )
            }

            MayaChain ->
                recoverIfAlreadyBroadcast(
                    tx = tx,
                    broadcast = {
                        mayaChainApi.broadcastTransaction(tx.rawTransaction).orKnownHash(tx)
                    },
                    verify = { hash -> isLandedOnChain(hash, chain) },
                )

            Polkadot ->
                recoverIfAlreadyBroadcast(
                    tx = tx,
                    broadcast = {
                        polkadotApi.broadcastTransaction(tx.rawTransaction).orKnownHash(tx)
                    },
                    verify = { hash -> polkadotApi.isExtrinsicInChain(hash, VERIFY_SCAN_DEPTH) },
                )

            // Mirrors Polkadot: a malformed / truncated RPC body now throws instead of resolving to
            // a fabricated success hash, so it must go through the recover path that verifies the
            // extrinsic is actually on-chain (via the Bittensor status provider) before reporting
            // success. A duplicate rebroadcast from a peer still resolves to the known hash.
            Chain.Bittensor ->
                recoverIfAlreadyBroadcast(
                    tx = tx,
                    broadcast = {
                        bittensorApi.broadcastTransaction(tx.rawTransaction).orKnownHash(tx)
                    },
                    verify = { hash -> isLandedOnChain(hash, chain) },
                )

            // Sui digest is not pre-computable from the raw transaction, so transactionHash
            // is always blank and recovery cannot work; broadcast directly.
            Sui -> suiApi.executeTransactionBlock(tx.rawTransaction, tx.signature ?: "")

            Ton ->
                recoverIfAlreadyBroadcast(
                    tx = tx,
                    broadcast = { tonApi.broadcastTransaction(tx.rawTransaction).orKnownHash(tx) },
                    verify = { hash -> tonApi.getTsStatus(hash).transactions.isNotEmpty() },
                )

            Ripple ->
                recoverIfAlreadyBroadcast(
                    tx = tx,
                    broadcast = { rippleApi.broadcastTransaction(tx.rawTransaction) },
                    verify = { hash ->
                        rippleApi.getTsStatus(hash)?.result?.hash?.isNotBlank() == true
                    },
                )

            Chain.Tron ->
                recoverIfAlreadyBroadcast(
                    tx = tx,
                    broadcast = { tronApi.broadcastTransaction(tx.rawTransaction) },
                    verify = { hash ->
                        tronApi.getTsStatus(chain, hash)?.txId?.isNotBlank() == true
                    },
                )
            Chain.Cardano ->
                recoverIfAlreadyBroadcast(
                    tx = tx,
                    broadcast = {
                        try {
                            cardanoApi
                                .broadcastTransaction(chain.name, tx.rawTransaction)
                                .orKnownHash(tx)
                        } catch (e: CardanoTransactionAlreadyBroadcastException) {
                            // Ogmios says the inputs are already spent by the peer's byte-identical
                            // tx, so our locally computed hash is canonical. A Cardano block takes
                            // ~20s, far longer than the verify window, so this node signal is the
                            // only timely proof the tx landed (issue #5337).
                            tx.transactionHash.takeIf { it.isNotBlank() } ?: throw e
                        }
                    },
                    // Koios tx_status echoes the queried hash back even for unknown txs, so
                    // txHash is never blank; only a positive confirmation count proves it landed.
                    verify = { hash -> (cardanoApi.getTxStatus(hash)?.numConfirmations ?: 0) > 0 },
                )
        }

    private suspend fun recoverIfAlreadyBroadcast(
        tx: SignedTransactionResult,
        broadcast: suspend () -> String?,
        verify: suspend (String) -> Boolean,
    ): String? =
        try {
            broadcast()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val hash = tx.transactionHash
            if (hash.isNotBlank() && isAlreadyOnChain(hash, verify)) {
                Timber.d(
                    "broadcast failed but tx %s is already on-chain; treating as success",
                    hash,
                )
                hash
            } else {
                throw e
            }
        }

    // HTTP-client retry and this loop handle distinct failure modes, so they don't multiply.
    private suspend fun isAlreadyOnChain(
        hash: String,
        verify: suspend (String) -> Boolean,
    ): Boolean {
        repeat(VERIFY_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(VERIFY_BACKOFF_MS)
            try {
                if (verify(hash)) return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(
                    e,
                    "tx %s verify attempt %d/%d failed; retrying",
                    hash,
                    attempt + 1,
                    VERIFY_ATTEMPTS,
                )
            }
        }
        return false
    }

    /**
     * For duplicate-broadcast recovery we only need to know that the peer's byte-identical tx
     * actually committed — not whether it succeeded. Any terminal on-chain outcome (confirmed,
     * network-refunded, or failed execution) proves the sequence was consumed by our tx, so the
     * locally computed hash is canonical. Only a still-pending / not-found / timed-out result means
     * our tx isn't on chain yet, in which case the rejection must propagate rather than have the
     * user re-send a landed transaction.
     */
    private suspend fun isLandedOnChain(hash: String, chain: Chain): Boolean =
        when (transactionStatusRepository.checkTransactionStatus(hash, chain)) {
            is TransactionResult.Confirmed,
            is TransactionResult.Refunded,
            is TransactionResult.Failed -> true
            is TransactionResult.Pending,
            is TransactionResult.NotFound,
            is TransactionResult.TimedOut -> false
        }

    private fun String?.orKnownHash(tx: SignedTransactionResult): String? =
        this ?: tx.transactionHash.takeIf { it.isNotBlank() }

    private companion object {
        const val VERIFY_ATTEMPTS = 3
        const val VERIFY_BACKOFF_MS = 2_000L
        // The duplicate-broadcast peer raced us moments ago, so its extrinsic is at the head; a
        // shallow scan is enough to confirm it landed without re-fetching deep history each
        // attempt.
        const val VERIFY_SCAN_DEPTH = 5
    }
}
