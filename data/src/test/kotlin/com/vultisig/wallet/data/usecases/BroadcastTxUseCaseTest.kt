package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.BittensorApi
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CardanoApi
import com.vultisig.wallet.data.api.CosmosApi
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
import com.vultisig.wallet.data.api.errors.CosmosBroadcastException
import com.vultisig.wallet.data.api.models.BlockChainStatusDeserialized
import com.vultisig.wallet.data.api.models.BlockChairStatusResponse
import com.vultisig.wallet.data.api.models.ContextData
import com.vultisig.wallet.data.api.models.TransactionData
import com.vultisig.wallet.data.api.models.TransactionInfo
import com.vultisig.wallet.data.api.models.cosmos.CosmosTxStatusJson
import com.vultisig.wallet.data.api.models.cosmos.TxResponse
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class BroadcastTxUseCaseTest {

    @Test
    fun `falls back to signed transaction hash when cosmos broadcast omits hash`() = runTest {
        val cosmosApi = mockk<CosmosApi>()
        val cosmosApiFactory = mockk<CosmosApiFactory>()
        coEvery { cosmosApi.broadcastTransaction(RAW_TRANSACTION) } returns null
        every { cosmosApiFactory.createCosmosApi(Chain.Kujira) } returns cosmosApi

        val txHash =
            createUseCase(cosmosApiFactory = cosmosApiFactory)(Chain.Kujira, signedTransaction())

        assertEquals(KNOWN_TRANSACTION_HASH, txHash)
    }

    @Test
    fun `falls back to signed transaction hash when thorchain broadcast omits hash`() = runTest {
        val thorChainApi = mockk<ThorChainApi>()
        coEvery { thorChainApi.broadcastTransaction(RAW_TRANSACTION) } returns null

        val txHash =
            createUseCase(thorChainApi = thorChainApi)(Chain.ThorChain, signedTransaction())

        assertEquals(KNOWN_TRANSACTION_HASH, txHash)
    }

    @Test
    fun `falls back to signed transaction hash when mayachain broadcast omits hash`() = runTest {
        val mayaChainApi = mockk<MayaChainApi>()
        coEvery { mayaChainApi.broadcastTransaction(RAW_TRANSACTION) } returns null

        val txHash =
            createUseCase(mayaChainApi = mayaChainApi)(Chain.MayaChain, signedTransaction())

        assertEquals(KNOWN_TRANSACTION_HASH, txHash)
    }

    @Test
    fun `thorchain recovers with local hash when a rejected broadcast is already on chain`() =
        runTest {
            val thorChainApi = mockk<ThorChainApi>()
            val statusRepo = mockk<TransactionStatusRepository>()
            coEvery { thorChainApi.broadcastTransaction(RAW_TRANSACTION) } throws sequenceMismatch()
            coEvery {
                statusRepo.checkTransactionStatus(KNOWN_TRANSACTION_HASH, Chain.ThorChain)
            } returns TransactionResult.Confirmed

            val txHash =
                createUseCase(
                    thorChainApi = thorChainApi,
                    transactionStatusRepository = statusRepo,
                )(Chain.ThorChain, signedTransaction())

            assertEquals(KNOWN_TRANSACTION_HASH, txHash)
        }

    @Test
    fun `thorchain recovers when the landed tx was refunded (a terminal on-chain outcome)`() =
        runTest {
            val thorChainApi = mockk<ThorChainApi>()
            val statusRepo = mockk<TransactionStatusRepository>()
            coEvery { thorChainApi.broadcastTransaction(RAW_TRANSACTION) } throws sequenceMismatch()
            // A refunded swap still landed on chain; the sequence was consumed by our tx, so the
            // rejection is a benign duplicate and the local hash is canonical.
            coEvery {
                statusRepo.checkTransactionStatus(KNOWN_TRANSACTION_HASH, Chain.ThorChain)
            } returns TransactionResult.Refunded("swap refunded")

            val txHash =
                createUseCase(
                    thorChainApi = thorChainApi,
                    transactionStatusRepository = statusRepo,
                )(Chain.ThorChain, signedTransaction())

            assertEquals(KNOWN_TRANSACTION_HASH, txHash)
        }

    @Test
    fun `mayachain rethrows a rejected broadcast when the tx is not confirmed on chain`() =
        runTest {
            val mayaChainApi = mockk<MayaChainApi>()
            val statusRepo = mockk<TransactionStatusRepository>()
            val rejection = sequenceMismatch()
            coEvery { mayaChainApi.broadcastTransaction(RAW_TRANSACTION) } throws rejection
            coEvery {
                statusRepo.checkTransactionStatus(KNOWN_TRANSACTION_HASH, Chain.MayaChain)
            } returns TransactionResult.Pending

            val thrown =
                assertFailsWith<CosmosBroadcastException> {
                    createUseCase(
                        mayaChainApi = mayaChainApi,
                        transactionStatusRepository = statusRepo,
                    )(Chain.MayaChain, signedTransaction())
                }
            assertEquals(rejection, thrown)
            coVerify(exactly = 3) {
                statusRepo.checkTransactionStatus(KNOWN_TRANSACTION_HASH, Chain.MayaChain)
            }
        }

    @Test
    fun `uses broadcast hash when cosmos broadcast returns hash`() = runTest {
        val cosmosApi = mockk<CosmosApi>()
        val cosmosApiFactory = mockk<CosmosApiFactory>()
        coEvery { cosmosApi.broadcastTransaction(RAW_TRANSACTION) } returns BROADCAST_HASH
        every { cosmosApiFactory.createCosmosApi(Chain.Kujira) } returns cosmosApi

        val txHash =
            createUseCase(cosmosApiFactory = cosmosApiFactory)(Chain.Kujira, signedTransaction())

        assertEquals(BROADCAST_HASH, txHash)
    }

    @Test
    fun `cosmos recovers with local hash when a code-32 rejection is already on chain`() = runTest {
        val cosmosApi = mockk<CosmosApi>()
        val cosmosApiFactory = mockk<CosmosApiFactory>()
        coEvery { cosmosApi.broadcastTransaction(RAW_TRANSACTION) } throws sequenceMismatch()
        coEvery { cosmosApi.getTxStatus(KNOWN_TRANSACTION_HASH) } returns txStatus(code = 0)
        every { cosmosApiFactory.createCosmosApi(Chain.Kujira) } returns cosmosApi

        val txHash =
            createUseCase(cosmosApiFactory = cosmosApiFactory)(Chain.Kujira, signedTransaction())

        assertEquals(KNOWN_TRANSACTION_HASH, txHash)
        coVerify(exactly = 1) { cosmosApi.broadcastTransaction(RAW_TRANSACTION) }
    }

    @Test
    fun `cosmos rethrows a code-32 rejection when the committed tx failed execution`() = runTest {
        val cosmosApi = mockk<CosmosApi>()
        val cosmosApiFactory = mockk<CosmosApiFactory>()
        val rejection = sequenceMismatch()
        coEvery { cosmosApi.broadcastTransaction(RAW_TRANSACTION) } throws rejection
        // Our hash is on chain but the execution failed (non-zero code): no funds moved.
        coEvery { cosmosApi.getTxStatus(KNOWN_TRANSACTION_HASH) } returns txStatus(code = 5)
        every { cosmosApiFactory.createCosmosApi(Chain.Kujira) } returns cosmosApi

        val thrown =
            assertFailsWith<CosmosBroadcastException> {
                createUseCase(cosmosApiFactory = cosmosApiFactory)(
                    Chain.Kujira,
                    signedTransaction(),
                )
            }
        assertEquals(rejection, thrown)
    }

    @Test
    fun `cosmos rethrows a code-32 rejection when our tx is not on chain`() = runTest {
        val cosmosApi = mockk<CosmosApi>()
        val cosmosApiFactory = mockk<CosmosApiFactory>()
        val rejection = sequenceMismatch()
        coEvery { cosmosApi.broadcastTransaction(RAW_TRANSACTION) } throws rejection
        // A different tx consumed the sequence, so the LCD never finds our hash.
        coEvery { cosmosApi.getTxStatus(KNOWN_TRANSACTION_HASH) } returns null
        every { cosmosApiFactory.createCosmosApi(Chain.Kujira) } returns cosmosApi

        val thrown =
            assertFailsWith<CosmosBroadcastException> {
                createUseCase(cosmosApiFactory = cosmosApiFactory)(
                    Chain.Kujira,
                    signedTransaction(),
                )
            }
        assertEquals(rejection, thrown)
        coVerify(exactly = 3) { cosmosApi.getTxStatus(KNOWN_TRANSACTION_HASH) }
    }

    @Test
    fun `bitcoin cash broadcast returns hash from blockchair on success`() = runTest {
        val blockChairApi = mockk<BlockChairApi>()
        coEvery { blockChairApi.broadcastTransaction(Chain.BitcoinCash, RAW_TRANSACTION) } returns
            BROADCAST_HASH

        val txHash =
            createUseCase(blockChairApi = blockChairApi)(Chain.BitcoinCash, signedTransaction())

        assertEquals(BROADCAST_HASH, txHash)
    }

    @Test
    fun `bitcoin cash broadcast recovers when peer already broadcast`() = runTest {
        val blockChairApi = mockk<BlockChairApi>()
        coEvery { blockChairApi.broadcastTransaction(Chain.BitcoinCash, RAW_TRANSACTION) } throws
            RuntimeException("fail to broadcast transaction: transaction already known")
        coEvery { blockChairApi.getTsStatus(Chain.BitcoinCash, KNOWN_TRANSACTION_HASH) } returns
            blockchairResult(blockId = -1)

        val txHash =
            createUseCase(blockChairApi = blockChairApi)(Chain.BitcoinCash, signedTransaction())

        assertEquals(KNOWN_TRANSACTION_HASH, txHash)
        coVerify(exactly = 1) {
            blockChairApi.broadcastTransaction(Chain.BitcoinCash, RAW_TRANSACTION)
        }
    }

    @Test
    fun `bitcoin cash broadcast propagates error when blockchair has no record`() = runTest {
        val blockChairApi = mockk<BlockChairApi>()
        coEvery { blockChairApi.broadcastTransaction(Chain.BitcoinCash, RAW_TRANSACTION) } throws
            RuntimeException("fail to broadcast transaction: insufficient fee")
        coEvery { blockChairApi.getTsStatus(Chain.BitcoinCash, KNOWN_TRANSACTION_HASH) } returns
            BlockChainStatusDeserialized.Error("")

        assertFailsWith<RuntimeException> {
            createUseCase(blockChairApi = blockChairApi)(Chain.BitcoinCash, signedTransaction())
        }
    }

    @Test
    fun `bitcoin broadcast recovers when peer already broadcast`() = runTest {
        val blockChairApi = mockk<BlockChairApi>()
        coEvery { blockChairApi.broadcastTransaction(Chain.Bitcoin, RAW_TRANSACTION) } throws
            RuntimeException("Failed to broadcast transaction")
        coEvery { blockChairApi.getTsStatus(Chain.Bitcoin, KNOWN_TRANSACTION_HASH) } returns
            blockchairResult(blockId = 951320)

        val txHash =
            createUseCase(blockChairApi = blockChairApi)(Chain.Bitcoin, signedTransaction())

        assertEquals(KNOWN_TRANSACTION_HASH, txHash)
        coVerify(exactly = 1) { blockChairApi.broadcastTransaction(Chain.Bitcoin, RAW_TRANSACTION) }
    }

    @Test
    fun `bitcoin cash broadcast recovers on second verify attempt after indexer lag`() = runTest {
        val blockChairApi = mockk<BlockChairApi>()
        coEvery { blockChairApi.broadcastTransaction(Chain.BitcoinCash, RAW_TRANSACTION) } throws
            RuntimeException("fail to broadcast transaction: transaction already known")
        coEvery { blockChairApi.getTsStatus(Chain.BitcoinCash, KNOWN_TRANSACTION_HASH) } returnsMany
            listOf(
                BlockChainStatusDeserialized.Error("not yet indexed"),
                blockchairResult(blockId = -1),
            )

        val txHash =
            createUseCase(blockChairApi = blockChairApi)(Chain.BitcoinCash, signedTransaction())

        assertEquals(KNOWN_TRANSACTION_HASH, txHash)
        coVerify(exactly = 1) {
            blockChairApi.broadcastTransaction(Chain.BitcoinCash, RAW_TRANSACTION)
        }
        coVerify(exactly = 2) {
            blockChairApi.getTsStatus(Chain.BitcoinCash, KNOWN_TRANSACTION_HASH)
        }
    }

    @Test
    fun `bitcoin cash broadcast gives up and rethrows after all verify attempts fail`() = runTest {
        val blockChairApi = mockk<BlockChairApi>()
        val broadcastError = RuntimeException("fail to broadcast transaction: insufficient fee")
        coEvery { blockChairApi.broadcastTransaction(Chain.BitcoinCash, RAW_TRANSACTION) } throws
            broadcastError
        coEvery { blockChairApi.getTsStatus(Chain.BitcoinCash, KNOWN_TRANSACTION_HASH) } returns
            BlockChainStatusDeserialized.Error("not indexed")

        val thrown =
            assertFailsWith<RuntimeException> {
                createUseCase(blockChairApi = blockChairApi)(Chain.BitcoinCash, signedTransaction())
            }
        assertEquals(broadcastError, thrown)
        coVerify(exactly = 1) {
            blockChairApi.broadcastTransaction(Chain.BitcoinCash, RAW_TRANSACTION)
        }
        coVerify(exactly = 3) {
            blockChairApi.getTsStatus(Chain.BitcoinCash, KNOWN_TRANSACTION_HASH)
        }
    }

    @Test
    fun `verify cancellation propagates immediately without further retries`() = runTest {
        val blockChairApi = mockk<BlockChairApi>()
        coEvery { blockChairApi.broadcastTransaction(Chain.BitcoinCash, RAW_TRANSACTION) } throws
            RuntimeException("transaction already known")
        coEvery { blockChairApi.getTsStatus(Chain.BitcoinCash, KNOWN_TRANSACTION_HASH) } throws
            CancellationException("cancelled")

        assertFailsWith<CancellationException> {
            createUseCase(blockChairApi = blockChairApi)(Chain.BitcoinCash, signedTransaction())
        }
        coVerify(exactly = 1) {
            blockChairApi.getTsStatus(Chain.BitcoinCash, KNOWN_TRANSACTION_HASH)
        }
    }

    @Test
    fun `zcash broadcast recovers when peer already broadcast`() = runTest {
        val blockChairApi = mockk<BlockChairApi>()
        coEvery { blockChairApi.broadcastTransaction(Chain.Zcash, RAW_TRANSACTION) } throws
            RuntimeException("fail to broadcast transaction: transaction already known")
        coEvery { blockChairApi.getTsStatus(Chain.Zcash, KNOWN_TRANSACTION_HASH) } returns
            blockchairResult(blockId = -1)

        val txHash = createUseCase(blockChairApi = blockChairApi)(Chain.Zcash, signedTransaction())

        assertEquals(KNOWN_TRANSACTION_HASH, txHash)
    }

    private fun blockchairResult(blockId: Int) =
        BlockChainStatusDeserialized.Result(
            BlockChairStatusResponse(
                data =
                    mapOf(
                        KNOWN_TRANSACTION_HASH to
                            TransactionData(transaction = TransactionInfo(blockId = blockId))
                    ),
                context = ContextData(state = 951460),
            )
        )

    private fun createUseCase(
        thorChainApi: ThorChainApi = mockk(relaxed = true),
        mayaChainApi: MayaChainApi = mockk(relaxed = true),
        cosmosApiFactory: CosmosApiFactory = mockk(relaxed = true),
        blockChairApi: BlockChairApi = mockk(relaxed = true),
        transactionStatusRepository: TransactionStatusRepository = mockk(relaxed = true),
    ) =
        BroadcastTxUseCaseImpl(
            thorChainApi = thorChainApi,
            evmApiFactory = mockk<EvmApiFactory>(relaxed = true),
            blockChairApi = blockChairApi,
            mayaChainApi = mayaChainApi,
            cosmosApiFactory = cosmosApiFactory,
            solanaApi = mockk<SolanaApi>(relaxed = true),
            polkadotApi = mockk<PolkadotApi>(relaxed = true),
            bittensorApi = mockk<BittensorApi>(relaxed = true),
            suiApi = mockk<SuiApi>(relaxed = true),
            tonApi = mockk<TonApi>(relaxed = true),
            rippleApi = mockk<RippleApi>(relaxed = true),
            tronApi = mockk<TronApi>(relaxed = true),
            cardanoApi = mockk<CardanoApi>(relaxed = true),
            transactionStatusRepository = transactionStatusRepository,
        )

    private fun txStatus(code: Int) =
        CosmosTxStatusJson(
            txResponse = TxResponse(height = "1", txHash = KNOWN_TRANSACTION_HASH, code = code)
        )

    private fun sequenceMismatch() =
        CosmosBroadcastException.from(
            code = 32,
            codespace = "sdk",
            rawLog = "account sequence mismatch, expected 5, got 4",
            txHash = null,
        )

    private fun signedTransaction() =
        SignedTransactionResult(
            rawTransaction = RAW_TRANSACTION,
            transactionHash = KNOWN_TRANSACTION_HASH,
        )

    private companion object {
        const val RAW_TRANSACTION = "signed-transaction"
        const val KNOWN_TRANSACTION_HASH = "known-transaction-hash"
        const val BROADCAST_HASH = "broadcast-hash"
    }
}
