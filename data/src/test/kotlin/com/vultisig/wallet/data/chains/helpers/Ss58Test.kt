package com.vultisig.wallet.data.chains.helpers

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Vectors are the ones the extension's `decodeTransferCall.test.ts` pins for the same recipient
 * under each prefix, plus two AccountIds that are deliberately *not* Edwards points — Bob's sr25519
 * dev key and the treasury's pallet-derived id — since those are exactly what WalletCore's
 * `AnyAddress` route could not spell.
 */
class Ss58Test {

    @Test
    fun `an ed25519 account id encodes under the Polkadot and generic prefixes`() {
        Ss58.encode(ALICE, POLKADOT) shouldBe "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5"
        Ss58.encode(ALICE, GENERIC) shouldBe "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
    }

    @Test
    fun `an sr25519 account id that is not an Edwards point still encodes`() {
        Ss58.encode(BOB, POLKADOT) shouldBe "14E5nqKAp3oAJcmzgZhUD2RcptBeUBScxKHgJKU4HPNcKVf3"
        Ss58.encode(BOB, GENERIC) shouldBe "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty"
    }

    @Test
    fun `a pallet account id encodes`() {
        Ss58.encode(TREASURY, POLKADOT) shouldBe "13UVJyLnbVp9RBZYFwFGyDvVd1y27Tt8tkntv6Q7JVPhFsTB"
    }

    @Test
    fun `leading zero bytes survive as leading ones`() {
        Ss58.encode(ByteArray(32), POLKADOT) shouldBe "111111111111111111111111111111111HC1"
    }

    @Test
    fun `anything but a 32-byte account id is refused`() {
        shouldThrow<IllegalArgumentException> { Ss58.encode(ByteArray(31), POLKADOT) }
        shouldThrow<IllegalArgumentException> { Ss58.encode(ByteArray(33), POLKADOT) }
    }

    @Test
    fun `a prefix outside the single-byte range is refused`() {
        shouldThrow<IllegalArgumentException> { Ss58.encode(ALICE, 64) }
        shouldThrow<IllegalArgumentException> { Ss58.encode(ALICE, -1) }
    }

    private companion object {
        const val POLKADOT = 0
        const val GENERIC = 42

        val ALICE =
            "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d".hexToByteArray()

        val BOB =
            "8eaf04151687736326c9fea17e25fc5287613693c912909cb226aa4794f26a48".hexToByteArray()

        /** `modlpy/trsry` zero-padded — the Polkadot treasury. */
        val TREASURY = "modlpy/trsry".toByteArray().copyOf(32)
    }
}
