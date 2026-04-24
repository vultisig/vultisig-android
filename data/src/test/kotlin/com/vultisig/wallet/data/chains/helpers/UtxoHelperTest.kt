package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.utils.getDustThreshold
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import wallet.core.jni.CoinType
import wallet.core.jni.CoinTypeConfiguration

/** Tests for [UtxoHelper] dust thresholds and transaction plan generation. */
class UtxoHelperTest {

    // ─── Dust thresholds (pure Kotlin, no JNI required) ──────────────────────

    @Test
    fun `BITCOIN dust threshold is 546 satoshis`() {
        assertEquals(546L, CoinType.BITCOIN.getDustThreshold)
    }

    @Test
    fun `DOGECOIN dust threshold is 1_000_000 satoshis`() {
        assertEquals(1_000_000L, CoinType.DOGECOIN.getDustThreshold)
    }

    @Test
    fun `LITECOIN dust threshold is 1_000 litoshis`() {
        assertEquals(1_000L, CoinType.LITECOIN.getDustThreshold)
    }

    @Test
    fun `DASH dust threshold is 1_000 duffs`() {
        assertEquals(1_000L, CoinType.DASH.getDustThreshold)
    }

    @Test
    fun `ZCASH dust threshold is 1_000 zatoshis`() {
        assertEquals(1_000L, CoinType.ZCASH.getDustThreshold)
    }

    // ─── getBitcoinTransactionPlan (requires WalletCore JNI) ──────────────────
    //
    // These tests are guarded with assumeTrue and skipped in JVM unit test runs.
    // They validate behaviour when the native WalletCore library is loaded
    // (e.g. on an Android device or emulator via connectedAndroidTest).

    private val nativeAvailable =
        runCatching { CoinTypeConfiguration.getSymbol(CoinType.BITCOIN) }.isSuccess

    // Fixtures from the shared utxo.json integration-test file:
    // address bc1q4e4y3g85dtkx0yp3l2flj2nmugf23c9wwtjwu5
    // vault key 023e4b76…a452b, 1 UTXO of 193 796 sats, byte-fee 1
    private val btcCoin =
        Coin(
            chain = Chain.Bitcoin,
            ticker = "BTC",
            logo = "btc",
            address = "bc1q4e4y3g85dtkx0yp3l2flj2nmugf23c9wwtjwu5",
            decimal = 8,
            hexPublicKey = "023e4b76861289ad4528b33c2fd21b3a5160cd37b3294234914e21efb6ed4a452b",
            priceProviderID = "bitcoin",
            contractAddress = "",
            isNativeToken = true,
        )

    private val singleUtxo =
        UtxoInfo(
            hash = "631fad872ac6bea810cf6073f02e6cbd121cac83193b79f381f711ce93b531f0",
            amount = 193_796L,
            index = 1u,
        )

    private fun buildPayload(sendMaxAmount: Boolean, toAmount: Long, memo: String? = null) =
        KeysignPayload(
            coin = btcCoin,
            toAddress = btcCoin.address,
            toAmount = BigInteger.valueOf(toAmount),
            blockChainSpecific =
                BlockChainSpecific.UTXO(byteFee = BigInteger.ONE, sendMaxAmount = sendMaxAmount),
            utxos = listOf(singleUtxo),
            memo = memo,
            vaultPublicKeyECDSA =
                "023e4b76861289ad4528b33c2fd21b3a5160cd37b3294234914e21efb6ed4a452b",
            vaultLocalPartyID = "device1",
            libType = SigningLibType.DKLS,
            wasmExecuteContractPayload = null,
        )

    @Test
    fun `getBitcoinTransactionPlan returns plan with positive fee for single utxo`() {
        assumeTrue(nativeAvailable)

        val helper =
            UtxoHelper(coinType = CoinType.BITCOIN, vaultHexPublicKey = "", vaultHexChainCode = "")
        val plan = helper.getBitcoinTransactionPlan(buildPayload(false, 100_000L))

        assertTrue(plan.fee > 0L, "Expected positive transaction fee")
    }

    @Test
    fun `getBitcoinTransactionPlan max-send uses full utxo amount minus fee`() {
        assumeTrue(nativeAvailable)

        val helper =
            UtxoHelper(coinType = CoinType.BITCOIN, vaultHexPublicKey = "", vaultHexChainCode = "")
        val plan = helper.getBitcoinTransactionPlan(buildPayload(true, singleUtxo.amount))

        assertEquals(singleUtxo.amount, plan.amount + plan.fee, "Max-send: amount + fee = utxo")
    }

    @Test
    fun `getBitcoinTransactionPlan with short op-return memo produces ok plan`() {
        assumeTrue(nativeAvailable)

        val helper =
            UtxoHelper(coinType = CoinType.BITCOIN, vaultHexPublicKey = "", vaultHexChainCode = "")
        val plan =
            helper.getBitcoinTransactionPlan(
                buildPayload(false, 10_000L, memo = "swap:BTC.BTC:bc1q…")
            )

        assertTrue(plan.fee > 0L, "Plan with short memo should have positive fee")
    }
}
