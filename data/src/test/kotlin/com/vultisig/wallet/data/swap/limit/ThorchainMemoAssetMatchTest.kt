package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * What a signed memo's target asset may be said to be, given a coin a screen is about to label it
 * with (#5734 review).
 *
 * The asymmetry is the whole point: only a spelling this app can resolve *and* that resolves to
 * something else is [MemoAssetMatch.DIFFERENT]. THORChain accepts several spellings of one asset,
 * and reading an unfamiliar-but-valid one as a mismatch would hide a floor the signer really has.
 */
class ThorchainMemoAssetMatchTest {

    @Test
    fun `a layer-1 memo names its own native coin`() {
        assertEquals(MemoAssetMatch.SAME, btc.compareToMemoAsset("BTC.BTC"))
        assertEquals(MemoAssetMatch.SAME, btc.compareToMemoAsset("btc.btc"))
    }

    @Test
    fun `another chain's asset is a mismatch`() {
        assertEquals(MemoAssetMatch.DIFFERENT, btc.compareToMemoAsset("ETH.ETH"))
    }

    @Test
    fun `another token on the same chain is a mismatch`() {
        assertEquals(MemoAssetMatch.DIFFERENT, usdc.compareToMemoAsset("ETH.ETH"))
    }

    @Test
    fun `a token's contract may be abbreviated the way the memo builder abbreviates it`() {
        assertEquals(MemoAssetMatch.SAME, usdc.compareToMemoAsset(usdc.thorchainMemoAsset()))
        assertEquals(MemoAssetMatch.SAME, usdc.compareToMemoAsset(usdc.thorchainCancelMemoAsset()))
        // A ticker THORChain can resolve on its own carries no contract to disagree with.
        assertEquals(MemoAssetMatch.SAME, usdc.compareToMemoAsset("ETH.USDC"))
    }

    @Test
    fun `the same ticker at another contract is a mismatch`() {
        assertEquals(MemoAssetMatch.DIFFERENT, usdc.compareToMemoAsset("ETH.USDC-DEADBE"))
    }

    @Test
    fun `synth and trade notations name the same asset as the layer 1`() {
        assertEquals(MemoAssetMatch.SAME, btc.compareToMemoAsset("BTC/BTC"))
        assertEquals(MemoAssetMatch.SAME, btc.compareToMemoAsset("BTC~BTC"))
    }

    @Test
    fun `a secured denom is read through to its underlying chain and contract`() {
        val secured =
            coin(Chain.ThorChain, "USDC", native = false, contract = "eth-usdc-0x$USDC_CONTRACT")

        assertEquals(MemoAssetMatch.SAME, secured.compareToMemoAsset("eth-usdc-0x$USDC_CONTRACT"))
        assertEquals(MemoAssetMatch.SAME, secured.compareToMemoAsset("ETH.USDC-06EB48"))
        assertEquals(MemoAssetMatch.DIFFERENT, secured.compareToMemoAsset("BTC.BTC"))
    }

    @Test
    fun `a THORChain token's denom is not a contract and never disagrees with one`() {
        val tcy = coin(Chain.ThorChain, "TCY", native = false, contract = "tcy")

        assertEquals(MemoAssetMatch.SAME, tcy.compareToMemoAsset("THOR.TCY"))
        assertEquals(MemoAssetMatch.DIFFERENT, tcy.compareToMemoAsset("THOR.RUNE"))
    }

    @Test
    fun `Cosmos native compares as ATOM, and Noble under THORChain's own prefix`() {
        assertEquals(
            MemoAssetMatch.SAME,
            coin(Chain.GaiaChain, "ATOM", native = true).compareToMemoAsset("GAIA.ATOM"),
        )
        assertEquals(
            MemoAssetMatch.SAME,
            coin(Chain.Noble, "USDC", native = true).compareToMemoAsset("NOBLE.USDC"),
        )
    }

    @Test
    fun `MayaChain's own assets compare, though Maya is not THORChain-routable`() {
        val cacao = coin(Chain.MayaChain, "CACAO", native = true)

        assertEquals(MemoAssetMatch.SAME, cacao.compareToMemoAsset("MAYA.CACAO"))
        // The cosigner case this guards: a Maya memo carried under another chain's destination.
        assertEquals(MemoAssetMatch.DIFFERENT, btc.compareToMemoAsset("MAYA.CACAO"))
    }

    @Test
    fun `a short code is unreadable rather than a mismatch`() {
        // THORChain resolves `e.eth` against its live pool list; no table an app holds can.
        assertEquals(MemoAssetMatch.UNREADABLE, btc.compareToMemoAsset("e.eth"))
        assertEquals(MemoAssetMatch.UNREADABLE, btc.compareToMemoAsset("b.b"))
    }

    @Test
    fun `an asset with no chain at all is unreadable`() {
        assertEquals(MemoAssetMatch.UNREADABLE, btc.compareToMemoAsset("RUNE"))
        assertEquals(MemoAssetMatch.UNREADABLE, btc.compareToMemoAsset(""))
        assertEquals(MemoAssetMatch.UNREADABLE, btc.compareToMemoAsset("BTC."))
        assertEquals(MemoAssetMatch.UNREADABLE, btc.compareToMemoAsset(".BTC"))
    }

    private fun coin(chain: Chain, ticker: String, native: Boolean, contract: String = "") =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contract,
            isNativeToken = native,
        )

    private val btc = coin(Chain.Bitcoin, "BTC", native = true)
    private val usdc = coin(Chain.Ethereum, "USDC", native = false, contract = "0x$USDC_CONTRACT")

    private companion object {
        const val USDC_CONTRACT = "a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
    }
}
