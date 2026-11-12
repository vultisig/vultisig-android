package com.vultisig.wallet.ui.models.send

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.supportsLegacyGas
import com.vultisig.wallet.data.utils.median
import java.math.BigInteger

private val GWEI: BigInteger = BigInteger.TEN.pow(9)
private val L2_MIN_PRIORITY_FEE_WEI: BigInteger = BigInteger.valueOf(20)
private val POLYGON_MIN_PRIORITY_FEE_WEI: BigInteger = GWEI * BigInteger.valueOf(30)
private val UTXO_LOW_NUMERATOR = BigInteger.valueOf(3)
private val UTXO_LOW_DENOMINATOR = BigInteger.valueOf(4)
private val UTXO_FAST_NUMERATOR = BigInteger.valueOf(5)
private val UTXO_FAST_DENOMINATOR = BigInteger.valueOf(2)

/**
 * Whether the advanced gas settings offer Low / Normal / Fast for [chain]: EIP-1559 EVM chains (a
 * legacy-gas chain has no priority fee, and zkSync prices its own fee), and the UTXO chains whose
 * byte fee follows the network rate (Zcash's is fixed).
 */
internal val Chain.hasPriorityTiers: Boolean
    get() =
        when (standard) {
            TokenStandard.EVM -> !supportsLegacyGas && this != Chain.ZkSync
            TokenStandard.UTXO -> this != Chain.Zcash && this != Chain.Cardano
            else -> false
        }

/**
 * The iOS Low / Normal / Fast priority fee per gas, in wei, from an ascending [sortedFeeHistory]:
 * its lowest, median and highest reward, each raised to the chain's floor. Null when the history
 * holds nothing, so no tier is offered.
 */
internal fun evmPriorityFeeTiers(
    chain: Chain,
    sortedFeeHistory: List<BigInteger>,
): Map<PriorityFee, BigInteger>? {
    val normal = sortedFeeHistory.median() ?: return null
    return mapOf(
        PriorityFee.LOW to chain.floorPriorityFee(sortedFeeHistory.first()),
        PriorityFee.NORMAL to chain.floorPriorityFee(normal),
        PriorityFee.FAST to chain.floorPriorityFee(sortedFeeHistory.last()),
    )
}

/**
 * The Low / Normal / Fast byte fee from the [normalByteFee] the form uses today: ×0.75, ×1 and
 * ×2.5, truncated toward zero as iOS does.
 */
internal fun utxoByteFeeTiers(normalByteFee: BigInteger): Map<PriorityFee, BigInteger> =
    mapOf(
        PriorityFee.LOW to normalByteFee * UTXO_LOW_NUMERATOR / UTXO_LOW_DENOMINATOR,
        PriorityFee.NORMAL to normalByteFee,
        PriorityFee.FAST to normalByteFee * UTXO_FAST_NUMERATOR / UTXO_FAST_DENOMINATOR,
    )

/**
 * The iOS per-chain floor on a priority fee (`EthereumFeeService.calculateMaxPriorityFeePerGas`).
 */
private fun Chain.floorPriorityFee(fee: BigInteger): BigInteger =
    when (this) {
        Chain.Avalanche -> fee
        Chain.Arbitrum,
        Chain.Mantle,
        Chain.Robinhood -> BigInteger.ZERO
        Chain.Base,
        Chain.Blast,
        Chain.Optimism -> fee.max(L2_MIN_PRIORITY_FEE_WEI)
        Chain.Polygon -> fee.max(POLYGON_MIN_PRIORITY_FEE_WEI)
        Chain.Ethereum,
        Chain.BscChain,
        Chain.CronosChain,
        Chain.ZkSync,
        Chain.Sei,
        Chain.Hyperliquid -> fee.max(GWEI)
        Chain.ThorChain,
        Chain.MayaChain,
        Chain.Bitcoin,
        Chain.BitcoinCash,
        Chain.Litecoin,
        Chain.Dogecoin,
        Chain.Dash,
        Chain.Zcash,
        Chain.Cardano,
        Chain.GaiaChain,
        Chain.Dydx,
        Chain.Osmosis,
        Chain.Terra,
        Chain.TerraClassic,
        Chain.Noble,
        Chain.Akash,
        Chain.Solana,
        Chain.Polkadot,
        Chain.Bittensor,
        Chain.Sui,
        Chain.Ton,
        Chain.Ripple,
        Chain.Tron,
        Chain.Near,
        Chain.Qbtc -> error("$this has no EVM priority fee")
    }
