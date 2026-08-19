package com.vultisig.wallet.ui.screens.v2.defi

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coins
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Test

internal class StakingTabHeaderIconTest {

    @Test
    fun `the compounded bRUNE card carries the bRUNE logo`() {
        // ybRUNE holds none of the other tickers' substrings — the b breaks yrune — so before it
        // was matched explicitly the card fell through to the generic fallback and rendered an
        // unrelated logo.
        assertEquals(R.drawable.brune, getHeaderIcon(Coins.ThorChain.ybRUNE.ticker))
        assertNotEquals(R.drawable.om, getHeaderIcon(Coins.ThorChain.ybRUNE.ticker))
    }

    @Test
    fun `a receipt ticker never takes the logo of the asset it receipts`() {
        assertEquals(R.drawable.ytcy, getHeaderIcon(Coins.ThorChain.yTCY.ticker))
        assertEquals(R.drawable.stcy, getHeaderIcon(Coins.ThorChain.sTCY.ticker))
        assertEquals(R.drawable.yrune, getHeaderIcon(Coins.ThorChain.yRUNE.ticker))
        assertEquals(R.drawable.tcy_staking, getHeaderIcon(Coins.ThorChain.TCY.ticker))
    }

    @Test
    fun `both RUJI positions share the RUJI staking logo`() {
        assertEquals(R.drawable.ruji_staking, getHeaderIcon(Coins.ThorChain.RUJI.ticker))
        assertEquals(R.drawable.ruji_staking, getHeaderIcon(Coins.ThorChain.sRUJI.ticker))
    }

    @Test
    fun `the Cacao position keeps its own logo`() {
        assertEquals(R.drawable.cacao, getHeaderIcon(Coins.MayaChain.CACAO.ticker))
    }
}
