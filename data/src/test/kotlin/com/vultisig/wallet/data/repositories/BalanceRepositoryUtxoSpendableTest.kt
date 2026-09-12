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
import com.vultisig.wallet.data.api.models.BlockChairAddress
import com.vultisig.wallet.data.api.models.BlockChairInfo
import com.vultisig.wallet.data.api.models.BlockChairUtxoInfo
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingDeFiBalanceService
import com.vultisig.wallet.data.blockchain.ethereum.CircleDeFiBalanceService
import com.vultisig.wallet.data.blockchain.maya.MayaDeFiBalanceService
import com.vultisig.wallet.data.blockchain.solana.SolanaDeFiBalanceService
import com.vultisig.wallet.data.blockchain.thorchain.ThorchainDeFiBalanceService
import com.vultisig.wallet.data.blockchain.ton.TonDeFiBalanceService
import com.vultisig.wallet.data.blockchain.tron.TronDeFiBalanceService
import com.vultisig.wallet.data.db.dao.TokenValueDao
import com.vultisig.wallet.data.db.models.TokenValueEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Pins the displayed native balance of the Blockchair-backed UTXO chains to the sum of the outputs
 * coin selection will actually spend (#5867), rather than Blockchair's `address.balance`, which
 * also counts a stranger's zero-conf, explicitly unspendable outputs and dust — money the wallet
 * showed and then refused to send.
 */
class BalanceRepositoryUtxoSpendableTest {

    private val blockchairApi = mockk<BlockChairApi>()
    private val tokenValueDao = mockk<TokenValueDao>(relaxed = true)
    private val transactionHistoryRepository =
        mockk<TransactionHistoryRepository> {
            coEvery { getUnconfirmedTxHashes(any(), any()) } returns emptySet()
        }

    private val repository =
        BalanceRepositoryImpl(
            thorChainApi = mockk<ThorChainApi>(relaxed = true),
            blockchairApi = blockchairApi,
            evmApiFactory = mockk<EvmApiFactory>(relaxed = true),
            mayaChainApi = mockk<MayaChainApi>(relaxed = true),
            cosmosApiFactory = mockk<CosmosApiFactory>(relaxed = true),
            solanaApi = mockk<SolanaApi>(relaxed = true),
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
            cardanoApi = mockk<CardanoApi>(relaxed = true),
            tokenValueDao = tokenValueDao,
            thorchainDeFiBalanceService = mockk<ThorchainDeFiBalanceService>(relaxed = true),
            circleDeFiBalanceService = mockk<CircleDeFiBalanceService>(relaxed = true),
            mayaDeFiBalanceService = mockk<MayaDeFiBalanceService>(relaxed = true),
            tronDeFiBalanceService = mockk<TronDeFiBalanceService>(relaxed = true),
            tonDeFiBalanceService = mockk<TonDeFiBalanceService>(relaxed = true),
            cosmosStakingDeFiBalanceService =
                mockk<CosmosStakingDeFiBalanceService>(relaxed = true),
            solanaDeFiBalanceService = mockk<SolanaDeFiBalanceService>(relaxed = true),
            transactionHistoryRepository = transactionHistoryRepository,
        )

    @Test
    fun `balance is the sum of confirmed spendable outputs, not the Blockchair aggregate`() =
        runTest {
            givenUtxos(
                Chain.Bitcoin,
                reportedBalance = 120_000,
                confirmed("a", 50_000),
                confirmed("b", 70_000),
            )

            balanceOf(Coins.Bitcoin.BTC) shouldBe BigInteger.valueOf(120_000)
        }

    @Test
    fun `a stranger's unconfirmed inbound output is not counted`() = runTest {
        givenUtxos(
            Chain.Bitcoin,
            reportedBalance = 90_000,
            confirmed("a", 50_000),
            unconfirmed("inbound-from-stranger", 40_000),
        )

        balanceOf(Coins.Bitcoin.BTC) shouldBe BigInteger.valueOf(50_000)
    }

    @Test
    fun `own unconfirmed change is counted`() = runTest {
        coEvery {
            transactionHistoryRepository.getUnconfirmedTxHashes(Chain.Bitcoin, ADDRESS)
        } returns setOf("OWN-SEND")
        givenUtxos(
            Chain.Bitcoin,
            reportedBalance = 90_000,
            confirmed("a", 50_000),
            unconfirmed("own-send", 40_000),
        )

        balanceOf(Coins.Bitcoin.BTC) shouldBe BigInteger.valueOf(90_000)
    }

    @Test
    fun `an explicitly unspendable output is not counted`() = runTest {
        givenUtxos(
            Chain.Bitcoin,
            reportedBalance = 70_000,
            confirmed("a", 50_000),
            confirmed("frozen", 20_000).copy(isSpendable = false),
        )

        balanceOf(Coins.Bitcoin.BTC) shouldBe BigInteger.valueOf(50_000)
    }

    /** Bitcoin's dust threshold is 546 sats; the boundary is inclusive, as on iOS. */
    @Test
    fun `dust is not counted but an output exactly at the threshold is`() = runTest {
        givenUtxos(
            Chain.Bitcoin,
            reportedBalance = 51_091,
            confirmed("a", 50_000),
            confirmed("dust", 545),
            confirmed("at-threshold", 546),
        )

        balanceOf(Coins.Bitcoin.BTC) shouldBe BigInteger.valueOf(50_546)
    }

    @Test
    fun `Zcash is read from the same spendable set`() = runTest {
        givenUtxos(
            Chain.Zcash,
            reportedBalance = 90_000,
            confirmed("a", 50_000),
            unconfirmed("inbound-from-stranger", 40_000),
        )

        balanceOf(Coins.Zcash.ZEC) shouldBe BigInteger.valueOf(50_000)
    }

    @Test
    fun `a reported balance with no unspent outputs is a failed read, not a zero`() = runTest {
        givenUtxos(Chain.Bitcoin, reportedBalance = 50_000)

        assertThrows<IllegalStateException> {
            repository.getTokenValue(ADDRESS, Coins.Bitcoin.BTC).first()
        }

        coVerify(exactly = 0) { tokenValueDao.insertTokenValue(any<TokenValueEntity>()) }
    }

    @Test
    fun `outputs that are all present and all filtered out are a genuine zero`() = runTest {
        givenUtxos(
            Chain.Bitcoin,
            reportedBalance = 40_000,
            unconfirmed("inbound-from-stranger", 40_000),
        )

        balanceOf(Coins.Bitcoin.BTC) shouldBe BigInteger.ZERO
        coVerify(exactly = 1) {
            tokenValueDao.insertTokenValue(
                match<TokenValueEntity> { it.tokenValue == BigInteger.ZERO.toString() }
            )
        }
    }

    @Test
    fun `Dash still reads the Blockchair aggregate`() = runTest {
        coEvery { blockchairApi.getAddressInfo(Chain.Dash, ADDRESS) } returns
            BlockChairInfo(
                address = BlockChairAddress(balance = 90_000, unspentOutputCount = 2),
                utxos = listOf(confirmed("a", 50_000), unconfirmed("inbound-from-stranger", 40_000)),
            )

        balanceOf(Coins.Dash.DASH) shouldBe BigInteger.valueOf(90_000)
        coVerify(exactly = 0) { blockchairApi.getAllUtxos(any(), any()) }
    }

    private suspend fun balanceOf(coin: Coin): BigInteger =
        repository.getTokenValue(ADDRESS, coin).first().value

    private fun givenUtxos(chain: Chain, reportedBalance: Long, vararg utxos: BlockChairUtxoInfo) {
        coEvery { blockchairApi.getAllUtxos(chain, ADDRESS) } returns
            BlockChairInfo(
                address =
                    BlockChairAddress(balance = reportedBalance, unspentOutputCount = utxos.size),
                utxos = utxos.toList(),
            )
    }

    private fun confirmed(hash: String, value: Long) =
        BlockChairUtxoInfo(transactionHash = hash, index = 0, value = value, blockId = 800_000)

    private fun unconfirmed(hash: String, value: Long) =
        BlockChairUtxoInfo(transactionHash = hash, index = 1, value = value, blockId = -1)

    private companion object {
        const val ADDRESS = "bc1qexampleaddress"
    }
}
