package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.models.deposit.DepositFieldStates
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class CustomStrategyTest {

    private val fields = DepositFieldStates()
    private val specificRepo: BlockChainSpecificRepository = mockk()
    private val rune = gateCoin(Chain.ThorChain, "RUNE", RUNE_ADDRESS)
    private val tcy = gateCoin(Chain.ThorChain, "TCY", RUNE_ADDRESS, isNative = false)

    @BeforeEach
    fun setUp() {
        fields.customMemoFieldState.edit { replace(0, length, MEMO) }
        coEvery {
            specificRepo.getSpecific(any(), any(), any(), any(), any(), any(), any())
        } returns BlockChainSpecificAndUtxo(mockk(relaxed = true))
    }

    @Test
    fun `a RUNE amount that leaves no room for the fee is refused`() = runTest {
        enterAmount("1")
        val runeAccount = gateAccount(rune, ONE_COIN)

        val error =
            assertFailsWith<InvalidTransactionDataException> {
                build(rune, runeAccount, gateAddress(runeAccount)).build()
            }

        assertInsufficientBalance(error)
    }

    @Test
    fun `a RUNE amount the balance covers with the fee builds the memo deposit`() = runTest {
        enterAmount("1")
        val runeAccount = gateAccount(rune, ONE_COIN + GAS)

        val tx = build(rune, runeAccount, gateAddress(runeAccount)).build()

        assertEquals(ONE_COIN, tx.srcTokenValue.value)
        assertEquals(MEMO, tx.memo)
        assertEquals("", tx.dstAddress)
    }

    @Test
    fun `a zero amount memo builds without consulting any balance`() = runTest {
        enterAmount("0")

        val tx = build(rune, selectedAccount = null, formAddress = null).build()

        assertEquals(BigInteger.ZERO, tx.srcTokenValue.value)
        assertEquals(MEMO, tx.memo)
    }

    @Test
    fun `a token amount above the token balance is refused`() = runTest {
        enterAmount("2")
        val tcyAccount = gateAccount(tcy, ONE_COIN)
        val address = gateAddress(gateAccount(rune, ONE_COIN), tcyAccount)

        val error =
            assertFailsWith<InvalidTransactionDataException> {
                build(tcy, tcyAccount, address).build()
            }

        assertInsufficientBalance(error)
    }

    @Test
    fun `a token amount with too little RUNE for the fee is refused`() = runTest {
        enterAmount("1")
        val tcyAccount = gateAccount(tcy, ONE_COIN)
        val address = gateAddress(gateAccount(rune, BigInteger.ZERO), tcyAccount)

        val error =
            assertFailsWith<InvalidTransactionDataException> {
                build(tcy, tcyAccount, address).build()
            }

        val text = error.text
        assertTrue(text is UiText.FormattedText)
        assertEquals(R.string.insufficient_native_token, text.resId)
    }

    private fun enterAmount(amount: String) {
        fields.tokenAmountFieldState.edit { replace(0, length, amount) }
    }

    private fun build(
        selectedToken: Coin,
        selectedAccount: Account?,
        formAddress: Address?,
    ): CustomStrategy =
        CustomStrategy(
            vaultIdProvider = { GATE_VAULT_ID },
            chainProvider = { Chain.ThorChain },
            selectedTokenProvider = { selectedToken },
            selectedAccountProvider = { selectedAccount },
            addressProvider = { formAddress },
            customMemoFieldState = fields.customMemoFieldState,
            tokenAmountFieldState = fields.tokenAmountFieldState,
            requireTokenAmount = amountGate(Chain.ThorChain, fields)::requireTokenAmount,
            blockChainSpecificRepository = specificRepo,
            calculateGasFee = { _, _, _ -> TokenValue(GAS, rune) },
            getFeesFiatValue = { _, gasFee, _ -> gateEstimatedFee(gasFee) },
        )

    private companion object {
        const val RUNE_ADDRESS = "thor1self"
        const val MEMO = "=:THOR.RUNE:thor1self"
        val ONE_COIN: BigInteger = BigInteger.TEN.pow(8)
        val GAS: BigInteger = BigInteger.ONE
    }
}
