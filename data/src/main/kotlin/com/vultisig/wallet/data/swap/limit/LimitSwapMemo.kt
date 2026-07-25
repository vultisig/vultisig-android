package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenStandard
import java.math.BigDecimal
import java.math.BigInteger

/** Allowed limit-order lifetimes, in hours. Anything else is rejected. */
val limitSwapExpiryHours: List<Int> = listOf(12, 24, 72)

/**
 * Builds THORChain `=<` limit-order memos.
 *
 * The memo *is* the order: THORChain reads the trade target (`LIM`) straight out of the memo, so
 * the LIM math, the 1e8 scaling of the source amount, and the byte budget all have to be exact.
 * This mirrors the Vultisig SDK's `buildLimitSwapMemo`; the only intentional deviation is the
 * affiliate thorname, which stays [THORChainSwaps.AFFILIATE_FEE_ADDRESS] (`va`) to match Android's
 * market swaps rather than the SDK's `v0`.
 *
 * Format: `=<:TARGET_ASSET:DEST_ADDR:LIM/INTERVAL/0[:AFFILIATE:BPS]`.
 */
object LimitSwapMemo {

    /**
     * THORChain memo prefix selecting the advanced swap queue — what makes a deposit a resting
     * order.
     */
    const val PREFIX = "=<:"

    /** A UTXO source carries the memo in an OP_RETURN (80 bytes); other chains allow far more. */
    const val UTXO_BYTE_LIMIT = 80
    const val OTHER_BYTE_LIMIT = 250

    private val PRICE_SCALE: BigInteger = BigInteger.TEN.pow(8)

    private val expiryHoursToIntervalBlocks: Map<Int, Int> =
        mapOf(12 to 7_200, 24 to 14_400, 72 to 43_200)

    private val PRICE_PATTERN = Regex("^(\\d+)(?:\\.(\\d{1,8})?)?$")

    /**
     * Blocks the resting order survives for. THORChain evaluates the order once per ~6s block, so
     * an hours-based lifetime maps directly onto a block count.
     */
    fun intervalBlocks(expiryHours: Int): Int =
        expiryHoursToIntervalBlocks[expiryHours]
            ?: throw IllegalArgumentException(
                "expiry_hours must be one of ${limitSwapExpiryHours.joinToString(", ")}, " +
                    "got $expiryHours"
            )

    /**
     * The order's guaranteed minimum output (LIM), in the target's 1e8 fixed point.
     *
     * Computed with integer math and floored. A LIM of 0 must never reach a signer: THORChain reads
     * a zero trade target as an *unprotected market order*, silently dropping the user's price
     * protection — so a source/price combination that floors to 0 fails closed here.
     */
    fun getLimitAmount(sourceAmount: BigInteger, targetPrice: String): BigInteger {
        require(sourceAmount > BigInteger.ZERO) { "source_amount must be greater than 0" }
        val targetPriceScaled = parsePositiveDecimalToScaled(targetPrice)
        val limit = sourceAmount * targetPriceScaled / PRICE_SCALE
        require(limit > BigInteger.ZERO) {
            "source_amount and target_price combination is too small to express as a limit swap: " +
                "the computed minimum-received amount (LIM) floors to 0, which THORChain treats as " +
                "an unprotected market order. Increase source_amount or target_price."
        }
        return limit
    }

    /**
     * Build the memo.
     *
     * @param sourceAsset THORChain memo notation of the sold asset (`ETH.ETH`), used only to derive
     *   the source chain kind for the byte budget — it does not appear in the memo.
     * @param sourceAmount sold amount already in THORChain 1e8 fixed point.
     * @param targetAsset THORChain memo notation of the bought asset (`BTC.BTC`).
     * @param destAddr payout address on the target chain.
     * @param targetPrice target units per source unit, as a decimal string (≤ 8 fractional digits).
     * @param expiryHours one of [limitSwapExpiryHours].
     */
    fun build(
        sourceAsset: String,
        sourceAmount: BigInteger,
        targetAsset: String,
        destAddr: String,
        targetPrice: String,
        expiryHours: Int,
    ): String {
        val sourceChain = getSupportedThorchainAssetChain(sourceAsset, "source_asset")
        val targetChain = getSupportedThorchainAssetChain(targetAsset, "target_asset")
        assertMemoSegmentSafe(destAddr, "dest_addr")
        assertValidLimitSwapDestinationAddress(targetChain, destAddr)

        val limit = getLimitAmount(sourceAmount, targetPrice)
        val interval = intervalBlocks(expiryHours)
        val tradeTarget = "$limit/$interval/0"
        val base = "$PREFIX$targetAsset:$destAddr:$tradeTarget"
        val withAffiliate =
            "$base:${THORChainSwaps.AFFILIATE_FEE_ADDRESS}:${THORChainSwaps.AFFILIATE_FEE_RATE_BP}"

        if (sourceChain.standard == TokenStandard.UTXO) {
            // OP_RETURN is scarce: keep the affiliate segment only if the whole memo still fits,
            // otherwise drop it rather than truncate into a memo that would route differently.
            if (byteLength(withAffiliate) <= UTXO_BYTE_LIMIT) return withAffiliate
            assertMemoByteLength(base, UTXO_BYTE_LIMIT, "utxo")
            return base
        }

        assertMemoByteLength(withAffiliate, OTHER_BYTE_LIMIT, "other")
        return withAffiliate
    }

    fun assertMemoByteLength(memo: String, limit: Int, kindLabel: String) {
        val byteLength = byteLength(memo)
        require(byteLength <= limit) {
            "THORChain limit swap memo is $byteLength bytes, exceeding $kindLabel limit $limit"
        }
    }

    private fun byteLength(value: String): Int = value.toByteArray(Charsets.UTF_8).size

    private fun getSupportedThorchainAssetChain(asset: String, fieldName: String): Chain {
        val prefix = asset.substringBefore('.')
        require(prefix.isNotEmpty() && prefix != asset && asset.substringAfter('.').isNotEmpty()) {
            "$fieldName must be in CHAIN.ASSET notation, got \"$asset\""
        }
        return thorchainAssetPrefixToChain[prefix.uppercase()]
            ?: throw IllegalArgumentException(
                "$fieldName has unsupported THORChain asset prefix: $prefix"
            )
    }

    private fun parsePositiveDecimalToScaled(value: String): BigInteger {
        val normalized = value.trim()
        val match =
            PRICE_PATTERN.matchEntire(normalized)
                ?: throw IllegalArgumentException(
                    "target_price must be a positive decimal with at most 8 fractional digits"
                )
        val whole = match.groupValues[1]
        val fractional = match.groupValues[2]
        val scaled = BigInteger(whole) * PRICE_SCALE + BigInteger(fractional.padEnd(8, '0'))
        require(scaled > BigInteger.ZERO) { "target_price must be greater than 0" }
        return scaled
    }
}

/**
 * Build the `=<` limit-order memo for a pair of vault coins.
 *
 * Bridges the app's [Coin] shape to [LimitSwapMemo.build]: derives THORChain asset notation for
 * both sides and rescales the source amount from native units to the 1e8 fixed point the memo's LIM
 * math assumes. Throws rather than returning a partial memo — every failure here (unroutable chain,
 * unencodable asset, a LIM flooring to zero, an over-long memo) would otherwise become a signed
 * order that routes or prices differently than the user asked for.
 *
 * @param amount source amount in the coin's native smallest units.
 * @param targetPrice target asset units per source unit, as the user entered it.
 * @param destinationAddress payout address — the user's own address on the target chain.
 */
fun buildLimitSwapMemoForCoins(
    fromCoin: Coin,
    toCoin: Coin,
    amount: BigInteger,
    targetPrice: BigDecimal,
    expiryHours: Int,
    destinationAddress: String,
): String =
    LimitSwapMemo.build(
        sourceAsset = fromCoin.thorchainMemoAsset(),
        sourceAmount = toThorchainFixedPoint(amount, fromCoin.decimal),
        targetAsset = toCoin.thorchainMemoAsset(),
        destAddr = destinationAddress,
        targetPrice = targetPrice.stripTrailingZeros().toPlainString(),
        expiryHours = expiryHours,
    )
