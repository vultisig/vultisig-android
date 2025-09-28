package com.vultisig.wallet.data.blockchain.tron

import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.TronApiImpl.Companion.TRANSFER_FUNCTION_SELECTOR
import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.api.models.TronChainParametersJson
import com.vultisig.wallet.data.blockchain.BasicFee
import com.vultisig.wallet.data.blockchain.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.Transfer
import com.vultisig.wallet.data.blockchain.TronFees
import com.vultisig.wallet.data.utils.Numeric
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import wallet.core.jni.Base58
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

/**
 *
 * TRON uses a resource-based fee model instead of a fixed gas fee like Ethereum.
 * Transactions consume resources, and if the sender does not have enough resources
 * (from staking or free quota), the equivalent in TRX is burned as fees.
 *
 * Fee Components:
 *
 * 1. Bandwidth Fee
 *    - Every transaction consumes bandwidth (measured in bytes).
 *    - Native TRX transfers typically cost ~300 bytes.
 *    - TRC20 transfers (smart contract interactions) cost slightly more (~345 bytes).
 *    - If the sender has enough free or staked bandwidth, no TRX is burned.
 *    - Otherwise, the cost is: bytes * bandwidthPrice (≈1000 SUN per byte).
 *
 *    How Users Earn Bandwidth:
 *    - Free daily quota: Each account receives a small amount of bandwidth points daily (600 bandwidth, 2 transfers).
 *    - Staking TRX: Users can freeze (stake) TRX to earn additional bandwidth points.
 *      - The more TRX frozen, the more bandwidth points allocated per day.
 *    - Bandwidth is consumed with each transaction until depleted; any shortfall is paid in TRX.
 *
 * 2. Energy Fee (for smart contracts)
 *    - TRC20 and other contract calls consume "energy".
 *    - Energy can be obtained by staking TRX, or else TRX is burned.
 *    - Cost formula: (energyRequired - availableEnergy) * energyPrice (≈280 SUN per unit).
 *    - Example: a TRC20 transfer consumes ~65,000 energy → ~18.2 TRX if no energy available.
 *
 *    How Users Earn Energy:
 *    - Staking TRX: Freezing TRX can also grant energy points.
 *      - Energy is specifically required for smart contract execution.
 *    - Unlike bandwidth, there is no daily free quota for energy; only staking provides it.
 *
 * 3. Memo Fee
 *    - If a transaction includes a memo, a flat fee applies.
 *    - Default memo fee: 1 TRX (1,000,000 SUN).
 *
 * 4. Account Activation Fee
 *    - Sending TRX to a new account requires paying an activation fee.
 *    - Covers creation and system cost, typically ~1.1 TRX total.
 *    - When this applies, the bandwidth fee is waived (since activation includes it).
 *
 * Notes:
 * - Resource availability is checked first (bandwidth/energy from free quota or staking).
 * - Any shortfall is covered by burning TRX as fees.
 * - This service fetches chain parameters dynamically via TronApi, but provides
 *   defaults as fallback.
 */
class TronFeeService @Inject constructor(
    private val tronApi: TronApi,
) : FeeService {

    private var chainParameters: TronChainParametersJson? = null

    override suspend fun calculateFees(transaction: BlockchainTransaction): TronFees = coroutineScope {
        require(transaction is Transfer) {
            "Transaction type not supported $"
        }

        val coin = transaction.coin
        val fromAddress = transaction.coin.address
        val toAddress = transaction.to
        val memo = transaction.memo

        val srcAccountDeferred = async { tronApi.getAccountResource(fromAddress) }
        val dstAccountDeferred = async { tronApi.getAccount(toAddress) }

        val srcAccount = srcAccountDeferred.await()
        val dstAccount = dstAccountDeferred.await()

        if (coin.isNativeToken) {
            calculateNativeTrxFee(
                srcAccount = srcAccount,
                dstAccount = dstAccount,
                hasMemo = !memo.isNullOrEmpty()
            )
        } else {
            calculateTrc20Fee(
                srcAccount = srcAccount,
                dstAccount = dstAccount,
                transaction = transaction,
            )
        }
    }

    private suspend fun calculateNativeTrxFee(
        srcAccount: TronAccountResourceJson?,
        dstAccount: TronAccountJson?,
        hasMemo: Boolean
    ): TronFees {
        var totalFee = BigInteger.ZERO

        // 1) Bandwidth fee
        val bandwidthFee = calculateBandwidthFee(
            srcAccount = srcAccount,
            isContract = false,
        )

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

        return bandwidthFee.copy(
            amount = totalFee,
        )
    }

    private fun TronAccountJson?.isNewAccount(): Boolean = this == null || address.isEmpty()

    // Both transfers COIN and TRC-20 are quite deterministic in terms of bandwidth
    // Bandwidth represents the transaction size in bytes, 250-300 for COIN and around 350 for TRC-20
    // To consider implementing a tx serializer for swaps. This can be easily achieve by :
    // headers & others(fixed) + signature(fixed) + rawCallDataSize (return by simulation)
    private suspend fun calculateBandwidthFee(
        srcAccount: TronAccountResourceJson?,
        isContract: Boolean,
    ): TronFees {
        val bytesRequired = if (isContract) {
            BYTES_PER_CONTRACT_TX
        } else {
            BYTES_PER_COIN_TX
        }

        val bandwidthPrice = getCacheTronChainParameters().bandwidthFeePrice

        // For contracts, always pay bandwidth fee
        if (isContract) {
            return TronFees(
                bandwidthDiscounted = BYTES_PER_CONTRACT_TX.toBigInteger(),
                bandwidthRequired = BYTES_PER_CONTRACT_TX.toBigInteger(),
                amount = BigInteger.valueOf(bytesRequired * bandwidthPrice),
            )
        }

        // For native transfers, check available bandwidth
        val availableBandwidth = srcAccount?.calculateAvailableBandwidth() ?: 0L

        // Bandwidth apply all or nothing
        val trxAmount = if (availableBandwidth >= bytesRequired) {
            BigInteger.ZERO
        } else {
            BigInteger.valueOf(bytesRequired * bandwidthPrice)
        }

        return TronFees(
            bandwidthDiscounted = if (trxAmount == BigInteger.ZERO) {
                BigInteger.ZERO
            } else {
                BYTES_PER_COIN_TX.toBigInteger()
            },
            bandwidthRequired = BYTES_PER_COIN_TX.toBigInteger(),
            amount = trxAmount,
        )
    }

    private suspend fun calculateActivationFee(): BigInteger {
        val createAccountFee = getCacheTronChainParameters().createAccountFeeEstimate
        val systemFee = getCacheTronChainParameters().createNewAccountFeeEstimateContract

        return BigInteger.valueOf(createAccountFee + systemFee)
    }

    // https://developers.tron.network/docs/resource-model#account-bandwidth-balance-query
    private fun TronAccountResourceJson.calculateAvailableBandwidth(): Long {
        val freeBandwidth = freeNetLimit - freeNetUsed
        val stakingBandwidth = netLimit - netUsed

        return freeBandwidth + stakingBandwidth
    }

    private suspend fun calculateTrc20Fee(
        srcAccount: TronAccountResourceJson?,
        dstAccount: TronAccountJson?,
        transaction: Transfer,
    ): TronFees {
        var totalFee = BigInteger.ZERO

        // 1. Bandwidth fee (always paid for TRC20)
        val bandwidthFee = calculateBandwidthFee(
            srcAccount = srcAccount,
            isContract = true,
        )

        totalFee = totalFee.add(bandwidthFee.amount)

        // 2. Energy fee
        val energyFee = calculateEnergyFee(
            srcAccount = srcAccount,
            transaction = transaction,
        )

        totalFee = totalFee.add(energyFee.amount)

        // 3. Account activation fee (if destination is new)
        if (dstAccount.isNewAccount()) {
            val activationFee = calculateActivationFee()
            totalFee = totalFee.add(activationFee)
        }

        return bandwidthFee.copy(
            maxEnergyRequired = energyFee.maxEnergyRequired,
            energyDiscounted = energyFee.energyDiscounted,
            energyRequired = energyFee.energyRequired,
            amount = totalFee,
        )
    }

    // https://developers.tron.network/docs/resource-model#dynamic-energy-model
    // https://developers.tron.network/docs/set-feelimit
    // https://developers.tron.network/docs/resource-model#principle
    private suspend fun calculateEnergyFee(
        srcAccount: TronAccountResourceJson?,
        transaction: Transfer,
    ): TronFees = supervisorScope {
        val fromAddress = transaction.coin.address
        val toAddress = Numeric.toHexString(Base58.decode(transaction.to))
        val contract = transaction.coin.contractAddress
        val amount = transaction.amount

        val contractMetadata = async { tronApi.getContractMetadata(contract) }

        val simulationResult = tronApi.getTriggerConstantContractFee(
            ownerAddressBase58 = fromAddress,
            contractAddressBase58 = contract,
            recipientAddressHex = toAddress,
            functionSelector = TRANSFER_FUNCTION_SELECTOR,
            amount = amount,
        )

        if (!simulationResult.isSuccessfulSimulation()) {
            Timber.e("Tron Simulation Failed: ${simulationResult.result ?: ""}")
            throw RuntimeException("Tron Simulated failed")
        }

        val contractEnergyFactor =
            contractMetadata.await().contractState.energyFactor.toBigDecimal()
        val contractMaxEnergyFactor = getCacheTronChainParameters().maxEnergyFactor.toBigDecimal()

        val energyRequired = if (contractEnergyFactor == BigDecimal.ZERO) {
            simulationResult.energyUsed
        } else {
            simulationResult.energyUsed - simulationResult.energyPenalty
        }

        if (energyRequired == 0L) {
            throw RuntimeException("Tron Simulated failed")
        }

        val energyFactor =
            (contractEnergyFactor.divide(ENERGY_FACTOR, 10, RoundingMode.HALF_UP)) + BigDecimal.ONE
        val maxFactor =
            (contractMaxEnergyFactor.divide(ENERGY_FACTOR, 10, RoundingMode.HALF_UP)) + BigDecimal.ONE

        val energyUnitsRequired =
            energyRequired.toBigDecimal().multiply(energyFactor).toBigInteger()
        val maxEnergyUnitsRequired =
            energyRequired.toBigDecimal().multiply(maxFactor).toBigInteger()

        // Apply Energy discount: If account has staked energy
        val availableEnergy =
            srcAccount?.calculateAvailableEnergy()?.toBigInteger() ?: BigInteger.ZERO
        val energyToPay = if (availableEnergy >= energyUnitsRequired) {
            BigInteger.ZERO
        } else {
            energyUnitsRequired - availableEnergy
        }
        val energyPrice = getCacheTronChainParameters().energyFee

        TronFees(
            maxEnergyRequired = maxEnergyUnitsRequired,
            energyRequired = energyUnitsRequired,
            energyDiscounted = energyToPay,
            amount = energyToPay * energyPrice.toBigInteger()
        )
    }

    private fun TronAccountResourceJson.calculateAvailableEnergy(): Long {
        return maxOf(energyLimit - energyUsed, 0L)
    }

    private suspend fun getCacheTronChainParameters(): TronChainParametersJson {
        return if (chainParameters == null) {
            val params = tronApi.getChainParameters()
            chainParameters = params
            params
        } else {
            chainParameters!!
        }
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        require(transaction is Transfer) {
            "Invalid Transaction Type: ${transaction::class.simpleName}"
        }

        val toAddress = transaction.to
        val isNativeCoin = transaction.coin.isNativeToken
        val hasMemo = !transaction.memo.isNullOrEmpty()
        val isTokenTransfer = !transaction.coin.isNativeToken

        val isNewAccount = runCatching {
            tronApi.getAccount(toAddress).isNewAccount()
        }.getOrDefault(true)

        val baseFee = when {
            isNativeCoin -> (BYTES_PER_CONTRACT_TX * 1000).toBigInteger()
            else -> DEFAULT_TOKEN_TRANSFER_FEE
        }

        val accountFee = if (isNewAccount) {
            DEFAULT_CREATE_ACCOUNT_FEE + DEFAULT_CREATE_ACCOUNT_SYSTEM_FEE
        } else {
            BigInteger.ZERO
        }

        val memoFee = if (hasMemo) {
            DEFAULT_MEMO_TRANSFER_FEE
        } else {
            BigInteger.ZERO
        }

        val totalFee = baseFee + accountFee + memoFee

        val maxEnergyUnitsRequired = if (isTokenTransfer) {
            BigInteger.ZERO // TODO: Check actual energy
        } else {
            BigInteger.ZERO
        }
        return TronFees(
            maxEnergyRequired = maxEnergyUnitsRequired,
            amount = totalFee
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

        private val ENERGY_FACTOR = "10000".toBigDecimal()
    }
}