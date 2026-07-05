package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.UtxoInfo
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import wallet.core.jni.CoinType
import wallet.core.jni.proto.Common.SigningError

/**
 * Real-WalletCore regression for the Zcash ZIP-317 conventional-fee guard in [UtxoHelper].
 * WalletCore's `zip_0317` planner flat-sizes OP_RETURN and ignores `byteFee`, so memo txs plan one
 * logical action short; the helper re-plans with `zip_0317` off until the fee clears the
 * conventional fee.
 *
 * Golden vectors mirror the iOS `ZcashZip317PlanTests` (extracted from a live SDK resolver run
 * against wallet-core 4.7.0 — the version this module pins, `gradle/libs.versions.toml`). The
 * planned fee/amount/change must be byte-identical across platforms or MPC co-signing devices
 * derive different preimage digests and keysign fails. Skipped gracefully when the WalletCore
 * native library is unavailable in the JVM test host.
 */
class ZcashZip317PlanTest {

    private val zcashAddress = "t1PoLLLwEcVhqMBhk53tANtSepnPXAQJkPM"
    private val branchIdHex = "30f33754"

    @Test
    fun `plain send keeps the zip_0317 plan at the floor`() {
        planTest(amount = 5_000_000L, balance = 8_300_000L, memo = null) { plan ->
            assertEquals(SigningError.OK, plan.error)
            assertEquals(10_000L, plan.fee)
            assertEquals(5_000_000L, plan.amount)
            assertEquals(3_290_000L, plan.change)
        }
    }

    @Test
    fun `memo send re-plans to meet the conventional fee`() {
        // zip_0317 plans 15,000 where ZIP-317 requires 20,000; the guard re-plans to 20,020.
        planTest(amount = 5_000_000L, balance = 8_300_000L, memo = "m".repeat(40)) { plan ->
            assertEquals(SigningError.OK, plan.error)
            assertEquals(20_020L, plan.fee)
            assertEquals(5_000_000L, plan.amount)
            assertEquals(3_279_980L, plan.change)
        }
    }

    @Test
    fun `send-max memo send re-plans to meet the conventional fee`() {
        planTest(
            amount = 2_200_000L,
            balance = 2_200_000L,
            memo = "m".repeat(40),
            sendMax = true,
        ) { plan ->
            assertEquals(SigningError.OK, plan.error)
            assertEquals(15_142L, plan.fee)
            assertEquals(2_184_858L, plan.amount)
            assertEquals(0L, plan.change)
        }
    }

    @Test
    fun `long memo spanning extra actions clears the conventional fee`() {
        planTest(amount = 5_000_000L, balance = 8_300_000L, memo = "m".repeat(200)) { plan ->
            assertEquals(SigningError.OK, plan.error)
            assertEquals(45_240L, plan.fee)
            assertEquals(3_254_760L, plan.change)
        }
    }

    @Test
    fun `Maya-swap-shaped memo re-plans to meet the conventional fee`() {
        // Maya-native ZEC swaps ride the plain send path with the swap instruction as the memo;
        // this is the live shape the issue reports.
        val memo =
            "=:ETH.USDC-0XA0B86991C6218B36C1D19D4A2E9EB0CE3606EB48:" +
                "0x92009f858E52D5C48CBaBFE0EE9AB05EF5eEC865:1494322902:vi:35"
        planTest(amount = 5_000_000L, balance = 8_300_000L, memo = memo) { plan ->
            assertEquals(SigningError.OK, plan.error)
            assertEquals(30_160L, plan.fee)
            assertEquals(3_269_840L, plan.change)
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
            assertEquals(20_020L, plan.fee)
            assertEquals(3_279_980L, plan.change)
        }
    }

    @Test
    fun `insufficient funds plan passes through untouched`() {
        // Balance can't cover amount + fee + dust, so WalletCore selects no UTXOs. The
        // conventional-fee guard must not hijack this with a ZIP-317 error — the send flow owns the
        // insufficient-funds outcome.
        planTest(amount = 90_000L, balance = 100_000L, memo = "m".repeat(40)) { plan ->
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
