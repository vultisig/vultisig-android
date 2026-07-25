package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.Coin
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

/**
 * Notional value the reference-price probe aims for.
 *
 * A probe sized to "1 unit" prices cheaply-denominated assets (RUNE, DOGE) below THORChain's
 * minimum swappable amount and comes back as a dust error instead of a price. Quoting a realistic
 * trade size also keeps the reported price closer to what the user will actually get.
 */
private val MARKET_PROBE_FIAT_VALUE = BigDecimal(100)

/**
 * Smallest native amount that still survives conversion to THORChain's 1e8 scale.
 *
 * A single native unit is not enough past 8 decimals: 1 wei of an 18-decimal asset converts to 0,
 * and quoting `amount=0` returns no usable price. Assets at or under 8 decimals scale up, so one
 * unit is always safe there.
 */
private fun getMinimumProbeAmount(decimals: Int): BigInteger =
    if (decimals > THORCHAIN_FIXED_POINT_DECIMALS) {
        BigInteger.TEN.pow(decimals - THORCHAIN_FIXED_POINT_DECIMALS)
    } else {
        BigInteger.ONE
    }

/**
 * Source amount, in native smallest units, to quote for a reference price.
 *
 * @param price fiat price of one natural unit of the source asset.
 */
fun getMarketProbeAmount(price: BigDecimal, decimals: Int): BigInteger {
    val units =
        if (price.signum() > 0) {
            MARKET_PROBE_FIAT_VALUE.divide(price, 18, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ONE
        }
    val amount =
        units
            .multiply(BigDecimal.TEN.pow(decimals))
            .setScale(0, RoundingMode.HALF_UP)
            .toBigInteger()
            .max(BigInteger.ONE)

    return amount.max(getMinimumProbeAmount(decimals))
}

/**
 * Derive the market price — target natural units per source natural unit — from a quote's expected
 * output.
 *
 * THORChain reports `expected_amount_out` in 1e8 fixed point regardless of the target chain's own
 * decimals, so only the source side needs its decimals applied. Kept pure so the scaling is
 * unit-testable without a round trip.
 *
 * @param expectedAmountOut `expected_amount_out` from a THORChain quote, in 1e8 fixed point.
 * @param sourceAmount the amount that was quoted, in the source coin's native smallest units.
 */
fun getLimitSwapMarketPrice(
    expectedAmountOut: String,
    sourceAmount: BigInteger,
    sourceDecimals: Int,
): BigDecimal {
    val expected =
        runCatching { fromThorchainFixedPoint(BigInteger(expectedAmountOut.trim())) }
            .getOrElse {
                throw IllegalArgumentException(
                    "Invalid expected_amount_out from THORChain quote: $expectedAmountOut"
                )
            }
    val source = BigDecimal(sourceAmount).divide(BigDecimal.TEN.pow(sourceDecimals))
    require(source.signum() != 0) { "Cannot derive a market price from a zero source amount" }
    return expected.divide(source, 18, RoundingMode.HALF_UP)
}

/**
 * The current market price for a limit-swap pair, used to seed the form's price field and presets.
 */
interface LimitSwapMarketPriceRepository {
    /**
     * @param destinationAddress optional payout address; some quotes price differently per
     *   destination. Never influences a signed value — this price only seeds the UI.
     */
    suspend fun getMarketPrice(
        fromCoin: Coin,
        toCoin: Coin,
        sourcePrice: BigDecimal,
        destinationAddress: String? = null,
    ): BigDecimal
}

internal class LimitSwapMarketPriceRepositoryImpl
@Inject
constructor(private val thorChainApi: ThorChainApi) : LimitSwapMarketPriceRepository {

    override suspend fun getMarketPrice(
        fromCoin: Coin,
        toCoin: Coin,
        sourcePrice: BigDecimal,
        destinationAddress: String?,
    ): BigDecimal {
        val sourceAmount = getMarketProbeAmount(sourcePrice, fromCoin.decimal)
        val expectedAmountOut =
            thorChainApi.getLimitSwapReferenceExpectedOut(
                fromAsset = fromCoin.thorchainMemoAsset(),
                toAsset = toCoin.thorchainMemoAsset(),
                amount = toThorchainFixedPoint(sourceAmount, fromCoin.decimal).toString(),
                destination = destinationAddress,
            )
        return getLimitSwapMarketPrice(
            expectedAmountOut = expectedAmountOut,
            sourceAmount = sourceAmount,
            sourceDecimals = fromCoin.decimal,
        )
    }
}
