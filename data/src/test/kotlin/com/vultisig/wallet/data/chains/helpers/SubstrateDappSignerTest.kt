package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.payload.SubstrateSignerPayload
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * Pins the signing bytes to the output of the extension's `constructPolkadotSigningPayload`
 * (`@polkadot/util` `compactToU8a` / `hexToU8a` / `blake2AsU8a`), captured for each fixture with
 * the real library. The initiator signs those bytes and nothing else, so one differing byte here is
 * a ceremony that signs two messages.
 */
class SubstrateDappSignerTest {

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun payload(
        vararg overrides: Pair<String, String?>,
        extras: String = "\"signedExtensions\":[]",
    ): SubstrateSignerPayload {
        val fields = REALISTIC.toMutableMap()
        overrides.forEach { (key, value) ->
            if (value == null) fields.remove(key) else fields[key] = value
        }
        val json =
            fields.entries.joinToString(",", prefix = "{", postfix = ",$extras,\"version\":4}") {
                "\"${it.key}\":\"${it.value}\""
            }
        return requireNotNull(SubstrateSignerPayload.fromMemo(json))
    }

    @Test
    fun `the playground's all-zero payload is signed raw, field by field`() {
        val playground =
            payload(
                "address" to "",
                "blockHash" to "0x" + "00".repeat(32),
                "blockNumber" to "0x00000000",
                "era" to "0x0000",
                "method" to "0x0000",
                "nonce" to "0x00000000",
                "specVersion" to "0x00000000",
                "tip" to "0x" + "00".repeat(16),
                "transactionVersion" to "0x00000000",
            )

        hex(SubstrateDappSigner.signingBytes(playground)) shouldBe
            "0000" + // method
                "0000" + // era
                "00" + // compact(nonce = 0)
                "00" + // compact(tip = 0)
                "00" + // mode = 0
                "00000000" + // specVersion
                "00000000" + // transactionVersion
                GENESIS.removePrefix("0x") +
                "00".repeat(32) +
                "00" // Option<metadataHash> = None
    }

    @Test
    fun `a mortal transfer with a four-byte compact tip matches the extension byte for byte`() {
        hex(SubstrateDappSigner.signingBytes(payload())) shouldBe
            "050300d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d0700e40b5402" +
                "f502" + // era
                "1d01" + // compact(0x47 = 71): two-byte mode
                "1e5a4b00" + // compact(1_234_567): four-byte mode
                "00" + // CheckMetadataHash mode = 0
                "f84e0f00" + // specVersion 1_003_256 LE
                "1a000000" + // transactionVersion 26 LE
                GENESIS.removePrefix("0x") +
                BLOCK_HASH.removePrefix("0x") +
                "00" // Option<metadataHash> = None
    }

    @Test
    fun `a tip past 2^30 takes the big-integer compact mode with a length prefix`() {
        val bytes =
            SubstrateDappSigner.signingBytes(
                payload(
                    "nonce" to "0x00000100",
                    "tip" to "0x" + BigInteger.TWO.pow(40).toString(16).padStart(32, '0'),
                )
            )

        hex(bytes) shouldBe
            "050300d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d0700e40b5402" +
                "f502" +
                "0104" + // compact(256)
                "0b000000000001" + // compact(2^40): (5 bytes - 4) << 2 | 0b11, then LE magnitude
                "00" +
                "f84e0f00" +
                "1a000000" +
                GENESIS.removePrefix("0x") +
                BLOCK_HASH.removePrefix("0x") +
                "00"
    }

    @Test
    fun `a payload longer than 256 bytes is blake2b-256 hashed before signing`() {
        val bytes =
            SubstrateDappSigner.signingBytes(payload("method" to "0x0700" + "ab".repeat(300)))

        bytes.size shouldBe 32
        hex(bytes) shouldBe "6b59d26a37914c7bd362b8dee98e2790b5643d1867cda84d5b28c093d1da634f"
    }

    @Test
    fun `a missing tip is a zero tip`() {
        hex(SubstrateDappSigner.signingBytes(payload("tip" to null))) shouldBe
            hex(SubstrateDappSigner.signingBytes(payload("tip" to "0x" + "00".repeat(16))))
    }

    @Test
    fun `a decimal tip reads like a hex tip of the same value`() {
        hex(SubstrateDappSigner.signingBytes(payload("tip" to "1234567"))) shouldBe
            hex(SubstrateDappSigner.signingBytes(payload()))
    }

    // Vectors from `@polkadot/types` 16.5.6 `ExtrinsicPayload.toU8a({ method: true })` with the
    // relay chain's signed extensions. `mode` sits after the tip, `Option<metadataHash>` after the
    // block hash.
    @Test
    fun `CheckMetadataHash adds the mode byte and a None metadata hash`() {
        val bytes =
            SubstrateDappSigner.signingBytes(
                payload(extras = "$RELAY_EXTENSIONS,\"mode\":0,\"metadataHash\":null")
            )

        hex(bytes) shouldBe
            "050300d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d0700e40b5402" +
                "f502" +
                "1d01" +
                "1e5a4b00" +
                "00" + // mode = 0
                "f84e0f00" +
                "1a000000" +
                GENESIS.removePrefix("0x") +
                BLOCK_HASH.removePrefix("0x") +
                "00" // Option<metadataHash> = None
    }

    @Test
    fun `CheckMetadataHash mode 1 carries the metadata hash as Some`() {
        val bytes =
            SubstrateDappSigner.signingBytes(
                payload(
                    extras =
                        "$RELAY_EXTENSIONS,\"mode\":1,\"metadataHash\":\"0x${"ab".repeat(32)}\""
                )
            )

        hex(bytes) shouldBe
            "050300d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d0700e40b5402" +
                "f502" +
                "1d01" +
                "1e5a4b00" +
                "01" + // mode = 1
                "f84e0f00" +
                "1a000000" +
                GENESIS.removePrefix("0x") +
                BLOCK_HASH.removePrefix("0x") +
                "01" + // Option<metadataHash> = Some
                "ab".repeat(32)
    }

    @Test
    fun `the metadata hash bytes are signed whatever signedExtensions lists`() {
        val unlisted = SubstrateDappSigner.signingBytes(payload())
        val listed =
            SubstrateDappSigner.signingBytes(
                payload(extras = "$RELAY_EXTENSIONS,\"mode\":0,\"metadataHash\":null")
            )

        hex(unlisted) shouldBe hex(listed)
    }

    @Test
    fun `a malformed mode or metadata hash is refused`() {
        shouldThrow<IllegalStateException> {
            SubstrateDappSigner.signingBytes(payload(extras = "$RELAY_EXTENSIONS,\"mode\":2"))
        }
        shouldThrow<IllegalStateException> {
            SubstrateDappSigner.signingBytes(payload(extras = "$RELAY_EXTENSIONS,\"mode\":1"))
        }
        shouldThrow<IllegalStateException> {
            SubstrateDappSigner.signingBytes(
                payload(extras = "$RELAY_EXTENSIONS,\"mode\":0,\"metadataHash\":\"0x${"ab".repeat(32)}\"")
            )
        }
        shouldThrow<IllegalStateException> {
            SubstrateDappSigner.signingBytes(
                payload(extras = "$RELAY_EXTENSIONS,\"mode\":1,\"metadataHash\":\"0xabcd\"")
            )
        }
    }

    @Test
    fun `the pre-signed image hash is the signing bytes as unprefixed hex`() {
        SubstrateDappSigner.getPreSignedImageHash(payload()) shouldBe
            listOf(hex(SubstrateDappSigner.signingBytes(payload())))
    }

    // The extension's hexToU8a pads an odd-length string and parseInt stops at the first bad
    // character; a payload only a broken initiator produces is refused here, not guessed at.
    @Test
    fun `malformed fields are refused rather than padded or truncated`() {
        shouldThrow<IllegalStateException> {
            SubstrateDappSigner.signingBytes(payload("method" to "0x050"))
        }
        shouldThrow<IllegalStateException> {
            SubstrateDappSigner.signingBytes(payload("era" to "0xzz"))
        }
        shouldThrow<IllegalStateException> {
            SubstrateDappSigner.signingBytes(payload("nonce" to "0x100000000"))
        }
        shouldThrow<IllegalStateException> {
            SubstrateDappSigner.signingBytes(payload("specVersion" to ""))
        }
        shouldThrow<IllegalStateException> {
            SubstrateDappSigner.signingBytes(payload("tip" to "-1"))
        }
    }

    private companion object {
        const val GENESIS = "0x91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val RELAY_EXTENSIONS =
            "\"signedExtensions\":[\"CheckNonZeroSender\",\"CheckSpecVersion\"," +
                "\"CheckTxVersion\",\"CheckGenesis\",\"CheckMortality\",\"CheckNonce\"," +
                "\"CheckWeight\",\"ChargeTransactionPayment\",\"PrevalidateAttests\"," +
                "\"CheckMetadataHash\"]"
        const val BLOCK_HASH = "0x1f5a9d2c1b8e7f6a5d4c3b2a19087f6e5d4c3b2a19087f6e5d4c3b2a19087f6e"
        const val ALICE = "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"

        // transfer_keep_alive(Alice, 10 DOT), nonce 71, tip 0.0001234567 DOT, on runtime 1_003_256.
        val REALISTIC =
            mapOf(
                "address" to "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5",
                "blockHash" to BLOCK_HASH,
                "blockNumber" to "0x01312d00",
                "era" to "0xf502",
                "genesisHash" to GENESIS,
                "method" to "0x050300" + ALICE + "0700e40b5402",
                "nonce" to "0x00000047",
                "specVersion" to "0x000f4ef8",
                "tip" to "0x0000000000000000000000000012d687",
                "transactionVersion" to "0x0000001a",
            )
    }
}
