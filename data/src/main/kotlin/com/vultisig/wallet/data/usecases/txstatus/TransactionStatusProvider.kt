package com.vultisig.wallet.data.usecases.txstatus

import com.vultisig.wallet.data.api.txstatus.NearStatusProvider
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import javax.inject.Inject

sealed class TransactionResult {
    data object Confirmed : TransactionResult()

    data object Pending : TransactionResult()

    data object NotFound : TransactionResult()

    data object TimedOut : TransactionResult()

    data class Failed(val reason: String) : TransactionResult()

    /**
     * The chain accepted the inbound transaction but the network refunded it (e.g. paused pool,
     * unmet swap limit, full savers capacity). Funds are back with the sender; the intended side
     * effect did not happen.
     */
    data class Refunded(val reason: String) : TransactionResult()
}

interface TransactionStatusRepository {
    /** [senderAccountId] is required where the chain looks transactions up by sender (NEAR). */
    suspend fun checkTransactionStatus(
        txHash: String,
        chain: Chain,
        senderAccountId: String? = null,
    ): TransactionResult
}

internal class TransactionStatusRepositoryImpl
@Inject
constructor(
    @param:EvmTxStatus private val evmProvider: TransactionStatusProvider,
    @param:UtxoTxStatus private val utxoProvider: TransactionStatusProvider,
    @param:CosmosTxStatus private val cosmosProvider: TransactionStatusProvider,
    @param:ThorChainTxStatus private val thorChainProvider: TransactionStatusProvider,
    @param:SolanaTxStatus private val solanaProvider: TransactionStatusProvider,
    @param:SuiTxStatus private val suiProvider: TransactionStatusProvider,
    @param:TonTxStatus private val tonProvider: TransactionStatusProvider,
    @param:PolkadotTxStatus private val polkadotProvider: TransactionStatusProvider,
    @param:CardanoTxStatus private val cardanoProvider: TransactionStatusProvider,
    @param:RippleTxStatus private val rippleProvider: TransactionStatusProvider,
    @param:TronTxStatus private val tronProvider: TransactionStatusProvider,
    @param:BittensorTxStatus private val bittensorProvider: TransactionStatusProvider,
    private val nearProvider: NearStatusProvider,
) : TransactionStatusRepository {
    private fun getProvider(chain: Chain) =
        when (chain.standard) {
            TokenStandard.EVM -> evmProvider
            TokenStandard.UTXO if chain == Chain.Cardano -> cardanoProvider
            TokenStandard.UTXO -> utxoProvider

            TokenStandard.COSMOS -> cosmosProvider
            TokenStandard.THORCHAIN -> thorChainProvider
            TokenStandard.SOL -> solanaProvider
            TokenStandard.SUBSTRATE if chain == Chain.Bittensor -> bittensorProvider
            TokenStandard.SUBSTRATE -> polkadotProvider
            TokenStandard.SUI -> suiProvider
            TokenStandard.TON -> tonProvider
            TokenStandard.RIPPLE -> rippleProvider
            TokenStandard.TRC20 -> tronProvider
            TokenStandard.NEAR ->
                error("NEAR status needs the sender and is resolved in checkTransactionStatus")
        }

    override suspend fun checkTransactionStatus(
        txHash: String,
        chain: Chain,
        senderAccountId: String?,
    ): TransactionResult {
        if (chain.standard == TokenStandard.NEAR) {
            return nearProvider.checkStatus(txHash, senderAccountId)
        }
        return getProvider(chain).checkStatus(txHash = txHash, chain = chain)
    }
}

interface TransactionStatusProvider {
    suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult
}
