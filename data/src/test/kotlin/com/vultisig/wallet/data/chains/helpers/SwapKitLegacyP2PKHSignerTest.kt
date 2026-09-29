package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.utils.Numeric
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import wallet.core.jni.CoinType
import wallet.core.jni.Hash
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

/**
 * Tests for [SwapKitLegacyP2PKHSigner] (DOGE / BCH / DASH legacy-P2PKH PSBT signing).
 *
 * The PSBT parsing + structural validation (P2PKH shape, output count, fee sign, prev-UTXO
 * presence) is pure JVM and runs headlessly — those rejections fire before any WalletCore call. The
 * presigning happy path needs the WalletCore JNI (frozen plan → `TransactionCompiler`, address
 * derivation) and skips gracefully when it is unavailable, the same pattern [SwapKitBtcSignerTest]
 * uses.
 */
class SwapKitLegacyP2PKHSignerTest {

    // An empty chain code makes the signer use the key as-is, so its vault script is the P2PKH of
    // VAULT_PUB_KEY's hash160 on every chain.
    private fun signer(coin: CoinType = CoinType.DOGECOIN) =
        SwapKitLegacyP2PKHSigner(VAULT_PUB_KEY, "", coin)

    /** hash160 of the vault key; needs the WalletCore JNI, so only call it inside JNI tests. */
    private fun vaultKeyHash(): String =
        Numeric.toHexStringNoPrefix(Hash.sha256RIPEMD(Numeric.hexStringToByteArray(VAULT_PUB_KEY)))

    @Test
    fun `rejects empty payload`() {
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(ByteArray(0), "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("empty"))
    }

    @Test
    fun `rejects a non-P2PKH input scriptPubKey`() {
        // A P2WPKH input (segwit) is not a legacy P2PKH — must be refused before any signing.
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 100_000,
                            prevScriptHex = "0014" + "11".repeat(20), // P2WPKH, not P2PKH
                        )
                    ),
                outputs = listOf(LegacyOut(amount = 99_000, scriptHex = p2pkh("33".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("input #0"))
        assertTrue(e.message!!.contains("not P2PKH"))
    }

    @Test
    fun `rejects a non-P2PKH output`() {
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 100_000,
                            prevScriptHex = p2pkh("11".repeat(20)),
                        )
                    ),
                // OP_RETURN-style output — not P2PKH, can't be re-emitted through the address API.
                outputs = listOf(LegacyOut(amount = 99_000, scriptHex = "6a04deadbeef")),
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("output #0"))
        assertTrue(e.message!!.contains("not P2PKH"))
    }

    @Test
    fun `rejects more than two outputs`() {
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 100_000,
                            prevScriptHex = p2pkh("11".repeat(20)),
                        )
                    ),
                outputs =
                    listOf(
                        LegacyOut(amount = 30_000, scriptHex = p2pkh("22".repeat(20))),
                        LegacyOut(amount = 30_000, scriptHex = p2pkh("33".repeat(20))),
                        LegacyOut(amount = 30_000, scriptHex = p2pkh("44".repeat(20))),
                    ),
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("3 outputs"))
    }

    @Test
    fun `rejects negative fee`() {
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 50_000,
                            prevScriptHex = p2pkh("11".repeat(20)),
                        )
                    ),
                // Outputs exceed inputs → fee would be negative.
                outputs = listOf(LegacyOut(amount = 60_000, scriptHex = p2pkh("22".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("negative fee"))
    }

    @Test
    fun `rejects a missing prev-tx UTXO record`() {
        // Input map present but carries neither NON_WITNESS_UTXO (0x00) nor WITNESS_UTXO (0x01).
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 100_000,
                            prevScriptHex = p2pkh("11".repeat(20)),
                            omitPrevUtxo = true,
                        )
                    ),
                outputs = listOf(LegacyOut(amount = 99_000, scriptHex = p2pkh("33".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("missing a prev-tx UTXO record"))
    }

    @Test
    fun `rejects an unsigned-tx input carrying a non-empty scriptSig`() {
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 100_000,
                            prevScriptHex = p2pkh("11".repeat(20)),
                            unsignedScriptSigHex = "ab",
                        )
                    ),
                outputs = listOf(LegacyOut(amount = 99_000, scriptHex = p2pkh("33".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("scriptSig must be empty"))
    }

    @Test
    fun `rejects a version-2 unsigned tx whose input enables a BIP68 relative locktime`() {
        // WalletCore rebuilds as version 1, which would silently drop the relative locktime.
        val psbt = singleInputPsbt(version = 2, sequence = 0x0000000AL)
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("BIP68"))
    }

    @Test
    fun `rejects an unsigned-tx version other than 1 or 2`() {
        val psbt = singleInputPsbt(version = 3, sequence = 0xFFFFFFFFL)
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("version 3 is unsupported"))
    }

    @Test
    fun `version rule accepts v1 with any sequence and v2 only with BIP68 disabled`() {
        SwapKitLegacyPsbtVersion.requireSignableAsV1(1, listOf(0L, 0xFFFFFFFFL))
        SwapKitLegacyPsbtVersion.requireSignableAsV1(2, listOf(0xFFFFFFFFL, 0xFFFFFFFEL, 1L shl 31))
        assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
            SwapKitLegacyPsbtVersion.requireSignableAsV1(2, listOf(0xFFFFFFFFL, 0x7FFFFFFFL))
        }
        assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
            SwapKitLegacyPsbtVersion.requireSignableAsV1(0, emptyList())
        }
    }

    @Test
    fun `normalizeToV1 rewrites only the version bytes of a version-2 PSBT`() {
        val v2 = singleInputPsbt(version = 2, sequence = 0xFFFFFFFEL, lockTime = 770_000)
        val normalized = SwapKitLegacyPsbtVersion.normalizeToV1(v2)
        // Same PSBT built as version 1 from the start: nothing else may differ.
        assertArrayEquals(
            singleInputPsbt(version = 1, sequence = 0xFFFFFFFEL, lockTime = 770_000),
            normalized,
        )
        assertEquals(1, v2.indices.count { v2[it] != normalized[it] })
    }

    @Test
    fun `normalizeToV1 leaves a version-1 PSBT unchanged`() {
        val v1 = singleInputPsbt(version = 1, sequence = 0x0000000AL)
        assertArrayEquals(v1, SwapKitLegacyPsbtVersion.normalizeToV1(v1))
    }

    @Test
    fun `normalizeToV1 refuses a PSBT it can't sign as version 1`() {
        assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
            SwapKitLegacyPsbtVersion.normalizeToV1(
                singleInputPsbt(version = 2, sequence = 0x0000000AL)
            )
        }
        assertThrows(SwapKitPsbtException::class.java) {
            SwapKitLegacyPsbtVersion.normalizeToV1(byteArrayOf(0x70, 0x73, 0x62, 0x74))
        }
    }

    @Test
    fun `normalizeToV1 turns live SwapKit version-2 PSBTs into version 1 (DOGE, BCH, DASH)`() {
        val fixtures = loadLiveV2Psbts()
        assertEquals(setOf("DOGE", "BCH", "DASH"), fixtures.keys)
        fixtures.forEach { (chain, psbt) ->
            val offset = SwapKitPsbtParser.unsignedTxOffset(psbt)
            assertEquals(
                "02000000",
                Numeric.toHexStringNoPrefix(psbt.copyOfRange(offset, offset + 4)),
            )

            val normalized = SwapKitLegacyPsbtVersion.normalizeToV1(psbt)

            assertEquals(
                "01000000",
                Numeric.toHexStringNoPrefix(normalized.copyOfRange(offset, offset + 4)),
                chain,
            )
            assertArrayEquals(
                psbt.copyOfRange(offset + 4, psbt.size),
                normalized.copyOfRange(offset + 4, normalized.size),
                chain,
            )
            assertArrayEquals(psbt.copyOfRange(0, offset), normalized.copyOfRange(0, offset), chain)
        }
    }

    @Test
    fun `rejects an unsigned-tx body with trailing bytes`() {
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 100_000,
                            prevScriptHex = p2pkh("11".repeat(20)),
                        )
                    ),
                outputs = listOf(LegacyOut(amount = 99_000, scriptHex = p2pkh("33".repeat(20)))),
                unsignedTxTrailerHex = "ff",
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("trailing bytes"))
    }

    @Test
    fun `getPreSignedImageHash - DOGE happy path returns one sighash per input (NON_WITNESS_UTXO)`() {
        try {
            val psbt =
                encodeLegacyPsbt(
                    inputs =
                        listOf(
                            LegacyIn(
                                prevTxIdDisplay = TXID_ONE,
                                vout = 0,
                                sequence = 0xFFFFFFFFL,
                                amount = 100_000,
                                prevScriptHex = p2pkh(vaultKeyHash()),
                                useNonWitnessUtxo = true,
                            )
                        ),
                    outputs =
                        listOf(
                            LegacyOut(amount = 60_000, scriptHex = p2pkh("22".repeat(20))),
                            LegacyOut(amount = 39_000, scriptHex = p2pkh(vaultKeyHash())),
                        ),
                )
            val hashes = signer(CoinType.DOGECOIN).getPreSignedImageHash(psbt, "", FROM_AMOUNT)
            assertEquals(1, hashes.size)
            assertEquals(64, hashes[0].length)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `getPreSignedImageHash - DASH happy path with two inputs (NON_WITNESS_UTXO)`() {
        try {
            val prevScript = p2pkh(vaultKeyHash())
            val psbt =
                encodeLegacyPsbt(
                    inputs =
                        listOf(
                            LegacyIn(
                                prevTxIdDisplay = TXID_ONE,
                                vout = 0,
                                sequence = 0xFFFFFFFFL,
                                amount = 70_000,
                                prevScriptHex = prevScript,
                                useNonWitnessUtxo = true,
                            ),
                            LegacyIn(
                                prevTxIdDisplay = TXID_TWO,
                                vout = 1,
                                sequence = 0xFFFFFFFFL,
                                amount = 40_000,
                                prevScriptHex = prevScript,
                                useNonWitnessUtxo = true,
                            ),
                        ),
                    outputs =
                        listOf(
                            LegacyOut(amount = 100_000, scriptHex = p2pkh("22".repeat(20))),
                            LegacyOut(amount = 9_000, scriptHex = prevScript),
                        ),
                )
            val hashes = signer(CoinType.DASH).getPreSignedImageHash(psbt, "", FROM_AMOUNT)
            // One sighash per signable input.
            assertEquals(2, hashes.size)
            // Sorted, distinct, 32-byte hex digests.
            assertEquals(hashes.sorted(), hashes)
            assertEquals(2, hashes.toSet().size)
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `buildSigningInputData - frozen plan reproduces the PSBT inputs, outputs, lockTime (DOGE)`() {
        try {
            // The frozen Bitcoin.SigningInput is what WalletCore compiles into the broadcast tx, so
            // pinning every field it carries pins the reconstruction the PR leans on. Building it
            // needs the WalletCore JNI (address derivation, script building); parsing the proto
            // back
            // is pure, and the expectations are derived from the PSBT — no precomputed golden
            // needed.
            val depositHash = "22".repeat(20)
            val changeHash = vaultKeyHash()
            val psbt =
                encodeLegacyPsbt(
                    inputs =
                        listOf(
                            LegacyIn(
                                prevTxIdDisplay = TXID_ONE,
                                vout = 3,
                                sequence = 0xFFFFFFFEL,
                                amount = 100_000,
                                prevScriptHex = p2pkh(changeHash),
                                useNonWitnessUtxo = true,
                            )
                        ),
                    outputs =
                        listOf(
                            LegacyOut(amount = 60_000, scriptHex = p2pkh(depositHash)),
                            LegacyOut(amount = 39_000, scriptHex = p2pkh(changeHash)),
                        ),
                    lockTime = 770_000,
                )

            val input =
                Bitcoin.SigningInput.parseFrom(
                    signer(CoinType.DOGECOIN).buildSigningInputData(psbt, "", FROM_AMOUNT)
                )

            // lockTime threaded through (the review fix) so the rebuilt tx_id matches the PSBT.
            assertEquals(770_000, input.lockTime)

            // Frozen plan: amounts/fee derived verbatim from the PSBT, never replanned.
            assertEquals(SigningError.OK, input.plan.error)
            assertEquals(100_000L, input.plan.availableAmount)
            assertEquals(60_000L, input.plan.amount)
            assertEquals(39_000L, input.plan.change)
            assertEquals(1_000L, input.plan.fee)

            // The single UTXO is re-emitted verbatim: outpoint (LE hash + index + sequence),
            // amount,
            // and the exact prevout scriptPubKey.
            assertEquals(1, input.utxoCount)
            val utxo = input.getUtxo(0)
            assertArrayEquals(
                sha256d(encodePrevTx(3, 100_000, p2pkh(changeHash))),
                utxo.outPoint.hash.toByteArray(),
            )
            assertEquals(3, utxo.outPoint.index)
            assertEquals(0xFFFFFFFE.toInt(), utxo.outPoint.sequence)
            assertEquals(100_000L, utxo.amount)
            assertEquals(p2pkh(changeHash), Numeric.toHexStringNoPrefix(utxo.script.toByteArray()))

            // toAddress/changeAddress are derived from the PSBT's own output hash160s (DOGE `D…`),
            // so WalletCore re-emits the exact output scripts the PSBT shipped.
            assertTrue(input.toAddress.startsWith("D"), "deposit ${input.toAddress}")
            assertTrue(input.changeAddress.startsWith("D"), "change ${input.changeAddress}")
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `buildSigningInputData - BCH reproduces the PSBT and selects the SIGHASH_FORKID hash type`() {
        try {
            // BCH rides the same legacy path as DOGE/DASH but is the one chain whose CoinType flips
            // the sighash to SIGHASH_ALL | SIGHASH_FORKID (0x41). Pin both the reconstruction and
            // that FORKID selection so the BCH-specific branch is covered.
            val depositHash = "22".repeat(20)
            val changeHash = vaultKeyHash()
            val psbt =
                encodeLegacyPsbt(
                    inputs =
                        listOf(
                            LegacyIn(
                                prevTxIdDisplay = TXID_ONE,
                                vout = 3,
                                sequence = 0xFFFFFFFEL,
                                amount = 100_000,
                                prevScriptHex = p2pkh(changeHash),
                            )
                        ),
                    outputs =
                        listOf(
                            LegacyOut(amount = 60_000, scriptHex = p2pkh(depositHash)),
                            LegacyOut(amount = 39_000, scriptHex = p2pkh(changeHash)),
                        ),
                    lockTime = 770_000,
                )

            val input =
                Bitcoin.SigningInput.parseFrom(
                    signer(CoinType.BITCOINCASH).buildSigningInputData(psbt, "", FROM_AMOUNT)
                )

            // SIGHASH_ALL (0x01) | SIGHASH_FORKID (0x40) — the BCH-only sighash flag.
            assertEquals(0x41, input.hashType)

            assertEquals(770_000, input.lockTime)
            assertEquals(SigningError.OK, input.plan.error)
            assertEquals(100_000L, input.plan.availableAmount)
            assertEquals(60_000L, input.plan.amount)
            assertEquals(39_000L, input.plan.change)
            assertEquals(1_000L, input.plan.fee)

            assertEquals(1, input.utxoCount)
            val utxo = input.getUtxo(0)
            assertArrayEquals(
                Numeric.hexStringToByteArray(TXID_ONE).reversedArray(),
                utxo.outPoint.hash.toByteArray(),
            )
            assertEquals(3, utxo.outPoint.index)
            assertEquals(0xFFFFFFFE.toInt(), utxo.outPoint.sequence)
            assertEquals(100_000L, utxo.amount)
            assertEquals(p2pkh(changeHash), Numeric.toHexStringNoPrefix(utxo.script.toByteArray()))

            // BCH legacy P2PKH addresses use version byte 0x00 → Base58 `1…` (CashAddr derives the
            // same hash160), so WalletCore re-emits the exact output scripts the PSBT shipped.
            assertTrue(input.toAddress.startsWith("1"), "deposit ${input.toAddress}")
            assertTrue(input.changeAddress.startsWith("1"), "change ${input.changeAddress}")
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `rejects a NON_WITNESS_UTXO that does not hash to the outpoint txid`() {
        // BIP-174 binding: the embedded prev-tx must double-SHA256 to the input's outpoint txid.
        // corruptNonWitnessTxid points the outpoint at an unrelated txid while still embedding a
        // (well-formed) prev-tx — the binding check must reject it before any signing.
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 100_000,
                            prevScriptHex = p2pkh("11".repeat(20)),
                            useNonWitnessUtxo = true,
                            corruptNonWitnessTxid = true,
                        )
                    ),
                outputs = listOf(LegacyOut(amount = 99_000, scriptHex = p2pkh("33".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", FROM_AMOUNT)
            }
        assertTrue(e.message!!.contains("does not hash to its outpoint txid"))
    }

    @Test
    fun `rejects a miner fee that exceeds the quoted swap amount`() {
        // inputs 100_000, single 10_000 output → 90_000 fee. With a quoted swap amount of only
        // 1_000 the fee dwarfs it, so the ceiling refuses to sign (the balance-burn guard).
        val psbt =
            encodeLegacyPsbt(
                inputs =
                    listOf(
                        LegacyIn(
                            prevTxIdDisplay = TXID_ONE,
                            vout = 0,
                            sequence = 0xFFFFFFFFL,
                            amount = 100_000,
                            prevScriptHex = p2pkh("11".repeat(20)),
                        )
                    ),
                outputs = listOf(LegacyOut(amount = 10_000, scriptHex = p2pkh("22".repeat(20)))),
            )
        val e =
            assertThrows(SwapKitLegacyP2PKHSignerException::class.java) {
                signer().buildSigningInputData(psbt, "", BigInteger.valueOf(1_000))
            }
        assertTrue(e.message!!.contains("exceeds the quoted swap amount"))
    }

    private fun singleInputPsbt(version: Long, sequence: Long, lockTime: Long = 0) =
        encodeLegacyPsbt(
            inputs =
                listOf(
                    LegacyIn(
                        prevTxIdDisplay = TXID_ONE,
                        vout = 0,
                        sequence = sequence,
                        amount = 100_000,
                        prevScriptHex = p2pkh("11".repeat(20)),
                        useNonWitnessUtxo = true,
                    )
                ),
            outputs = listOf(LegacyOut(amount = 99_000, scriptHex = p2pkh("33".repeat(20)))),
            lockTime = lockTime,
            version = version,
        )

    /** Unsigned version-2 PSBTs SwapKit returned for DOGE / BCH / DASH, captured live. */
    private fun loadLiveV2Psbts(): Map<String, ByteArray> {
        val text =
            requireNotNull(javaClass.getResourceAsStream("/swapkit/legacy-psbt-v2.json")) {
                    "missing /swapkit/legacy-psbt-v2.json"
                }
                .bufferedReader()
                .use { it.readText() }
        return Json.parseToJsonElement(text)
            .jsonObject
            .filterKeys { !it.startsWith("_") }
            .mapValues { (_, value) -> Base64.getDecoder().decode(value.jsonPrimitive.content) }
    }

    private data class LegacyIn(
        val prevTxIdDisplay: String,
        val vout: Long,
        val sequence: Long,
        val amount: Long,
        val prevScriptHex: String,
        val useNonWitnessUtxo: Boolean = false,
        val omitPrevUtxo: Boolean = false,
        val unsignedScriptSigHex: String? = null,
        // Force the unsigned-tx outpoint to [prevTxIdDisplay] instead of the embedded prev-tx's
        // real
        // double-SHA256, so the NON_WITNESS_UTXO binding check fails.
        val corruptNonWitnessTxid: Boolean = false,
    )

    private data class LegacyOut(val amount: Long, val scriptHex: String)

    /**
     * P2PKH scriptPubKey for a 20-byte hash160 hex: `OP_DUP OP_HASH160 PUSH20 <hash> EQ CHECKSIG`.
     */
    private fun p2pkh(hash20Hex: String): String = "76a914$hash20Hex" + "88ac"

    /**
     * Minimal BIP-174 PSBT encoder for a **legacy** (non-segwit) tx: magic + global unsigned-tx +
     * per-input WITNESS_UTXO or NON_WITNESS_UTXO maps. The unsigned-tx body has no marker/flag.
     */
    private fun encodeLegacyPsbt(
        inputs: List<LegacyIn>,
        outputs: List<LegacyOut>,
        lockTime: Long = 0,
        version: Long = 1,
        unsignedTxTrailerHex: String? = null,
    ): ByteArray {
        // Precompute the embedded prev-tx for NON_WITNESS inputs so the unsigned-tx outpoint can
        // bind to its real double-SHA256 (the txid), matching BIP-174.
        val prevTxByInput =
            inputs.map { input ->
                if (input.useNonWitnessUtxo && !input.omitPrevUtxo) {
                    encodePrevTx(input.vout, input.amount, input.prevScriptHex)
                } else null
            }
        fun outpointLE(i: Int): ByteArray {
            val prevTx = prevTxByInput[i]
            return if (prevTx != null && !inputs[i].corruptNonWitnessTxid) sha256d(prevTx)
            else Numeric.hexStringToByteArray(inputs[i].prevTxIdDisplay).reversedArray()
        }

        val unsigned = ByteArrayOutputStream()
        unsigned.write(le32(version))
        unsigned.write(varInt(inputs.size.toLong()))
        inputs.forEachIndexed { i, input ->
            unsigned.write(outpointLE(i))
            unsigned.write(le32(input.vout))
            val scriptSig =
                input.unsignedScriptSigHex?.let { Numeric.hexStringToByteArray(it) } ?: ByteArray(0)
            unsigned.write(varInt(scriptSig.size.toLong()))
            unsigned.write(scriptSig)
            unsigned.write(le32(input.sequence))
        }
        unsigned.write(varInt(outputs.size.toLong()))
        outputs.forEach { output ->
            unsigned.write(le64(output.amount))
            val script = Numeric.hexStringToByteArray(output.scriptHex)
            unsigned.write(varInt(script.size.toLong()))
            unsigned.write(script)
        }
        unsigned.write(le32(lockTime))
        unsignedTxTrailerHex?.let { unsigned.write(Numeric.hexStringToByteArray(it)) }

        val psbt = ByteArrayOutputStream()
        psbt.write(MAGIC)
        val unsignedBytes = unsigned.toByteArray()
        psbt.write(byteArrayOf(0x01, 0x00)) // global key 0x00
        psbt.write(varInt(unsignedBytes.size.toLong()))
        psbt.write(unsignedBytes)
        psbt.write(0x00) // global map terminator

        inputs.forEachIndexed { i, input ->
            if (!input.omitPrevUtxo) {
                if (input.useNonWitnessUtxo) {
                    // NON_WITNESS_UTXO (key 0x00): a full prev-tx whose output[vout] is the UTXO.
                    val prevTx = prevTxByInput[i]!!
                    psbt.write(byteArrayOf(0x01, 0x00))
                    psbt.write(varInt(prevTx.size.toLong()))
                    psbt.write(prevTx)
                } else {
                    // WITNESS_UTXO (key 0x01): amount(8 LE) + varint(scriptLen) + scriptPubKey.
                    val witnessUtxo = ByteArrayOutputStream()
                    witnessUtxo.write(le64(input.amount))
                    val script = Numeric.hexStringToByteArray(input.prevScriptHex)
                    witnessUtxo.write(varInt(script.size.toLong()))
                    witnessUtxo.write(script)
                    val wu = witnessUtxo.toByteArray()
                    psbt.write(byteArrayOf(0x01, 0x01))
                    psbt.write(varInt(wu.size.toLong()))
                    psbt.write(wu)
                }
            }
            psbt.write(0x00) // input map terminator
        }
        outputs.forEach { psbt.write(0x00) } // empty per-output maps
        return psbt.toByteArray()
    }

    /**
     * A minimal legacy prev-transaction whose output [vout] carries [amount]/[scriptHex]; padding
     * outputs before [vout] are tiny P2PKH so the parser walks past them to the target.
     */
    private fun encodePrevTx(vout: Long, amount: Long, scriptHex: String): ByteArray {
        val tx = ByteArrayOutputStream()
        tx.write(le32(1)) // version
        tx.write(varInt(1)) // one input
        tx.write(ByteArray(32)) // prev txid (zeros)
        tx.write(le32(0)) // prev vout
        tx.write(varInt(0)) // empty scriptSig
        tx.write(le32(0xFFFFFFFFL)) // sequence
        val outCount = vout + 1
        tx.write(varInt(outCount))
        for (i in 0 until outCount) {
            if (i == vout) {
                tx.write(le64(amount))
                val script = Numeric.hexStringToByteArray(scriptHex)
                tx.write(varInt(script.size.toLong()))
                tx.write(script)
            } else {
                tx.write(le64(1_000))
                val pad = Numeric.hexStringToByteArray(p2pkh("00".repeat(20)))
                tx.write(varInt(pad.size.toLong()))
                tx.write(pad)
            }
        }
        tx.write(le32(0)) // locktime
        return tx.toByteArray()
    }

    private fun le32(v: Long): ByteArray =
        byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())

    private fun le64(v: Long): ByteArray = ByteArray(8) { i -> (v ushr (i * 8)).toByte() }

    private fun varInt(v: Long): ByteArray =
        when {
            v < 0xFDL -> byteArrayOf(v.toByte())
            v <= 0xFFFFL -> byteArrayOf(0xFDu.toByte(), v.toByte(), (v ushr 8).toByte())
            else -> byteArrayOf(0xFEu.toByte()) + le32(v)
        }

    private fun sha256d(data: ByteArray): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(digest.digest(data))
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

    private companion object {
        private val MAGIC = byteArrayOf(0x70, 0x73, 0x62, 0x74, 0xff.toByte())
        // Generous quoted swap amount — well above every fixture's fee so the fee-ceiling bound
        // never trips except in the dedicated rejection test (which passes its own small value).
        private val FROM_AMOUNT = BigInteger.valueOf(1_000_000)
        private const val VAULT_PUB_KEY =
            "025476c2e83188368da1ff3e292e7acafcdb3566bb0ad253f62fc70f07aeee6357"
        private const val TXID_ONE =
            "0000000000000000000000000000000000000000000000000000000000000001"
        private const val TXID_TWO =
            "0000000000000000000000000000000000000000000000000000000000000002"
    }
}
