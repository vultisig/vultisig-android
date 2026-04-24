package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.getDustThreshold
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.payload.UtxoInfo
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import wallet.core.jni.CoinType

/**
 * Tests for [UtxoHelper] — dust thresholds (no JNI) and plan/memo behaviour (JNI, skipped when
 * native library is absent).
 */
class UtxoHelperTest {

    private val btcAddress = "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"

    private val btcCoin =
        Coin(
            chain = Chain.Bitcoin,
            ticker = "BTC",
            logo = "",
            address = btcAddress,
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun utxoPayload(
        sendMaxAmount: Boolean = false,
        byteFee: BigInteger = BigInteger.ONE,
        utxoAmount: Long = 100_000L,
        toAmount: BigInteger = BigInteger.valueOf(10_000L),
        memo: String? = null,
        swapPayload: SwapPayload? = null,
    ): KeysignPayload =
        KeysignPayload(
            coin = btcCoin,
            toAddress = btcAddress,
            toAmount = toAmount,
            blockChainSpecific =
                BlockChainSpecific.UTXO(byteFee = byteFee, sendMaxAmount = sendMaxAmount),
            utxos =
                listOf(
                    UtxoInfo(
                        hash = "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b",
                        amount = utxoAmount,
                        index = 0u,
                    )
                ),
            memo = memo,
            swapPayload = swapPayload,
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = SigningLibType.GG20,
            wasmExecuteContractPayload = null,
        )

    // Dust threshold tests — pure Kotlin when-expression, no native calls required

    @Test
    fun `Bitcoin dust threshold is 546 satoshis`() {
        assertEquals(BigInteger.valueOf(546L), Chain.Bitcoin.getDustThreshold)
    }

    @Test
    fun `Dogecoin dust threshold is 1000000 satoshis`() {
        assertEquals(BigInteger.valueOf(1_000_000L), Chain.Dogecoin.getDustThreshold)
    }

    @Test
    fun `Litecoin dust threshold is 1000 satoshis`() {
        assertEquals(BigInteger.valueOf(1_000L), Chain.Litecoin.getDustThreshold)
    }

    @Test
    fun `Dash dust threshold is 1000 satoshis`() {
        assertEquals(BigInteger.valueOf(1_000L), Chain.Dash.getDustThreshold)
    }

    @Test
    fun `Zcash dust threshold is 1000 satoshis`() {
        assertEquals(BigInteger.valueOf(1_000L), Chain.Zcash.getDustThreshold)
    }

    // JNI-dependent tests — skipped gracefully when WalletCore native library is unavailable

    @Test
    fun `getBitcoinTransactionPlan - single UTXO produces plan with positive fee`() {
        val helper = UtxoHelper(CoinType.BITCOIN, "", "")
        val payload = utxoPayload(utxoAmount = 100_000L, toAmount = BigInteger.valueOf(10_000L))
        try {
            val plan = helper.getBitcoinTransactionPlan(payload)
            assertTrue(plan.fee > 0L, "Expected positive fee, got ${plan.fee}")
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `getBitcoinTransactionPlan - max send plan amount equals utxo minus fee`() {
        val helper = UtxoHelper(CoinType.BITCOIN, "", "")
        val utxoAmount = 100_000L
        val payload =
            utxoPayload(
                sendMaxAmount = true,
                utxoAmount = utxoAmount,
                toAmount = BigInteger.valueOf(utxoAmount),
            )
        try {
            val plan = helper.getBitcoinTransactionPlan(payload)
            assertEquals(utxoAmount - plan.fee, plan.amount)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `getSwapPreSigningInputData - memo shorter than 80 bytes is encoded as OP_RETURN`() {
        val memo = "SWAP:BTC.BTC:$btcAddress:0:t:0:30"
        assertTrue(memo.toByteArray().size < 80)
        verifySwapOpReturn(memo)
    }

    @Test
    fun `getSwapPreSigningInputData - memo longer than 80 bytes is still passed through as OP_RETURN`() {
        val memo = "SWAP:BTC.BTC:$btcAddress:0:t:0:30/${"x".repeat(60)}"
        assertTrue(memo.toByteArray().size > 80)
        verifySwapOpReturn(memo)
    }

    private fun verifySwapOpReturn(memo: String) {
        val helper = UtxoHelper(CoinType.BITCOIN, "", "")
        val swapPayload =
            SwapPayload.ThorChain(
                THORChainSwapPayload(
                    fromAddress = btcAddress,
                    fromCoin = btcCoin,
                    toCoin = btcCoin,
                    vaultAddress = btcAddress,
                    routerAddress = null,
                    fromAmount = BigInteger.valueOf(10_000L),
                    toAmountDecimal = BigDecimal.ZERO,
                    toAmountLimit = "0",
                    streamingInterval = "0",
                    streamingQuantity = "0",
                    expirationTime = 0uL,
                    isAffiliate = false,
                )
            )
        val payload = utxoPayload(memo = memo, swapPayload = swapPayload)
        try {
            val input = helper.getSwapPreSigningInputData(payload)
            assertEquals(ByteString.copyFromUtf8(memo), input.outputOpReturn)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    private fun skipIfJniUnavailable(e: Throwable) {
        if (
            e is UnsatisfiedLinkError ||
                e is ExceptionInInitializerError ||
                e is NoClassDefFoundError
        ) {
            assumeTrue(false, "WalletCore JNI not available: ${e.message}")
        } else throw e
    }
}
