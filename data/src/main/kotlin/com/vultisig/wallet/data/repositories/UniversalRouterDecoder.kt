package com.vultisig.wallet.data.repositories

import java.math.BigInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

/**
 * Aggregate swap intent extracted from a Uniswap Universal Router `execute(...)` call.
 *
 * Addresses are stored as canonical `0x`-prefixed lowercase 40-char hex; native ETH is represented
 * by [UniversalRouterDecoder.NATIVE_TOKEN_ADDRESS] (matching V4's Currency.unwrap convention) so
 * callers should translate that sentinel to the chain's fee coin when displaying.
 *
 * The [amountIn] / [amountOutMin] semantics differ by direction:
 * - Exact-in ([isExactOut] = false): [amountIn] is the user-supplied input, [amountOutMin] is the
 *   floor on what they accepted.
 * - Exact-out ([isExactOut] = true): [amountIn] is the upper bound the user authorized
 *   (amountInMax), [amountOutMin] is the exact output the user is buying.
 */
data class UniversalRouterSwapIntent(
    val fromToken: String,
    val toToken: String,
    val amountIn: BigInteger,
    val amountOutMin: BigInteger,
    val isExactOut: Boolean,
)

/**
 * Decoder for Uniswap Universal Router `execute(bytes commands, bytes[] inputs, uint256 deadline)`
 * (and the deadline-less overload) calldata.
 *
 * Phase 1's 4byte path resolves the outer signature and JSON-decodes the three arguments, but the
 * useful information lives inside the `bytes[]` and is opaque to that path — the verify screen
 * shows "Execute" with no swap context. This decoder walks the `commands` byte string, dispatches
 * each command opcode to a per-shape input decoder (V2 / V3 / V4 in exact-in and exact-out
 * variants, plus the WRAP_ETH / UNWRAP_WETH wrappers that frame native-ETH legs), and aggregates
 * them into a single [UniversalRouterSwapIntent] for display.
 *
 * Returns `null` for inputs that are not a recognisable Universal Router execute call. Unknown
 * opcodes inside an otherwise valid execute call are skipped rather than rejected — the router
 * frequently bundles Permit2, sweep, and transfer commands around the actual swap.
 *
 * Aligned with the SDK reference (`@vultisig/core-chain/.../universalRouter/decode`) so swap intent
 * stays consistent across Android, iOS, and the Extension when each platform's Blockaid simulation
 * falls back to the local fallback path.
 */
object UniversalRouterDecoder {

    /**
     * Zero address — the V4 Currency convention for native ETH. Callers translate this to the
     * chain's fee coin (ETH on Ethereum, MATIC on Polygon, …) when rendering.
     */
    const val NATIVE_TOKEN_ADDRESS = "0x0000000000000000000000000000000000000000"

    /**
     * Sentinel value sometimes passed as `amountIn` to mean "spend the router's full balance of the
     * input token". Treated specially so a `WRAP_ETH` upstream wins over the sentinel.
     */
    private val CONTRACT_BALANCE_SENTINEL: BigInteger = BigInteger.ONE.shiftLeft(255)

    /**
     * The high bit (`0x80`) on a command byte is the "allow revert" flag. Only the lower 6 bits
     * identify the opcode, so we mask before dispatching.
     */
    private const val COMMAND_TYPE_MASK = 0x3f

    private const val V3_SWAP_EXACT_IN = 0x00
    private const val V3_SWAP_EXACT_OUT = 0x01
    private const val V2_SWAP_EXACT_IN = 0x08
    private const val V2_SWAP_EXACT_OUT = 0x09
    private const val WRAP_ETH = 0x0b
    private const val UNWRAP_WETH = 0x0c
    private const val V4_SWAP = 0x10

    private const val V4_ACTION_SWAP_EXACT_IN_SINGLE = 0x06
    private const val V4_ACTION_SWAP_EXACT_IN = 0x07
    private const val V4_ACTION_SWAP_EXACT_OUT_SINGLE = 0x08
    private const val V4_ACTION_SWAP_EXACT_OUT = 0x09

    private const val WORD = 32

    private val EXECUTE_SIGNATURES =
        setOf("execute(bytes,bytes[],uint256)", "execute(bytes,bytes[])")

    /**
     * `true` when [signature] (as returned by `FourByteRepository.decodeFunction`) is one of the
     * Universal Router execute overloads. Whitespace inside the signature is tolerated so a 4byte
     * response with cosmetic spacing still matches.
     */
    fun isUniversalRouterExecuteSignature(signature: String?): Boolean {
        if (signature.isNullOrBlank()) return false
        return signature.replace("\\s+".toRegex(), "") in EXECUTE_SIGNATURES
    }

    /**
     * Decode the pretty inputs JSON produced by [FourByteRepositoryImpl.decodeFunctionArgs] for a
     * Universal Router execute call into an aggregate [UniversalRouterSwapIntent].
     *
     * The expected JSON shape is a top-level array whose first element is the `commands` byte
     * string (lowercase `0x…` hex) and second element is the `inputs` array (each element another
     * `0x…` hex string). A third `deadline` element may follow; it's ignored. Returns `null` when
     * the JSON doesn't match that shape, when commands/inputs lengths disagree, or when no
     * recognisable swap opcode is present.
     */
    fun decode(inputsJson: String?, json: Json): UniversalRouterSwapIntent? {
        if (inputsJson.isNullOrBlank()) return null
        val root = runCatching { json.parseToJsonElement(inputsJson).jsonArray }.getOrNull()
        if (root == null || root.size < 2) return null
        val commandsHex = root[0].asHexStringOrNull() ?: return null
        val inputsArray = root[1] as? JsonArray ?: return null
        val commands = decodeHex(commandsHex) ?: return null
        if (commands.size != inputsArray.size) return null
        val inputs = inputsArray.map { it.asHexStringOrNull()?.let(::decodeHex) ?: return null }
        return aggregate(commands, inputs)
    }

    private fun aggregate(
        commands: ByteArray,
        inputs: List<ByteArray>,
    ): UniversalRouterSwapIntent? {
        val swaps = mutableListOf<SwapEntry>()
        var wrapEthAmount: BigInteger? = null
        var sawUnwrapWeth = false
        var sawKnownCommand = false

        for (i in commands.indices) {
            val opcode = commands[i].toInt() and COMMAND_TYPE_MASK
            val input = inputs[i]
            when (opcode) {
                V2_SWAP_EXACT_IN -> {
                    sawKnownCommand = true
                    decodeV2Swap(input, isExactOut = false)?.let(swaps::add)
                }
                V2_SWAP_EXACT_OUT -> {
                    sawKnownCommand = true
                    decodeV2Swap(input, isExactOut = true)?.let(swaps::add)
                }
                V3_SWAP_EXACT_IN -> {
                    sawKnownCommand = true
                    decodeV3Swap(input, isExactOut = false)?.let(swaps::add)
                }
                V3_SWAP_EXACT_OUT -> {
                    sawKnownCommand = true
                    decodeV3Swap(input, isExactOut = true)?.let(swaps::add)
                }
                WRAP_ETH -> {
                    sawKnownCommand = true
                    // Only the wrap that precedes the first swap matters for the user's input
                    // side; later wraps are intermediate routing.
                    if (swaps.isEmpty()) wrapEthAmount = decodeWrapEth(input)
                }
                UNWRAP_WETH -> {
                    sawKnownCommand = true
                    sawUnwrapWeth = true
                }
                V4_SWAP -> {
                    sawKnownCommand = true
                    decodeV4Swap(input)?.let(swaps::add)
                }
            // Anything else is intentionally skipped — Permit2, sweep, transfer, etc.
            }
        }

        if (!sawKnownCommand || swaps.isEmpty()) return null

        val first = swaps.first()
        val last = swaps.last()

        // Uniswap splits a single pair across multiple legs for better execution. Each leg is its
        // own swap command but they share fromToken/toToken; reporting one leg's amounts would
        // dramatically under-count the trade.
        val isSplitRoute =
            swaps.size > 1 &&
                swaps.all { it.fromToken == first.fromToken && it.toToken == first.toToken }

        var fromToken = first.fromToken
        var toToken = if (isSplitRoute) first.toToken else last.toToken
        var amountIn =
            if (isSplitRoute) swaps.fold(BigInteger.ZERO) { acc, s -> acc.add(s.amountIn) }
            else first.amountIn
        val amountOutMin =
            if (isSplitRoute) swaps.fold(BigInteger.ZERO) { acc, s -> acc.add(s.amountOutMin) }
            else last.amountOutMin

        wrapEthAmount?.let { wrap ->
            fromToken = NATIVE_TOKEN_ADDRESS
            // WRAP_ETH's amount is the user's total native input for the whole sequence. The first
            // swap leg's amountIn/amountInMax only covers that leg (wrong for multi-hop or
            // exact-out), so prefer the wrap amount unless it's the "use router balance" sentinel.
            if (wrap != CONTRACT_BALANCE_SENTINEL || amountIn == CONTRACT_BALANCE_SENTINEL) {
                amountIn = wrap
            }
        }

        if (sawUnwrapWeth) {
            // UNWRAP_WETH has two reasons that mean opposite things for the user-facing output:
            //   1. Output conversion — the final swap lands in WETH and UNWRAP_WETH converts it to
            //      native (ERC-20 → NATIVE).
            //   2. Leftover refund — an exact-out flow wrapped too much ETH upfront and
            //      UNWRAP_WETH refunds the unused remainder. The swap output is still the last
            //      leg's ERC-20 toToken.
            // Distinguish them: leftover refund only happens AFTER a WRAP_ETH, and only when the
            // last swap's toToken is something other than the wrapped working asset (= first
            // swap's fromToken). When the last swap's toToken matches the wrapped native, it's an
            // output conversion back to native.
            val isLeftoverRefund = wrapEthAmount != null && last.toToken != first.fromToken
            if (!isLeftoverRefund) toToken = NATIVE_TOKEN_ADDRESS
        }

        return UniversalRouterSwapIntent(
            fromToken = fromToken,
            toToken = toToken,
            amountIn = amountIn,
            amountOutMin = amountOutMin,
            isExactOut = last.isExactOut,
        )
    }

    private data class SwapEntry(
        val fromToken: String,
        val toToken: String,
        val amountIn: BigInteger,
        val amountOutMin: BigInteger,
        val isExactOut: Boolean,
    )

    // V2 input shape: (address recipient, uint256 amountA, uint256 amountB, address[] path, bool
    // payerIsUser). For exact-in: amountA = amountIn, amountB = amountOutMin. For exact-out:
    // amountA = amountOut, amountB = amountInMax.
    private fun decodeV2Swap(input: ByteArray, isExactOut: Boolean): SwapEntry? {
        if (input.size < 5 * WORD) return null
        val amountA = readUint(input, WORD) ?: return null
        val amountB = readUint(input, 2 * WORD) ?: return null
        val pathOffset = readPositiveInt(input, 3 * WORD) ?: return null
        val path = readAddressArray(input, pathOffset) ?: return null
        if (path.size < 2) return null
        val fromToken = path.first()
        val toToken = path.last()
        return if (isExactOut) {
            SwapEntry(
                fromToken,
                toToken,
                amountIn = amountB,
                amountOutMin = amountA,
                isExactOut = true,
            )
        } else {
            SwapEntry(
                fromToken,
                toToken,
                amountIn = amountA,
                amountOutMin = amountB,
                isExactOut = false,
            )
        }
    }

    // V3 input shape: (address recipient, uint256 amountA, uint256 amountB, bytes path, bool
    // payerIsUser). V3 packs the path as tokenA(20) || fee(3) || tokenB(20) || fee(3) || ... For
    // exact-in the path is in swap order; for exact-out it's reversed (tokenOut first), so we flip
    // the endpoints to keep the user's perspective consistent.
    private fun decodeV3Swap(input: ByteArray, isExactOut: Boolean): SwapEntry? {
        if (input.size < 5 * WORD) return null
        val amountA = readUint(input, WORD) ?: return null
        val amountB = readUint(input, 2 * WORD) ?: return null
        val pathOffset = readPositiveInt(input, 3 * WORD) ?: return null
        val pathBytes = readBytes(input, pathOffset) ?: return null
        if (pathBytes.size < 20) return null
        val firstToken = readAddress(pathBytes, 0)
        val lastToken = readAddress(pathBytes, pathBytes.size - 20)
        return if (isExactOut) {
            SwapEntry(
                fromToken = lastToken,
                toToken = firstToken,
                amountIn = amountB,
                amountOutMin = amountA,
                isExactOut = true,
            )
        } else {
            SwapEntry(
                fromToken = firstToken,
                toToken = lastToken,
                amountIn = amountA,
                amountOutMin = amountB,
                isExactOut = false,
            )
        }
    }

    // WRAP_ETH input shape: (address recipient, uint256 amount).
    private fun decodeWrapEth(input: ByteArray): BigInteger? {
        if (input.size < 2 * WORD) return null
        return readUint(input, WORD)
    }

    // V4_SWAP outer shape: (bytes actions, bytes[] params). First recognised swap action wins.
    // V4 swaps emit one action per command in practice; Take-All / Settle-All actions surround
    // it but don't change the aggregate intent.
    private fun decodeV4Swap(input: ByteArray): SwapEntry? {
        if (input.size < 2 * WORD) return null
        val actionsOffset = readPositiveInt(input, 0) ?: return null
        val paramsOffset = readPositiveInt(input, WORD) ?: return null
        val actions = readBytes(input, actionsOffset) ?: return null
        val params = readBytesArray(input, paramsOffset) ?: return null
        if (actions.size != params.size) return null
        for (i in actions.indices) {
            val action = actions[i].toInt() and 0xff
            val payload = params[i]
            val entry =
                when (action) {
                    V4_ACTION_SWAP_EXACT_IN_SINGLE ->
                        decodeV4SingleSwap(payload, isExactOut = false)
                    V4_ACTION_SWAP_EXACT_OUT_SINGLE ->
                        decodeV4SingleSwap(payload, isExactOut = true)
                    V4_ACTION_SWAP_EXACT_IN -> decodeV4MultiSwap(payload, isExactOut = false)
                    V4_ACTION_SWAP_EXACT_OUT -> decodeV4MultiSwap(payload, isExactOut = true)
                    else -> null
                }
            if (entry != null) return entry
        }
        return null
    }

    /**
     * V4 single-pool swap params, both directions:
     * - Encoded as a single dynamic-tuple arg, so the payload starts with a 32-byte offset (= 32)
     *   pointing at the tuple body.
     * - Tuple body: `(PoolKey poolKey, bool zeroForOne, uint128 amount1, uint128 amount2, bytes
     *   hookData)` where `PoolKey = (address currency0, address currency1, uint24 fee, int24
     *   tickSpacing, address hooks)` is all static (5 words inline). For exact-in: `amount1 =
     *   amountIn`, `amount2 = amountOutMinimum`. For exact-out: `amount1 = amountOut`, `amount2 =
     *   amountInMaximum`.
     */
    private fun decodeV4SingleSwap(payload: ByteArray, isExactOut: Boolean): SwapEntry? {
        if (payload.size < WORD) return null
        val tupleStart = readPositiveInt(payload, 0) ?: return null
        // poolKey (5 words) + zeroForOne (1) + amount1 (1) + amount2 (1) — we don't read past
        // amount2 so we don't need room for the hookData offset.
        if (payload.size < tupleStart + 8 * WORD) return null
        val currency0 = readAddressWord(payload, tupleStart)
        val currency1 = readAddressWord(payload, tupleStart + WORD)
        val zeroForOne = readBoolWord(payload, tupleStart + 5 * WORD)
        val amount1 = readUint(payload, tupleStart + 6 * WORD) ?: return null
        val amount2 = readUint(payload, tupleStart + 7 * WORD) ?: return null
        val fromToken = if (zeroForOne) currency0 else currency1
        val toToken = if (zeroForOne) currency1 else currency0
        return if (isExactOut) {
            SwapEntry(
                fromToken = fromToken,
                toToken = toToken,
                amountIn = amount2,
                amountOutMin = amount1,
                isExactOut = true,
            )
        } else {
            SwapEntry(
                fromToken = fromToken,
                toToken = toToken,
                amountIn = amount1,
                amountOutMin = amount2,
                isExactOut = false,
            )
        }
    }

    /**
     * V4 multi-hop swap params, both directions:
     * - Encoded as a single dynamic-tuple arg: 32-byte offset (= 32) + tuple body.
     * - Tuple body: `(address currencyIn|Out, PathKey[] path, uint128 amount1, uint128 amount2)`
     *   where `PathKey = (address intermediateCurrency, uint24 fee, int24 tickSpacing, address
     *   hooks, bytes hookData)` is dynamic (has bytes). For exact-in: fromToken = currencyIn,
     *   toToken = last(path).intermediateCurrency, amount1 = amountIn, amount2 = amountOutMinimum.
     *   For exact-out: fromToken = first(path).intermediateCurrency, toToken = currencyOut, amount1
     *   = amountOut, amount2 = amountInMaximum.
     */
    private fun decodeV4MultiSwap(payload: ByteArray, isExactOut: Boolean): SwapEntry? {
        if (payload.size < WORD) return null
        val tupleStart = readPositiveInt(payload, 0) ?: return null
        if (payload.size < tupleStart + 4 * WORD) return null
        val currency = readAddressWord(payload, tupleStart)
        val pathOffsetRelative = readPositiveInt(payload, tupleStart + WORD) ?: return null
        val pathKeys = readPathKeys(payload, tupleStart + pathOffsetRelative) ?: return null
        if (pathKeys.isEmpty()) return null
        val amount1 = readUint(payload, tupleStart + 2 * WORD) ?: return null
        val amount2 = readUint(payload, tupleStart + 3 * WORD) ?: return null
        return if (isExactOut) {
            SwapEntry(
                fromToken = pathKeys.first(),
                toToken = currency,
                amountIn = amount2,
                amountOutMin = amount1,
                isExactOut = true,
            )
        } else {
            SwapEntry(
                fromToken = currency,
                toToken = pathKeys.last(),
                amountIn = amount1,
                amountOutMin = amount2,
                isExactOut = false,
            )
        }
    }

    /**
     * PathKey[] at [start]. Layout: 32-byte length, then N 32-byte offset pointers (each relative
     * to the area after the length), then each PathKey's dynamic tuple body. We only care about the
     * leading `intermediateCurrency` address so the rest of the PathKey is skipped.
     */
    private fun readPathKeys(data: ByteArray, start: Int): List<String>? {
        val length = readPositiveInt(data, start) ?: return null
        val elementsStart = start + WORD
        val out = mutableListOf<String>()
        for (i in 0 until length) {
            val pointer = readPositiveInt(data, elementsStart + i * WORD) ?: return null
            val keyStart = elementsStart + pointer
            if (data.size < keyStart + WORD) return null
            out += readAddressWord(data, keyStart)
        }
        return out
    }

    // ─── Low-level ABI byte reading (offsets are always in bytes) ────────────────

    private fun decodeHex(hex: String): ByteArray? {
        val cleaned = hex.removePrefix("0x").removePrefix("0X")
        if (cleaned.length % 2 != 0) return null
        if (cleaned.isEmpty()) return ByteArray(0)
        val out = ByteArray(cleaned.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(cleaned[i * 2], 16)
            val lo = Character.digit(cleaned[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    /** Read a 32-byte unsigned big-endian integer at [offset]. Returns null if out of bounds. */
    private fun readUint(data: ByteArray, offset: Int): BigInteger? {
        if (offset < 0 || data.size < offset + WORD) return null
        return BigInteger(1, data.copyOfRange(offset, offset + WORD))
    }

    /**
     * Read a 32-byte uint at [offset] and convert to a non-negative `Int` for use as a byte offset
     * into the payload. Returns null if the value would overflow `Int` or is negative.
     */
    private fun readPositiveInt(data: ByteArray, offset: Int): Int? {
        val value = readUint(data, offset) ?: return null
        if (value.signum() < 0 || value.bitLength() > 31) return null
        return value.toInt()
    }

    /** Read an `address` packed in a 32-byte word at [offset] (last 20 bytes). */
    private fun readAddressWord(data: ByteArray, offset: Int): String {
        if (offset < 0 || data.size < offset + WORD) return NATIVE_TOKEN_ADDRESS
        return readAddress(data, offset + 12)
    }

    /** Read a 20-byte address starting at the byte [offset], formatted as 0x-prefixed lowercase. */
    private fun readAddress(data: ByteArray, offset: Int): String {
        if (offset < 0 || data.size < offset + 20) return NATIVE_TOKEN_ADDRESS
        val buf = StringBuilder(2 + 40)
        buf.append("0x")
        for (i in 0 until 20) {
            val b = data[offset + i].toInt() and 0xff
            buf.append(HEX_CHARS[b ushr 4])
            buf.append(HEX_CHARS[b and 0x0f])
        }
        return buf.toString()
    }

    private fun readBoolWord(data: ByteArray, offset: Int): Boolean {
        if (offset < 0 || data.size < offset + WORD) return false
        // ABI encodes booleans as a full 32-byte word with the value in the last byte.
        return data[offset + WORD - 1].toInt() != 0
    }

    /** Read dynamic `bytes` at [offset]: 32-byte length followed by the payload bytes. */
    private fun readBytes(data: ByteArray, offset: Int): ByteArray? {
        val length = readPositiveInt(data, offset) ?: return null
        val start = offset + WORD
        if (data.size < start + length) return null
        return data.copyOfRange(start, start + length)
    }

    /** Read `address[]` at [offset]: 32-byte length followed by N word-padded addresses. */
    private fun readAddressArray(data: ByteArray, offset: Int): List<String>? {
        val length = readPositiveInt(data, offset) ?: return null
        val start = offset + WORD
        if (data.size < start + length * WORD) return null
        return (0 until length).map { i -> readAddressWord(data, start + i * WORD) }
    }

    /**
     * Read `bytes[]` at [offset]: 32-byte length, N 32-byte offset pointers (relative to the area
     * after the length), then each element's `bytes` encoding (length + payload).
     */
    private fun readBytesArray(data: ByteArray, offset: Int): List<ByteArray>? {
        val length = readPositiveInt(data, offset) ?: return null
        val elementsStart = offset + WORD
        val out = mutableListOf<ByteArray>()
        for (i in 0 until length) {
            val pointer = readPositiveInt(data, elementsStart + i * WORD) ?: return null
            out += readBytes(data, elementsStart + pointer) ?: return null
        }
        return out
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    private fun JsonElement.asHexStringOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("0x", ignoreCase = true) }
}
