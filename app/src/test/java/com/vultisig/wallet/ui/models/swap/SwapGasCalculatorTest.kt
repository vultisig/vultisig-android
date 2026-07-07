@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.BasicFee
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.ui.models.send.SendSrc
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

internal class SwapGasCalculatorTest {

    private lateinit var feeServiceComposite: FeeServiceComposite
    private lateinit var vaultRepository: VaultRepository
    private lateinit var tokenRepository: TokenRepository
    private lateinit var blockChainSpecificRepository: BlockChainSpecificRepository
    private lateinit var gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase

    private lateinit var calculator: SwapGasCalculator

    @BeforeEach
    fun setUp() {
        feeServiceComposite = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        tokenRepository = mockk(relaxed = true)
        blockChainSpecificRepository = mockk(relaxed = true)
        gasFeeToEstimatedFee = mockk(relaxed = true)

        mockkObject(UtxoHelper.Companion)

        calculator =
            SwapGasCalculator(
                feeServiceComposite = feeServiceComposite,
                vaultRepository = vaultRepository,
                tokenRepository = tokenRepository,
                blockChainSpecificRepository = blockChainSpecificRepository,
                gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(UtxoHelper.Companion)
    }

    /**
     * Regression test for #4164: for every UTXO chain that goes through the WalletCore plan path
     * (i.e. all UTXO chains except Cardano), `GasCalculationResult.gasFee` MUST remain the per-byte
     * rate so `UtxoHelper.setByteFee(...)` receives sats/byte (not total sats) at signing. The
     * plan's total fee is routed into `estimated` for display / balance checks only.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = Chain::class,
        names = ["Bitcoin", "BitcoinCash", "Litecoin", "Dogecoin", "Dash", "Zcash"],
    )
    fun `utxo swap keeps gasFee as per-byte rate and routes plan fee only into estimated`(
        chain: Chain
    ) = runTest {
        val byteFeeRate = BigInteger("15") // sats/byte
        val planTotalFee = 4200L // total sats for the built tx
        val sendSrc = utxoSendSrc(chain)
        val nativeCoin = sendSrc.account.token

        stubCommon(chain, nativeCoin, BasicFee(byteFeeRate))

        coEvery {
            blockChainSpecificRepository.getSpecific(
                chain = any(),
                address = any(),
                token = any(),
                gasFee = any(),
                isSwap = any(),
                isMaxAmountEnabled = any(),
                isDeposit = any(),
            )
        } returns
            BlockChainSpecificAndUtxo(
                blockChainSpecific = BlockChainSpecific.UTXO(byteFee = byteFeeRate, true),
                utxos = emptyList(),
            )

        val utxoHelper = mockk<UtxoHelper>()
        every { UtxoHelper.getHelper(any(), any()) } returns utxoHelper
        every { utxoHelper.getBitcoinTransactionPlan(any()) } returns
            Bitcoin.TransactionPlan.newBuilder()
                .setFee(planTotalFee)
                .setError(SigningError.OK)
                .build()

        val capturedParams = slot<GasFeeParams>()
        val estimatedTotal = estimatedFee(nativeCoin, BigInteger.valueOf(planTotalFee))
        coEvery { gasFeeToEstimatedFee(capture(capturedParams)) } returns estimatedTotal

        val result = calculator.calculateGasFee(sendSrc, VAULT_ID)

        requireNotNull(result)
        // (1) gasFee stays as per-byte sats/byte — what UtxoHelper.setByteFee(...) expects.
        assertEquals(byteFeeRate, result.gasFee.value)
        // (2) The total fee from the plan is what goes into the estimate (for display /
        // balance check).
        assertEquals(BigInteger.valueOf(planTotalFee), capturedParams.captured.gasFee.value)
        assertEquals(estimatedTotal, result.estimated)
    }

    /**
     * `getBitcoinTransactionPlan` returning `null` (plan.error != OK) must short-circuit the whole
     * calculation for every UTXO chain that uses the plan path — otherwise callers see a stale zero
     * estimate while signing would fail later.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = Chain::class,
        names = ["Bitcoin", "BitcoinCash", "Litecoin", "Dogecoin", "Dash", "Zcash"],
    )
    fun `utxo swap returns null when bitcoin plan fails`(chain: Chain) = runTest {
        val sendSrc = utxoSendSrc(chain)
        val nativeCoin = sendSrc.account.token

        stubCommon(chain, nativeCoin, BasicFee(BigInteger("10")))

        coEvery {
            blockChainSpecificRepository.getSpecific(
                chain = any(),
                address = any(),
                token = any(),
                gasFee = any(),
                isSwap = any(),
                isMaxAmountEnabled = any(),
                isDeposit = any(),
            )
        } returns
            BlockChainSpecificAndUtxo(
                blockChainSpecific = BlockChainSpecific.UTXO(BigInteger("10"), true),
                utxos = emptyList(),
            )

        val utxoHelper = mockk<UtxoHelper>()
        every { UtxoHelper.getHelper(any(), any()) } returns utxoHelper
        every { utxoHelper.getBitcoinTransactionPlan(any()) } returns
            Bitcoin.TransactionPlan.newBuilder()
                .setFee(0L)
                .setError(SigningError.Error_not_enough_utxos)
                .build()

        val result = calculator.calculateGasFee(sendSrc, VAULT_ID)

        assertNull(result)
    }

    /**
     * Cardano is marked as the UTXO standard but is explicitly excluded from the WalletCore Bitcoin
     * plan path. The fee service's value must flow through untouched and the UTXO helper must not
     * be invoked.
     */
    @Test
    fun `cardano swap bypasses bitcoin plan path and uses fee service value directly`() = runTest {
        val feeAmount = BigInteger("170000") // lovelace
        val sendSrc = utxoSendSrc(Chain.Cardano)
        val nativeCoin = sendSrc.account.token

        stubCommon(Chain.Cardano, nativeCoin, BasicFee(feeAmount))

        val capturedParams = slot<GasFeeParams>()
        val estimated = estimatedFee(nativeCoin, feeAmount)
        coEvery { gasFeeToEstimatedFee(capture(capturedParams)) } returns estimated

        val result = calculator.calculateGasFee(sendSrc, VAULT_ID)

        requireNotNull(result)
        assertEquals(feeAmount, result.gasFee.value)
        assertEquals(feeAmount, capturedParams.captured.gasFee.value)
        assertEquals(estimated, result.estimated)
        verify(exactly = 0) { UtxoHelper.getHelper(any(), any()) }
        coVerify(exactly = 0) {
            blockChainSpecificRepository.getSpecific(
                chain = any(),
                address = any(),
                token = any(),
                gasFee = any(),
                isSwap = any(),
                isMaxAmountEnabled = any(),
                isDeposit = any(),
            )
        }
    }

    /**
     * Negative scenario: non-UTXO chains (e.g. Ethereum) must never enter the Bitcoin plan path.
     * The fee service's value is the estimate directly and `UtxoHelper` must not be touched.
     */
    @Test
    fun `non-utxo swap skips bitcoin plan path entirely`() = runTest {
        val feeAmount = BigInteger("210000000000000") // wei
        val sendSrc = ethSendSrc()
        val nativeCoin = sendSrc.account.token

        stubCommon(Chain.Ethereum, nativeCoin, BasicFee(feeAmount))

        val capturedParams = slot<GasFeeParams>()
        val estimated = estimatedFee(nativeCoin, feeAmount)
        coEvery { gasFeeToEstimatedFee(capture(capturedParams)) } returns estimated

        val result = calculator.calculateGasFee(sendSrc, VAULT_ID)

        requireNotNull(result)
        assertEquals(feeAmount, result.gasFee.value)
        assertEquals(feeAmount, capturedParams.captured.gasFee.value)
        assertEquals(estimated, result.estimated)
        verify(exactly = 0) { UtxoHelper.getHelper(any(), any()) }
        coVerify(exactly = 0) {
            blockChainSpecificRepository.getSpecific(
                chain = any(),
                address = any(),
                token = any(),
                gasFee = any(),
                isSwap = any(),
                isMaxAmountEnabled = any(),
                isDeposit = any(),
            )
        }
    }

    // ── computeUtxoPlanFeeResult tests ──────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = Chain::class,
        names = ["Bitcoin", "BitcoinCash", "Litecoin", "Dogecoin", "Dash", "Zcash"],
    )
    fun `computeUtxoPlanFeeResult returns plan fee for UTXO chain`(chain: Chain) = runTest {
        val planFee = 3936L
        val nativeCoin = nativeCoinFor(chain)
        coEvery { vaultRepository.get(VAULT_ID) } returns vault()
        coEvery { tokenRepository.getNativeToken(chain.id) } returns nativeCoin

        val utxoHelper = mockk<UtxoHelper>()
        every { UtxoHelper.getHelper(any(), any()) } returns utxoHelper
        every { utxoHelper.getBitcoinTransactionPlan(any()) } returns
            Bitcoin.TransactionPlan.newBuilder().setFee(planFee).setError(SigningError.OK).build()

        val capturedParams = slot<GasFeeParams>()
        val expected = estimatedFee(nativeCoin, BigInteger.valueOf(planFee))
        coEvery { gasFeeToEstimatedFee(capture(capturedParams)) } returns expected

        val result =
            calculator.computeUtxoPlanFeeResult(
                vaultId = VAULT_ID,
                srcToken = nativeCoin,
                dstAddress = "dstAddr",
                tokenAmountInt = BigInteger("100000"),
                specificAndUtxo = utxoSpecific(),
                memo = "SWAP:ETH.ETH",
            )

        requireNotNull(result)
        assertEquals(BigInteger.valueOf(planFee), capturedParams.captured.gasFee.value)
        assertEquals(expected, result.estimated)
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = Chain::class,
        names = ["Bitcoin", "BitcoinCash", "Litecoin", "Dogecoin", "Dash", "Zcash"],
    )
    fun `computeUtxoPlanFeeResult throws InsufficientUtxosException on Error_not_enough_utxos`(
        chain: Chain
    ) = runTest {
        val nativeCoin = nativeCoinFor(chain)
        coEvery { vaultRepository.get(VAULT_ID) } returns vault()

        val utxoHelper = mockk<UtxoHelper>()
        every { UtxoHelper.getHelper(any(), any()) } returns utxoHelper
        every { utxoHelper.getBitcoinTransactionPlan(any()) } returns
            Bitcoin.TransactionPlan.newBuilder()
                .setFee(0L)
                .setError(SigningError.Error_not_enough_utxos)
                .build()

        var threw = false
        try {
            calculator.computeUtxoPlanFeeResult(
                vaultId = VAULT_ID,
                srcToken = nativeCoin,
                dstAddress = "dstAddr",
                tokenAmountInt = BigInteger("100000"),
                specificAndUtxo = utxoSpecific(),
                memo = null,
            )
        } catch (e: InsufficientUtxosException) {
            threw = true
        }
        assertTrue(threw)
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = Chain::class,
        names = ["Bitcoin", "BitcoinCash", "Litecoin", "Dogecoin", "Dash", "Zcash"],
    )
    fun `computeUtxoPlanFeeResult returns null on other plan errors`(chain: Chain) = runTest {
        val nativeCoin = nativeCoinFor(chain)
        coEvery { vaultRepository.get(VAULT_ID) } returns vault()

        val utxoHelper = mockk<UtxoHelper>()
        every { UtxoHelper.getHelper(any(), any()) } returns utxoHelper
        every { utxoHelper.getBitcoinTransactionPlan(any()) } returns
            Bitcoin.TransactionPlan.newBuilder()
                .setFee(0L)
                .setError(SigningError.Error_general)
                .build()

        val result =
            calculator.computeUtxoPlanFeeResult(
                vaultId = VAULT_ID,
                srcToken = nativeCoin,
                dstAddress = "dstAddr",
                tokenAmountInt = BigInteger("100000"),
                specificAndUtxo = utxoSpecific(),
                memo = null,
            )

        assertNull(result)
    }

    @Test
    fun `computeUtxoPlanFeeResult returns null for non-UTXO chain`() = runTest {
        val coin = nativeCoinFor(Chain.Ethereum)
        val result =
            calculator.computeUtxoPlanFeeResult(
                vaultId = VAULT_ID,
                srcToken = coin,
                dstAddress = "0xDest",
                tokenAmountInt = BigInteger("1000000000000000000"),
                specificAndUtxo =
                    BlockChainSpecificAndUtxo(
                        blockChainSpecific = BlockChainSpecific.UTXO(BigInteger.ONE, false),
                        utxos = emptyList(),
                    ),
                memo = null,
            )

        assertNull(result)
        verify(exactly = 0) { UtxoHelper.getHelper(any(), any()) }
    }

    @Test
    fun `computeUtxoPlanFeeResult returns null for Cardano`() = runTest {
        val coin = nativeCoinFor(Chain.Cardano)
        val result =
            calculator.computeUtxoPlanFeeResult(
                vaultId = VAULT_ID,
                srcToken = coin,
                dstAddress = "adaAddr",
                tokenAmountInt = BigInteger("1000000"),
                specificAndUtxo =
                    BlockChainSpecificAndUtxo(
                        blockChainSpecific = BlockChainSpecific.UTXO(BigInteger.ONE, false),
                        utxos = emptyList(),
                    ),
                memo = null,
            )

        assertNull(result)
        verify(exactly = 0) { UtxoHelper.getHelper(any(), any()) }
    }

    // resolveUtxoPlanFee tests

    @Test
    fun `resolveUtxoPlanFee returns Success with estimated fee when the plan succeeds`() = runTest {
        val planFee = 3936L
        val nativeCoin = nativeCoinFor(Chain.Bitcoin)
        stubGetSpecific()
        coEvery { vaultRepository.get(VAULT_ID) } returns vault()
        coEvery { tokenRepository.getNativeToken(Chain.Bitcoin.id) } returns nativeCoin

        val utxoHelper = mockk<UtxoHelper>()
        every { UtxoHelper.getHelper(any(), any()) } returns utxoHelper
        every { utxoHelper.getBitcoinTransactionPlan(any()) } returns
            Bitcoin.TransactionPlan.newBuilder().setFee(planFee).setError(SigningError.OK).build()

        val expected = estimatedFee(nativeCoin, BigInteger.valueOf(planFee))
        coEvery { gasFeeToEstimatedFee(any()) } returns expected

        val result =
            calculator.resolveUtxoPlanFee(
                vaultId = VAULT_ID,
                srcToken = nativeCoin,
                srcAddress = nativeCoin.address,
                dstAddress = "dstAddr",
                memo = "SWAP:ETH.ETH",
                tokenAmountInt = BigInteger("100000"),
                gasFee = TokenValue(BigInteger("15"), nativeCoin),
            )

        val success = assertIs<UtxoPlanFeeResult.Success>(result)
        assertEquals(expected, success.estimated)
    }

    @Test
    fun `resolveUtxoPlanFee returns InsufficientUtxos when the plan has not enough utxos`() =
        runTest {
            val nativeCoin = nativeCoinFor(Chain.Bitcoin)
            stubGetSpecific()
            coEvery { vaultRepository.get(VAULT_ID) } returns vault()

            val utxoHelper = mockk<UtxoHelper>()
            every { UtxoHelper.getHelper(any(), any()) } returns utxoHelper
            every { utxoHelper.getBitcoinTransactionPlan(any()) } returns
                Bitcoin.TransactionPlan.newBuilder()
                    .setFee(0L)
                    .setError(SigningError.Error_not_enough_utxos)
                    .build()

            val result =
                calculator.resolveUtxoPlanFee(
                    vaultId = VAULT_ID,
                    srcToken = nativeCoin,
                    srcAddress = nativeCoin.address,
                    dstAddress = "dstAddr",
                    memo = null,
                    tokenAmountInt = BigInteger("100000"),
                    gasFee = TokenValue(BigInteger("15"), nativeCoin),
                )

            assertEquals(UtxoPlanFeeResult.InsufficientUtxos, result)
        }

    @Test
    fun `resolveUtxoPlanFee returns Unavailable on a generic plan error`() = runTest {
        val nativeCoin = nativeCoinFor(Chain.Bitcoin)
        stubGetSpecific()
        coEvery { vaultRepository.get(VAULT_ID) } returns vault()

        val utxoHelper = mockk<UtxoHelper>()
        every { UtxoHelper.getHelper(any(), any()) } returns utxoHelper
        every { utxoHelper.getBitcoinTransactionPlan(any()) } returns
            Bitcoin.TransactionPlan.newBuilder()
                .setFee(0L)
                .setError(SigningError.Error_general)
                .build()

        val result =
            calculator.resolveUtxoPlanFee(
                vaultId = VAULT_ID,
                srcToken = nativeCoin,
                srcAddress = nativeCoin.address,
                dstAddress = "dstAddr",
                memo = null,
                tokenAmountInt = BigInteger("100000"),
                gasFee = TokenValue(BigInteger("15"), nativeCoin),
            )

        assertEquals(UtxoPlanFeeResult.Unavailable, result)
    }

    @Test
    fun `resolveUtxoPlanFee returns Unavailable when fetching chain-specific data fails`() =
        runTest {
            val nativeCoin = nativeCoinFor(Chain.Bitcoin)
            coEvery {
                blockChainSpecificRepository.getSpecific(
                    chain = any(),
                    address = any(),
                    token = any(),
                    gasFee = any(),
                    isSwap = any(),
                    isMaxAmountEnabled = any(),
                    isDeposit = any(),
                    gasLimit = any(),
                    dstAddress = any(),
                    tokenAmountValue = any(),
                    memo = any(),
                    transactionType = any(),
                    isThorchainRouterDeposit = any(),
                )
            } throws RuntimeException("boom")

            val result =
                calculator.resolveUtxoPlanFee(
                    vaultId = VAULT_ID,
                    srcToken = nativeCoin,
                    srcAddress = nativeCoin.address,
                    dstAddress = "dstAddr",
                    memo = null,
                    tokenAmountInt = BigInteger("100000"),
                    gasFee = TokenValue(BigInteger("15"), nativeCoin),
                )

            assertEquals(UtxoPlanFeeResult.Unavailable, result)
            verify(exactly = 0) { UtxoHelper.getHelper(any(), any()) }
        }

    // ── rebaseEvmSwapNetworkFee tests (issue #5056) ─────────────────────────

    /**
     * Regression for #5056: a native-ETH aggregator swap is signed with the route's own gas limit,
     * so the displayed fee must be re-based from the flat 600k baseline onto that limit — the bug
     * was a ~2x over-estimate. baselineGasFee = maxFeePerGas(10) × 600_000; route gas 286_146 → fee
     * 10 × 286_146.
     */
    @Test
    fun `rebaseEvmSwapNetworkFee re-bases a native ETH swap fee onto the route gas`() = runTest {
        val ethCoin = nativeCoinFor(Chain.Ethereum)
        coEvery { tokenRepository.getNativeToken(Chain.Ethereum.id) } returns ethCoin
        val capturedParams = slot<GasFeeParams>()
        val estimated = estimatedFee(ethCoin, BigInteger.valueOf(2_861_460))
        coEvery { gasFeeToEstimatedFee(capture(capturedParams)) } returns estimated

        val result =
            calculator.rebaseEvmSwapNetworkFee(
                srcToken = ethCoin,
                baselineGasFee = TokenValue(BigInteger.valueOf(6_000_000), ethCoin),
                routeGas = 286_146L,
            )

        requireNotNull(result)
        assertEquals(BigInteger.valueOf(2_861_460), result.gasFee.value)
        assertEquals(BigInteger.valueOf(2_861_460), capturedParams.captured.gasFee.value)
        assertEquals(estimated, result.estimated)
    }

    /**
     * A route with no usable gas (e.g. Jupiter's Solana quotes) leaves the baseline fee untouched.
     */
    @Test
    fun `rebaseEvmSwapNetworkFee returns null when route gas is zero`() = runTest {
        val ethCoin = nativeCoinFor(Chain.Ethereum)
        val result =
            calculator.rebaseEvmSwapNetworkFee(
                srcToken = ethCoin,
                baselineGasFee = TokenValue(BigInteger.valueOf(6_000_000), ethCoin),
                routeGas = 0L,
            )
        assertNull(result)
    }

    /**
     * ERC-20 swaps are signed with at least the 600k bond, so a sub-600k route gas lands back on
     * the baseline — return null to keep the gas-pass value (and any OP-stack L1 component)
     * unscaled.
     */
    @Test
    fun `rebaseEvmSwapNetworkFee returns null when the limit lands on the swap default`() =
        runTest {
            val erc20 = evmErc20Coin(Chain.Ethereum)
            val result =
                calculator.rebaseEvmSwapNetworkFee(
                    srcToken = erc20,
                    baselineGasFee =
                        TokenValue(BigInteger.valueOf(6_000_000), nativeCoinFor(Chain.Ethereum)),
                    routeGas = 286_146L,
                )
            assertNull(result)
        }

    /**
     * A route gas above the 600k bond re-bases up for ERC-20 swaps: 6_000_000 × 900_000 / 600_000.
     */
    @Test
    fun `rebaseEvmSwapNetworkFee re-bases up when the route gas exceeds the swap default`() =
        runTest {
            val erc20 = evmErc20Coin(Chain.Ethereum)
            val ethCoin = nativeCoinFor(Chain.Ethereum)
            coEvery { tokenRepository.getNativeToken(Chain.Ethereum.id) } returns ethCoin
            val capturedParams = slot<GasFeeParams>()
            coEvery { gasFeeToEstimatedFee(capture(capturedParams)) } returns
                estimatedFee(ethCoin, BigInteger.valueOf(9_000_000))

            val result =
                calculator.rebaseEvmSwapNetworkFee(
                    srcToken = erc20,
                    baselineGasFee = TokenValue(BigInteger.valueOf(6_000_000), ethCoin),
                    routeGas = 900_000L,
                )

            requireNotNull(result)
            assertEquals(BigInteger.valueOf(9_000_000), capturedParams.captured.gasFee.value)
        }

    /**
     * OP-stack L2s (Base/Optimism/Blast) fold an L1 data fee into the baseline, which the gas-limit
     * ratio must not scale — so the rebase is skipped there even when route gas exceeds 600k
     * (#5056, CodeRabbit). Base native floors at 600k via getGasLimit==null, and routeGas 900k
     * would otherwise rebase up.
     */
    @Test
    fun `rebaseEvmSwapNetworkFee skips OP-stack L2 chains to avoid scaling the L1 fee`() = runTest {
        val baseCoin = nativeCoinFor(Chain.Base)
        val result =
            calculator.rebaseEvmSwapNetworkFee(
                srcToken = baseCoin,
                baselineGasFee = TokenValue(BigInteger.valueOf(6_000_000), baseCoin),
                routeGas = 900_000L,
            )
        assertNull(result)
    }

    /** Native Arbitrum floors the route gas at its 400k limit: 6_000_000 × 400_000 / 600_000. */
    @Test
    fun `rebaseEvmSwapNetworkFee floors a native arbitrum swap at the arbitrum limit`() = runTest {
        val arbCoin = nativeCoinFor(Chain.Arbitrum)
        coEvery { tokenRepository.getNativeToken(Chain.Arbitrum.id) } returns arbCoin
        val capturedParams = slot<GasFeeParams>()
        coEvery { gasFeeToEstimatedFee(capture(capturedParams)) } returns
            estimatedFee(arbCoin, BigInteger.valueOf(4_000_000))

        val result =
            calculator.rebaseEvmSwapNetworkFee(
                srcToken = arbCoin,
                baselineGasFee = TokenValue(BigInteger.valueOf(6_000_000), arbCoin),
                routeGas = 100_000L,
            )

        requireNotNull(result)
        assertEquals(BigInteger.valueOf(4_000_000), capturedParams.captured.gasFee.value)
    }

    /**
     * Proof for #5121 ("SwapKit EVM verify-screen Network Fee is understated"): the displayed
     * network fee is re-based off the ORACLE gas price, never SwapKit's `fees[].inbound`
     * placeholder or its stale quote-time `gasPrice`. Fed the exact FLASHNET/NEAR fixture from the
     * issue — `tx.gas = 0x186a0` (100k), `tx.gasPrice = 0x4946111` (~0.077 gwei), inbound
     * placeholder 130 wei — against a realistic 20 gwei oracle. The re-based fee is `20 gwei × 100k
     * = 0.002 ETH`, which is neither the 130-wei placeholder nor SwapKit's `gasPrice × gas` seed
     * (~7.68e12 wei).
     *
     * Structural point the assertion encodes: `rebaseEvmSwapNetworkFee` takes only (srcToken,
     * baselineGasFee, routeGas) — the inbound placeholder is not even an input, so it cannot leak
     * into the Network Fee by construction. (`baselineGasFee` is the oracle `maxFeePerGas × 600k`
     * produced by `calculateGasFee`.)
     */
    @Test
    fun `rebaseEvmSwapNetworkFee ignores the SwapKit inbound placeholder and returns the oracle bond (#5121)`() =
        runTest {
            val ethCoin = nativeCoinFor(Chain.Ethereum)
            coEvery { tokenRepository.getNativeToken(Chain.Ethereum.id) } returns ethCoin

            // Oracle maxFeePerGas = 20 gwei; baseline = 20 gwei × 600k (the flat swap bond).
            val oracleMaxFeePerGasWei = BigInteger.valueOf(20_000_000_000L)
            val baselineGasFee =
                TokenValue(
                    oracleMaxFeePerGasWei * BigInteger.valueOf(600_000L), // 1.2e16 wei
                    ethCoin,
                )
            // Exact issue fixture (hex → decimal).
            val routeGas = 0x186a0L // 100_000
            val swapKitQuoteGasPriceWei = BigInteger.valueOf(0x4946111L) // ~0.077 gwei
            val inboundPlaceholderWei = BigInteger.valueOf(130L) // FLASHNET near-zero placeholder

            val expectedOracleBond =
                oracleMaxFeePerGasWei * BigInteger.valueOf(routeGas) // 20 gwei × 100k = 2e15 wei
            val capturedParams = slot<GasFeeParams>()
            coEvery { gasFeeToEstimatedFee(capture(capturedParams)) } returns
                estimatedFee(ethCoin, expectedOracleBond)

            val result =
                calculator.rebaseEvmSwapNetworkFee(
                    srcToken = ethCoin,
                    baselineGasFee = baselineGasFee,
                    routeGas = routeGas,
                )

            requireNotNull(result)
            // Network Fee = oracle bond (0.002 ETH), the fee the signed tx actually commits to.
            assertEquals(expectedOracleBond, result.gasFee.value)
            assertEquals(expectedOracleBond, capturedParams.captured.gasFee.value)
            // …and provably NOT the SwapKit inbound placeholder…
            assertTrue(result.gasFee.value != inboundPlaceholderWei)
            // …nor SwapKit's stale quote-time `gasPrice × gas` seed (the value iOS #4707
            // mis-showed).
            assertTrue(
                result.gasFee.value != swapKitQuoteGasPriceWei * BigInteger.valueOf(routeGas)
            )
        }

    // ── evmSwapDisplayGasLimit: the shared floor used by both initiator and joiner (#5056) ──

    @Test
    fun `evmSwapDisplayGasLimit returns route gas for native ETH above the 40k floor`() {
        assertEquals(
            BigInteger.valueOf(286_146),
            evmSwapDisplayGasLimit(nativeCoinFor(Chain.Ethereum), 286_146L),
        )
    }

    @Test
    fun `evmSwapDisplayGasLimit floors native Arbitrum at its 400k limit`() {
        assertEquals(
            BigInteger.valueOf(400_000),
            evmSwapDisplayGasLimit(nativeCoinFor(Chain.Arbitrum), 100_000L),
        )
    }

    @Test
    fun `evmSwapDisplayGasLimit returns null for ERC-20 at or below the 600k default`() {
        assertNull(evmSwapDisplayGasLimit(evmErc20Coin(Chain.Ethereum), 286_146L))
    }

    @Test
    fun `evmSwapDisplayGasLimit returns route gas for ERC-20 above the 600k default`() {
        assertEquals(
            BigInteger.valueOf(900_000),
            evmSwapDisplayGasLimit(evmErc20Coin(Chain.Ethereum), 900_000L),
        )
    }

    @Test
    fun `evmSwapDisplayGasLimit returns null for OP-stack L2s and for zero route gas`() {
        assertNull(evmSwapDisplayGasLimit(nativeCoinFor(Chain.Base), 900_000L))
        assertNull(evmSwapDisplayGasLimit(nativeCoinFor(Chain.Ethereum), 0L))
    }

    private fun stubGetSpecific() {
        coEvery {
            blockChainSpecificRepository.getSpecific(
                chain = any(),
                address = any(),
                token = any(),
                gasFee = any(),
                isSwap = any(),
                isMaxAmountEnabled = any(),
                isDeposit = any(),
                gasLimit = any(),
                dstAddress = any(),
                tokenAmountValue = any(),
                memo = any(),
                transactionType = any(),
                isThorchainRouterDeposit = any(),
            )
        } returns utxoSpecific()
    }

    private fun utxoSpecific() =
        BlockChainSpecificAndUtxo(
            blockChainSpecific =
                BlockChainSpecific.UTXO(byteFee = BigInteger("15"), sendMaxAmount = false),
            utxos = emptyList(),
        )

    private fun stubCommon(chain: Chain, nativeCoin: Coin, fee: BasicFee) {
        coEvery { vaultRepository.get(VAULT_ID) } returns vault()
        coEvery { feeServiceComposite.calculateFees(any()) } returns fee
        coEvery { tokenRepository.getNativeToken(chain.id) } returns nativeCoin
    }

    companion object {
        private const val VAULT_ID = "test-vault-id"

        private fun vault() =
            Vault(
                id = VAULT_ID,
                name = "test-vault",
                pubKeyECDSA = "02" + "00".repeat(32),
                hexChainCode = "11".repeat(32),
                localPartyID = "local-party",
            )

        private fun nativeCoinFor(chain: Chain): Coin {
            val ticker =
                when (chain) {
                    Chain.Bitcoin -> "BTC"
                    Chain.BitcoinCash -> "BCH"
                    Chain.Litecoin -> "LTC"
                    Chain.Dogecoin -> "DOGE"
                    Chain.Dash -> "DASH"
                    Chain.Zcash -> "ZEC"
                    Chain.Cardano -> "ADA"
                    Chain.Ethereum -> "ETH"
                    else -> chain.raw
                }
            return Coin(
                chain = chain,
                ticker = ticker,
                logo = ticker.lowercase(),
                address = "${ticker}addr",
                decimal = if (chain == Chain.Ethereum) 18 else 8,
                hexPublicKey = "hex",
                priceProviderID = ticker.lowercase(),
                contractAddress = "",
                isNativeToken = true,
            )
        }

        private fun evmErc20Coin(chain: Chain): Coin =
            Coin(
                chain = chain,
                ticker = "USDC",
                logo = "usdc",
                address = "0xerc20",
                decimal = 6,
                hexPublicKey = "hex",
                priceProviderID = "usd-coin",
                contractAddress = "0xtoken",
                isNativeToken = false,
            )

        private fun utxoSendSrc(chain: Chain): SendSrc = buildSendSrc(nativeCoinFor(chain))

        private fun ethSendSrc(): SendSrc = buildSendSrc(nativeCoinFor(Chain.Ethereum))

        private fun buildSendSrc(coin: Coin): SendSrc {
            val account =
                Account(
                    token = coin,
                    tokenValue = TokenValue(BigInteger("500000000"), coin),
                    fiatValue = null,
                    price = null,
                )
            val address =
                Address(chain = coin.chain, address = coin.address, accounts = listOf(account))
            return SendSrc(address, account)
        }

        private fun estimatedFee(token: Coin, value: BigInteger): EstimatedGasFee =
            EstimatedGasFee(
                formattedTokenValue = "$value ${token.ticker}",
                formattedFiatValue = "$0.00",
                tokenValue = TokenValue(value = value, token = token),
                fiatValue = FiatValue(BigDecimal.ZERO, "USD"),
            )
    }
}
