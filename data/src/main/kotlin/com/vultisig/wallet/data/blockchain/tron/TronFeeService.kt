package com.vultisig.wallet.data.blockchain.tron

import androidx.annotation.VisibleForTesting
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.TronApiImpl.Companion.TRANSFER_FUNCTION_SELECTOR
import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.api.models.TronChainParametersJson
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Swap
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.TronFees
import com.vultisig.wallet.data.chains.helpers.TronFunctions.tronAddressToHex
import com.vultisig.wallet.data.chains.helpers.TronHelper.Companion.TRON_DEFAULT_ESTIMATION_FEE
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * TRON uses a resource-based fee model instead of a fixed gas fee like Ethereum. Transactions
 * consume resources, and if the sender does not have enough resources (from staking or free quota),
 * the equivalent in TRX is burned as fees.
 *
 * Fee Components:
 * 1. Bandwidth Fee
 *     - Every transaction consumes bandwidth (measured in bytes).
 *     - Native TRX transfers typically cost ~300 bytes.
 *     - TRC20 transfers (smart contract interactions) cost slightly more (~345 bytes).
 *     - If the sender's staked bandwidth covers the whole transaction, or failing that their free
 *       bandwidth does, no TRX is burned. The two pools are never combined.
 *     - Otherwise, the cost is: bytes * bandwidthPrice (≈1000 SUN per byte).
 *
 *   How Users Earn Bandwidth:
 *     - Free daily quota: Each account receives a small amount of bandwidth points daily (600
 *       bandwidth, 2 transfers).
 *     - Staking TRX: Users can freeze (stake) TRX to earn additional bandwidth points.
 *         - The more TRX frozen, the more bandwidth points allocated per day.
 *     - Bandwidth is consumed with each transaction until depleted; any shortfall is paid in TRX.
 * 2. Energy Fee (for smart contracts)
 *     - TRC20 and other contract calls consume "energy".
 *     - Energy can be obtained by staking TRX, or else TRX is burned.
 *     - Cost formula: (energyRequired - availableEnergy) * energyPrice (≈280 SUN per unit).
 *     - Example: a TRC20 transfer consumes ~65,000 energy → ~18.2 TRX if no energy available.
 *
 *   How Users Earn Energy:
 *     - Staking TRX: Freezing TRX can also grant energy points.
 *         - Energy is specifically required for smart contract execution.
 *     - Unlike bandwidth, there is no daily free quota for energy; only staking provides it.
 * 3. Memo Fee
 *     - If the signed transaction carries a memo (`raw_data.data`), a flat fee applies.
 *     - Default memo fee: 1 TRX (1,000,000 SUN).
 * 4. Account Activation Fee
 *     - Sending TRX to a new account requires paying an activation fee.
 *     - Covers creation and system cost, typically ~1.1 TRX total.
 *     - When this applies, the bandwidth fee is waived (since activation includes it).
 *     - A TRC20 transfer never pays it: the recipient's balance lives in the contract's storage and
 *       the chain creates no account for them.
 *
 * Notes:
 * - Resource availability is checked first (bandwidth/energy from free quota or staking).
 * - Any shortfall is covered by burning TRX as fees.
 * - This service fetches chain parameters dynamically via TronApi, but provides defaults as
 *   fallback.
 */
class TronFeeService @Inject constructor(private val tronApi: TronApi) : FeeService {

    private val chainParametersMutex = Mutex()
    @Volatile private var chainParameters: TronChainParametersJson? = null

    override suspend fun calculateFees(transaction: BlockchainTransaction): TronFees =
        coroutineScope {
            // THORChain swap outbound tx shape is known only at signing time. The default estimator
            // already returns a swap-safe TronFees, so short-circuit here instead of attempting an
            // RPC simulation that relies on Transfer-only fields (contract, amount, memo).
            if (transaction is Swap) {
                return@coroutineScope calculateDefaultFees(transaction)
            }
            require(transaction is Transfer) {
                "Transaction type not supported ${transaction::class.simpleName}"
            }

            val coin = transaction.coin
            val fromAddress = transaction.coin.address
            val toAddress = transaction.to

            val srcAccountDeferred = async { tronApi.getAccountResource(fromAddress) }

            if (coin.isNativeToken) {
                // Only a plain transfer can activate its destination, so only it needs to know
                // whether the account exists.
                val dstAccountDeferred = async { tronApi.getAccount(toAddress) }
                calculateNativeTrxFee(
                    srcAccount = srcAccountDeferred.await(),
                    dstAccount = dstAccountDeferred.await(),
                    hasMemo = transaction.paysMemoFee(),
                )
            } else {
                calculateTrc20Fee(
                    srcAccount = srcAccountDeferred.await(),
                    transaction = transaction,
                )
            }
        }

    private suspend fun calculateNativeTrxFee(
        srcAccount: TronAccountResourceJson?,
        dstAccount: TronAccountJson?,
        hasMemo: Boolean,
    ): TronFees {
        var totalFee = BigInteger.ZERO

        // 1) Bandwidth fee
        val bandwidthFee = calculateBandwidthFee(srcAccount = srcAccount, isContract = false)

        totalFee = totalFee.add(bandwidthFee.amount)

        // 2) Account activation fee (if destination is new)
        // New accounts don't pay bandwidth fee (it's included in activation)
        if (dstAccount.isNewAccount()) {
            val activationFee = calculateActivationFee()
            totalFee = totalFee.add(activationFee)
            totalFee = totalFee.subtract(bandwidthFee.amount)
        }

        // 3) Memo fee
        if (hasMemo) {
            val memoFee = getCacheTronChainParameters().memoFeeEstimate.toBigInteger()
            totalFee = totalFee + memoFee
        }

        return bandwidthFee.copy(feeLimit = NATIVE_FEE_LIMIT, amount = totalFee)
    }

    private fun TronAccountJson?.isNewAccount(): Boolean = this == null || address.isEmpty()

    /**
     * Tron burns the memo fee only for a non-empty `raw_data.data`, so the staking routing signal
     * parked in the memo field — which never reaches the signed contract — must not be priced as
     * one.
     */
    private fun BlockchainTransaction.paysMemoFee(): Boolean {
        val memo = (this as? Transfer)?.memo ?: return false
        return memo.isNotEmpty() && !isTronStakingTransfer(coin, to, memo)
    }

    // Both transfers COIN and TRC-20 are quite deterministic in terms of bandwidth
    // Bandwidth represents the transaction size in bytes, 250-300 for COIN and around 350 for
    // TRC-20
    // To consider implementing a tx serializer for swaps. This can be easily achieve by :
    // headers & others(fixed) + signature(fixed) + rawCallDataSize (return by simulation)
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun calculateBandwidthFee(
        srcAccount: TronAccountResourceJson?,
        isContract: Boolean,
    ): TronFees {
        val bytesRequired =
            if (isContract) {
                BYTES_PER_CONTRACT_TX
            } else {
                BYTES_PER_COIN_TX
            }

        val bandwidthPrice = getCacheTronChainParameters().bandwidthFeePrice

        // A contract call draws on the same free and staked bandwidth as a plain transfer — only
        // the size differs — so it is charged on the same terms. Bandwidth applies all or nothing:
        // TRX is burned for the whole transaction, or for none of it.
        val isCovered = srcAccount?.coversBandwidth(bytesRequired) == true

        return TronFees(
            bandwidthDiscounted =
                if (isCovered) {
                    BigInteger.ZERO
                } else {
                    bytesRequired.toBigInteger()
                },
            bandwidthRequired = bytesRequired.toBigInteger(),
            amount =
                if (isCovered) {
                    BigInteger.ZERO
                } else {
                    BigInteger.valueOf(bytesRequired * bandwidthPrice)
                },
        )
    }

    private suspend fun calculateActivationFee(): BigInteger {
        val createAccountFee = getCacheTronChainParameters().createAccountFeeEstimate
        val systemFee = getCacheTronChainParameters().createNewAccountFeeEstimateContract

        return BigInteger.valueOf(createAccountFee + systemFee)
    }

    /**
     * The chain spends staked bandwidth first and free bandwidth second, and whichever pool it
     * lands on has to cover the whole transaction by itself — java-tron's `BandwidthProcessor`
     * tries `useAccountNet` then `useFreeNet` and never combines the two. Summing them would quote
     * a sender with 200 of each as covered for a 345-byte call the chain burns TRX for.
     * https://developers.tron.network/docs/resource-model#account-bandwidth-balance-query
     */
    private fun TronAccountResourceJson.coversBandwidth(bytes: Long): Boolean =
        netLimit - netUsed >= bytes || freeNetLimit - freeNetUsed >= bytes

    private suspend fun calculateTrc20Fee(
        srcAccount: TronAccountResourceJson?,
        transaction: Transfer,
    ): TronFees {
        // 1. Bandwidth fee
        val bandwidthFee = calculateBandwidthFee(srcAccount = srcAccount, isContract = true)

        // 2. Energy fee
        val energyFee = calculateEnergyFee(srcAccount = srcAccount, transaction = transaction)

        // 3. Memo fee — TronHelper writes the memo into `raw_data.data` of a contract call exactly
        //    as it does for a plain transfer, and the chain bills it the same way.
        val memoFee =
            if (transaction.paysMemoFee()) {
                getCacheTronChainParameters().memoFeeEstimate.toBigInteger()
            } else {
                BigInteger.ZERO
            }

        // No activation fee: a TRC20 transfer writes the recipient's balance into the contract's
        // storage and never creates a TRON account for them (java-tron's contractCreateNewAccount
        // is false for TriggerSmartContract), so the chain charges nothing for a fresh recipient
        // beyond the extra energy the simulation already prices for their first storage write.
        val totalFee = bandwidthFee.amount + energyFee.amount + memoFee

        // Bandwidth and memo are burnt outside contract execution, so they stay out of the ceiling
        // TRON enforces against energy.
        return bandwidthFee.copy(
            maxEnergyRequired = energyFee.maxEnergyRequired,
            energyDiscounted = energyFee.energyDiscounted,
            energyRequired = energyFee.energyRequired,
            feeLimit = energyFee.feeLimit,
            amount = totalFee,
        )
    }

    // https://developers.tron.network/docs/resource-model#dynamic-energy-model
    // https://developers.tron.network/docs/set-feelimit
    // https://developers.tron.network/docs/resource-model#principle
    private suspend fun calculateEnergyFee(
        srcAccount: TronAccountResourceJson?,
        transaction: Transfer,
    ): TronFees {
        val simulationResult =
            tronApi.getTriggerConstantContractFee(
                ownerAddressBase58 = transaction.coin.address,
                contractAddressBase58 = transaction.coin.contractAddress,
                recipientAddressHex = tronAddressToHex(transaction.to),
                functionSelector = TRANSFER_FUNCTION_SELECTOR,
                // The amount being sent, not the whole balance: a TRC20 transfer's energy depends
                // on it, so simulating anything else prices a transaction nobody is signing.
                amount = transaction.amount,
            )

        // `energy_used` is already the total energy this call burns; `energy_penalty` is the
        // Dynamic Energy share inside that total, not a term to add on top of it. Rebuilding the
        // base from the two keeps that relationship explicit and rejects a response whose reported
        // share exceeds the total it belongs to.
        // https://developers.tron.network/docs/set-feelimit#estimating-energy-before-broadcasting
        val totalEnergy = simulationResult.energyUsed
        val penalty = simulationResult.energyPenalty
        check(totalEnergy > 0L && penalty in 0L..totalEnergy) {
            "Tron simulation returned an unusable energy estimate: " +
                "used=$totalEnergy penalty=$penalty"
        }
        val baseEnergy = totalEnergy - penalty

        val chainParameters = getCacheTronChainParameters()
        val maxFactor =
            chainParameters.maxEnergyFactor
                .toBigDecimal()
                .divide(ENERGY_FACTOR, 10, RoundingMode.DOWN) + BigDecimal.ONE
        val energyPrice = chainParameters.energyFee.toBigInteger()

        val energyUnitsRequired = totalEnergy.toBigInteger()
        val maxEnergyUnitsRequired = baseEnergy.toBigDecimal().multiply(maxFactor).toBigInteger()

        // Staked energy discounts what the sender is expected to burn, never the signed ceiling.
        val availableEnergy =
            srcAccount?.calculateAvailableEnergy()?.toBigInteger() ?: BigInteger.ZERO
        val energyToPay = (energyUnitsRequired - availableEnergy).coerceAtLeast(BigInteger.ZERO)

        return TronFees(
            maxEnergyRequired = maxEnergyUnitsRequired,
            energyRequired = energyUnitsRequired,
            energyDiscounted = energyToPay,
            // A fee_limit above the chain's own ceiling is rejected outright, and one derived from
            // a zeroed energy price would guarantee OUT_OF_ENERGY — clamp both ends rather than
            // sign either.
            feeLimit =
                contractFeeLimit(energyUnitsRequired, energyPrice)
                    .coerceIn(BigInteger.ZERO, chainParameters.maxFeeLimit.toBigInteger()),
            amount = energyToPay * energyPrice,
        )
    }

    private fun TronAccountResourceJson.calculateAvailableEnergy(): Long {
        return maxOf(energyLimit - energyUsed, 0L)
    }

    private suspend fun getCacheTronChainParameters(): TronChainParametersJson {
        chainParameters?.let {
            return it
        }
        return chainParametersMutex.withLock {
            chainParameters?.let {
                return it
            }
            tronApi.getChainParameters().also { chainParameters = it }
        }
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): TronFees {
        val toAddress = transaction.to
        val isNativeCoin = transaction.coin.isNativeToken
        val hasMemo = transaction.paysMemoFee()
        val isTokenTransfer = !transaction.coin.isNativeToken

        // Only a plain transfer activates its destination; a token call never does.
        val isNewAccount =
            isNativeCoin &&
                runCatching { tronApi.getAccount(toAddress).isNewAccount() }.getOrDefault(true)

        val baseFee =
            when {
                isNativeCoin -> (BYTES_PER_CONTRACT_TX * 1000).toBigInteger()
                else -> DEFAULT_TOKEN_TRANSFER_FEE
            }

        val accountFee =
            if (isNewAccount) {
                DEFAULT_CREATE_ACCOUNT_FEE + DEFAULT_CREATE_ACCOUNT_SYSTEM_FEE
            } else {
                BigInteger.ZERO
            }

        val memoFee =
            if (hasMemo) {
                DEFAULT_MEMO_TRANSFER_FEE
            } else {
                BigInteger.ZERO
            }

        val totalFee = baseFee + accountFee + memoFee

        val maxEnergyUnitsRequired =
            if (isTokenTransfer) {
                DEFAULT_MAX_ENERGY_USED
            } else {
                BigInteger.ZERO
            }
        return TronFees(
            maxEnergyRequired = maxEnergyUnitsRequired,
            // No simulation to size a ceiling from, so a token call falls back to the same flat
            // amount it reports as the fee. Being conservative here is the safe direction: the
            // ceiling is only ever charged for what execution actually consumes.
            feeLimit = if (isNativeCoin) NATIVE_FEE_LIMIT else totalFee,
            amount = totalFee,
        )
    }

    companion object {
        // Bandwidth requirements
        private const val BYTES_PER_COIN_TX = 300L // Native TRX transfer
        private const val BYTES_PER_CONTRACT_TX = 345L // TRC20 token transfer

        private val DEFAULT_TOKEN_TRANSFER_FEE = "30000000".toBigInteger()
        private val DEFAULT_MEMO_TRANSFER_FEE = "1000000".toBigInteger()

        // Default inactive destination values
        private val DEFAULT_CREATE_ACCOUNT_FEE = "1000000".toBigInteger() // 1 TRX
        private val DEFAULT_CREATE_ACCOUNT_SYSTEM_FEE = "100000".toBigInteger() // 0.1 TRX
        private val DEFAULT_MAX_ENERGY_USED = 50000000.toBigInteger()

        private val ENERGY_FACTOR = "10000".toBigDecimal()

        /**
         * A plain TRX transfer never writes `fee_limit` at all, so this flat ceiling only governs
         * the native-TRX contract calls that share this fee model — staking (freeze/unfreeze/vote)
         * and dApp `TriggerSmartContract` payloads, neither of which is simulated here.
         */
        private val NATIVE_FEE_LIMIT = TRON_DEFAULT_ESTIMATION_FEE.toBigInteger()

        // 30% headroom on top of the simulated energy, covering a contract's per-call dynamic
        // energy_factor surge between simulation and broadcast. Matches iOS's
        // TronService.contractFeeLimit (ENERGY_SAFETY_NUMERATOR/DENOMINATOR = 13/10).
        // https://developers.tron.network/docs/resource-model#dynamic-energy-model
        private val ENERGY_SAFETY_NUMERATOR = BigInteger.valueOf(13)
        private val ENERGY_SAFETY_DENOMINATOR = BigInteger.TEN

        /**
         * Translates simulated energy into the `fee_limit` cap, in SUN. Multiplies before dividing
         * so the 30% margin survives truncation, and stays in [BigInteger] so an unexpectedly large
         * estimate or energy price cannot overflow mid-calculation.
         */
        internal fun contractFeeLimit(
            totalEnergyUsed: BigInteger,
            energyPrice: BigInteger,
        ): BigInteger =
            totalEnergyUsed
                .multiply(ENERGY_SAFETY_NUMERATOR)
                .divide(ENERGY_SAFETY_DENOMINATOR)
                .multiply(energyPrice)
    }
}
