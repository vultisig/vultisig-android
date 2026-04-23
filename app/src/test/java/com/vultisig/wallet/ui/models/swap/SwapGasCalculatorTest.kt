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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
     * Regression test for #4164: `GasCalculationResult.gasFee` MUST remain the per-byte rate so
     * WalletCore's `setByteFee(...)` receives sats/byte (not total sats) at signing. The plan's
     * total fee goes to `estimated` for display only.
     */
    @Test
    fun `utxo swap keeps gasFee as per-byte rate and routes plan fee only into estimated`() =
        runTest {
            val byteFeeRate = BigInteger("15") // sats/byte
            val planTotalFee = 4200L // total sats for the built tx
            val sendSrc = dogeSendSrc()
            val nativeDoge = sendSrc.account.token

            coEvery { vaultRepository.get(VAULT_ID) } returns vault()
            coEvery { feeServiceComposite.calculateFees(any()) } returns BasicFee(byteFeeRate)
            coEvery { tokenRepository.getNativeToken(Chain.Dogecoin.id) } returns nativeDoge
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
            val estimatedTotal =
                EstimatedGasFee(
                    formattedTokenValue = "$planTotalFee DOGE",
                    formattedFiatValue = "$0.00",
                    tokenValue =
                        TokenValue(value = BigInteger.valueOf(planTotalFee), token = nativeDoge),
                    fiatValue = FiatValue(BigDecimal.ZERO, "USD"),
                )
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
     * calculation — otherwise callers see a stale zero estimate while signing would fail later.
     */
    @Test
    fun `utxo swap returns null when bitcoin plan fails`() = runTest {
        val sendSrc = dogeSendSrc()
        val nativeDoge = sendSrc.account.token

        coEvery { vaultRepository.get(VAULT_ID) } returns vault()
        coEvery { feeServiceComposite.calculateFees(any()) } returns BasicFee(BigInteger("10"))
        coEvery { tokenRepository.getNativeToken(Chain.Dogecoin.id) } returns nativeDoge
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

        assertEquals(null, result)
    }

    companion object {
        private const val VAULT_ID = "test-vault-id"

        private val DOGE_COIN =
            Coin(
                chain = Chain.Dogecoin,
                ticker = "DOGE",
                logo = "doge",
                address = "DOGEaddr",
                decimal = 8,
                hexPublicKey = "hex",
                priceProviderID = "dogecoin",
                contractAddress = "",
                isNativeToken = true,
            )

        private fun vault() =
            Vault(
                id = VAULT_ID,
                name = "test-vault",
                pubKeyECDSA = "02" + "00".repeat(32),
                hexChainCode = "11".repeat(32),
                localPartyID = "local-party",
            )

        private fun dogeSendSrc(): SendSrc {
            val account =
                Account(
                    token = DOGE_COIN,
                    tokenValue = TokenValue(BigInteger("500000000"), DOGE_COIN),
                    fiatValue = null,
                    price = null,
                )
            val address =
                Address(
                    chain = Chain.Dogecoin,
                    address = DOGE_COIN.address,
                    accounts = listOf(account),
                )
            return SendSrc(address, account)
        }
    }
}
