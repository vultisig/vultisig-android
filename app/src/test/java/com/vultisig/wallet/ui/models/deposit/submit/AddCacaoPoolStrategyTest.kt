package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.models.deposit.DepositFieldStates
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AddCacaoPoolStrategyTest {

    private val fields = DepositFieldStates()
    private val accountsRepository: AccountsRepository = mockk()
    private val specificRepo: BlockChainSpecificRepository = mockk()
    private val cacao = gateCoin(Chain.MayaChain, "CACAO", "maya1self", decimal = 10)

    @BeforeEach
    fun setUp() {
        // The repository's first emission carries no balances, as in the app.
        coEvery { accountsRepository.loadAddress(GATE_VAULT_ID, Chain.MayaChain) } returns
            flowOf(gateAddress(gateAccount(cacao, balance = null)))
        coEvery {
            specificRepo.getSpecific(any(), any(), any(), any(), any(), any(), any())
        } returns BlockChainSpecificAndUtxo(mockk(relaxed = true))
    }

    @Test
    fun `an amount that leaves no room for the fee is refused`() = runTest {
        fields.tokenAmountFieldState.edit { replace(0, length, "10") }

        val error =
            assertFailsWith<InvalidTransactionDataException> {
                build(formBalance = BigInteger.TEN.pow(11)).build()
            }

        assertInsufficientBalance(error)
    }

    @Test
    fun `an amount the balance covers with the fee builds the pool deposit`() = runTest {
        fields.tokenAmountFieldState.edit { replace(0, length, "9") }

        val tx = build(formBalance = BigInteger.TEN.pow(11)).build()

        assertEquals(BigInteger("90000000000"), tx.srcTokenValue.value)
        assertEquals("POOL+", tx.memo)
        assertEquals("", tx.dstAddress)
        assertEquals(GAS, tx.estimatedFees.value)
    }

    private fun build(formBalance: BigInteger): AddCacaoPoolStrategy {
        val formAddress: Address = gateAddress(gateAccount(cacao, formBalance))
        return AddCacaoPoolStrategy(
            vaultIdProvider = { GATE_VAULT_ID },
            chainProvider = { Chain.MayaChain },
            tokenAmountFieldState = fields.tokenAmountFieldState,
            accountsRepository = accountsRepository,
            addressProvider = { formAddress },
            requireTokenAmount = amountGate(Chain.MayaChain, fields)::requireTokenAmount,
            blockChainSpecificRepository = specificRepo,
            calculateGasFee = { _, coin, _ -> TokenValue(GAS, coin) },
            getFeesFiatValue = { _, gasFee, _ -> gateEstimatedFee(gasFee) },
        )
    }

    private companion object {
        val GAS: BigInteger = BigInteger.ONE
    }
}
