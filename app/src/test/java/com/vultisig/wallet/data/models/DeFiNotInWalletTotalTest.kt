package com.vultisig.wallet.data.models

import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import org.junit.jupiter.api.Test

internal class DeFiNotInWalletTotalTest {

    private fun usd(value: Int) = FiatValue(BigDecimal(value), "USD")

    @Test
    fun `unknown wallet total gives no combined total`() {
        combinedWalletAndDeFiTotal(wallet = null, deFi = usd(5)) shouldBe null
    }

    @Test
    fun `nothing resolved on the DeFi side adds nothing`() {
        combinedWalletAndDeFiTotal(wallet = usd(100), deFi = null) shouldBe usd(100)
    }

    @Test
    fun `wallet and DeFi in the same currency are summed`() {
        combinedWalletAndDeFiTotal(wallet = usd(100), deFi = usd(25)) shouldBe usd(125)
    }

    @Test
    fun `sides in different currencies give no combined total`() {
        combinedWalletAndDeFiTotal(wallet = usd(100), deFi = FiatValue(BigDecimal(25), "EUR")) shouldBe
            null
    }
}
