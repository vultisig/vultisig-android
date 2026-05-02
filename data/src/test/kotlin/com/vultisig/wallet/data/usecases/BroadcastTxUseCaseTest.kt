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
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `uses broadcast hash when cosmos broadcast returns hash`() = runTest {
        val cosmosApi = mockk<CosmosApi>()
        val cosmosApiFactory = mockk<CosmosApiFactory>()
        coEvery { cosmosApi.broadcastTransaction(RAW_TRANSACTION) } returns BROADCAST_HASH
        every { cosmosApiFactory.createCosmosApi(Chain.Kujira) } returns cosmosApi

        val txHash =
            createUseCase(cosmosApiFactory = cosmosApiFactory)(Chain.Kujira, signedTransaction())

        assertEquals(BROADCAST_HASH, txHash)
    }

    private fun createUseCase(
        thorChainApi: ThorChainApi = mockk(relaxed = true),
        mayaChainApi: MayaChainApi = mockk(relaxed = true),
        cosmosApiFactory: CosmosApiFactory = mockk(relaxed = true),
    ) =
        BroadcastTxUseCaseImpl(
            thorChainApi = thorChainApi,
            evmApiFactory = mockk<EvmApiFactory>(relaxed = true),
            blockChairApi = mockk<BlockChairApi>(relaxed = true),
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
