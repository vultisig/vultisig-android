package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import java.math.BigInteger
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class ExceedsStakeBalanceTest {

    private val token = Coins.ThorChain.RUJI

    /** [displayBalance] is deliberately locale-formatted: the check must use the raw value. */
    private fun model(
        raw: BigInteger,
        displayBalance: String,
        defiType: DeFiNavActions? = DeFiNavActions.STAKE_RUJI,
    ): SendFormUiModel {
        val account =
            Account(
                token = token,
                tokenValue = TokenValue(raw, token),
                fiatValue = null,
                price = null,
            )
        return SendFormUiModel(
            defiType = defiType,
            selectedCoin =
                TokenBalanceUiModel(
                    model = SendSrc(Address(token.chain, token.address, listOf(account)), account),
                    title = token.ticker,
                    balance = displayBalance,
                    fiatValue = null,
                    isNativeToken = false,
                    isLayer2 = false,
                    tokenStandard = null,
                    tokenLogo = "",
                    chainLogo = 0,
                ),
        )
    }

    // 1,234.5 tokens at 8 decimals.
    private val raw = BigInteger("123450000000")

    @Test
    fun `amount above a grouped balance is refused`() {
        assertTrue(model(raw, "1,234.5").exceedsStakeBalance("1234.50000001"))
    }

    @Test
    fun `amount within a grouped balance is allowed`() {
        assertFalse(model(raw, "1,234.5").exceedsStakeBalance("500"))
    }

    @Test
    fun `dot-grouped locale display does not wrongly refuse`() {
        // "1.234" in de/it/nl/pt means 1234, not 1.234.
        assertFalse(model(BigInteger("123400000000"), "1.234").exceedsStakeBalance("500"))
    }

    @Test
    fun `empty balance is refused`() {
        assertTrue(model(BigInteger.ZERO, "0").exceedsStakeBalance("0"))
    }

    @Test
    fun `non-stake flows are never gated`() {
        assertFalse(model(raw, "1,234.5", defiType = null).exceedsStakeBalance("9999"))
    }
}
