@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AddressParserRepository
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AccountValidatorTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private val tokenAmountFieldState = TextFieldState()
    private val addressFieldState = TextFieldState()
    private val gasFee = MutableStateFlow<TokenValue?>(null)
    private val addressParserRepository: AddressParserRepository = mockk(relaxed = true)

    private var vaultId: String? = "vault-1"
    private var account: Account? = null

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `validate throws no_token when vaultId is null`() = runTest {
        vaultId = null
        val ex = assertFailsWith<InvalidTransactionDataException> { build().validate() }
        assertEquals(R.string.send_error_no_token, ex.text.stringId())
    }

    @Test
    fun `validate throws no_token when selectedAccount is null`() = runTest {
        account = null
        val ex = assertFailsWith<InvalidTransactionDataException> { build().validate() }
        assertEquals(R.string.send_error_no_token, ex.text.stringId())
    }

    @Test
    fun `validate throws no_amount when tokenAmount is empty`() = runTest {
        account = ethAccount()
        val ex = assertFailsWith<InvalidTransactionDataException> { build().validate() }
        assertEquals(R.string.send_error_no_amount, ex.text.stringId())
    }

    @Test
    fun `validate throws no_amount when tokenAmount is non-positive`() = runTest {
        account = ethAccount()
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0")
        val ex = assertFailsWith<InvalidTransactionDataException> { build().validate() }
        assertEquals(R.string.send_error_no_amount, ex.text.stringId())
    }

    @Test
    fun `validate throws no_gas_fee when gasFee is zero on a non-zero-gas chain`() = runTest {
        account = ethAccount()
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")
        gasFee.value = TokenValue(value = BigInteger.ZERO, token = ethCoin())
        val ex = assertFailsWith<InvalidTransactionDataException> { build().validate() }
        assertEquals(R.string.send_error_no_gas_fee, ex.text.stringId())
    }

    @Test
    fun `validate returns ValidatedAccount on happy path`() = runTest {
        account = ethAccount()
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")
        addressFieldState.setTextAndPlaceCursorAtEnd("0xabc123")
        gasFee.value = TokenValue(value = BigInteger.valueOf(21000), token = ethCoin())
        coEvery { addressParserRepository.resolveName("0xabc123", Chain.Ethereum) } returns
            "0xresolved"

        val result = build().validate()

        assertEquals("vault-1", result.vaultId)
        assertEquals(Chain.Ethereum, result.chain)
        assertEquals("0xresolved", result.dstAddress)
        assertEquals(BigInteger.valueOf(21000), result.gasFee.value)
    }

    @Test
    fun `validate wraps resolveName failure in failed_to_resolve_address`() = runTest {
        account = ethAccount()
        tokenAmountFieldState.setTextAndPlaceCursorAtEnd("0.5")
        addressFieldState.setTextAndPlaceCursorAtEnd("notanaddr.eth")
        gasFee.value = TokenValue(value = BigInteger.valueOf(21000), token = ethCoin())
        coEvery { addressParserRepository.resolveName(any(), any()) } throws
            RuntimeException("ENS lookup failed")

        val ex = assertFailsWith<InvalidTransactionDataException> { build().validate() }
        assertEquals(R.string.failed_to_resolve_address, ex.text.stringId())
    }

    @Test
    fun `awaitGasFee returns cached value immediately`() = runTest {
        account = ethAccount()
        gasFee.value = TokenValue(value = BigInteger.valueOf(7), token = ethCoin())
        val result = build().awaitGasFee()
        assertEquals(BigInteger.valueOf(7), result.value)
    }

    private fun build(): AccountValidator =
        AccountValidator(
            vaultIdProvider = { vaultId },
            selectedAccountProvider = { account },
            tokenAmountFieldState = tokenAmountFieldState,
            addressFieldState = addressFieldState,
            gasFee = gasFee,
            addressParserRepository = addressParserRepository,
        )

    private fun ethAccount(): Account =
        Account(
            token = ethCoin(),
            tokenValue = TokenValue(BigInteger.valueOf(1_000_000_000_000_000_000), ethCoin()),
            fiatValue = null,
            price = null,
        )

    private fun ethCoin(): Coin =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "",
            address = "0xself",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun UiText?.stringId(): Int? = (this as? UiText.StringResource)?.resId
}
