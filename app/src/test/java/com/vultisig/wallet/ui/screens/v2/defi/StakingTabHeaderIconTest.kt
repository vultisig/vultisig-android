package com.vultisig.wallet.ui.screens.v2.defi

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coins
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

internal class StakingTabHeaderIconTest {

    @Test
    fun `the compounded bRUNE card carries the bRUNE logo`() {
        // ybRUNE holds none of the other tickers' substrings — the b breaks yrune — so before it
        // was matched explicitly the card fell through to the generic fallback and rendered an
        // unrelated logo.
        getHeaderIcon(Coins.ThorChain.ybRUNE.ticker) shouldBe R.drawable.brune
        getHeaderIcon(Coins.ThorChain.ybRUNE.ticker) shouldNotBe R.drawable.om
    }

    @Test
    fun `a receipt ticker never takes the logo of the asset it receipts`() {
        getHeaderIcon(Coins.ThorChain.yTCY.ticker) shouldBe R.drawable.ytcy
        getHeaderIcon(Coins.ThorChain.sTCY.ticker) shouldBe R.drawable.stcy
        getHeaderIcon(Coins.ThorChain.yRUNE.ticker) shouldBe R.drawable.yrune
        getHeaderIcon(Coins.ThorChain.TCY.ticker) shouldBe R.drawable.tcy_staking
    }

    @Test
    fun `both RUJI positions share the RUJI staking logo`() {
        getHeaderIcon(Coins.ThorChain.RUJI.ticker) shouldBe R.drawable.ruji_staking
        getHeaderIcon(Coins.ThorChain.sRUJI.ticker) shouldBe R.drawable.ruji_staking
    }

    @Test
    fun `the Cacao position keeps its own logo`() {
        getHeaderIcon(Coins.MayaChain.CACAO.ticker) shouldBe R.drawable.cacao
    }
}
