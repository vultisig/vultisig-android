package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.UtxoInfo
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import wallet.core.jni.CoinType
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

/**
 * Real-WalletCore regression for Zcash fee planning in [UtxoHelper]. WalletCore's `zip_0317`
 * planner hardcodes 5,000 zats per action, flat-sizes OP_RETURN and ignores `byteFee`, so the
 * helper never uses it: every Zcash plan runs with `zip_0317` off, raising `byteFee` from 1 until
 * the fee clears the ZIP 2009 conventional fee (1,000 zats per action).
 *
 * The exact fees are cross-platform vectors shared with the SDK's `utxo.zcashFee.test.ts` and the
 * iOS `ZcashZip317PlanTests`; the SDK derives them from WalletCore WASM, this module from the
 * wallet-core version pinned in `gradle/libs.versions.toml`. The planned fee/amount/change must be
 * byte-identical across platforms or MPC co-signing devices derive different preimage digests and
 * keysign fails. Skipped gracefully when the WalletCore native library is unavailable in the JVM
 * test host.
 */
class ZcashZip317PlanTest {

    private val zcashAddress = "t1PoLLLwEcVhqMBhk53tANtSepnPXAQJkPM"
    private val branchIdHex = "30f33754"

    @Test
    fun `plain send plans at the 1,000-zat rate, not WalletCore's hardcoded 10,000`() {
        // Required 2,000; 226 vbytes x ceil(2,000 / 226) = 9 zats/byte.
        planTest(amount = 5_000_000L, balance = 8_300_000L, memo = null) { plan ->
            assertEquals(SigningError.OK, plan.error)
            assertEquals(2_034L, plan.fee)
            assertEquals(5_000_000L, plan.amount)
            assertEquals(3_297_966L, plan.change)
        }
    }

    @Test
    fun `plain send-max plans at the 1,000-zat rate`() {
        // One output, still two grace actions: required 2,000; 192 vbytes x 11.
        planTest(amount = 2_200_000L, balance = 2_200_000L, memo = null, sendMax = true) { plan ->
            assertEquals(SigningError.OK, plan.error)
            assertEquals(2_112L, plan.fee)
            assertEquals(2_197_888L, plan.amount)
            assertEquals(0L, plan.change)
        }
    }

    @Test
    fun `never hands the plan to WalletCore's zip_0317 planner`() {
        val helper = UtxoHelper(CoinType.ZCASH, "", "")
        try {
            val payload = makePayload(amount = 5_000_000L, balance = 8_300_000L, memo = null, sendMax = false)
            val input =
                Bitcoin.SigningInput.parseFrom(
                    helper.getSigningInputData(payload, helper.getBitcoinSigningInput(payload))
                )
            assertFalse(input.zip0317)
            assertEquals(2_034L, input.plan.fee)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `memo send with change meets the conventional fee`() {
        planTest(amount = 5_000_000L, balance = 8_300_000L, memo = "m".repeat(40)) { plan ->
            assertEquals(SigningError.OK, plan.error)
            assertEquals(4_160L, plan.fee)
            assertEquals(5_000_000L, plan.amount)
            assertEquals(3_295_840L, plan.change)
        }
    }

    @Test
    fun `send-max memo send meets the conventional fee`() {
        planTest(
            amount = 2_200_000L,
            balance = 2_200_000L,
            memo = "m".repeat(40),
            sendMax = true,
        ) { plan ->
            assertEquals(SigningError.OK, plan.error)
            assertEquals(3_164L, plan.fee)
            assertEquals(2_196_836L, plan.amount)
            assertEquals(0L, plan.change)
        }
    }

    @Test
    fun `long memo spanning extra actions clears the conventional fee`() {
        planTest(amount = 5_000_000L, balance = 8_300_000L, memo = "m".repeat(200)) { plan ->
            assertEquals(SigningError.OK, plan.error)
            assertEquals(9_100L, plan.fee)
            assertEquals(3_290_900L, plan.change)
        }
    }

    @Test
    fun `Maya-swap-shaped memo meets the conventional fee`() {
        // Maya-native ZEC swaps ride the plain send path with the swap instruction as the memo.
        val memo =
            "=:ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48:" +
                "0x92009f858E52D5C48CBaBFE0EE9AB05EF5eEC865:1494322902:vi:35"
        planTest(amount = 5_000_000L, balance = 8_300_000L, memo = memo) { plan ->
            assertEquals(SigningError.OK, plan.error)
            assertEquals(6_240L, plan.fee)
            assertEquals(3_293_760L, plan.change)
        }
    }

    @Test
    fun `non-ascii memo is charged by utf8 byte length`() {
        // TextEncoder().encode(memo) and Kotlin's UTF-8 encoding of outputOpReturn must agree on
        // the byte length that sizes the OP_RETURN — 17 characters but 34 UTF-8 bytes.
        val memo = "меморандум-🚀-мемо"
        assertEquals(34, memo.toByteArray(Charsets.UTF_8).size)
        planTest(amount = 5_000_000L, balance = 8_300_000L, memo = memo) { plan ->
            assertEquals(SigningError.OK, plan.error)
            assertEquals(4_160L, plan.fee)
            assertEquals(3_295_840L, plan.change)
        }
    }

    @Test
    fun `insufficient funds plan passes through untouched`() {
        // Balance can't cover amount + fee + dust, so WalletCore selects no UTXOs. The
        // conventional-fee guard must not hijack this with a ZIP-317 error — the send flow owns the
        // insufficient-funds outcome.
        planTest(amount = 99_000L, balance = 100_000L, memo = "m".repeat(40)) { plan ->
            assertTrue(
                plan.utxosList.isEmpty(),
                "Expected no selected UTXOs, got ${plan.utxosCount}",
            )
            assertEquals(0L, plan.fee)
        }
    }

    private fun planTest(
        amount: Long,
        balance: Long,
        memo: String?,
        sendMax: Boolean = false,
        assertions: (wallet.core.jni.proto.Bitcoin.TransactionPlan) -> Unit,
    ) {
        val helper = UtxoHelper(CoinType.ZCASH, "", "")
        try {
            val plan =
                helper.getBitcoinTransactionPlan(
                    makePayload(amount = amount, balance = balance, memo = memo, sendMax = sendMax)
                )
            assertions(plan)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    private fun makePayload(
        amount: Long,
        balance: Long,
        memo: String?,
        sendMax: Boolean,
    ): KeysignPayload =
        KeysignPayload(
            coin =
                Coin(
                    chain = Chain.Zcash,
                    ticker = "ZEC",
                    logo = "",
                    address = zcashAddress,
                    decimal = 8,
                    hexPublicKey = "",
                    priceProviderID = "",
                    contractAddress = "",
                    isNativeToken = true,
                ),
            toAddress = zcashAddress,
            toAmount = BigInteger.valueOf(amount),
            blockChainSpecific =
                BlockChainSpecific.UTXO(
                    byteFee = BigInteger.valueOf(100L),
                    sendMaxAmount = sendMax,
                    zcashBranchId = branchIdHex,
                ),
            utxos = listOf(UtxoInfo(hash = "00".repeat(32), amount = balance, index = 0u)),
            memo = memo,
            swapPayload = null,
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = SigningLibType.GG20,
            wasmExecuteContractPayload = null,
        )

    private fun skipIfJniUnavailable(e: Throwable) {
        if (
            e is UnsatisfiedLinkError ||
                e is ExceptionInInitializerError ||
                e is NoClassDefFoundError
        ) {
            assumeTrue(false, "WalletCore JNI not available: ${e.message}")
        } else {
            throw e
        }
    }
}
