package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.chains.ton.TonJettonMetadata
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.ui.components.hero.HeroContent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.TonMessage

internal class TonDappHeroResolverTest {

    // Jetton transfer of 100000000 base units, forward_ton_amount = 1000000 nanoton.
    private val jettonTransfer =
        "te6cckEBAQEAWQAArg+KfqUAAAAAAAAwOUBfXhAIAf//////////////////////////////////////////AAvDfWFG0oYX19jwNDNBBL1rKNT9XfaGP9HyTb5nb2Emhh6EgOvlFRU="

    // DeDust native swap (TON -> jetton): root op 0xea06185d, target = pool (not a jetton wallet).
    private val dedustNativeSwap =
        "te6cckEBAwEASAABa+oGGF067C2U2WZEMUBfXhAIAHy/+VG7+ebYbZP+jeYqxVVqNzIpCLWthNl7zJOrFKsQMCjRFAECCWoxFXwOAgIACC4c+oJiXsgi"
    private val dedustNativeVault = "EQDa4VOnTYlLvDJ0gZjNYm5PXfSmmtL6Vs6A_CZEtXCNICq_"

    private val tonApi: TonApi = mockk()
    private val resolver = TonDappHeroResolver(tonApi)

    private fun jettonCoin(master: String) =
        Coin(
            chain = Chain.Ton,
            ticker = "USDT",
            logo = "usdt",
            address = "EQowner",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "tether",
            contractAddress = master,
            isNativeToken = false,
        )

    @Test
    fun `resolves a single-sided jetton transfer hero from a held vault coin`() = runTest {
        coEvery { tonApi.getJettonMasterAddress("EQwallet") } returns "EQmaster"

        val hero =
            resolver.resolveHero(
                messages =
                    listOf(
                        TonMessage(to = "EQwallet", amount = "50000000", payload = jettonTransfer)
                    ),
                vaultCoins = listOf(jettonCoin("EQmaster")),
                toUserFriendly = { it },
            )

        val send = hero as HeroContent.Send
        assertEquals("USDT", send.coin.ticker)
        assertEquals("100", send.coin.amount)
    }

    @Test
    fun `returns null when the jetton is not held in the vault`() = runTest {
        coEvery { tonApi.getJettonMasterAddress("EQwallet") } returns "EQmaster"

        val hero =
            resolver.resolveHero(
                messages =
                    listOf(TonMessage(to = "EQwallet", amount = "1", payload = jettonTransfer)),
                vaultCoins = emptyList(),
                toUserFriendly = { it },
            )

        assertNull(hero)
    }

    @Test
    fun `resolves a DeDust swap hero with the pool output from a held vault coin`() = runTest {
        coEvery { tonApi.getDedustPoolOutputMaster(any()) } returns "EQpoolMaster"

        val hero =
            resolver.resolveHero(
                messages =
                    listOf(
                        TonMessage(
                            to = dedustNativeVault,
                            amount = "100000000",
                            payload = dedustNativeSwap,
                        )
                    ),
                vaultCoins = listOf(jettonCoin("EQpoolMaster")),
                toUserFriendly = { it },
            )

        val swap = hero as HeroContent.Swap
        assertEquals("GRAM", swap.from.ticker)
        assertEquals("USDT", swap.to.ticker)
    }

    @Test
    fun `resolves a DeDust swap output from on-chain metadata when not held in the vault`() =
        runTest {
            coEvery { tonApi.getDedustPoolOutputMaster(any()) } returns "EQpoolMaster"
            coEvery { tonApi.getJettonMetadata("EQpoolMaster") } returns
                TonJettonMetadata(ticker = "USDT", decimals = 6, logo = "usdt")

            val hero =
                resolver.resolveHero(
                    messages =
                        listOf(
                            TonMessage(
                                to = dedustNativeVault,
                                amount = "100000000",
                                payload = dedustNativeSwap,
                            )
                        ),
                    vaultCoins = emptyList(),
                    toUserFriendly = { it },
                )

            val swap = hero as HeroContent.Swap
            assertEquals("USDT", swap.to.ticker)
        }

    @Test
    fun `returns null when there are no messages`() = runTest {
        val hero = resolver.resolveHero(messages = emptyList(), vaultCoins = emptyList()) { it }

        assertNull(hero)
    }

    @Test
    fun `resolves a row coin from on-chain metadata for a jetton the vault does not hold`() =
        runTest {
            // The case the rows exist for: no vault coin, so no hero — the row still needs the
            // ticker and scale to state what is leaving.
            coEvery { tonApi.getJettonMasterAddress("EQwallet") } returns "EQmaster"
            coEvery { tonApi.getJettonMetadata("EQmaster") } returns
                TonJettonMetadata(ticker = "USDT", decimals = 6, logo = "usdt")

            val coins =
                resolver.resolveJettonRowCoins(
                    messages =
                        listOf(
                            TonMessage(
                                to = "EQwallet",
                                amount = "50000000",
                                payload = jettonTransfer,
                            )
                        ),
                    vaultCoins = emptyList(),
                    toUserFriendly = { it },
                )

            assertEquals(TonHeroCoin("USDT", 6, "usdt"), coins["EQwallet"])
        }

    @Test
    fun `leaves a row coin unresolved when the jetton has no metadata`() = runTest {
        coEvery { tonApi.getJettonMasterAddress("EQwallet") } returns "EQmaster"
        coEvery { tonApi.getJettonMetadata("EQmaster") } returns null

        val coins =
            resolver.resolveJettonRowCoins(
                messages =
                    listOf(
                        TonMessage(to = "EQwallet", amount = "50000000", payload = jettonTransfer)
                    ),
                vaultCoins = emptyList(),
                toUserFriendly = { it },
            )

        assertEquals(emptyMap(), coins)
    }

    @Test
    fun `keeps the other rows' coins when one jetton lookup fails`() = runTest {
        coEvery { tonApi.getJettonMasterAddress("EQwallet") } throws RuntimeException("offline")
        coEvery { tonApi.getJettonMasterAddress("EQwallet2") } returns "EQmaster2"

        val coins =
            resolver.resolveJettonRowCoins(
                messages =
                    listOf(
                        TonMessage(to = "EQwallet", amount = "50000000", payload = jettonTransfer),
                        TonMessage(to = "EQwallet2", amount = "50000000", payload = jettonTransfer),
                    ),
                vaultCoins = listOf(jettonCoin("EQmaster2")),
                toUserFriendly = { it },
            )

        assertEquals(mapOf("EQwallet2" to TonHeroCoin("USDT", 6, "usdt")), coins)
    }

    @Test
    fun `fetches a jetton master once for both the rows and the hero`() = runTest {
        coEvery { tonApi.getJettonMasterAddress("EQwallet") } returns "EQmaster"
        val messages =
            listOf(TonMessage(to = "EQwallet", amount = "50000000", payload = jettonTransfer))

        resolver.resolveJettonRowCoins(messages, listOf(jettonCoin("EQmaster"))) { it }
        resolver.resolveHero(messages, listOf(jettonCoin("EQmaster"))) { it }

        coVerify(exactly = 1) { tonApi.getJettonMasterAddress("EQwallet") }
    }

    @Test
    fun `ignores messages that are not jetton transfers`() = runTest {
        val coins =
            resolver.resolveJettonRowCoins(
                messages = listOf(TonMessage(to = "EQwallet", amount = "50000000")),
                vaultCoins = emptyList(),
                toUserFriendly = { it },
            )

        assertEquals(emptyMap(), coins)
    }
}
