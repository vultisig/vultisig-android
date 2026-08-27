package com.vultisig.wallet.ui.models.referral

import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.ThorChainPoolCoin
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the THORName memo a referral edit signs (issue #5684).
 *
 * Thornode reads it positionally — `~:name:chain:address:owner:preferred_asset` — so a shifted or
 * dropped field silently registers the wrong thing: an alias on the wrong chain, or an ownership
 * transfer. Matches iOS `ReferralCodeMemoFactory.createEdit`.
 */
internal class EditReferralMemoTest {

    private val thorAddress = "thor1qzr6dfsxw8fjc9pj39cj7cxwlm8v49g0v5f9tw"

    @Test
    fun `extending the expiry alone re-registers only the THOR alias`() {
        val memo =
            buildEditReferralMemo(
                referralCode = "vult",
                thorAddress = thorAddress,
                payoutAsset = null,
                payoutAssetAddress = null,
            )

        assertEquals("~:VULT:THOR:$thorAddress:$thorAddress", memo)
    }

    @Test
    fun `a payout asset registers its own chain's alias and the asset`() {
        val ethAddress = "0x1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f00"
        val asset =
            ThorChainPoolCoin(
                asset = "ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48",
                coin = Coins.Ethereum.USDC,
            )

        val memo =
            buildEditReferralMemo(
                referralCode = "vult",
                thorAddress = thorAddress,
                payoutAsset = asset,
                payoutAssetAddress = ethAddress,
            )

        assertEquals(
            "~:VULT:ETH:$ethAddress:$thorAddress:ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48",
            memo,
        )
    }

    @Test
    fun `the asset id is carried verbatim, only the code is uppercased`() {
        val asset = ThorChainPoolCoin(asset = "THOR.TCY", coin = Coins.ThorChain.TCY)

        val memo =
            buildEditReferralMemo(
                referralCode = "vUlT",
                thorAddress = thorAddress,
                payoutAsset = asset,
                payoutAssetAddress = thorAddress,
            )

        assertEquals("~:VULT:THOR:$thorAddress:$thorAddress:THOR.TCY", memo)
    }

    @Test
    fun `an asset with no derivable address falls back to the alias-only memo`() {
        val asset = ThorChainPoolCoin(asset = "BTC.BTC", coin = Coins.Bitcoin.BTC)

        val memo =
            buildEditReferralMemo(
                referralCode = "vult",
                thorAddress = thorAddress,
                payoutAsset = asset,
                payoutAssetAddress = null,
            )

        assertEquals("~:VULT:THOR:$thorAddress:$thorAddress", memo)
    }
}
