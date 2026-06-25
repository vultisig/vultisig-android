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
import wallet.core.jni.Base58
import wallet.core.jni.BitcoinScript
import wallet.core.jni.CoinType
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
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

    private fun zcashPayload(
        address: String,
        utxoAmounts: List<Long>,
        toAmount: BigInteger,
    ): KeysignPayload =
        KeysignPayload(
            coin =
                Coin(
                    chain = Chain.Zcash,
                    ticker = "ZEC",
                    logo = "",
                    address = address,
                    decimal = 8,
                    hexPublicKey = "",
                    priceProviderID = "",
                    contractAddress = "",
                    isNativeToken = true,
                ),
            toAddress = address,
            toAmount = toAmount,
            blockChainSpecific =
                BlockChainSpecific.UTXO(
                    byteFee = BigInteger.valueOf(1_000L),
                    sendMaxAmount = false,
                ),
            utxos =
                utxoAmounts.mapIndexed { i, amount ->
                    UtxoInfo(
                        hash = "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda3$i$i",
                        amount = amount,
                        index = i.toUInt(),
                    )
                },
            memo = null,
            swapPayload = null,
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
    fun `getBitcoinTransactionPlan - Zcash applies ZIP-317 fee that scales with logical actions`() {
        // ZIP-317 conventional fee = 5000 * max(2 grace actions, max(inputs, outputs)). WalletCore
        // only computes this when zip_0317 is set on the signing input; without it the Zcash fee
        // does not scale with input count, so multi-UTXO sends pay below the conventional fee and
        // the node rejects them with "tx unpaid action limit exceeds limit of 0".
        val helper = UtxoHelper(CoinType.ZCASH, "", "")

        try {
            // t1 transparent address (P2PKH, prefix 0x1CB8) built with a Base58Check checksum.
            val zcashAddress =
                Base58.encode(byteArrayOf(0x1C, 0xB8.toByte()) + ByteArray(20) { it.toByte() })

            // Single input + recipient + change => 2 logical actions => grace floor => 5000 * 2.
            val singleInputPlan =
                helper.getBitcoinTransactionPlan(
                    zcashPayload(
                        address = zcashAddress,
                        utxoAmounts = listOf(100_000L),
                        toAmount = BigInteger.valueOf(50_000L),
                    )
                )
            assertEquals(SigningError.OK, singleInputPlan.error)
            assertEquals(10_000L, singleInputPlan.fee)

            // Three inputs are required to fund the send => 3 logical actions => 5000 * 3.
            val multiInputPlan =
                helper.getBitcoinTransactionPlan(
                    zcashPayload(
                        address = zcashAddress,
                        utxoAmounts = listOf(40_000L, 40_000L, 40_000L),
                        toAmount = BigInteger.valueOf(100_000L),
                    )
                )
            assertEquals(SigningError.OK, multiInputPlan.error)
            assertEquals(3, multiInputPlan.utxosList.size)
            assertEquals(15_000L, multiInputPlan.fee)
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

    @Test
    fun `getSwapPreSigningInputData - applies the payload byte fee, not a placeholder`() {
        val helper = UtxoHelper(CoinType.BITCOIN, "", "")
        val payload =
            utxoPayload(
                byteFee = BigInteger.valueOf(7L),
                memo = "SWAP:BTC.BTC:$btcAddress:0:t:0:30",
                swapPayload = thorChainSwapPayload(),
            )
        try {
            val input = helper.getSwapPreSigningInputData(payload)
            assertEquals(7L, input.byteFee)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    private fun verifySwapOpReturn(memo: String) {
        val helper = UtxoHelper(CoinType.BITCOIN, "", "")
        val payload = utxoPayload(memo = memo, swapPayload = thorChainSwapPayload())
        try {
            val input = helper.getSwapPreSigningInputData(payload)
            assertEquals(ByteString.copyFromUtf8(memo), input.outputOpReturn)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    private fun thorChainSwapPayload(): SwapPayload.ThorChain =
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
    fun `getBitcoinTransactionPlanFromSignBitcoin - rejects negative input amount`() {
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
                            // int64 negative — would two's-complement to ~2^64 in BIP-143 preimage
                            amount = -100L,
                            scriptPubKey = "0014" + "11".repeat(20),
                            scriptType = "p2wpkh",
                            isOurs = true,
                        )
                    ),
                outputs =
                    listOf(BitcoinOutput(amount = 50L, scriptPubKey = "0014" + "22".repeat(20))),
            )

        assertThrows(IllegalArgumentException::class.java) {
            helper.getBitcoinTransactionPlanFromSignBitcoin(signBitcoin)
        }
    }

    @Test
    fun `getBitcoinTransactionPlanFromSignBitcoin - rejects negative output amount`() {
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
                    listOf(BitcoinOutput(amount = -10L, scriptPubKey = "0014" + "22".repeat(20))),
            )

        assertThrows(IllegalArgumentException::class.java) {
            helper.getBitcoinTransactionPlanFromSignBitcoin(signBitcoin)
        }
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

    // Destination-binding tests (#4488): the Verify screen displays payload.toAddress /
    // payload.toAmount, so getPreSignedImageHashFromSignBitcoin must reject any PSBT whose
    // non-change outputs would render those legacy fields a lie. Each test exercises one
    // failure mode of the binding plus the Windows-companion isChange semantic.

    private val vaultPubKeyHex =
        "025476c2e83188368da1ff3e292e7acafcdb3566bb0ad253f62fc70f07aeee6357"
    // SECP256K1 generator G — an unrelated pubkey we use to derive a stable "attacker" address.
    private val attackerPubKeyHex =
        "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
    // SECP256K1 2*G — a second unrelated pubkey for tests that need three distinct parties
    // (vault, displayed recipient, hidden second recipient).
    private val secondAttackerPubKeyHex =
        "02c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"

    private fun ownedHelper(): UtxoHelper = UtxoHelper(CoinType.BITCOIN, vaultPubKeyHex, "")

    private fun addressFor(pubKeyHex: String): String =
        CoinType.BITCOIN.deriveAddressFromPublicKey(
            PublicKey(Numeric.hexStringToByteArray(pubKeyHex), PublicKeyType.SECP256K1)
        )

    private fun vaultAddress(): String = addressFor(vaultPubKeyHex)

    private fun scriptHexFor(address: String): String =
        Numeric.toHexStringNoPrefix(
            BitcoinScript.lockScriptForAddress(address, CoinType.BITCOIN).data()
        )

    private fun ownedInput(amount: Long = 100_000L): BitcoinInput {
        val keyHash =
            BitcoinScript.lockScriptForAddress(vaultAddress(), CoinType.BITCOIN)
                .matchPayToWitnessPublicKeyHash()
        return BitcoinInput(
            hash = "0000000000000000000000000000000000000000000000000000000000000001",
            index = 0u,
            amount = amount,
            scriptPubKey = "0014" + Numeric.toHexStringNoPrefix(keyHash),
            scriptType = "p2wpkh",
            isOurs = true,
        )
    }

    @Test
    fun `getPreSignedImageHashFromSignBitcoin - rejects PSBT whose non-change output diverges from payload toAddress`() {
        try {
            val helper = ownedHelper()
            val vault = vaultAddress()
            val recipient = addressFor(attackerPubKeyHex)
            val signBitcoin =
                SignBitcoin(
                    version = 2u,
                    locktime = 0u,
                    inputs = listOf(ownedInput(amount = 100_000L)),
                    outputs =
                        listOf(
                            // dApp pays the attacker instead of the address the user sees.
                            BitcoinOutput(
                                amount = 60_000L,
                                address = recipient,
                                scriptPubKey = scriptHexFor(recipient),
                                isChange = false,
                            ),
                            BitcoinOutput(
                                amount = 39_500L,
                                address = vault,
                                scriptPubKey = scriptHexFor(vault),
                                isChange = true,
                            ),
                        ),
                )
            // Verify screen would show a third unrelated address; verifyOwnership must abort.
            val displayedToAddress = addressFor(secondAttackerPubKeyHex)
            assertThrows(IllegalArgumentException::class.java) {
                helper.getPreSignedImageHashFromSignBitcoin(
                    signBitcoin,
                    expectedToAddress = displayedToAddress,
                    expectedToAmount = BigInteger.valueOf(60_000L),
                )
            }
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `getPreSignedImageHashFromSignBitcoin - rejects PSBT whose non-change sum diverges from payload toAmount`() {
        try {
            val helper = ownedHelper()
            val vault = vaultAddress()
            val recipient = addressFor(attackerPubKeyHex)
            val signBitcoin =
                SignBitcoin(
                    version = 2u,
                    locktime = 0u,
                    inputs = listOf(ownedInput(amount = 100_000L)),
                    outputs =
                        listOf(
                            BitcoinOutput(
                                amount = 50_000L,
                                address = recipient,
                                scriptPubKey = scriptHexFor(recipient),
                                isChange = false,
                            ),
                            BitcoinOutput(
                                amount = 49_500L,
                                address = vault,
                                scriptPubKey = scriptHexFor(vault),
                                isChange = true,
                            ),
                        ),
                )
            // Verify shows toAmount=60_000 but the signed non-change total is 50_000.
            assertThrows(IllegalArgumentException::class.java) {
                helper.getPreSignedImageHashFromSignBitcoin(
                    signBitcoin,
                    expectedToAddress = recipient,
                    expectedToAmount = BigInteger.valueOf(60_000L),
                )
            }
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `getPreSignedImageHashFromSignBitcoin - rejects is_change=true on an output whose address is not the sender`() {
        try {
            val helper = ownedHelper()
            val recipient = addressFor(attackerPubKeyHex)
            val signBitcoin =
                SignBitcoin(
                    version = 2u,
                    locktime = 0u,
                    inputs = listOf(ownedInput(amount = 100_000L)),
                    outputs =
                        listOf(
                            BitcoinOutput(
                                amount = 60_000L,
                                address = recipient,
                                scriptPubKey = scriptHexFor(recipient),
                                isChange = false,
                            ),
                            // dApp flips the change flag onto an attacker-controlled output —
                            // Windows-companion semantic says change iff address == senderAddress.
                            BitcoinOutput(
                                amount = 39_500L,
                                address = recipient,
                                scriptPubKey = scriptHexFor(recipient),
                                isChange = true,
                            ),
                        ),
                )
            assertThrows(IllegalArgumentException::class.java) {
                helper.getPreSignedImageHashFromSignBitcoin(
                    signBitcoin,
                    expectedToAddress = recipient,
                    expectedToAmount = BigInteger.valueOf(60_000L),
                )
            }
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `getPreSignedImageHashFromSignBitcoin - rejects is_change=true whose scriptPubKey does not lock to the vault`() {
        try {
            val helper = ownedHelper()
            val vault = vaultAddress()
            val recipient = addressFor(attackerPubKeyHex)
            val signBitcoin =
                SignBitcoin(
                    version = 2u,
                    locktime = 0u,
                    inputs = listOf(ownedInput(amount = 100_000L)),
                    outputs =
                        listOf(
                            BitcoinOutput(
                                amount = 60_000L,
                                address = recipient,
                                scriptPubKey = scriptHexFor(recipient),
                                isChange = false,
                            ),
                            // dApp sets address=vault to pass the Windows-companion derivation
                            // but points scriptPubKey at the attacker — the sighash commits to
                            // scriptPubKey, so without a byte-level check this would sign away
                            // 39_500 sats to the attacker disguised as harmless change.
                            BitcoinOutput(
                                amount = 39_500L,
                                address = vault,
                                scriptPubKey = scriptHexFor(recipient),
                                isChange = true,
                            ),
                        ),
                )
            assertThrows(IllegalArgumentException::class.java) {
                helper.getPreSignedImageHashFromSignBitcoin(
                    signBitcoin,
                    expectedToAddress = recipient,
                    expectedToAmount = BigInteger.valueOf(60_000L),
                )
            }
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `getPreSignedImageHashFromSignBitcoin - rejects extra value-bearing non-change output even when sum matches`() {
        try {
            val helper = ownedHelper()
            val vault = vaultAddress()
            val recipient = addressFor(attackerPubKeyHex)
            val hiddenPayee = addressFor(secondAttackerPubKeyHex)
            val signBitcoin =
                SignBitcoin(
                    version = 2u,
                    locktime = 0u,
                    inputs = listOf(ownedInput(amount = 100_000L)),
                    outputs =
                        listOf(
                            // Legitimate but reduced output to the displayed recipient...
                            BitcoinOutput(
                                amount = 10_000L,
                                address = recipient,
                                scriptPubKey = scriptHexFor(recipient),
                                isChange = false,
                            ),
                            // ...paired with a second non-change output siphoning value to a
                            // third party. Sum still = 60_000 and one output matches
                            // expectedScript, but Verify screen lists only the displayed recipient.
                            BitcoinOutput(
                                amount = 50_000L,
                                address = hiddenPayee,
                                scriptPubKey = scriptHexFor(hiddenPayee),
                                isChange = false,
                            ),
                            BitcoinOutput(
                                amount = 39_500L,
                                address = vault,
                                scriptPubKey = scriptHexFor(vault),
                                isChange = true,
                            ),
                        ),
                )
            assertThrows(IllegalArgumentException::class.java) {
                helper.getPreSignedImageHashFromSignBitcoin(
                    signBitcoin,
                    expectedToAddress = recipient,
                    expectedToAmount = BigInteger.valueOf(60_000L),
                )
            }
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `getPreSignedImageHashFromSignBitcoin - rejects PSBT that duplicates the legitimate payee output`() {
        try {
            val helper = ownedHelper()
            val vault = vaultAddress()
            val recipient = addressFor(attackerPubKeyHex)
            val signBitcoin =
                SignBitcoin(
                    version = 2u,
                    locktime = 0u,
                    inputs = listOf(ownedInput(amount = 100_000L)),
                    outputs =
                        listOf(
                            // dApp splits the displayed payment into two outputs that both lock
                            // to expectedToAddress. Sum non-change = 60_000 still matches the
                            // payload, the divergence check passes (no non-matching output), but
                            // the Verify screen lists a single payee while the signed tx funds
                            // two — caught by the `matches == 1` invariant.
                            BitcoinOutput(
                                amount = 30_000L,
                                address = recipient,
                                scriptPubKey = scriptHexFor(recipient),
                                isChange = false,
                            ),
                            BitcoinOutput(
                                amount = 30_000L,
                                address = recipient,
                                scriptPubKey = scriptHexFor(recipient),
                                isChange = false,
                            ),
                            BitcoinOutput(
                                amount = 39_500L,
                                address = vault,
                                scriptPubKey = scriptHexFor(vault),
                                isChange = true,
                            ),
                        ),
                )
            assertThrows(IllegalArgumentException::class.java) {
                helper.getPreSignedImageHashFromSignBitcoin(
                    signBitcoin,
                    expectedToAddress = recipient,
                    expectedToAmount = BigInteger.valueOf(60_000L),
                )
            }
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `getPreSignedImageHashFromSignBitcoin - rejects is_ours=true input whose witness pubkey hash diverges from the vault hash`() {
        try {
            val helper = ownedHelper()
            val vault = vaultAddress()
            val recipient = addressFor(attackerPubKeyHex)
            // Craft an input shaped exactly like ours (p2wpkh, isOurs=true) but whose witness
            // program is HASH160(attackerPubKey), not HASH160(vaultPubKey). This is the core
            // is_ours-spoofing shape the binding is meant to reject: a dApp flips the flag
            // hoping to coerce the wallet into signing for a UTXO it doesn't actually own.
            val attackerKeyHash =
                BitcoinScript.lockScriptForAddress(recipient, CoinType.BITCOIN)
                    .matchPayToWitnessPublicKeyHash()
            val maliciousInput =
                BitcoinInput(
                    hash = "0000000000000000000000000000000000000000000000000000000000000001",
                    index = 0u,
                    amount = 100_000L,
                    scriptPubKey = "0014" + Numeric.toHexStringNoPrefix(attackerKeyHash),
                    scriptType = "p2wpkh",
                    isOurs = true,
                )
            val signBitcoin =
                SignBitcoin(
                    version = 2u,
                    locktime = 0u,
                    inputs = listOf(maliciousInput),
                    outputs =
                        listOf(
                            BitcoinOutput(
                                amount = 60_000L,
                                address = recipient,
                                scriptPubKey = scriptHexFor(recipient),
                                isChange = false,
                            ),
                            BitcoinOutput(
                                amount = 39_500L,
                                address = vault,
                                scriptPubKey = scriptHexFor(vault),
                                isChange = true,
                            ),
                        ),
                )
            assertThrows(IllegalArgumentException::class.java) {
                helper.getPreSignedImageHashFromSignBitcoin(
                    signBitcoin,
                    expectedToAddress = recipient,
                    expectedToAmount = BigInteger.valueOf(60_000L),
                )
            }
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `getPreSignedImageHashFromSignBitcoin - happy path with OP_RETURN as second non-change output`() {
        try {
            val helper = ownedHelper()
            val vault = vaultAddress()
            val recipient = addressFor(attackerPubKeyHex)
            val signBitcoin =
                SignBitcoin(
                    version = 2u,
                    locktime = 0u,
                    inputs = listOf(ownedInput(amount = 100_000L)),
                    outputs =
                        listOf(
                            BitcoinOutput(
                                amount = 60_000L,
                                address = recipient,
                                scriptPubKey = scriptHexFor(recipient),
                                isChange = false,
                            ),
                            BitcoinOutput(
                                amount = 0L,
                                address = "",
                                scriptPubKey = "6a04deadbeef",
                                isChange = false,
                            ),
                            BitcoinOutput(
                                amount = 39_500L,
                                address = vault,
                                scriptPubKey = scriptHexFor(vault),
                                isChange = true,
                            ),
                        ),
                )
            // OP_RETURN with 0 sats is non-change; sum non-change = 60_000 matches the legacy
            // payload, and there's exactly one non-change output decoding to toAddress.
            val hashes =
                helper.getPreSignedImageHashFromSignBitcoin(
                    signBitcoin,
                    expectedToAddress = recipient,
                    expectedToAmount = BigInteger.valueOf(60_000L),
                )
            assertEquals(1, hashes.size)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `getPreSignedImageHashFromSignBitcoin - accepts a deposit that nets the miner fee out of the displayed amount`() {
        try {
            val helper = ownedHelper()
            val recipient = addressFor(attackerPubKeyHex)
            // Real /v3/swap PSBTs net the fee out of the deposit: a single send-max output with no
            // change, so the deposit is fromAmount - fee. Verify shows toAmount=100_000 while the
            // signed deposit is 99_500 (fee 500). The binding must accept this — the shortfall is
            // exactly the miner fee, not a hidden recipient.
            val signBitcoin =
                SignBitcoin(
                    version = 2u,
                    locktime = 0u,
                    inputs = listOf(ownedInput(amount = 100_000L)),
                    outputs =
                        listOf(
                            BitcoinOutput(
                                amount = 99_500L,
                                address = recipient,
                                scriptPubKey = scriptHexFor(recipient),
                                isChange = false,
                            )
                        ),
                )
            val hashes =
                helper.getPreSignedImageHashFromSignBitcoin(
                    signBitcoin,
                    expectedToAddress = recipient,
                    expectedToAmount = BigInteger.valueOf(100_000L),
                )
            assertEquals(1, hashes.size)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `getPreSignedImageHashFromSignBitcoin - rejects a PSBT whose miner fee blows past the sat per vByte ceiling`() {
        try {
            val helper = ownedHelper()
            val recipient = addressFor(attackerPubKeyHex)
            // 1 BTC of inputs, a 60_000 sat deposit and no change ⇒ ~99.94M sat fee over a ~110
            // vByte tx (~900k sat/vByte). The Verify screen never shows the fee, so without a
            // ceiling this signs away the balance to the miner. toAmount == the deposit so the
            // amount binding passes; the rejection must come from the fee ceiling.
            val signBitcoin =
                SignBitcoin(
                    version = 2u,
                    locktime = 0u,
                    inputs = listOf(ownedInput(amount = 100_000_000L)),
                    outputs =
                        listOf(
                            BitcoinOutput(
                                amount = 60_000L,
                                address = recipient,
                                scriptPubKey = scriptHexFor(recipient),
                                isChange = false,
                            )
                        ),
                )
            assertThrows(IllegalArgumentException::class.java) {
                helper.getPreSignedImageHashFromSignBitcoin(
                    signBitcoin,
                    expectedToAddress = recipient,
                    expectedToAmount = BigInteger.valueOf(60_000L),
                )
            }
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
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
