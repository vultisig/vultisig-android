package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ChainValidationServiceTest {

    private val service = ChainValidationService()

    // region validateSlippage

    @Test
    fun `validateSlippage - null returns required error`() {
        assertTrue(service.validateSlippage(null) is UiText.StringResource)
    }

    @Test
    fun `validateSlippage - blank returns required error`() {
        assertTrue(service.validateSlippage("  ") is UiText.StringResource)
    }

    @Test
    fun `validateSlippage - valid value returns null`() {
        assertNull(service.validateSlippage("1.0"))
    }

    @Test
    fun `validateSlippage - value above 100 returns invalid error`() {
        assertTrue(service.validateSlippage("101") is UiText.StringResource)
    }

    @Test
    fun `validateSlippage - negative value returns invalid error`() {
        assertTrue(service.validateSlippage("-1") is UiText.StringResource)
    }

    @Test
    fun `validateSlippage - non-numeric returns format error`() {
        assertTrue(service.validateSlippage("abc") is UiText.StringResource)
    }

    // endregion

    // region formatSlippage

    @Test
    fun `formatSlippage - converts 1 percent to decimal`() {
        assertEquals("0.01", service.formatSlippage("1.0"))
    }

    @Test
    fun `formatSlippage - invalid input returns default`() {
        assertEquals("0.01", service.formatSlippage("invalid"))
    }

    // endregion

    // region checkIsReapable

    private val dotCoin =
        Coin(
            chain = Chain.Polkadot,
            ticker = "DOT",
            logo = "",
            address = "",
            decimal = 10,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    @Test
    fun `checkIsReapable - null account returns null`() {
        val gasFee = TokenValue(BigInteger.ONE, "DOT", 10)
        assertNull(service.checkIsReapable(null, dotCoin, "1.0", gasFee))
    }

    @Test
    fun `checkIsReapable - polkadot sufficient balance returns null`() {
        // 20 DOT balance, sending 5 DOT, 0.1 DOT fee → 14.9 DOT remaining > 1 DOT threshold
        val balance = BigInteger.valueOf(200_000_000_000L) // 20 DOT (10 decimals)
        val account = Account(dotCoin, TokenValue(balance, "DOT", 10), null, null)
        val gasFee = TokenValue(BigInteger.valueOf(1_000_000_000L), "DOT", 10) // 0.1 DOT
        assertNull(service.checkIsReapable(account, dotCoin, "5.0", gasFee))
    }

    @Test
    fun `checkIsReapable - polkadot balance below existential deposit returns warning`() {
        // 11 DOT balance, sending 10 DOT, 0.5 DOT fee → 0.5 DOT remaining < 1 DOT threshold
        val balance = BigInteger.valueOf(110_000_000_000L) // 11 DOT
        val account = Account(dotCoin, TokenValue(balance, "DOT", 10), null, null)
        val gasFee = TokenValue(BigInteger.valueOf(5_000_000_000L), "DOT", 10) // 0.5 DOT
        val result = service.checkIsReapable(account, dotCoin, "10.0", gasFee)
        assertTrue(result is UiText.StringResource)
    }

    @Test
    fun `checkIsReapable - non-reaping chain returns null`() {
        val ethCoin = dotCoin.copy(chain = Chain.Ethereum, ticker = "ETH")
        val account = Account(ethCoin, TokenValue(BigInteger.ONE, "ETH", 18), null, null)
        val gasFee = TokenValue(BigInteger.ONE, "ETH", 18)
        assertNull(service.checkIsReapable(account, ethCoin, "0.001", gasFee))
    }

    // endregion
}
