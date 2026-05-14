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
import com.vultisig.wallet.data.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.BitcoinInput
import vultisig.keysign.v1.BitcoinOutput
import vultisig.keysign.v1.SignBitcoin
import wallet.core.jni.CoinType
import wallet.core.jni.proto.Common.SigningError

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

    // BIP-143 sighash tests — exercise the new from-scratch PSBT signing path against the
    // canonical test vectors from https://github.com/bitcoin/bips/blob/master/bip-0143.mediawiki
    // These call `computeOurSighashes` directly because the public
    // `getPreSignedImageHashFromSignBitcoin` entry point also verifies that every is_ours input
    // redeems to the wallet's HASH160(pubkey) — that vault-ownership check is orthogonal to
    // the BIP-143 sighash algorithm itself, which is what these vectors validate.

    /**
     * BIP-143 "Native P2WPKH" vector. Two-input tx where input #1 is a P2WPKH output we own. The
     * expected sighash for that input is documented in the BIP itself.
     */
    @Test
    fun `computeOurSighashes - BIP-143 native P2WPKH vector`() {
        val helper = newHelper()
        val signBitcoin =
            SignBitcoin(
                version = 1u,
                locktime = 17u,
                inputs =
                    listOf(
                        // Input 0: P2PK, not ours — included in hashPrevouts/hashSequence only.
                        BitcoinInput(
                            hash =
                                "9f96ade4b41d5433f4eda31e1738ec2b36f6e7d1420d94a6af99801a88f7f7ff",
                            index = 0u,
                            amount = 625_000_000L,
                            scriptPubKey =
                                "2103c9f4836b9a4f77fc0d81f7bcb01b7f1b35916864b9476c241ce9fc198bd25432ac",
                            scriptType = "p2pk",
                            isOurs = false,
                            sequence = 0xFFFFFFEEu,
                        ),
                        // Input 1: P2WPKH, ours — the input we co-sign.
                        BitcoinInput(
                            hash =
                                "8ac60eb9575db5b2d987e29f301b5b819ea83a5c6579d282d189cc04b8e151ef",
                            index = 1u,
                            amount = 600_000_000L,
                            scriptPubKey = "00141d0f172a0ecb48aee1be1f2687d2963ae33f71a1",
                            scriptType = "p2wpkh",
                            isOurs = true,
                            sequence = 0xFFFFFFFFu,
                        ),
                    ),
                outputs =
                    listOf(
                        BitcoinOutput(
                            amount = 112_340_000L,
                            scriptPubKey = "76a9148280b37df378db99f66f85c95a783a76ac7a6d5988ac",
                        ),
                        BitcoinOutput(
                            amount = 223_450_000L,
                            scriptPubKey = "76a9143bde42dbee7e4dbe6a21b2d50ce2f0167faa815988ac",
                        ),
                    ),
            )

        try {
            val hashes =
                helper.computeOurSighashes(signBitcoin).map { Numeric.toHexStringNoPrefix(it) }
            assertEquals(1, hashes.size, "Only the is_ours input should produce a sighash")
            assertEquals(
                "c37af31116d1b27caf68aae9e3ac82f1477929014d5b917657d0eb49478cb670",
                hashes[0],
            )
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    /**
     * BIP-143 "P2SH-P2WPKH" vector. Single-input tx wrapped in P2SH; sighash uses the embedded
     * witness program from the redeem script, not the outer scriptPubKey.
     */
    @Test
    fun `computeOurSighashes - BIP-143 P2SH-P2WPKH vector`() {
        val helper = newHelper()
        val signBitcoin =
            SignBitcoin(
                version = 1u,
                locktime = 1170u,
                inputs =
                    listOf(
                        BitcoinInput(
                            hash =
                                "77541aeb3c4dac9260b68f74f44c973081a9d4cb2ebe8038b2d70faa201b6bdb",
                            index = 1u,
                            amount = 1_000_000_000L,
                            scriptPubKey = "a9144733f37cf4db86fbc2efed2500b4f4e49f31202387",
                            scriptType = "p2sh-p2wpkh",
                            redeemScript = "001479091972186c449eb1ded22b78e40d009bdf0089",
                            isOurs = true,
                            sequence = 0xFFFFFFFEu,
                        )
                    ),
                outputs =
                    listOf(
                        BitcoinOutput(
                            amount = 199_996_600L,
                            scriptPubKey = "76a914a457b684d7f0d539a46a45bbc043f35b59d0d96388ac",
                        ),
                        BitcoinOutput(
                            amount = 800_000_000L,
                            scriptPubKey = "76a914fd270b1ee6abcaea97fea7ad0402e8bd8ad6d77c88ac",
                        ),
                    ),
            )

        try {
            val hashes =
                helper.computeOurSighashes(signBitcoin).map { Numeric.toHexStringNoPrefix(it) }
            assertEquals(1, hashes.size)
            assertEquals(
                "64f3b0f4dd2bb3aa1ce8566d220cc74dda9df97d8490cc81d89d735c92e59fb6",
                hashes[0],
            )
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `computeOurSighashes - all sighashes are computed for is_ours inputs`() {
        val helper = newHelper()
        // Two P2WPKH inputs we own; differ by index, scriptPubKey, and amount so sighashes differ.
        val signBitcoin =
            SignBitcoin(
                version = 2u,
                locktime = 0u,
                inputs =
                    listOf(
                        BitcoinInput(
                            hash =
                                "0000000000000000000000000000000000000000000000000000000000000001",
                            index = 0u,
                            amount = 100_000L,
                            scriptPubKey = "0014" + "11".repeat(20),
                            scriptType = "p2wpkh",
                            isOurs = true,
                        ),
                        BitcoinInput(
                            hash =
                                "0000000000000000000000000000000000000000000000000000000000000002",
                            index = 1u,
                            amount = 200_000L,
                            scriptPubKey = "0014" + "22".repeat(20),
                            scriptType = "p2wpkh",
                            isOurs = true,
                        ),
                    ),
                outputs =
                    listOf(
                        BitcoinOutput(amount = 250_000L, scriptPubKey = "0014" + "33".repeat(20))
                    ),
            )

        try {
            val hashes =
                helper.computeOurSighashes(signBitcoin).map { Numeric.toHexStringNoPrefix(it) }
            assertEquals(2, hashes.size)
            assertTrue(hashes.all { it.length == 64 }, "Each sighash must be 32 bytes (64 hex)")
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `computeOurSighashes - P2TR is rejected`() {
        val helper = newHelper()
        val signBitcoin =
            SignBitcoin(
                version = 2u,
                locktime = 0u,
                inputs =
                    listOf(
                        BitcoinInput(
                            hash =
                                "0000000000000000000000000000000000000000000000000000000000000001",
                            index = 0u,
                            amount = 100_000L,
                            scriptPubKey = "5120" + "aa".repeat(32),
                            scriptType = "p2tr",
                            isOurs = true,
                        )
                    ),
                outputs =
                    listOf(BitcoinOutput(amount = 90_000L, scriptPubKey = "0014" + "bb".repeat(20))),
            )

        try {
            assertThrows(IllegalStateException::class.java) {
                helper.computeOurSighashes(signBitcoin)
            }
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    // Synthetic transaction-plan tests for the PSBT path. The structured-payload variant must
    // bypass WalletCore (whose planner sees zero utxos and bails out with
    // Error_missing_input_utxos) and derive fee/amount/change from `signBitcoin.inputs/outputs`.

    @Test
    fun `getBitcoinTransactionPlanFromSignBitcoin - fee equals inputs minus outputs`() {
        val helper = newHelper()
        // Mirrors the example in PR #4478: 1 input of 564 sats, outputs 30 + 82 (change) + 0
        // (OP_RETURN) => fee = 452 sats.
        val signBitcoin =
            SignBitcoin(
                version = 2u,
                locktime = 0u,
                inputs =
                    listOf(
                        BitcoinInput(
                            hash =
                                "0000000000000000000000000000000000000000000000000000000000000001",
                            index = 0u,
                            amount = 564L,
                            scriptPubKey = "0014" + "11".repeat(20),
                            scriptType = "p2wpkh",
                            isOurs = true,
                        )
                    ),
                outputs =
                    listOf(
                        BitcoinOutput(
                            amount = 30L,
                            scriptPubKey = "0014" + "22".repeat(20),
                            isChange = false,
                        ),
                        BitcoinOutput(
                            amount = 82L,
                            scriptPubKey = "0014" + "11".repeat(20),
                            isChange = true,
                        ),
                        BitcoinOutput(amount = 0L, scriptPubKey = "6a04deadbeef", isChange = false),
                    ),
            )

        val plan = helper.getBitcoinTransactionPlanFromSignBitcoin(signBitcoin)

        assertEquals(SigningError.OK, plan.error)
        assertEquals(564L, plan.availableAmount)
        assertEquals(30L, plan.amount)
        assertEquals(82L, plan.change)
        assertEquals(452L, plan.fee)
    }

    @Test
    fun `getBitcoinTransactionPlanFromSignBitcoin - rejects payload where outputs exceed inputs`() {
        val helper = newHelper()
        val signBitcoin =
            SignBitcoin(
                version = 2u,
                locktime = 0u,
                inputs =
                    listOf(
                        BitcoinInput(
                            hash =
                                "0000000000000000000000000000000000000000000000000000000000000001",
                            index = 0u,
                            amount = 100L,
                            scriptPubKey = "0014" + "11".repeat(20),
                            scriptType = "p2wpkh",
                            isOurs = true,
                        )
                    ),
                outputs =
                    listOf(BitcoinOutput(amount = 200L, scriptPubKey = "0014" + "22".repeat(20))),
            )

        assertThrows(IllegalArgumentException::class.java) {
            helper.getBitcoinTransactionPlanFromSignBitcoin(signBitcoin)
        }
    }

    @Test
    fun `computeOurSighashes - rejects non-SIGHASH_ALL inputs`() {
        val helper = newHelper()
        val signBitcoin =
            SignBitcoin(
                version = 2u,
                locktime = 0u,
                inputs =
                    listOf(
                        BitcoinInput(
                            hash =
                                "0000000000000000000000000000000000000000000000000000000000000001",
                            index = 0u,
                            amount = 100_000L,
                            scriptPubKey = "0014" + "11".repeat(20),
                            scriptType = "p2wpkh",
                            isOurs = true,
                            // SIGHASH_SINGLE — would require per-input hashOutputs recomputation
                            sighashType = 0x03u,
                        )
                    ),
                outputs =
                    listOf(BitcoinOutput(amount = 90_000L, scriptPubKey = "0014" + "22".repeat(20))),
            )

        try {
            assertThrows(IllegalArgumentException::class.java) {
                helper.computeOurSighashes(signBitcoin)
            }
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `getBitcoinTransactionPlan - dispatches to PSBT helper when signBitcoin is present`() {
        val helper = newHelper()
        val signBitcoin =
            SignBitcoin(
                version = 2u,
                locktime = 0u,
                inputs =
                    listOf(
                        BitcoinInput(
                            hash =
                                "0000000000000000000000000000000000000000000000000000000000000001",
                            index = 0u,
                            amount = 100_000L,
                            scriptPubKey = "0014" + "11".repeat(20),
                            scriptType = "p2wpkh",
                            isOurs = true,
                        )
                    ),
                outputs =
                    listOf(
                        BitcoinOutput(
                            amount = 60_000L,
                            scriptPubKey = "0014" + "22".repeat(20),
                            isChange = false,
                        ),
                        BitcoinOutput(
                            amount = 39_500L,
                            scriptPubKey = "0014" + "11".repeat(20),
                            isChange = true,
                        ),
                    ),
            )
        val payload =
            utxoPayload(utxoAmount = 100_000L, toAmount = BigInteger.valueOf(60_000L))
                .copy(utxos = emptyList(), signBitcoin = signBitcoin)

        val plan = helper.getBitcoinTransactionPlan(payload)

        // The empty `utxos` list would force the WalletCore planner to
        // Error_missing_input_utxos; the dispatch to the structured-payload
        // helper bypasses that path entirely.
        assertEquals(SigningError.OK, plan.error)
        assertEquals(500L, plan.fee)
        assertEquals(60_000L, plan.amount)
        assertEquals(100_000L, plan.availableAmount)
        assertEquals(39_500L, plan.change)
    }

    private fun newHelper(): UtxoHelper = UtxoHelper(CoinType.BITCOIN, "", "")

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
