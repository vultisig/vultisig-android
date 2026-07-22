package com.vultisig.wallet.ui.models

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AddressExtensionsTest {

    private fun coin(
        chain: Chain,
        ticker: String,
        contractAddress: String,
        isNativeToken: Boolean = false,
    ) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "thor1abc",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = isNativeToken,
        )

    private fun account(token: Coin) =
        Account(token = token, tokenValue = null, fiatValue = null, price = null)

    @Test
    fun `firstSendSrc resolves the exact secured asset selected, not another one sharing its ticker`() {
        // Regression: before Coin.id was contract-qualified for secured assets, both accounts
        // below shared the id "USDC-THORChain" and this lookup could silently resolve to
        // whichever happened to come first in the list, quoting/swapping the wrong denom.
        val ethUsdc =
            coin(Chain.ThorChain, "USDC", "eth-usdc-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
        val avaxUsdc =
            coin(Chain.ThorChain, "USDC", "avax-usdc-0xb97ef9ef8734c71904d8002f8b6bc66dd9c48a6e")
        val addresses =
            listOf(
                Address(
                    chain = Chain.ThorChain,
                    address = "thor1abc",
                    accounts = listOf(account(ethUsdc), account(avaxUsdc)),
                )
            )

        val resolved = addresses.firstSendSrc(selectedTokenId = avaxUsdc.id, filterByChain = null)

        assertEquals(avaxUsdc.contractAddress, resolved?.account?.token?.contractAddress)
    }
}
