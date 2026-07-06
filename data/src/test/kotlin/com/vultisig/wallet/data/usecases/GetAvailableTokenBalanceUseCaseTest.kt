package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class GetAvailableTokenBalanceUseCaseTest {

    private val useCase = GetAvailableTokenBalanceUseCaseImpl()

    private fun coin(
        chain: Chain,
        ticker: String,
        contractAddress: String,
        isNativeToken: Boolean,
    ) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = isNativeToken,
        )

    private fun account(coin: Coin, balance: BigInteger) =
        Account(
            token = coin,
            tokenValue = TokenValue(value = balance, unit = coin.ticker, decimals = coin.decimal),
            fiatValue = null,
            price = null,
        )

    @Test
    fun `native token reserves the gas cost`() = runTest {
        val acc =
            account(coin(Chain.TerraClassic, "LUNC", "", isNativeToken = true), BigInteger("1000"))
        val result = useCase(acc, BigInteger("250"))
        assertEquals(BigInteger("750"), result?.value)
    }

    @Test
    fun `terra classic bank denom reserves the fee from its own balance`() = runTest {
        // USTC pays gas + burn tax in uusd, so the fee must be reserved out of the USTC balance
        // or a MAX send would sign amount+fee > balance and the chain would reject it.
        val acc =
            account(
                coin(Chain.TerraClassic, "USTC", "uusd", isNativeToken = false),
                BigInteger("1000"),
            )
        val result = useCase(acc, BigInteger("250"))
        assertEquals(BigInteger("750"), result?.value)
    }

    @Test
    fun `terra classic cw20 token reserves nothing (fee paid in native LUNC)`() = runTest {
        val acc =
            account(
                coin(Chain.TerraClassic, "ASTROC", "terra1xyz", isNativeToken = false),
                BigInteger("1000"),
            )
        val result = useCase(acc, BigInteger("250"))
        assertEquals(BigInteger("1000"), result?.value)
    }

    @Test
    fun `non-native token on another chain reserves nothing`() = runTest {
        // An ERC20 contract address must NOT be mistaken for a Terra Classic bank denom.
        val acc =
            account(
                coin(
                    Chain.Ethereum,
                    "USDC",
                    "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                    isNativeToken = false,
                ),
                BigInteger("1000"),
            )
        val result = useCase(acc, BigInteger("250"))
        assertEquals(BigInteger("1000"), result?.value)
    }

    @Test
    fun `reserved balance is floored at zero when the fee exceeds the balance`() = runTest {
        val acc =
            account(
                coin(Chain.TerraClassic, "USTC", "uusd", isNativeToken = false),
                BigInteger("100"),
            )
        val result = useCase(acc, BigInteger("250"))
        assertEquals(BigInteger.ZERO, result?.value)
    }

    @Test
    fun `polkadot native token reserves the existential deposit in addition to gas`() = runTest {
        // 1 DOT balance, 0.005 DOT gas → available should exclude the 0.01 DOT (Asset Hub) ED too
        val acc =
            account(
                coin(Chain.Polkadot, "DOT", "", isNativeToken = true),
                BigInteger.valueOf(10_000_000_000L),
            )
        val result = useCase(acc, BigInteger.valueOf(50_000_000L))
        // 10_000_000_000 - 50_000_000 (gas) - 100_000_000 (ED) = 9_850_000_000
        assertEquals(BigInteger.valueOf(9_850_000_000L), result?.value)
    }

    @Test
    fun `polkadot available balance is floored at zero when gas plus existential deposit exceed balance`() =
        runTest {
            val acc =
                account(
                    coin(Chain.Polkadot, "DOT", "", isNativeToken = true),
                    BigInteger.valueOf(100_000_000L),
                )
            val result = useCase(acc, BigInteger.valueOf(50_000_000L))
            assertEquals(BigInteger.ZERO, result?.value)
        }

    @Test
    fun `ripple native token reserves only gas, not an additional existential deposit`() = runTest {
        // RippleApi#getBalance already nets the live account reserve out of tokenValue before it
        // reaches this use case, so subtracting the ED constant again here would double-reserve
        // and under-fill MAX/percentage XRP sends.
        val acc =
            account(
                coin(Chain.Ripple, "XRP", "", isNativeToken = true),
                BigInteger.valueOf(5_000_000L),
            )
        val result = useCase(acc, BigInteger.valueOf(15L))
        assertEquals(BigInteger.valueOf(4_999_985L), result?.value)
    }
}
