package com.vultisig.wallet.data.blockchain.tron

import java.math.BigInteger

/**
 * Represents the energy fee calculation for TRON smart contract transactions.
 * 
 * TRON uses an energy-based system for smart contract execution where energy units
 * are consumed based on the computational complexity of the operation. Users can
 * obtain energy by staking TRX or pay for it directly by burning TRX.
 * 
 * @property maxEnergyRequired The maximum energy that could be consumed by this transaction.
 *                             This value can be used as the transaction's fee limit to prevent
 *                             excessive fees in case of contract execution issues.
 *                             Typically set higher than actual expected usage for safety.
 * 
 * @property energyUsed The actual energy units required for the transaction execution.
 *                      This is the real computational cost of running the smart contract
 *                      operation. Note: During maintenance cycles, the actual fee might
 *                      be lower due to network adjustments.
 * 
 * @property amount The calculated fee amount in SUN (1 TRX = 1,000,000 SUN).
 *                  Calculated as: energyUsed * energyPrice
 *                  This represents the TRX that will be burned if the user doesn't
 *                  have sufficient staked energy.
 * 
 * ## Energy Pricing
 * - Default energy price: ~280 SUN per energy unit
 * - Energy price can fluctuate based on network conditions
 * - During maintenance periods, fees may be adjusted by the network
 * 
 * ## Common Energy Costs
 * - TRC20 Transfer: ~65,000 energy units (~18.2 TRX)
 * - Complex DeFi operations: 100,000-500,000 energy units
 * - Simple contract calls: 10,000-50,000 energy units
 * 
 * ## Maintenance Period Edge Case
 * During TRON's maintenance cycle (every 6 hours for ~5 minutes):
 * - Network parameters can be updated
 * - Energy prices might be adjusted
 * - Actual fees could differ from pre-calculated estimates
 * 
 * @see <a href="https://developers.tron.network/docs/glossary#maintenance-period">TRON Maintenance Period</a>
 * @see <a href="https://developers.tron.network/docs/resource-model#energy">TRON Energy Model</a>
 */
internal data class TronEnergyFee(
    val maxEnergyRequired: BigInteger, // can be used as transaction limit,
    val energyUsed: BigInteger, // actual energy required for transaction, although if there is a maintenance cycle, fee could be low under edge case scenario: https://developers.tron.network/docs/glossary#maintenance-period
    val amount: BigInteger, // actual amount in SUN (energy * price)
)