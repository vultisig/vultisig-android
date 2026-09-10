package com.vultisig.wallet.data.blockchain.tron

import com.vultisig.wallet.data.api.BittensorApi
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CardanoApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.DashApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.ZcashApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.api.models.TronChainParameterJson
import com.vultisig.wallet.data.api.models.TronChainParametersJson
import com.vultisig.wallet.data.api.models.TronSpecificBlockHeaderJson
import com.vultisig.wallet.data.api.models.TronSpecificBlockJson
import com.vultisig.wallet.data.api.models.TronSpecificBlockRawDataJson
import com.vultisig.wallet.data.api.models.TronTriggerConstantContractJson
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * TRON used to price a send twice. [TronFeeService] simulated the amount being transferred to
 * produce the fee shown to the user and checked against their balance, while
 * [BlockChainSpecificRepositoryImpl] ran a second simulation — against the sender's whole balance,
 * with `energy_penalty` added on top of `energy_used` rather than read as the share inside it — for
 * the `fee_limit` that actually reached the wire. Both numbers now come out of the same
 * [TronFeeService] computation over the amount being sent. Each caller still runs it against the
 * chain as it stands — the specific is rebuilt at Continue — so they can drift as the chain moves,
 * but they no longer disagree about what is simulated or what the response means. They stay
 * deliberately different in one respect only: staked energy discounts what the sender is expected
 * to burn, never the ceiling the chain enforces.
 */
internal class TronFeeReconciliationTest {

    private val tronApi: TronApi = mockk(relaxed = true)
    private val feeService = TronFeeService(tronApi)

    @Test
    fun `the signed fee_limit and the displayed fee come out of the same pass over the amount sent`() =
        runTest {
            stubSimulation(energyUsed = 65_000L, energyPenalty = 50_000L)

            val displayed = feeService.calculateFees(trc20Transfer())
            val signed = trc20FeeLimit()

            assertEquals(displayed.feeLimit.toString(), signed)
            // 65,000 energy x 1.3 x 420 sun/energy, the same ceiling iOS pins in
            // TronServiceFeeLimitTests.testGetBlockInfo_trc20Transfer_usesSimulationResult.
            assertEquals("35490000", signed)
            assertEquals(BigInteger.valueOf(27_300_000L + CONTRACT_BANDWIDTH_FEE), displayed.amount)
            // One simulation per caller. What this pins is that both of them simulate the amount
            // being sent — not that a single RPC feeds both.
            coVerify(exactly = 2) {
                tronApi.getTriggerConstantContractFee(
                    ownerAddressBase58 = SENDER,
                    contractAddressBase58 = CONTRACT,
                    recipientAddressHex = any(),
                    functionSelector = any(),
                    amount = AMOUNT,
                )
            }
        }

    @Test
    fun `the whole balance is never simulated for the signed fee_limit`() = runTest {
        stubSimulation(energyUsed = 65_000L, energyPenalty = 50_000L)

        trc20FeeLimit()

        coVerify(exactly = 0) { tronApi.getBalance(any()) }
    }

    @Test
    fun `energy penalty is the share inside energy used, not a term added on top of it`() =
        runTest {
            stubSimulation(energyUsed = 65_000L, energyPenalty = 50_000L)

            val signed = trc20FeeLimit()

            // Adding the penalty would have signed (65,000 + 50,000) x 1.3 x 420 = 62,790,000 sun,
            // reserving 1.77x the energy the call actually burns.
            assertEquals("35490000", signed)
        }

    @Test
    fun `staked energy discounts the displayed fee and never the signed ceiling`() = runTest {
        stubSimulation(energyUsed = 65_000L, energyPenalty = 50_000L, availableEnergy = 65_000L)

        val displayed = feeService.calculateFees(trc20Transfer())

        // Energy the sender already owns costs nothing to spend, but it can be consumed by another
        // transaction during the signing ceremony, so the ceiling stays gross.
        assertEquals(BigInteger.valueOf(CONTRACT_BANDWIDTH_FEE), displayed.amount)
        assertEquals("35490000", trc20FeeLimit())
    }

    @Test
    fun `a contract call scales both numbers from the same simulated total`() = runTest {
        // A JustLend/SunSwap-style call burns an order of magnitude more energy than a wallet-to-
        // wallet transfer, and carries most of it as Dynamic Energy penalty.
        stubSimulation(energyUsed = 1_200_000L, energyPenalty = 900_000L)

        val displayed = feeService.calculateFees(trc20Transfer(to = MARKET))
        val signed = trc20FeeLimit(dstAddress = MARKET)

        assertEquals(displayed.feeLimit.toString(), signed)
        // 1,200,000 x 1.3 x 420 — not the 1,146,600,000 sun a double-counted penalty produced.
        assertEquals("655200000", signed)
        assertEquals(BigInteger.valueOf(504_000_000L + CONTRACT_BANDWIDTH_FEE), displayed.amount)
    }

    @Test
    fun `a simulation whose penalty exceeds its own total is refused`() = runTest {
        stubSimulation(energyUsed = 65_000L, energyPenalty = 70_000L)

        assertFailsWith<IllegalStateException> { feeService.calculateFees(trc20Transfer()) }
        assertFailsWith<IllegalStateException> { trc20FeeLimit() }
    }

    @Test
    fun `native TRX keeps its flat ceiling without simulating anything`() = runTest {
        stubSimulation(energyUsed = 65_000L, energyPenalty = 50_000L)

        // A plain transfer never writes fee_limit; the staking and dApp contract calls sharing this
        // branch are capped by the flat ceiling instead of by an estimate.
        assertEquals("800000", feeLimitOf(nativeCoin(), dstAddress = RECIPIENT))
        coVerify(exactly = 0) {
            tronApi.getTriggerConstantContractFee(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `the fee_limit safety multiplier survives truncation`() {
        // 65,001 x 13 / 10 = 84,501.3 truncated to 84,501, x 420 = 35,490,420 — the
        // multiply-before-divide order must hold or this pins a different, smaller value.
        assertEquals(
            BigInteger.valueOf(35_490_420L),
            TronFeeService.contractFeeLimit(
                totalEnergyUsed = BigInteger.valueOf(65_001L),
                energyPrice = BigInteger.valueOf(420L),
            ),
        )
    }

    private suspend fun trc20FeeLimit(dstAddress: String = RECIPIENT): String =
        feeLimitOf(trc20Coin(), dstAddress)

    private suspend fun feeLimitOf(token: Coin, dstAddress: String): String {
        val specific =
            repository()
                .getSpecific(
                    chain = Chain.Tron,
                    address = SENDER,
                    token = token,
                    gasFee = TokenValue(BigInteger.ZERO, nativeCoin()),
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = false,
                    dstAddress = dstAddress,
                    tokenAmountValue = AMOUNT,
                )
                .blockChainSpecific
        return (specific as BlockChainSpecific.Tron).gasFeeEstimation.toString()
    }

    private fun stubSimulation(energyUsed: Long, energyPenalty: Long, availableEnergy: Long = 0L) {
        coEvery { tronApi.getSpecific() } returns block()
        coEvery { tronApi.getChainParameters() } returns chainParameters()
        coEvery { tronApi.getAccountResource(any()) } returns
            TronAccountResourceJson(energyLimit = availableEnergy)
        coEvery { tronApi.getAccount(any()) } answers { TronAccountJson(address = firstArg()) }
        coEvery { tronApi.getTriggerConstantContractFee(any(), any(), any(), any(), any()) } returns
            TronTriggerConstantContractJson(
                energyUsed = energyUsed,
                energyPenalty = energyPenalty,
                transaction = TronTriggerConstantContractJson.Transaction(),
            )
    }

    private fun repository() =
        BlockChainSpecificRepositoryImpl(
            thorChainApi = mockk<ThorChainApi>(relaxed = true),
            mayaChainApi = mockk<MayaChainApi>(relaxed = true),
            evmApiFactory = mockk<EvmApiFactory>(relaxed = true),
            solanaApi = mockk<SolanaApi>(relaxed = true),
            cosmosApiFactory = mockk<CosmosApiFactory>(relaxed = true),
            blockChairApi = mockk<BlockChairApi>(relaxed = true),
            dashApi = mockk<DashApi>(relaxed = true),
            zcashApi = mockk<ZcashApi>(relaxed = true),
            polkadotApi = mockk<PolkadotApi>(relaxed = true),
            bittensorApi = mockk<BittensorApi>(relaxed = true),
            suiApi = mockk<SuiApi>(relaxed = true),
            tonApi = mockk<TonApi>(relaxed = true),
            rippleApi = mockk<RippleApi>(relaxed = true),
            tronApi = tronApi,
            cardanoApi = mockk<CardanoApi>(relaxed = true),
            feeServiceComposite = mockk<FeeServiceComposite>(relaxed = true),
            tronFeeService = feeService,
        )

    private fun trc20Transfer(to: String = RECIPIENT) =
        Transfer(
            coin = trc20Coin(),
            vault = VaultData(vaultHexPublicKey = "pub", vaultHexChainCode = "chain"),
            amount = AMOUNT,
            to = to,
        )

    private fun trc20Coin() =
        Coin(
            chain = Chain.Tron,
            ticker = "USDT",
            logo = "",
            address = SENDER,
            decimal = 6,
            hexPublicKey = "pub",
            priceProviderID = "tether",
            contractAddress = CONTRACT,
            isNativeToken = false,
        )

    private fun nativeCoin() =
        Coin(
            chain = Chain.Tron,
            ticker = "TRX",
            logo = "",
            address = SENDER,
            decimal = 6,
            hexPublicKey = "pub",
            priceProviderID = "tron",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun chainParameters() =
        TronChainParametersJson(
            listOf(
                TronChainParameterJson("getTransactionFee", 1000L),
                TronChainParameterJson("getCreateAccountFee", 100_000L),
                TronChainParameterJson("getCreateNewAccountFeeInSystemContract", 1_000_000L),
                TronChainParameterJson("getMemoFee", 1_000_000L),
                TronChainParameterJson("getEnergyFee", 420L),
                TronChainParameterJson("getDynamicEnergyMaxFactor", 1200L),
            )
        )

    private fun block() =
        TronSpecificBlockJson(
            blockHeader =
                TronSpecificBlockHeaderJson(
                    rawData =
                        TronSpecificBlockRawDataJson(
                            number = 1uL,
                            txTrieRoot = "00",
                            witnessAddress = "41",
                            parentHash = "00",
                            version = 1uL,
                            timeStamp = 1uL,
                        )
                )
        )

    private companion object {
        const val SENDER = "TA4Y62o6YC2Zsck9rZVGTvqW1AQ7X9zTnj"
        const val RECIPIENT = "TBthewbwcZKTd99XrfwoUzpTtvmkoFqt9q"
        const val CONTRACT = "TDisDrQngvcMNfYurnQLW4oRnh9PzwDFxh"
        const val MARKET = "TFZ2nmDdmHuF8BxHrtrsX8nPgTX3FfuGzz"

        /** 345 bytes at the test chain's 1,000 sun/byte — a contract call always pays it. */
        const val CONTRACT_BANDWIDTH_FEE = 345_000L

        val AMOUNT: BigInteger = BigInteger.valueOf(25_000_000L)
    }
}
