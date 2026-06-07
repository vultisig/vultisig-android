package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

internal class SwapValidatorTest {

    private val validator = SwapValidator()

    @Test
    fun `validateSwapPreflight passes for a native swap covered by balance plus fee`() {
        val src = nativeSendSrc(balance = BigInteger("1000"))

        val error =
            validator.validateSwapPreflight(
                selectedSrc = src,
                srcAmountValue = BigInteger("500"),
                selectedSrcBalance = BigInteger("1000"),
                estimatedNetworkFeeTokenValue = TokenValue(BigInteger("100"), src.account.token),
            )

        assertNull(error)
    }

    @Test
    fun `validateSwapPreflight flags a native swap when amount plus fee exceeds balance`() {
        val src = nativeSendSrc(balance = BigInteger("1000"))

        val error =
            validator.validateSwapPreflight(
                selectedSrc = src,
                srcAmountValue = BigInteger("950"),
                selectedSrcBalance = BigInteger("1000"),
                estimatedNetworkFeeTokenValue = TokenValue(BigInteger("100"), src.account.token),
            )

        assertEquals(
            UiText.FormattedText(
                R.string.swap_error_insufficient_balance_and_fees,
                listOf(src.account.token.ticker),
            ),
            error,
        )
    }

    @Test
    fun `validateSwapPreflight reports no native token when the gas account is missing`() {
        val src = tokenSendSrc(balance = BigInteger("1000"), nativeBalance = null)

        val error =
            validator.validateSwapPreflight(
                selectedSrc = src,
                srcAmountValue = BigInteger("500"),
                selectedSrcBalance = BigInteger("1000"),
                estimatedNetworkFeeTokenValue = TokenValue(BigInteger("100"), nativeCoin()),
            )

        assertEquals(UiText.StringResource(R.string.send_error_no_token), error)
    }

    @Test
    fun `validateSwapPreflight flags an insufficient source token balance`() {
        val src = tokenSendSrc(balance = BigInteger("400"), nativeBalance = BigInteger("1000"))

        val error =
            validator.validateSwapPreflight(
                selectedSrc = src,
                srcAmountValue = BigInteger("500"),
                selectedSrcBalance = BigInteger("400"),
                estimatedNetworkFeeTokenValue = TokenValue(BigInteger("100"), nativeCoin()),
            )

        assertEquals(
            UiText.FormattedText(
                R.string.swap_error_insufficient_source_token,
                listOf(src.account.token.ticker),
            ),
            error,
        )
    }

    @Test
    fun `validateSwapPreflight flags insufficient native balance for gas fees`() {
        val src = tokenSendSrc(balance = BigInteger("1000"), nativeBalance = BigInteger("50"))

        val error =
            validator.validateSwapPreflight(
                selectedSrc = src,
                srcAmountValue = BigInteger("500"),
                selectedSrcBalance = BigInteger("1000"),
                estimatedNetworkFeeTokenValue = TokenValue(BigInteger("100"), nativeCoin()),
            )

        assertEquals(
            UiText.FormattedText(
                R.string.swap_error_insufficient_gas_fees,
                listOf("${nativeCoin().ticker} (${nativeCoin().chain.raw})"),
            ),
            error,
        )
    }

    @Test
    fun `validateSwapPreflight passes for a token swap with sufficient source and gas balances`() {
        val src = tokenSendSrc(balance = BigInteger("1000"), nativeBalance = BigInteger("1000"))

        val error =
            validator.validateSwapPreflight(
                selectedSrc = src,
                srcAmountValue = BigInteger("500"),
                selectedSrcBalance = BigInteger("1000"),
                estimatedNetworkFeeTokenValue = TokenValue(BigInteger("100"), nativeCoin()),
            )

        assertNull(error)
    }

    private fun nativeCoin(): Coin =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "eth",
            address = "0xnative",
            decimal = 18,
            hexPublicKey = "hex",
            priceProviderID = "eth",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun tokenCoin(): Coin =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDC",
            logo = "usdc",
            address = "0xtoken",
            decimal = 6,
            hexPublicKey = "hex",
            priceProviderID = "usdc",
            contractAddress = "0xcontract",
            isNativeToken = false,
        )

    private fun nativeSendSrc(balance: BigInteger): SendSrc {
        val coin = nativeCoin()
        val account = Account(token = coin, tokenValue = TokenValue(balance, coin), null, null)
        val address =
            Address(chain = coin.chain, address = coin.address, accounts = listOf(account))
        return SendSrc(address, account)
    }

    private fun tokenSendSrc(balance: BigInteger, nativeBalance: BigInteger?): SendSrc {
        val token = tokenCoin()
        val native = nativeCoin()
        val tokenAccount =
            Account(token = token, tokenValue = TokenValue(balance, token), null, null)
        val nativeAccount =
            Account(
                token = native,
                tokenValue = nativeBalance?.let { TokenValue(it, native) },
                fiatValue = null,
                price = null,
            )
        val address =
            Address(
                chain = token.chain,
                address = token.address,
                accounts = listOf(tokenAccount, nativeAccount),
            )
        return SendSrc(address, tokenAccount)
    }
}
