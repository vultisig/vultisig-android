package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.SignTon
import vultisig.keysign.v1.TonMessage

internal class TonMessageDecodeTest {

    // Jetton transfer of 100000000 base units, forward_ton_amount = 1000000 nanoton.
    private val jettonTransfer =
        "te6cckEBAQEAWQAArg+KfqUAAAAAAAAwOUBfXhAIAf//////////////////////////////////////////AAvDfWFG0oYX19jwNDNBBL1rKNT9XfaGP9HyTb5nb2Emhh6EgOvlFRU="
    private val excessesBody = "te6cckEBAQEADgAAGNUydtsAAAAAAAAABxylUgg="
    private val recipient = "0:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

    // Jetton transfer to a STON.fi v2 router whose forward payload is a 0x6664de2a swap.
    private val stonfiSwap =
        "te6cckEBAwEA+gABrg+KfqUAAAAAAAAwOUBfXhAIAERavXfYMA5q4ilwZO4WHhXKx0RKR3nWMPjCFWxdsJIlAAvDfWFG0oYX19jwNDNBBL1rKNT9XfaGP9HyTb5nb2Emhh6EgQEB4WZk3iqAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABQALw31hRtKGF9fY8DQzQQS9ayjU/V32hj/R8k2+Z29hJqAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgAAAAAAAAD3AAgBTREaPhQgB//////////////////////////////////////////4AAAUQRrdCMQ=="
    private val stonfiRouterRaw =
        "0:222d5ebbec1807357114b832770b0f0ae563a22523bceb187c610ab62ed84912"
    private val stonfiRouterFriendly = "EQAiLV677BgHNXEUuDJ3Cw8K5WOiJSO86xh8YQq2LthJEoED"

    // DeDust native swap (TON -> jetton): root op 0xea06185d, target = pool (not a jetton wallet).
    private val dedustNativeSwap =
        "te6cckEBAwEASAABa+oGGF067C2U2WZEMUBfXhAIAHy/+VG7+ebYbZP+jeYqxVVqNzIpCLWthNl7zJOrFKsQMCjRFAECCWoxFXwOAgIACC4c+oJiXsgi"
    private val dedustNativeVault = "EQDa4VOnTYlLvDJ0gZjNYm5PXfSmmtL6Vs6A_CZEtXCNICq_"

    private fun jettonCoin() =
        Coin(
            chain = Chain.Ton,
            ticker = "USDT",
            logo = "usdt",
            address = "EQowner",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "tether",
            contractAddress = "EQmaster",
            isNativeToken = false,
        )

    @Test
    fun `maps a jetton transfer to a labelled row with the real recipient and forward amount`() {
        val row =
            mapTonMessages(
                    SignTon(
                        tonMessages =
                            listOf(
                                TonMessage(
                                    to = "EQwallet",
                                    amount = "50000000",
                                    payload = jettonTransfer,
                                )
                            )
                    ),
                    fromAddress = null,
                    formatAddress = { it },
                )
                .single()
        assertEquals(TonMessageOperation.JettonTransfer, row.operation)
        assertEquals(recipient, row.recipient)
        assertEquals("0.001 TON", row.amount)
        assertEquals(jettonTransfer, row.rawPayload)
    }

    @Test
    fun `maps an excesses body to an excess gas refund row with no recipient or amount`() {
        val row =
            mapTonMessages(
                    SignTon(
                        tonMessages =
                            listOf(
                                TonMessage(to = "EQwallet", amount = "0", payload = excessesBody)
                            )
                    ),
                    fromAddress = null,
                    formatAddress = { it },
                )
                .single()
        assertEquals(TonMessageOperation.ExcessGasRefund, row.operation)
        assertNull(row.recipient)
        assertNull(row.amount)
    }

    @Test
    fun `maps an undecodable body to a plain transfer showing the outer destination as-is`() {
        val row =
            mapTonMessages(
                    SignTon(tonMessages = listOf(TonMessage(to = "EQabc", amount = "1500000000"))),
                    fromAddress = null,
                    formatAddress = { error("plain transfer must not reformat the outer address") },
                )
                .single()
        assertEquals(TonMessageOperation.Transfer, row.operation)
        assertEquals("EQabc", row.recipient)
        assertEquals("1.5 TON", row.amount)
        assertNull(row.rawPayload)
    }

    @Test
    fun `resolves the jetton hero against a held vault coin`() = runTest {
        val hero =
            resolveTonJettonHero(
                messages =
                    listOf(
                        TonMessage(to = "EQwallet", amount = "50000000", payload = jettonTransfer)
                    ),
                vaultCoins = listOf(jettonCoin()),
                resolveJettonMaster = { "EQmaster" },
            )
        assertEquals("100", hero?.amount)
        assertEquals("USDT", hero?.ticker)
        assertEquals("usdt", hero?.logo)
    }

    @Test
    fun `returns no hero when the jetton is not held in the vault`() = runTest {
        val hero =
            resolveTonJettonHero(
                messages =
                    listOf(TonMessage(to = "EQwallet", amount = "1", payload = jettonTransfer)),
                vaultCoins = emptyList(),
                resolveJettonMaster = { "EQmaster" },
            )
        assertNull(hero)
    }

    @Test
    fun `returns no hero when the wallet does not resolve to a master`() = runTest {
        val hero =
            resolveTonJettonHero(
                messages =
                    listOf(TonMessage(to = "EQwallet", amount = "1", payload = jettonTransfer)),
                vaultCoins = listOf(jettonCoin()),
                resolveJettonMaster = { null },
            )
        assertNull(hero)
    }

    @Test
    fun `skips an excesses message and resolves the following jetton transfer`() = runTest {
        val hero =
            resolveTonJettonHero(
                messages =
                    listOf(
                        TonMessage(to = "EQwallet", amount = "0", payload = excessesBody),
                        TonMessage(to = "EQwallet", amount = "50000000", payload = jettonTransfer),
                    ),
                vaultCoins = listOf(jettonCoin()),
                resolveJettonMaster = { "EQmaster" },
            )
        assertEquals("USDT", hero?.ticker)
    }

    @Test
    fun `returns no hero when the resolved master case differs from the vault coin`() = runTest {
        // TON addresses are case-sensitive, so a different-case master must not match.
        val hero =
            resolveTonJettonHero(
                messages =
                    listOf(TonMessage(to = "EQwallet", amount = "1", payload = jettonTransfer)),
                vaultCoins = listOf(jettonCoin()),
                resolveJettonMaster = { "eqmaster" },
            )
        assertNull(hero)
    }

    @Test
    fun `resolves a swap hero when the router is allow-listed in raw or friendly form`() = runTest {
        // The decoder emits the router as raw workchain:hex; the allow-list literal is friendly.
        // The
        // gate normalizes both through toUserFriendly, so a canonicalizing normalizer matches
        // either.
        val normalize: (String) -> String? = {
            if (it == stonfiRouterRaw || it == stonfiRouterFriendly) stonfiRouterFriendly else it
        }
        val hero =
            resolveTonSwapHero(
                messages =
                    listOf(TonMessage(to = "EQwallet", amount = "100000000", payload = stonfiSwap)),
                nativeTon = TonHeroCoin("TON", 9, "ton"),
                toUserFriendly = normalize,
                resolveCoinByWallet = { TonHeroCoin("USDT", 6, "usdt") },
                resolveDedustOutputCoin = { null },
            )
        assertNotNull(hero)
        assertEquals("USDT", hero.from.ticker)
    }

    @Test
    fun `resolves a DeDust native swap hero from the pool output`() = runTest {
        // DeDust addresses the native vault (gated on the outer destination) and the swap's target
        // is the pool, so the output coin comes from resolveDedustOutputCoin, not a wallet lookup.
        val hero =
            resolveTonSwapHero(
                messages =
                    listOf(
                        TonMessage(
                            to = dedustNativeVault,
                            amount = "100000000",
                            payload = dedustNativeSwap,
                        )
                    ),
                nativeTon = TonHeroCoin("TON", 9, "ton"),
                toUserFriendly = { it },
                resolveCoinByWallet = { null },
                resolveDedustOutputCoin = { TonHeroCoin("USDT", 6, "usdt") },
            )
        assertNotNull(hero)
        assertEquals("TON", hero.from.ticker)
        assertEquals("USDT", hero.to.ticker)
    }

    @Test
    fun `returns no swap hero when the router is not allow-listed`() = runTest {
        // Identity normalizer leaves the candidate as raw workchain:hex, which is not in the
        // friendly-form allow-list, so the swap is rejected and degrades to the per-message
        // display.
        val hero =
            resolveTonSwapHero(
                messages =
                    listOf(TonMessage(to = "EQwallet", amount = "100000000", payload = stonfiSwap)),
                nativeTon = TonHeroCoin("TON", 9, "ton"),
                toUserFriendly = { it },
                resolveCoinByWallet = { TonHeroCoin("USDT", 6, "usdt") },
                resolveDedustOutputCoin = { null },
            )
        assertNull(hero)
    }

    @Test
    fun `hides a small self-addressed gas sidecar when a swap is present`() {
        val rows =
            mapTonMessages(
                SignTon(
                    tonMessages =
                        listOf(
                            // 0.005 TON self-transfer — a swap gas sidecar.
                            TonMessage(to = "EQself", amount = "5000000"),
                            TonMessage(to = "EQwallet", amount = "100000000", payload = stonfiSwap),
                        )
                ),
                fromAddress = "EQself",
                formatAddress = { it },
            )
        assertEquals(1, rows.size)
        assertEquals(TonMessageOperation.Swap, rows.single().operation)
    }

    @Test
    fun `maps a plain transfer with a negative amount to no amount`() {
        val row =
            mapTonMessages(
                    SignTon(tonMessages = listOf(TonMessage(to = "EQabc", amount = "-5"))),
                    fromAddress = null,
                    formatAddress = { it },
                )
                .single()
        assertEquals(TonMessageOperation.Transfer, row.operation)
        assertNull(row.amount)
    }
}
