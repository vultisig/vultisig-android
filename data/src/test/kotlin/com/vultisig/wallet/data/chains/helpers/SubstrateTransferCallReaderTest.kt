package com.vultisig.wallet.data.chains.helpers

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * Port of the extension's `decodeTransferCall.test.ts`, so the amount a co-signer reviews on
 * Android is the amount the initiator reviewed — both read out of the same call bytes.
 */
class SubstrateTransferCallReaderTest {

    private fun transferCall(
        callIndex: Int,
        amount: BigInteger,
        trailing: ByteArray = ByteArray(0),
    ) =
        byteArrayOf(BALANCES_PALLET, callIndex.toByte(), MULTI_ADDRESS_ID) +
            ALICE +
            SubstrateScale.compact(amount) +
            trailing

    @Test
    fun `reads the value of a transfer_allow_death call`() {
        val call =
            SubstrateTransferCallReader.read(transferCall(0, BigInteger.valueOf(1_000_000_000)))

        call.shouldNotBeNull().amount shouldBe BigInteger.valueOf(1_000_000_000)
        call.destination.toList() shouldBe ALICE.toList()
    }

    @Test
    fun `reads the value of a transfer_keep_alive call`() {
        SubstrateTransferCallReader.read(transferCall(3, BigInteger.valueOf(12_345_600_000)))
            .shouldNotBeNull()
            .amount shouldBe BigInteger.valueOf(12_345_600_000)
    }

    @Test
    fun `decodes every compact width`() {
        listOf(
                BigInteger.valueOf(42),
                BigInteger.valueOf(16_383),
                BigInteger.valueOf(1_073_741_823),
                BigInteger("21000000000000000000"),
            )
            .forEach { amount ->
                SubstrateTransferCallReader.read(transferCall(0, amount))
                    .shouldNotBeNull()
                    .amount shouldBe amount
            }
    }

    @Test
    fun `a call outside the Balances pallet is not a transfer`() {
        SubstrateTransferCallReader.read(byteArrayOf(7, 2) + ALICE).shouldBeNull()
    }

    @Test
    fun `transfer_all names no value and is not a transfer`() {
        SubstrateTransferCallReader.read(byteArrayOf(BALANCES_PALLET, 4, MULTI_ADDRESS_ID, 1))
            .shouldBeNull()
    }

    // `parity-scale-codec` refuses each of these with "out of range decoding Compact<T>", so the
    // extrinsic would never decode on chain; a value read from them is a number for bytes the chain
    // rejects.
    @Test
    fun `a non-canonical compact is refused`() {
        val prefix = byteArrayOf(BALANCES_PALLET, 0, MULTI_ADDRESS_ID) + ALICE
        // 1 spelled in two-byte mode.
        shouldThrow<IllegalStateException> {
            SubstrateTransferCallReader.read(prefix + byteArrayOf(0x05, 0x00))
        }
        // 1 spelled in four-byte mode.
        shouldThrow<IllegalStateException> {
            SubstrateTransferCallReader.read(prefix + byteArrayOf(0x06, 0x00, 0x00, 0x00))
        }
        // 1 spelled in big-integer mode with a zero most-significant byte.
        shouldThrow<IllegalStateException> {
            SubstrateTransferCallReader.read(prefix + byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00))
        }
        // A 17-byte magnitude is wider than a u128 balance.
        shouldThrow<IllegalStateException> {
            SubstrateTransferCallReader.read(prefix + byteArrayOf(0x37) + ByteArray(17) { 1 })
        }
    }

    @Test
    fun `a transfer with trailing bytes is refused`() {
        shouldThrow<IllegalStateException> {
            SubstrateTransferCallReader.read(
                transferCall(0, BigInteger.ONE, trailing = byteArrayOf(0))
            )
        }
    }

    @Test
    fun `a MultiAddress that is not an AccountId is refused`() {
        shouldThrow<IllegalStateException> {
            SubstrateTransferCallReader.read(byteArrayOf(BALANCES_PALLET, 0, 0x01) + ALICE)
        }
    }

    @Test
    fun `a call that ends mid-field is refused`() {
        shouldThrow<IllegalStateException> {
            SubstrateTransferCallReader.read(
                byteArrayOf(BALANCES_PALLET, 0, MULTI_ADDRESS_ID) + ALICE.copyOf(31)
            )
        }
        shouldThrow<IllegalStateException> { SubstrateTransferCallReader.read(byteArrayOf(5)) }
    }

    private companion object {
        const val BALANCES_PALLET: Byte = 5
        const val MULTI_ADDRESS_ID: Byte = 0

        // Alice's well-known development AccountId32.
        val ALICE =
            "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
                .chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
    }
}
