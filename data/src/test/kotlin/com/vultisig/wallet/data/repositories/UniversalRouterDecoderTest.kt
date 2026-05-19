package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.repositories.UrAbiTestEncoder.PathKey
import com.vultisig.wallet.data.repositories.UrAbiTestEncoder.buildExecuteInputsJson
import com.vultisig.wallet.data.repositories.UrAbiTestEncoder.commandsFor
import com.vultisig.wallet.data.repositories.UrAbiTestEncoder.encodeV2Input
import com.vultisig.wallet.data.repositories.UrAbiTestEncoder.encodeV3Input
import com.vultisig.wallet.data.repositories.UrAbiTestEncoder.encodeV3Path
import com.vultisig.wallet.data.repositories.UrAbiTestEncoder.encodeV4MultiSwapParams
import com.vultisig.wallet.data.repositories.UrAbiTestEncoder.encodeV4SingleSwapParams
import com.vultisig.wallet.data.repositories.UrAbiTestEncoder.encodeV4SwapInput
import com.vultisig.wallet.data.repositories.UrAbiTestEncoder.encodeWrapEthInput
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * Ports the SDK reference test suite for the Universal Router decoder so swap intent stays in
 * lockstep across Android, iOS, and the Extension when each platform's Blockaid simulation falls
 * back to the local fallback path.
 *
 * The tests feed pre-decoded inputs JSON (the shape `FourByteRepository.decodeFunctionArgs` already
 * produces for the outer `execute(bytes,bytes[],uint256)` call) so coverage runs on the JVM unit
 * test target without pulling in Trust Wallet Core's JNI.
 */
internal class UniversalRouterDecoderTest {

    private val json = Json

    // ─── Constants ──────────────────────────────────────────────────────────────

    private val WETH = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"
    private val USDC = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
    private val DAI = "0x6B175474E89094C44Da98b954EedeAC495271d0F"
    private val RECIPIENT = "0x1111111111111111111111111111111111111111"
    private val ZERO_ADDR = "0x0000000000000000000000000000000000000000"

    // V4 command opcodes — duplicated here to keep tests independent of decoder internals.
    private val V3_SWAP_EXACT_IN = 0x00
    private val V3_SWAP_EXACT_OUT = 0x01
    private val V2_SWAP_EXACT_IN = 0x08
    private val V2_SWAP_EXACT_OUT = 0x09
    private val WRAP_ETH = 0x0b
    private val UNWRAP_WETH = 0x0c
    private val V4_SWAP = 0x10

    private val V4_ACTION_SWAP_EXACT_IN_SINGLE = 0x06
    private val V4_ACTION_SWAP_EXACT_IN = 0x07
    private val V4_ACTION_SWAP_EXACT_OUT_SINGLE = 0x08
    private val V4_ACTION_SWAP_EXACT_OUT = 0x09

    private val CONTRACT_BALANCE_SENTINEL: BigInteger = BigInteger.ONE.shiftLeft(255)

    // ─── Null cases ─────────────────────────────────────────────────────────────

    @Test
    fun `returns null for blank inputs json`() {
        assertNull(UniversalRouterDecoder.decode(null, json))
        assertNull(UniversalRouterDecoder.decode("", json))
        assertNull(UniversalRouterDecoder.decode("   ", json))
    }

    @Test
    fun `returns null for malformed json`() {
        assertNull(UniversalRouterDecoder.decode("not json", json))
        assertNull(UniversalRouterDecoder.decode("{ }", json))
        assertNull(UniversalRouterDecoder.decode("[]", json))
        assertNull(UniversalRouterDecoder.decode("""["0x00"]""", json))
    }

    @Test
    fun `returns null when commands and inputs lengths disagree`() {
        val mismatched =
            buildExecuteInputsJson(
                commandsHex = "0x0008",
                inputHexes = listOf("0x"),
                deadline = null,
            )
        assertNull(UniversalRouterDecoder.decode(mismatched, json))
    }

    // ─── V2 ─────────────────────────────────────────────────────────────────────

    @Test
    fun `decodes a V2 exact-in swap (USDC -- DAI)`() {
        val input =
            encodeV2Input(
                recipient = RECIPIENT,
                amount1 = BigInteger.valueOf(1_000_000L),
                amount2 = BigInteger("990000000000000000"),
                path = listOf(USDC, DAI),
                payerIsUser = true,
            )
        val inputsJson = buildExecuteInputsJson(commandsFor(V2_SWAP_EXACT_IN), listOf(input))

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(USDC.lowercase(), intent.fromToken)
        assertEquals(DAI.lowercase(), intent.toToken)
        assertEquals(BigInteger.valueOf(1_000_000L), intent.amountIn)
        assertEquals(BigInteger("990000000000000000"), intent.amountOutMin)
        assertEquals(false, intent.isExactOut)
    }

    @Test
    fun `decodes a V2 exact-out swap (reports amountInMax as amountIn)`() {
        // For exact-out, the encoded order is (amountOut, amountInMax); we report amountInMax as
        // amountIn so the user sees what they could spend.
        val input =
            encodeV2Input(
                recipient = RECIPIENT,
                amount1 = BigInteger("5000000000000000000"),
                amount2 = BigInteger.valueOf(2_000_000L),
                path = listOf(USDC, DAI),
                payerIsUser = true,
            )
        val inputsJson = buildExecuteInputsJson(commandsFor(V2_SWAP_EXACT_OUT), listOf(input))

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(USDC.lowercase(), intent.fromToken)
        assertEquals(DAI.lowercase(), intent.toToken)
        assertEquals(BigInteger.valueOf(2_000_000L), intent.amountIn)
        assertEquals(BigInteger("5000000000000000000"), intent.amountOutMin)
        assertEquals(true, intent.isExactOut)
    }

    // ─── V3 ─────────────────────────────────────────────────────────────────────

    @Test
    fun `decodes a V3 exact-in multi-hop swap (USDC -- WETH -- DAI)`() {
        val path = encodeV3Path(listOf(USDC, WETH, DAI), listOf(500, 3000))
        val input =
            encodeV3Input(
                recipient = RECIPIENT,
                amount1 = BigInteger.valueOf(2_000_000L),
                amount2 = BigInteger("1900000000000000000"),
                path = path,
                payerIsUser = true,
            )
        val inputsJson = buildExecuteInputsJson(commandsFor(V3_SWAP_EXACT_IN), listOf(input))

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(USDC.lowercase(), intent.fromToken)
        assertEquals(DAI.lowercase(), intent.toToken)
        assertEquals(BigInteger.valueOf(2_000_000L), intent.amountIn)
        assertEquals(BigInteger("1900000000000000000"), intent.amountOutMin)
    }

    @Test
    fun `decodes a V3 exact-out swap with reversed path`() {
        // V3 encodes the path tokenOut→tokenIn for exact-out; decoder flips so the user sees their
        // direction.
        val reversedPath = encodeV3Path(listOf(DAI, USDC), listOf(500))
        val input =
            encodeV3Input(
                recipient = RECIPIENT,
                amount1 = BigInteger("1000000000000000000"),
                amount2 = BigInteger.valueOf(2_000_000L),
                path = reversedPath,
                payerIsUser = true,
            )
        val inputsJson = buildExecuteInputsJson(commandsFor(V3_SWAP_EXACT_OUT), listOf(input))

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(USDC.lowercase(), intent.fromToken)
        assertEquals(DAI.lowercase(), intent.toToken)
        assertEquals(BigInteger.valueOf(2_000_000L), intent.amountIn)
        assertEquals(BigInteger("1000000000000000000"), intent.amountOutMin)
        assertEquals(true, intent.isExactOut)
    }

    // ─── WRAP_ETH / UNWRAP_WETH framing ─────────────────────────────────────────

    @Test
    fun `maps WRAP_ETH + V3 swap to native ETH as input`() {
        val wrap = encodeWrapEthInput(RECIPIENT, BigInteger("1000000000000000000"))
        val swap =
            encodeV3Input(
                recipient = RECIPIENT,
                amount1 = BigInteger("1000000000000000000"),
                amount2 = BigInteger.valueOf(2_000_000_000L),
                path = encodeV3Path(listOf(WETH, USDC), listOf(500)),
                payerIsUser = false,
            )
        val inputsJson =
            buildExecuteInputsJson(commandsFor(WRAP_ETH, V3_SWAP_EXACT_IN), listOf(wrap, swap))

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(ZERO_ADDR, intent.fromToken)
        assertEquals(USDC.lowercase(), intent.toToken)
        assertEquals(BigInteger("1000000000000000000"), intent.amountIn)
    }

    @Test
    fun `substitutes WRAP_ETH amount when swap uses CONTRACT_BALANCE sentinel`() {
        val wrap = encodeWrapEthInput(RECIPIENT, BigInteger("5000000000000000000"))
        val swap =
            encodeV3Input(
                recipient = RECIPIENT,
                amount1 = CONTRACT_BALANCE_SENTINEL,
                amount2 = BigInteger.ONE,
                path = encodeV3Path(listOf(WETH, USDC), listOf(500)),
                payerIsUser = false,
            )
        val inputsJson =
            buildExecuteInputsJson(commandsFor(WRAP_ETH, V3_SWAP_EXACT_IN), listOf(wrap, swap))

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(BigInteger("5000000000000000000"), intent.amountIn)
    }

    @Test
    fun `ignores UNWRAP_WETH leftover refund after WRAP_ETH + exact-out swap`() {
        // Native ETH → USDC exact-out: wrap the maximum input, run V3 exact-out (path reversed:
        // USDC → WETH in calldata), then UNWRAP_WETH refunds the unused WETH. Output is USDC, not
        // native.
        val wrap = encodeWrapEthInput(RECIPIENT, BigInteger("2000000000000000000")) // max 2 ETH
        val swap =
            encodeV3Input(
                recipient = RECIPIENT,
                amount1 = BigInteger.valueOf(5_000_000L), // amountOut = 5 USDC
                amount2 = BigInteger("2000000000000000000"), // amountInMax = 2 WETH
                path = encodeV3Path(listOf(USDC, WETH), listOf(500)), // exact-out path is reversed
                payerIsUser = false,
            )
        val unwrap = encodeWrapEthInput(RECIPIENT, BigInteger.ZERO) // refund whatever's left
        val inputsJson =
            buildExecuteInputsJson(
                commandsFor(WRAP_ETH, V3_SWAP_EXACT_OUT, UNWRAP_WETH),
                listOf(wrap, swap, unwrap),
            )

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(ZERO_ADDR, intent.fromToken)
        assertEquals(USDC.lowercase(), intent.toToken)
        // WRAP_ETH amount wins over the first-leg amountInMax.
        assertEquals(BigInteger("2000000000000000000"), intent.amountIn)
        assertEquals(BigInteger.valueOf(5_000_000L), intent.amountOutMin)
        assertEquals(true, intent.isExactOut)
    }

    @Test
    fun `prefers WRAP_ETH amount over first-leg amountInMax for multi-hop exact-out`() {
        // WRAP_ETH is the total user input; each V3 leg only carries its own per-leg
        // amountInMax, so aggregating the first leg would under-report the spend.
        val wrapAmount = BigInteger("5000000000000000000") // 5 ETH total
        val wrap = encodeWrapEthInput(RECIPIENT, wrapAmount)
        val leg1 =
            encodeV3Input(
                recipient = RECIPIENT,
                amount1 = BigInteger.valueOf(1_000_000L),
                amount2 = BigInteger("3000000000000000000"), // leg-1 amountInMax: 3 WETH
                path = encodeV3Path(listOf(USDC, WETH), listOf(500)),
                payerIsUser = false,
            )
        val leg2 =
            encodeV3Input(
                recipient = RECIPIENT,
                amount1 = BigInteger.valueOf(2_000_000L),
                amount2 = BigInteger("2000000000000000000"), // leg-2 amountInMax: 2 WETH
                path = encodeV3Path(listOf(DAI, WETH), listOf(3000)),
                payerIsUser = false,
            )
        val unwrap = encodeWrapEthInput(RECIPIENT, BigInteger.ZERO)
        val inputsJson =
            buildExecuteInputsJson(
                commandsFor(WRAP_ETH, V3_SWAP_EXACT_OUT, V3_SWAP_EXACT_OUT, UNWRAP_WETH),
                listOf(wrap, leg1, leg2, unwrap),
            )

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(ZERO_ADDR, intent.fromToken)
        // Last leg's toToken in V3 exact-out path-reversed encoding is DAI (path: DAI ← WETH), so
        // the final aggregate output is DAI, not the WETH that UNWRAP_WETH refunds.
        assertEquals(DAI.lowercase(), intent.toToken)
        assertEquals(wrapAmount, intent.amountIn)
    }

    @Test
    fun `maps V3 swap + UNWRAP_WETH to native ETH as output`() {
        val swap =
            encodeV3Input(
                recipient = RECIPIENT,
                amount1 = BigInteger.valueOf(2_000_000L),
                amount2 = BigInteger("100000000000000000"),
                path = encodeV3Path(listOf(USDC, WETH), listOf(500)),
                payerIsUser = true,
            )
        val unwrap = encodeWrapEthInput(RECIPIENT, BigInteger("100000000000000000"))
        val inputsJson =
            buildExecuteInputsJson(commandsFor(V3_SWAP_EXACT_IN, UNWRAP_WETH), listOf(swap, unwrap))

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(USDC.lowercase(), intent.fromToken)
        assertEquals(ZERO_ADDR, intent.toToken)
    }

    // ─── Allow-revert masking ───────────────────────────────────────────────────

    @Test
    fun `ignores the allow-revert flag bit on command bytes`() {
        val withRevertFlag = 0x80 or V2_SWAP_EXACT_IN
        val input =
            encodeV2Input(
                recipient = RECIPIENT,
                amount1 = BigInteger.valueOf(100L),
                amount2 = BigInteger.valueOf(90L),
                path = listOf(USDC, DAI),
                payerIsUser = true,
            )
        val inputsJson = buildExecuteInputsJson(commandsFor(withRevertFlag), listOf(input))

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(USDC.lowercase(), intent.fromToken)
        assertEquals(DAI.lowercase(), intent.toToken)
    }

    // ─── V4 ─────────────────────────────────────────────────────────────────────

    @Test
    fun `decodes a V4 single-pool exact-in swap`() {
        val params =
            encodeV4SingleSwapParams(
                currency0 = DAI,
                currency1 = USDC,
                fee = 500,
                tickSpacing = 10,
                hooks = ZERO_ADDR,
                zeroForOne = true, // spending currency0 = DAI
                amount1 = BigInteger("1000000000000000000"),
                amount2 = BigInteger.valueOf(900_000L),
            )
        val actions = byteArrayOf(V4_ACTION_SWAP_EXACT_IN_SINGLE.toByte())
        val v4Input = encodeV4SwapInput(actions, listOf(params))
        val inputsJson = buildExecuteInputsJson(commandsFor(V4_SWAP), listOf(v4Input))

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(DAI.lowercase(), intent.fromToken)
        assertEquals(USDC.lowercase(), intent.toToken)
        assertEquals(BigInteger("1000000000000000000"), intent.amountIn)
        assertEquals(BigInteger.valueOf(900_000L), intent.amountOutMin)
        assertEquals(false, intent.isExactOut)
    }

    @Test
    fun `decodes a V4 single-pool exact-out swap with zeroForOne false`() {
        val params =
            encodeV4SingleSwapParams(
                currency0 = DAI,
                currency1 = USDC,
                fee = 500,
                tickSpacing = 10,
                hooks = ZERO_ADDR,
                zeroForOne = false, // spending currency1 = USDC
                amount1 = BigInteger("1000000000000000000"), // amountOut
                amount2 = BigInteger.valueOf(1_100_000L), // amountInMaximum
            )
        val actions = byteArrayOf(V4_ACTION_SWAP_EXACT_OUT_SINGLE.toByte())
        val v4Input = encodeV4SwapInput(actions, listOf(params))
        val inputsJson = buildExecuteInputsJson(commandsFor(V4_SWAP), listOf(v4Input))

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(USDC.lowercase(), intent.fromToken)
        assertEquals(DAI.lowercase(), intent.toToken)
        assertEquals(BigInteger.valueOf(1_100_000L), intent.amountIn)
        assertEquals(BigInteger("1000000000000000000"), intent.amountOutMin)
        assertEquals(true, intent.isExactOut)
    }

    @Test
    fun `decodes a V4 multi-hop exact-in swap`() {
        val pathKeys =
            listOf(
                PathKey(
                    intermediateCurrency = WETH,
                    fee = 500,
                    tickSpacing = 10,
                    hooks = ZERO_ADDR,
                ),
                PathKey(intermediateCurrency = DAI, fee = 3000, tickSpacing = 60, hooks = ZERO_ADDR),
            )
        val params =
            encodeV4MultiSwapParams(
                currency = USDC,
                path = pathKeys,
                amount1 = BigInteger.valueOf(2_000_000L),
                amount2 = BigInteger("1900000000000000000"),
            )
        val actions = byteArrayOf(V4_ACTION_SWAP_EXACT_IN.toByte())
        val v4Input = encodeV4SwapInput(actions, listOf(params))
        val inputsJson = buildExecuteInputsJson(commandsFor(V4_SWAP), listOf(v4Input))

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(USDC.lowercase(), intent.fromToken)
        assertEquals(DAI.lowercase(), intent.toToken)
        assertEquals(BigInteger.valueOf(2_000_000L), intent.amountIn)
        assertEquals(BigInteger("1900000000000000000"), intent.amountOutMin)
        assertEquals(false, intent.isExactOut)
    }

    @Test
    fun `decodes a V4 multi-hop exact-out swap`() {
        val pathKeys =
            listOf(
                PathKey(
                    intermediateCurrency = USDC,
                    fee = 500,
                    tickSpacing = 10,
                    hooks = ZERO_ADDR,
                ),
                PathKey(
                    intermediateCurrency = WETH,
                    fee = 3000,
                    tickSpacing = 60,
                    hooks = ZERO_ADDR,
                ),
            )
        val params =
            encodeV4MultiSwapParams(
                currency = DAI,
                path = pathKeys,
                amount1 = BigInteger.valueOf(1_000_000L), // amountOut
                amount2 = BigInteger("500000000000000000"), // amountInMaximum
            )
        val actions = byteArrayOf(V4_ACTION_SWAP_EXACT_OUT.toByte())
        val v4Input = encodeV4SwapInput(actions, listOf(params))
        val inputsJson = buildExecuteInputsJson(commandsFor(V4_SWAP), listOf(v4Input))

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(USDC.lowercase(), intent.fromToken)
        assertEquals(DAI.lowercase(), intent.toToken)
        assertEquals(BigInteger("500000000000000000"), intent.amountIn)
        assertEquals(BigInteger.valueOf(1_000_000L), intent.amountOutMin)
        assertEquals(true, intent.isExactOut)
    }

    // ─── Split routes ───────────────────────────────────────────────────────────

    @Test
    fun `sums amountIn and amountOutMin across split V3 exact-in legs`() {
        val legA =
            encodeV3Input(
                recipient = RECIPIENT,
                amount1 = BigInteger.valueOf(600_000L),
                amount2 = BigInteger("590000000000000000"),
                path = encodeV3Path(listOf(USDC, DAI), listOf(500)),
                payerIsUser = true,
            )
        val legB =
            encodeV3Input(
                recipient = RECIPIENT,
                amount1 = BigInteger.valueOf(400_000L),
                amount2 = BigInteger("395000000000000000"),
                path = encodeV3Path(listOf(USDC, DAI), listOf(3000)),
                payerIsUser = true,
            )
        val inputsJson =
            buildExecuteInputsJson(
                commandsFor(V3_SWAP_EXACT_IN, V3_SWAP_EXACT_IN),
                listOf(legA, legB),
            )

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(USDC.lowercase(), intent.fromToken)
        assertEquals(DAI.lowercase(), intent.toToken)
        assertEquals(BigInteger.valueOf(1_000_000L), intent.amountIn)
        assertEquals(BigInteger("985000000000000000"), intent.amountOutMin)
    }

    @Test
    fun `sums split V3 exact-out legs under WRAP_ETH + UNWRAP_WETH refund`() {
        val wrapAmount = BigInteger("2150000000000000")
        val wrap = encodeWrapEthInput(RECIPIENT, wrapAmount)
        fun mkLeg(amountOut: BigInteger, amountInMax: BigInteger) =
            encodeV3Input(
                recipient = RECIPIENT,
                amount1 = amountOut,
                amount2 = amountInMax,
                path = encodeV3Path(listOf(USDC, WETH), listOf(500)),
                payerIsUser = false,
            )
        val unwrap = encodeWrapEthInput(RECIPIENT, BigInteger.ZERO)
        val inputsJson =
            buildExecuteInputsJson(
                commandsFor(
                    WRAP_ETH,
                    V3_SWAP_EXACT_OUT,
                    V3_SWAP_EXACT_OUT,
                    V3_SWAP_EXACT_OUT,
                    UNWRAP_WETH,
                ),
                listOf(
                    wrap,
                    mkLeg(BigInteger.valueOf(500_000L), BigInteger("800000000000000")),
                    mkLeg(BigInteger.valueOf(400_000L), BigInteger("700000000000000")),
                    mkLeg(BigInteger.valueOf(300_000L), BigInteger("600000000000000")),
                    unwrap,
                ),
            )

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(ZERO_ADDR, intent.fromToken)
        assertEquals(USDC.lowercase(), intent.toToken)
        // WRAP_ETH wins for amountIn (full authorized input).
        assertEquals(wrapAmount, intent.amountIn)
        // amountOutMin is the sum of the three legs' amountOut values.
        assertEquals(BigInteger.valueOf(1_200_000L), intent.amountOutMin)
        assertEquals(true, intent.isExactOut)
    }

    // ─── No recognised swap ─────────────────────────────────────────────────────

    @Test
    fun `returns null when execute carries only unsupported commands`() {
        // 0x02 = PERMIT2_TRANSFER_FROM — not a swap, so the decoder reports no intent.
        val anyPayload = "0x" + "00".repeat(96)
        val inputsJson =
            buildExecuteInputsJson(commandsHex = "0x02", inputHexes = listOf(anyPayload))
        assertNull(UniversalRouterDecoder.decode(inputsJson, json))
    }

    @Test
    fun `returns null when commands is empty`() {
        val inputsJson = buildExecuteInputsJson(commandsHex = "0x", inputHexes = emptyList())
        assertNull(UniversalRouterDecoder.decode(inputsJson, json))
    }

    // ─── Deadline-less overload ────────────────────────────────────────────────

    @Test
    fun `decodes deadline-less execute(bytes, bytes array) overload`() {
        val input =
            encodeV2Input(
                recipient = RECIPIENT,
                amount1 = BigInteger.valueOf(123L),
                amount2 = BigInteger.valueOf(100L),
                path = listOf(USDC, DAI),
                payerIsUser = true,
            )
        val inputsJson =
            buildExecuteInputsJson(commandsFor(V2_SWAP_EXACT_IN), listOf(input), deadline = null)

        val intent = assertNotNull(UniversalRouterDecoder.decode(inputsJson, json))
        assertEquals(USDC.lowercase(), intent.fromToken)
        assertEquals(DAI.lowercase(), intent.toToken)
    }

    // ─── isUniversalRouterExecuteSignature ─────────────────────────────────────

    @Test
    fun `signature matcher accepts both UR overloads`() {
        assertTrue(
            UniversalRouterDecoder.isUniversalRouterExecuteSignature(
                "execute(bytes,bytes[],uint256)"
            )
        )
        assertTrue(
            UniversalRouterDecoder.isUniversalRouterExecuteSignature("execute(bytes,bytes[])")
        )
    }

    @Test
    fun `signature matcher tolerates whitespace`() {
        assertTrue(
            UniversalRouterDecoder.isUniversalRouterExecuteSignature(
                "execute( bytes , bytes[] , uint256 )"
            )
        )
    }

    @Test
    fun `signature matcher rejects other functions`() {
        assertEquals(false, UniversalRouterDecoder.isUniversalRouterExecuteSignature(null))
        assertEquals(false, UniversalRouterDecoder.isUniversalRouterExecuteSignature(""))
        assertEquals(
            false,
            UniversalRouterDecoder.isUniversalRouterExecuteSignature("approve(address,uint256)"),
        )
        // `execute` overload that doesn't match UR's signature should be rejected.
        assertEquals(
            false,
            UniversalRouterDecoder.isUniversalRouterExecuteSignature("execute(uint256,bytes)"),
        )
    }
}
