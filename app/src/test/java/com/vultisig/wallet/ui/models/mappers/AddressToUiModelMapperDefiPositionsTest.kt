@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DefiChainUiModel
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The DeFi home row counts the vault's active positions on the chain; the wallet row keeps counting
 * the tokens it tracks there.
 */
internal class AddressToUiModelMapperDefiPositionsTest {

    private val fiatValueToStringMapper =
        mockk<FiatValueToStringMapper>().also {
            coEvery { it.invoke(any(), any(), any()) } returns "$1.00"
        }

    private val tokenValueToStringWithUnitMapper =
        mockk<TokenValueToStringWithUnitMapper>().also {
            every { it.invoke(any()) } returns "1 RUNE"
        }

    private val chainToDefiChainUiMapper =
        mockk<ChainToDefiChainUiMapper>().also {
            every { it.invoke(any()) } answers
                {
                    val chain = firstArg<Chain>()
                    DefiChainUiModel(logo = 0, raw = chain.raw, chain = chain)
                }
        }

    private val mapper =
        AddressToUiModelMapperImpl(
            fiatValueToStringMapper = fiatValueToStringMapper,
            mapTokenValueToStringWithUnitMapper = tokenValueToStringWithUnitMapper,
            chainToDefiChainUiMapper = chainToDefiChainUiMapper,
        )

    private fun coin(ticker: String, isNativeToken: Boolean = false) =
        Coin(
            chain = Chain.ThorChain,
            ticker = ticker,
            logo = "",
            address = "thor1abc",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = isNativeToken,
        )

    private fun account(
        ticker: String,
        amount: BigInteger?,
        isNativeToken: Boolean = false,
        defiPositionsCount: Int? = null,
    ) =
        Account(
            token = coin(ticker, isNativeToken),
            tokenValue = amount?.let { TokenValue(it, ticker, 8) },
            fiatValue = amount?.let { FiatValue(BigDecimal.ONE, "USD") },
            price = null,
            defiPositionsCount = defiPositionsCount,
        )

    private fun address(isDefiProvider: Boolean, vararg accounts: Account) =
        Address(
            chain = Chain.ThorChain,
            address = "thor1abc",
            accounts = accounts.toList(),
            isDefiProvider = isDefiProvider,
        )

    @Test
    fun `a defi row reports its funded positions, not its token count`() = runTest {
        val model =
            mapper(
                address(
                    isDefiProvider = true,
                    account("RUNE", BigInteger("100"), isNativeToken = true),
                    account("TCY", BigInteger("50")),
                    account("RUJI", BigInteger.ZERO),
                    account("USDC", BigInteger.ZERO),
                )
            )

        model.defiPositionsCount shouldBe 2
        model.assetsSize shouldBe 4
        model.isDeFiProvider shouldBe true
    }

    @Test
    fun `a defi row with nothing staked reports no positions`() = runTest {
        val model =
            mapper(
                address(
                    isDefiProvider = true,
                    account("RUNE", BigInteger.ZERO, isNativeToken = true),
                    account("TCY", BigInteger.ZERO),
                )
            )

        model.defiPositionsCount shouldBe 0
    }

    @Test
    fun `a defi row reports multiple positions behind one token balance`() = runTest {
        val model =
            mapper(
                address(
                    isDefiProvider = true,
                    account("RUNE", BigInteger.ZERO, isNativeToken = true),
                    account("USDC", BigInteger("150"), defiPositionsCount = 2),
                )
            )

        model.defiPositionsCount shouldBe 2
        model.assetsSize shouldBe 2
    }

    @Test
    fun `a defi row still resolving reports an unknown count`() = runTest {
        val model =
            mapper(
                address(
                    isDefiProvider = true,
                    account("RUNE", null, isNativeToken = true),
                    account("TCY", null),
                )
            )

        model.defiPositionsCount shouldBe null
    }

    @Test
    fun `a wallet row carries no position count`() = runTest {
        val model =
            mapper(
                address(
                    isDefiProvider = false,
                    account("RUNE", BigInteger("100"), isNativeToken = true),
                    account("TCY", BigInteger("50")),
                )
            )

        model.defiPositionsCount shouldBe null
        model.assetsSize shouldBe 2
        model.isDeFiProvider shouldBe false
    }
}
