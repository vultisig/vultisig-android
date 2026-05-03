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
