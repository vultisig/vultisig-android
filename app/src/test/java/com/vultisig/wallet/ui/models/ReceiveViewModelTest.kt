package com.vultisig.wallet.ui.models

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.logo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReceiveViewModelTest {

    private fun coin(chain: Chain, ticker: String, isNativeToken: Boolean) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = if (isNativeToken) "" else "0xcontract",
            isNativeToken = isNativeToken,
        )

    private fun mapAddressesToUiModels(addresses: List<Address>): List<ChainToReceiveUiModel> =
        addresses.mapNotNull { address ->
            val ticker =
                address.accounts
                    .firstOrNull { account -> account.token.isNativeToken }
                    ?.token
                    ?.ticker ?: return@mapNotNull null
            ChainToReceiveUiModel(
                name = address.chain.raw,
                logo = address.chain.logo,
                address = address.address,
                ticker = ticker,
            )
        }

    @Test
    fun `address with native token maps to ui model`() {
        val addresses =
            listOf(
                Address(
                    chain = Chain.Ethereum,
                    address = "0xabc",
                    accounts =
                        listOf(
                            Account(
                                token = coin(Chain.Ethereum, "ETH", isNativeToken = true),
                                tokenValue = null,
                                fiatValue = null,
                                price = null,
                            )
                        ),
                )
            )

        val result = mapAddressesToUiModels(addresses)

        assertEquals(1, result.size)
        assertEquals("ETH", result[0].ticker)
        assertEquals("0xabc", result[0].address)
    }

    @Test
    fun `address without native token is excluded`() {
        val addresses =
            listOf(
                Address(
                    chain = Chain.Ethereum,
                    address = "0xabc",
                    accounts =
                        listOf(
                            Account(
                                token = coin(Chain.Ethereum, "USDC", isNativeToken = false),
                                tokenValue = null,
                                fiatValue = null,
                                price = null,
                            )
                        ),
                )
            )

        val result = mapAddressesToUiModels(addresses)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `address with empty accounts is excluded`() {
        val addresses =
            listOf(Address(chain = Chain.Ethereum, address = "0xabc", accounts = emptyList()))

        val result = mapAddressesToUiModels(addresses)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `mixed addresses filters out ones without native token`() {
        val addresses =
            listOf(
                Address(
                    chain = Chain.Ethereum,
                    address = "0xabc",
                    accounts =
                        listOf(
                            Account(
                                token = coin(Chain.Ethereum, "ETH", isNativeToken = true),
                                tokenValue = null,
                                fiatValue = null,
                                price = null,
                            )
                        ),
                ),
                Address(chain = Chain.Bitcoin, address = "bc1abc", accounts = emptyList()),
                Address(
                    chain = Chain.Solana,
                    address = "sol123",
                    accounts =
                        listOf(
                            Account(
                                token = coin(Chain.Solana, "SOL", isNativeToken = true),
                                tokenValue = null,
                                fiatValue = null,
                                price = null,
                            )
                        ),
                ),
            )

        val result = mapAddressesToUiModels(addresses)

        assertEquals(2, result.size)
        assertEquals("ETH", result[0].ticker)
        assertEquals("SOL", result[1].ticker)
    }
}
