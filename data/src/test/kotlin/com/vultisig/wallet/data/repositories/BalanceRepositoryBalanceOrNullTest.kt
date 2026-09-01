package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.BittensorApi
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CardanoApi
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
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingDeFiBalanceService
import com.vultisig.wallet.data.blockchain.ethereum.CircleDeFiBalanceService
import com.vultisig.wallet.data.blockchain.maya.MayaDeFiBalanceService
import com.vultisig.wallet.data.blockchain.solana.SolanaDeFiBalanceService
import com.vultisig.wallet.data.blockchain.thorchain.ThorchainDeFiBalanceService
import com.vultisig.wallet.data.blockchain.ton.TonDeFiBalanceService
import com.vultisig.wallet.data.blockchain.tron.TronDeFiBalanceService
import com.vultisig.wallet.data.db.dao.TokenValueDao
import com.vultisig.wallet.data.db.models.TokenValueEntity
import com.vultisig.wallet.data.models.Coins
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Pins how [BalanceRepository.getBalanceOrNull] answers a read it could not make.
 *
 * The gates that refuse a transaction the wallet cannot pay for need "unknown" to be distinct from
 * "empty". Solana is the chain where those two collapsed: [SolanaApi.getBalance] answers every RPC
 * failure with zero lamports, so a flaky node refused a funded wallet (#5607). The routing to the
 * nullable overload is what keeps them apart, and is what these tests hold in place.
 */
class BalanceRepositoryBalanceOrNullTest {

    private val solanaApi = mockk<SolanaApi>(relaxed = true)
    private val thorChainApi = mockk<ThorChainApi>(relaxed = true)
    private val blockchairApi = mockk<BlockChairApi>(relaxed = true)
    private val cardanoApi = mockk<CardanoApi>(relaxed = true)
    private val tokenValueDao = mockk<TokenValueDao>(relaxed = true)

    private val repository =
        BalanceRepositoryImpl(
            thorChainApi = thorChainApi,
            blockchairApi = blockchairApi,
            evmApiFactory = mockk<EvmApiFactory>(relaxed = true),
            mayaChainApi = mockk<MayaChainApi>(relaxed = true),
            cosmosApiFactory = mockk<CosmosApiFactory>(relaxed = true),
            solanaApi = solanaApi,
            splTokenRepository = mockk<SplTokenRepository>(relaxed = true),
            tokenPriceRepository = mockk<TokenPriceRepository>(relaxed = true),
            appCurrencyRepository = mockk<AppCurrencyRepository>(relaxed = true),
            tronResourceDataSource = mockk<TronResourceDataSource>(relaxed = true),
            polkadotApi = mockk<PolkadotApi>(relaxed = true),
            bittensorApi = mockk<BittensorApi>(relaxed = true),
            suiApi = mockk<SuiApi>(relaxed = true),
            tonApi = mockk<TonApi>(relaxed = true),
            rippleApi = mockk<RippleApi>(relaxed = true),
            tronApi = mockk<TronApi>(relaxed = true),
            cardanoApi = cardanoApi,
            tokenValueDao = tokenValueDao,
            thorchainDeFiBalanceService = mockk<ThorchainDeFiBalanceService>(relaxed = true),
            circleDeFiBalanceService = mockk<CircleDeFiBalanceService>(relaxed = true),
            mayaDeFiBalanceService = mockk<MayaDeFiBalanceService>(relaxed = true),
            tronDeFiBalanceService = mockk<TronDeFiBalanceService>(relaxed = true),
            tonDeFiBalanceService = mockk<TonDeFiBalanceService>(relaxed = true),
            cosmosStakingDeFiBalanceService =
                mockk<CosmosStakingDeFiBalanceService>(relaxed = true),
            solanaDeFiBalanceService = mockk<SolanaDeFiBalanceService>(relaxed = true),
        )

    @Test
    fun `a Solana balance the node would not answer for is unknown, not zero`() = runTest {
        coEvery { solanaApi.getBalanceOrNull(ADDRESS) } returns null

        repository.getBalanceOrNull(ADDRESS, Coins.Solana.SOL).shouldBeNull()
        // The zero-for-everything overload must not stand in for it, or the distinction is lost
        // again one call further down.
        coVerify(exactly = 0) { solanaApi.getBalance(any()) }
    }

    @Test
    fun `a Solana balance that was read comes back as read`() = runTest {
        coEvery { solanaApi.getBalanceOrNull(ADDRESS) } returns BigInteger("1320000")

        repository.getBalanceOrNull(ADDRESS, Coins.Solana.SOL) shouldBe BigInteger("1320000")
    }

    /** Every other chain reports a failed read by throwing, which is the same "unknown". */
    @Test
    fun `a read that throws is unknown too`() = runTest {
        coEvery { thorChainApi.getBalance(ADDRESS) } throws IllegalStateException("node down")

        repository.getBalanceOrNull(ADDRESS, Coins.ThorChain.RUNE).shouldBeNull()
    }

    /** A cancelled read is not a failed one — it must not be swallowed into a null. */
    @Test
    fun `cancellation propagates rather than reading as unknown`() = runTest {
        coEvery { thorChainApi.getBalance(ADDRESS) } throws CancellationException("cancelled")

        assertThrows<CancellationException> {
            repository.getBalanceOrNull(ADDRESS, Coins.ThorChain.RUNE)
        }
    }

    @Test
    fun `a Blockchair balance failure propagates and is not persisted as zero`() = runTest {
        coEvery { blockchairApi.getAddressInfo(Coins.Bitcoin.BTC.chain, ADDRESS) } throws
            IllegalStateException("blockchair down")

        assertThrows<IllegalStateException> {
            repository.getTokenValue(ADDRESS, Coins.Bitcoin.BTC).first()
        }

        coVerify(exactly = 0) { tokenValueDao.insertTokenValue(any<TokenValueEntity>()) }
    }

    @Test
    fun `a Blockchair absent address response persists a genuine zero`() = runTest {
        coEvery { blockchairApi.getAddressInfo(Coins.Bitcoin.BTC.chain, ADDRESS) } returns null

        repository.getTokenValue(ADDRESS, Coins.Bitcoin.BTC).first().value shouldBe BigInteger.ZERO

        coVerify(exactly = 1) {
            tokenValueDao.insertTokenValue(
                match<TokenValueEntity> { it.tokenValue == BigInteger.ZERO.toString() }
            )
        }
    }

    @Test
    fun `a Cardano balance failure propagates and is not persisted as zero`() = runTest {
        coEvery { cardanoApi.getBalance(Coins.Cardano.ADA) } throws
            IllegalStateException("koios down")

        assertThrows<IllegalStateException> {
            repository.getTokenValue(ADDRESS, Coins.Cardano.ADA).first()
        }

        coVerify(exactly = 0) { tokenValueDao.insertTokenValue(any<TokenValueEntity>()) }
    }

    @Test
    fun `a Solana native balance failure propagates and is not persisted as zero`() = runTest {
        coEvery { solanaApi.getBalanceOrNull(ADDRESS) } returns null

        assertThrows<IllegalStateException> {
            repository.getTokenValue(ADDRESS, Coins.Solana.SOL).first()
        }

        coVerify(exactly = 0) { solanaApi.getBalance(any()) }
        coVerify(exactly = 0) { tokenValueDao.insertTokenValue(any<TokenValueEntity>()) }
    }

    private companion object {
        const val ADDRESS = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"
    }
}
