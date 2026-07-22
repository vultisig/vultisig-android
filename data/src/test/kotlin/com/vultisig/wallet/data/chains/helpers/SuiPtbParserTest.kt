package com.vultisig.wallet.data.chains.helpers

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers [SuiPtbParser] against a real cross-platform-verified PTB (identical bytes decoded by
 * iOS/Windows in vultisig-sdk#705), hand-built fixtures per command/input variant, and malformed
 * input that must fall back to the raw-bytes view rather than crash.
 */
class SuiPtbParserTest {

    // Real dApp-shaped PTB (split + transfer with explicit gas data), byte-identical to the
    // fixture iOS/Windows use for their own decoders — decoding it here proves Android produces
    // the same shape for the same signed bytes.
    private val realPtb =
        "AAACAAhkAAAAAAAAAAAgW4yMD3sdSyqcPk9QYXKDlKW2x9jp8KGyw9Tl9gcYKTACAgABAQAAAQEDAAAAAAEBAFuMjA97" +
            "HUsqnD5PUGFyg5SltsfY6fChssPU5fYHGCkwARERERERERERERERERERERERERERERERERERERERERERAQAA" +
            "AAAAAAAgBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwdbjIwPex1LKpw+T1BhcoOUpbbH2OnwobLD1O" +
            "X2BxgpMOgDAAAAAAAAwMYtAAAAAAAA"

    @Test
    fun `parse decodes the real captured PTB into the shape iOS and Windows agree on`() {
        val parsed = SuiPtbParser.parse(realPtb)

        assertEquals(2, parsed.inputs.size)
        assertEquals(2, parsed.commands.size)
        assertEquals(1, parsed.gasPaymentCount)
        assertEquals(BigInteger.valueOf(3_000_000), parsed.gasBudget)
        assertEquals(BigInteger.valueOf(1000), parsed.gasPrice)
        assertTrue(parsed.sender.startsWith("0x"))

        val input0 = parsed.inputs[0] as SuiPtbInput.Pure
        assertEquals("u64", input0.value.type)
        val input1 = parsed.inputs[1] as SuiPtbInput.Pure
        assertEquals("address", input1.value.type)

        val split = parsed.commands[0] as SuiCommand.SplitCoins
        assertEquals(SuiArgument.GasCoin, split.coin)
        assertEquals(listOf(SuiArgument.Input(0)), split.amounts)

        val transfer = parsed.commands[1] as SuiCommand.TransferObjects
        assertEquals(listOf(SuiArgument.NestedResult(0, 0)), transfer.objects)
        assertEquals(SuiArgument.Input(1), transfer.address)
    }

    @Test
    fun `parse decodes a MoveCall with a shared object input and generic type arguments`() {
        val moveCall =
            Bcs.uleb(0) +
                Bcs.address(0xCD) +
                Bcs.string("pool") +
                Bcs.string("swap") +
                Bcs.vec(
                    Bcs.uleb(7) +
                        Bcs.address(0x02) +
                        Bcs.string("sui") +
                        Bcs.string("SUI") +
                        Bcs.vec()
                ) +
                Bcs.vec(Bcs.uleb(1) + Bcs.u16(0), Bcs.uleb(0))
        val sharedInput =
            Bcs.uleb(1) + Bcs.uleb(1) + Bcs.address(0xAB) + Bcs.u64(7) + Bcs.bool(true)

        val bytes =
            transactionData(
                inputs = Bcs.vec(sharedInput),
                commands = Bcs.vec(moveCall),
                sender = Bcs.address(0xEF),
                gasPrice = 1000,
                gasBudget = 5_000_000,
            )

        val parsed = SuiPtbParser.parse(Base64.getEncoder().encodeToString(bytes))

        val input = parsed.inputs.single() as SuiPtbInput.Object
        assertEquals(SuiObjectKind.SHARED, input.kind)
        assertEquals(true, input.mutable)
        assertEquals("0x" + "ab".repeat(32), input.objectId)

        val moveCallCommand = parsed.commands.single() as SuiCommand.MoveCall
        assertEquals("0x" + "cd".repeat(32), moveCallCommand.packageId)
        assertEquals("pool", moveCallCommand.module)
        assertEquals("swap", moveCallCommand.function)
        assertEquals(listOf("0x" + "02".repeat(32) + "::sui::SUI"), moveCallCommand.typeArguments)
        assertEquals(listOf(SuiArgument.Input(0), SuiArgument.GasCoin), moveCallCommand.arguments)
    }

    @Test
    fun `parse decodes TransferObjects, MergeCoins, Publish, MakeMoveVec and Upgrade commands`() {
        val transferObjects = Bcs.uleb(1) + Bcs.vec(Bcs.uleb(0)) + Bcs.uleb(1) + Bcs.u16(0)
        val mergeCoins = Bcs.uleb(3) + Bcs.uleb(0) + Bcs.vec(Bcs.uleb(1) + Bcs.u16(1))
        val publish =
            Bcs.uleb(4) + Bcs.vec(Bcs.bytesVec(byteArrayOf(1, 2))) + Bcs.vec(Bcs.address(0x01))
        val makeMoveVec =
            Bcs.uleb(5) + Bcs.uleb(1) + Bcs.uleb(2) /* TypeTag.u64 */ + Bcs.vec(Bcs.uleb(0))
        val upgrade =
            Bcs.uleb(6) +
                Bcs.vec(Bcs.bytesVec(byteArrayOf(9))) +
                Bcs.vec() +
                Bcs.address(0x03) +
                Bcs.uleb(0)

        val bytes =
            transactionData(
                inputs = Bcs.vec(),
                commands = Bcs.vec(transferObjects, mergeCoins, publish, makeMoveVec, upgrade),
            )

        val parsed = SuiPtbParser.parse(Base64.getEncoder().encodeToString(bytes))

        val transfer = parsed.commands[0] as SuiCommand.TransferObjects
        assertEquals(listOf(SuiArgument.GasCoin), transfer.objects)
        assertEquals(SuiArgument.Input(0), transfer.address)

        val merge = parsed.commands[1] as SuiCommand.MergeCoins
        assertEquals(SuiArgument.GasCoin, merge.destination)
        assertEquals(listOf(SuiArgument.Input(1)), merge.sources)

        val publishCommand = parsed.commands[2] as SuiCommand.Publish
        assertEquals(1, publishCommand.moduleCount)
        assertEquals(1, publishCommand.dependencyCount)

        val makeMoveVecCommand = parsed.commands[3] as SuiCommand.MakeMoveVec
        assertEquals("u64", makeMoveVecCommand.elementType)
        assertEquals(1, makeMoveVecCommand.elementCount)

        val upgradeCommand = parsed.commands[4] as SuiCommand.Upgrade
        assertEquals("0x" + "03".repeat(32), upgradeCommand.packageId)
        assertEquals(1, upgradeCommand.moduleCount)
        assertEquals(0, upgradeCommand.dependencyCount)
        assertEquals(SuiArgument.GasCoin, upgradeCommand.ticket)
    }

    @Test
    fun `parse decodes ImmOrOwned and Receiving object inputs without a mutability flag`() {
        val immOrOwned =
            Bcs.uleb(1) +
                Bcs.uleb(0) +
                Bcs.address(0x01) +
                Bcs.u64(1) +
                Bcs.bytesVec(byteArrayOf(1))
        val receiving =
            Bcs.uleb(1) +
                Bcs.uleb(2) +
                Bcs.address(0x02) +
                Bcs.u64(2) +
                Bcs.bytesVec(byteArrayOf(2))

        val bytes = transactionData(inputs = Bcs.vec(immOrOwned, receiving), commands = Bcs.vec())
        val parsed = SuiPtbParser.parse(Base64.getEncoder().encodeToString(bytes))

        val first = parsed.inputs[0] as SuiPtbInput.Object
        assertEquals(SuiObjectKind.IMM_OR_OWNED, first.kind)
        assertNull(first.mutable)

        val second = parsed.inputs[1] as SuiPtbInput.Object
        assertEquals(SuiObjectKind.RECEIVING, second.kind)
        assertNull(second.mutable)
    }

    @Test
    fun `parse classifies Pure input values by byte length`() {
        val boolInput = Bcs.uleb(0) + Bcs.bytesVec(byteArrayOf(1))
        val u8Input = Bcs.uleb(0) + Bcs.bytesVec(byteArrayOf(42))
        val u64Input = Bcs.uleb(0) + Bcs.bytesVec(Bcs.u64(1234))
        val addressInput = Bcs.uleb(0) + Bcs.bytesVec(Bcs.address(0x05))
        val bytesInput = Bcs.uleb(0) + Bcs.bytesVec(byteArrayOf(1, 2, 3))

        val bytes =
            transactionData(
                inputs = Bcs.vec(boolInput, u8Input, u64Input, addressInput, bytesInput),
                commands = Bcs.vec(),
            )
        val parsed = SuiPtbParser.parse(Base64.getEncoder().encodeToString(bytes))

        assertEquals(SuiPureValue("bool", "true"), (parsed.inputs[0] as SuiPtbInput.Pure).value)
        assertEquals(SuiPureValue("u8", "42"), (parsed.inputs[1] as SuiPtbInput.Pure).value)
        assertEquals(SuiPureValue("u64", "1234"), (parsed.inputs[2] as SuiPtbInput.Pure).value)
        assertEquals(
            SuiPureValue("address", "0x" + "05".repeat(32)),
            (parsed.inputs[3] as SuiPtbInput.Pure).value,
        )
        assertEquals(
            SuiPureValue("bytes(3)", "0x010203"),
            (parsed.inputs[4] as SuiPtbInput.Pure).value,
        )
    }

    @Test
    fun `parse handles a multi-byte ULEB128 vector count`() {
        // 200 requires two ULEB128 bytes (200 = 0xC8 -> 0xC8, 0x01), exercising the
        // continuation-bit path that every single-byte-count test above never reaches.
        val amounts = Bcs.vec(*Array(200) { Bcs.uleb(1) + Bcs.u16(0) })
        val splitCoins = Bcs.uleb(2) + Bcs.uleb(0) + amounts

        val bytes = transactionData(inputs = Bcs.vec(), commands = Bcs.vec(splitCoins))
        val parsed = SuiPtbParser.parse(Base64.getEncoder().encodeToString(bytes))

        val split = parsed.commands.single() as SuiCommand.SplitCoins
        assertEquals(200, split.amounts.size)
    }

    @Test
    fun `parse rejects an empty buffer`() {
        assertThrows(IllegalArgumentException::class.java) {
            SuiPtbParser.parse(Base64.getEncoder().encodeToString(ByteArray(0)))
        }
    }

    @Test
    fun `parse rejects invalid base64`() {
        assertThrows(IllegalArgumentException::class.java) { SuiPtbParser.parse("not-base64!!") }
    }

    @Test
    fun `parse rejects a truncated buffer`() {
        val bytes = transactionData(inputs = Bcs.vec(), commands = Bcs.vec())
        val truncated = bytes.copyOfRange(0, bytes.size - 10)

        assertThrows(IllegalArgumentException::class.java) {
            SuiPtbParser.parse(Base64.getEncoder().encodeToString(truncated))
        }
    }

    @Test
    fun `parse rejects an unsupported TransactionData variant`() {
        assertThrows(IllegalStateException::class.java) {
            SuiPtbParser.parse(Base64.getEncoder().encodeToString(Bcs.uleb(1)))
        }
    }

    @Test
    fun `parse rejects a non-ProgrammableTransaction TransactionKind`() {
        val bytes = Bcs.uleb(0) + Bcs.uleb(1) // TransactionKind.ChangeEpoch
        assertThrows(IllegalStateException::class.java) {
            SuiPtbParser.parse(Base64.getEncoder().encodeToString(bytes))
        }
    }

    @Test
    fun `parse rejects an unsupported CallArg variant`() {
        val bytes = transactionData(inputs = Bcs.vec(Bcs.uleb(9)), commands = Bcs.vec())
        assertThrows(IllegalStateException::class.java) {
            SuiPtbParser.parse(Base64.getEncoder().encodeToString(bytes))
        }
    }

    @Test
    fun `parse rejects an unsupported Command variant`() {
        val bytes = transactionData(inputs = Bcs.vec(), commands = Bcs.vec(Bcs.uleb(99)))
        assertThrows(IllegalStateException::class.java) {
            SuiPtbParser.parse(Base64.getEncoder().encodeToString(bytes))
        }
    }

    @Test
    fun `parse rejects an unsupported TypeTag variant in a MoveCall type argument`() {
        val moveCall =
            Bcs.uleb(0) +
                Bcs.address(0x01) +
                Bcs.string("m") +
                Bcs.string("f") +
                Bcs.vec(Bcs.uleb(42)) +
                Bcs.vec()
        val bytes = transactionData(inputs = Bcs.vec(), commands = Bcs.vec(moveCall))

        assertThrows(IllegalStateException::class.java) {
            SuiPtbParser.parse(Base64.getEncoder().encodeToString(bytes))
        }
    }

    @Test
    fun `parse rejects a vector count that exceeds the remaining buffer`() {
        // Declares 1000 inputs but the buffer holds none — a hostile/corrupted length must not
        // be trusted into a huge allocation loop.
        val bytes = Bcs.uleb(0) + Bcs.uleb(0) + Bcs.uleb(1000)
        assertThrows(IllegalArgumentException::class.java) {
            SuiPtbParser.parse(Base64.getEncoder().encodeToString(bytes))
        }
    }

    /** Assembles a full, valid `TransactionData::V1` envelope around caller-supplied PTB bytes. */
    private fun transactionData(
        inputs: ByteArray,
        commands: ByteArray,
        sender: ByteArray = Bcs.address(0x11),
        gasPayment: ByteArray = Bcs.vec(),
        gasOwner: ByteArray = Bcs.address(0x22),
        gasPrice: Long = 1000,
        gasBudget: Long = 3_000_000,
    ): ByteArray =
        Bcs.uleb(0) +
            Bcs.uleb(0) +
            inputs +
            commands +
            sender +
            gasPayment +
            gasOwner +
            Bcs.u64(gasPrice) +
            Bcs.u64(gasBudget) +
            Bcs.uleb(0) // TransactionExpiration.None, left unread by the parser
}

/** Hand-rolled BCS byte builders mirroring [SuiPtbParser]'s reader, one function per wire shape. */
private object Bcs {
    fun uleb(value: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var remaining = value
        while (true) {
            val byte = remaining and 0x7F
            remaining = remaining ushr 7
            if (remaining == 0) {
                out.write(byte)
                break
            } else {
                out.write(byte or 0x80)
            }
        }
        return out.toByteArray()
    }

    fun u16(value: Int): ByteArray =
        byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

    fun u64(value: Long): ByteArray = ByteArray(8) { i -> ((value ushr (8 * i)) and 0xFF).toByte() }

    fun bool(value: Boolean): ByteArray = byteArrayOf(if (value) 1 else 0)

    fun address(fill: Int): ByteArray = ByteArray(32) { fill.toByte() }

    fun bytesVec(bytes: ByteArray): ByteArray = uleb(bytes.size) + bytes

    fun string(value: String): ByteArray = bytesVec(value.toByteArray(Charsets.UTF_8))

    fun vec(vararg elements: ByteArray): ByteArray =
        uleb(elements.size) + elements.fold(ByteArray(0)) { acc, e -> acc + e }
}
