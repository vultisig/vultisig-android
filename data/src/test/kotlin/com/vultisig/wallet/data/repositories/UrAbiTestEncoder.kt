package com.vultisig.wallet.data.repositories

import java.math.BigInteger

/**
 * Minimal ABI encoder for the V2 / V3 / V4 / WRAP_ETH input shapes consumed by
 * [UniversalRouterDecoder]. Used only by tests to construct realistic Universal Router calldata
 * fixtures. Mirrors what ethers' `AbiCoder.defaultAbiCoder().encode(...)` would emit for the
 * specific tuples we exercise.
 *
 * Lives in the test source set; production code uses Trust Wallet Core's ABI codec instead.
 */
internal object UrAbiTestEncoder {

    private const val WORD = 32

    fun commandsFor(vararg opcodes: Int): String =
        opcodes.joinToString(prefix = "0x", separator = "") { it.toString(16).padStart(2, '0') }

    fun encodeV2Input(
        recipient: String,
        amount1: BigInteger,
        amount2: BigInteger,
        path: List<String>,
        payerIsUser: Boolean,
    ): String =
        encodeHeadTail(
            staticHeads =
                listOf(
                    0 to addressWord(recipient),
                    1 to uintWord(amount1),
                    2 to uintWord(amount2),
                    4 to boolWord(payerIsUser),
                ),
            tails = listOf(3 to addressArrayBytes(path)),
            slotCount = 5,
        )

    fun encodeV3Input(
        recipient: String,
        amount1: BigInteger,
        amount2: BigInteger,
        path: ByteArray,
        payerIsUser: Boolean,
    ): String =
        encodeHeadTail(
            staticHeads =
                listOf(
                    0 to addressWord(recipient),
                    1 to uintWord(amount1),
                    2 to uintWord(amount2),
                    4 to boolWord(payerIsUser),
                ),
            tails = listOf(3 to bytesField(path)),
            slotCount = 5,
        )

    fun encodeV3Path(tokens: List<String>, fees: List<Int>): ByteArray {
        require(tokens.size == fees.size + 1) { "fees must be tokens.size - 1" }
        var out = ByteArray(0)
        for (i in fees.indices) {
            out += address20(tokens[i])
            // Solidity packs the fee as 3 big-endian bytes.
            out += ((fees[i] ushr 16) and 0xff).toByte()
            out += ((fees[i] ushr 8) and 0xff).toByte()
            out += (fees[i] and 0xff).toByte()
        }
        out += address20(tokens.last())
        return out
    }

    fun encodeWrapEthInput(recipient: String, amount: BigInteger): String =
        toHex(addressWord(recipient) + uintWord(amount))

    /**
     * Encode the V4 single-pool action params (works for both exact-in and exact-out — the two
     * uints carry the matching pair of values per direction).
     */
    fun encodeV4SingleSwapParams(
        currency0: String,
        currency1: String,
        fee: Int,
        tickSpacing: Int,
        hooks: String,
        zeroForOne: Boolean,
        amount1: BigInteger,
        amount2: BigInteger,
        hookData: ByteArray = ByteArray(0),
    ): String {
        // Tuple body (head + dynamic bytes tail). Tuple is dynamic because of hookData.
        val tupleHead =
            addressWord(currency0) +
                addressWord(currency1) +
                uintWord(BigInteger.valueOf(fee.toLong())) +
                intWord(BigInteger.valueOf(tickSpacing.toLong())) +
                addressWord(hooks) +
                boolWord(zeroForOne) +
                uintWord(amount1) +
                uintWord(amount2) +
                // bytes hookData offset within tuple: 9 words = 288.
                uintWord(BigInteger.valueOf(9L * WORD))
        val tupleBody = tupleHead + bytesField(hookData)
        // Single dynamic-tuple arg: 32-byte offset (= 32) + tuple body.
        return toHex(uintWord(BigInteger.valueOf(WORD.toLong())) + tupleBody)
    }

    fun encodeV4MultiSwapParams(
        currency: String,
        path: List<PathKey>,
        amount1: BigInteger,
        amount2: BigInteger,
    ): String {
        // Tuple body: currency, PathKey[] offset, amount1, amount2, then PathKey[] data.
        // PathKey[] starts after 4 tuple-head words (currency, pathOffset, amount1, amount2).
        val pathOffsetWithinTuple = BigInteger.valueOf(4L * WORD)
        val tupleBody =
            addressWord(currency) +
                uintWord(pathOffsetWithinTuple) +
                uintWord(amount1) +
                uintWord(amount2) +
                pathKeyArrayBytes(path)
        return toHex(uintWord(BigInteger.valueOf(WORD.toLong())) + tupleBody)
    }

    data class PathKey(
        val intermediateCurrency: String,
        val fee: Int,
        val tickSpacing: Int,
        val hooks: String,
        val hookData: ByteArray = ByteArray(0),
    )

    fun encodeV4SwapInput(actions: ByteArray, paramHexes: List<String>): String {
        val paramsBytes = paramHexes.map { hexToBytes(it) }
        // (bytes actions, bytes[] params)
        return encodeHeadTail(
            staticHeads = emptyList(),
            tails = listOf(0 to bytesField(actions), 1 to bytesArrayField(paramsBytes)),
            slotCount = 2,
        )
    }

    /**
     * Build the JSON shape `FourByteRepository.decodeFunctionArgs` would emit for a Universal
     * Router `execute(bytes,bytes[],uint256)` call, given the commands hex and per-input hex. Pass
     * `deadline = null` for the deadline-less overload.
     */
    fun buildExecuteInputsJson(
        commandsHex: String,
        inputHexes: List<String>,
        deadline: BigInteger? = BigInteger.ZERO,
    ): String = buildString {
        append('[')
        append('"').append(commandsHex).append('"')
        append(',')
        append('[')
        inputHexes.forEachIndexed { i, hex ->
            if (i > 0) append(',')
            append('"').append(hex).append('"')
        }
        append(']')
        if (deadline != null) {
            append(',')
            append('"').append(deadline.toString()).append('"')
        }
        append(']')
    }

    // ─── Primitives ──────────────────────────────────────────────────────────────

    private fun addressWord(address: String): ByteArray {
        val padded = ByteArray(WORD)
        System.arraycopy(address20(address), 0, padded, 12, 20)
        return padded
    }

    private fun uintWord(value: BigInteger): ByteArray = toWord(value)

    private fun intWord(value: BigInteger): ByteArray {
        if (value.signum() >= 0) return toWord(value)
        // Two's complement 32-byte representation for negative ints.
        val twos = BigInteger.ONE.shiftLeft(WORD * 8).add(value)
        return toWord(twos)
    }

    private fun boolWord(flag: Boolean): ByteArray =
        ByteArray(WORD).also { if (flag) it[WORD - 1] = 1 }

    private fun toWord(value: BigInteger): ByteArray {
        require(value.signum() >= 0) { "expected unsigned for uint word" }
        val raw = value.toByteArray()
        // BigInteger.toByteArray() may include a leading sign byte; strip it if present.
        val stripped =
            if (raw.size > WORD && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        require(stripped.size <= WORD) { "value too large for 32-byte word" }
        val padded = ByteArray(WORD)
        System.arraycopy(stripped, 0, padded, WORD - stripped.size, stripped.size)
        return padded
    }

    private fun address20(address: String): ByteArray {
        val cleaned = address.removePrefix("0x").removePrefix("0X")
        require(cleaned.length == 40) { "address must be 20 bytes (40 hex chars)" }
        return hexToBytes("0x$cleaned")
    }

    private fun hexToBytes(hex: String): ByteArray {
        val cleaned = hex.removePrefix("0x").removePrefix("0X")
        require(cleaned.length % 2 == 0) { "odd-length hex: $hex" }
        val out = ByteArray(cleaned.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(cleaned[i * 2], 16)
            val lo = Character.digit(cleaned[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "invalid hex fixture: $hex" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun bytesField(payload: ByteArray): ByteArray =
        toWord(BigInteger.valueOf(payload.size.toLong())) + padTo32(payload)

    private fun addressArrayBytes(addresses: List<String>): ByteArray {
        var out = toWord(BigInteger.valueOf(addresses.size.toLong()))
        for (addr in addresses) out += addressWord(addr)
        return out
    }

    private fun bytesArrayField(elements: List<ByteArray>): ByteArray {
        val lengthWord = toWord(BigInteger.valueOf(elements.size.toLong()))
        if (elements.isEmpty()) return lengthWord
        // length || k offset pointers (relative to area after length) || each bytes data
        val encodedElements = elements.map { bytesField(it) }
        val pointersSize = elements.size * WORD
        var cursor = pointersSize
        var head = ByteArray(0)
        for (encoded in encodedElements) {
            head += toWord(BigInteger.valueOf(cursor.toLong()))
            cursor += encoded.size
        }
        var tail = ByteArray(0)
        for (elem in encodedElements) tail += elem
        return lengthWord + head + tail
    }

    private fun pathKeyArrayBytes(keys: List<PathKey>): ByteArray {
        val lengthWord = toWord(BigInteger.valueOf(keys.size.toLong()))
        // Each PathKey is dynamic (has bytes hookData). Layout: length || head pointers || tails.
        val encodedKeys = keys.map { encodePathKey(it) }
        val pointersSize = keys.size * WORD
        var cursor = pointersSize
        var head = ByteArray(0)
        for (encoded in encodedKeys) {
            head += toWord(BigInteger.valueOf(cursor.toLong()))
            cursor += encoded.size
        }
        var tail = ByteArray(0)
        for (data in encodedKeys) tail += data
        return lengthWord + head + tail
    }

    private fun encodePathKey(pk: PathKey): ByteArray {
        // PathKey is `(address intermediateCurrency, uint24 fee, int24 tickSpacing, address hooks,
        // bytes hookData)`. Dynamic due to hookData. Head: 5 inline words + bytes offset.
        val head =
            addressWord(pk.intermediateCurrency) +
                uintWord(BigInteger.valueOf(pk.fee.toLong())) +
                intWord(BigInteger.valueOf(pk.tickSpacing.toLong())) +
                addressWord(pk.hooks) +
                // bytes offset within this PathKey: 5 head words after = 5 * 32 = 160.
                uintWord(BigInteger.valueOf(5L * WORD))
        return head + bytesField(pk.hookData)
    }

    private fun padTo32(data: ByteArray): ByteArray {
        val remainder = data.size % WORD
        if (remainder == 0) return data
        return data + ByteArray(WORD - remainder)
    }

    /**
     * Standard ABI head-tail encoding. [staticHeads] supplies inline 32-byte head words at the
     * given slot indices; [tails] is one `(slotIndex, dynamicTailBytes)` per dynamic arg ordered by
     * slot index. Slots not in either list are left as 32 zero bytes.
     */
    private fun encodeHeadTail(
        staticHeads: List<Pair<Int, ByteArray>>,
        tails: List<Pair<Int, ByteArray>>,
        slotCount: Int,
    ): String {
        val staticBySlot = staticHeads.toMap()
        val tailBySlot = tails.toMap()
        val headSize = slotCount * WORD
        val offsets = mutableMapOf<Int, Int>()
        var cursor = headSize
        for ((slot, encoded) in tails) {
            offsets[slot] = cursor
            cursor += encoded.size
        }
        var encoded = ByteArray(0)
        for (slot in 0 until slotCount) {
            encoded +=
                when {
                    staticBySlot.containsKey(slot) -> {
                        val word = staticBySlot.getValue(slot)
                        require(word.size == WORD) { "static head slot $slot must be 32 bytes" }
                        word
                    }
                    tailBySlot.containsKey(slot) ->
                        toWord(BigInteger.valueOf(checkNotNull(offsets[slot]).toLong()))
                    else -> ByteArray(WORD)
                }
        }
        for ((_, tail) in tails) encoded += tail
        return toHex(encoded)
    }

    private fun toHex(data: ByteArray): String {
        val chars = "0123456789abcdef".toCharArray()
        val sb = StringBuilder(2 + data.size * 2)
        sb.append("0x")
        for (b in data) {
            val v = b.toInt() and 0xff
            sb.append(chars[v ushr 4])
            sb.append(chars[v and 0x0f])
        }
        return sb.toString()
    }
}
