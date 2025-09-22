package com.vultisig.wallet.data.blockchain.tron

import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.api.models.TronChainParametersJson
import com.vultisig.wallet.data.blockchain.BasicFee
import com.vultisig.wallet.data.blockchain.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.Transfer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.math.BigInteger
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
 * 2. Energy Fee (for smart contracts)
 *    - TRC20 and other contract calls consume "energy".
 *    - Energy can be obtained by staking TRX, or else TRX is burned.
 *    - Cost formula: (energyRequired - availableEnergy) * energyPrice (≈280 SUN per unit).
 *    - Example: a TRC20 transfer consumes ~65,000 energy → ~18.2 TRX if no energy available.
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

    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee = coroutineScope {
        require(transaction is Transfer) {
            "Transaction type not supported $"
        }

        val coin = transaction.coin
        val fromAddress = transaction.coin.address
        val toAddress = transaction.to
        val memo = transaction.memo

        val chainParamsDeferred = async { getCacheTronChainParameters() }
        val srcAccountDeferred = async { tronApi.getAccountResource(fromAddress) }
        val dstAccountDeferred = async { tronApi.getAccount(toAddress) }

        val chainParams = chainParamsDeferred.await()
        val srcAccount = srcAccountDeferred.await()
        val dstAccount = dstAccountDeferred.await()

        val fee = if (coin.isNativeToken) {
            calculateNativeTrxFee(
                srcAccount = srcAccount,
                dstAccount = dstAccount,
                hasMemo = !memo.isNullOrEmpty()
            )
        } else {
            calculateTrc20Fee(
                srcAccount = srcAccount,
                dstAccount = dstAccount,
                chainParams = chainParams,
                hasMemo = !memo.isNullOrEmpty(),
                contractAddress = coin.contractAddress
            )
        }
        error("")
    }

    private suspend fun calculateNativeTrxFee(
        srcAccount: TronAccountResourceJson?,
        dstAccount: TronAccountJson?,
        hasMemo: Boolean
    ): BigInteger {
        var totalFee = BigInteger.ZERO

        // 1) Bandwidth fee
        val bandwidthFee = calculateBandwidthFee(
            srcAccount = srcAccount,
            isContract = false,
        )

        totalFee = totalFee.add(bandwidthFee)

        // 2) Account activation fee (if destination is new)
        // New accounts don't pay bandwidth fee (it's included in activation)
        if (dstAccount.isNewAccount()) {
            val activationFee = calculateActivationFee()
            totalFee = totalFee.add(activationFee)
            totalFee = totalFee.subtract(bandwidthFee)
        }

        // 3) Memo fee
        if (hasMemo) {
            val memoFee = getCacheTronChainParameters().memoFeeEstimate.toBigInteger()
            totalFee = totalFee + memoFee
        }

        return totalFee
    }

    private fun TronAccountJson?.isNewAccount(): Boolean = this == null

    private suspend fun calculateBandwidthFee(
        srcAccount: TronAccountResourceJson?,
        isContract: Boolean,
    ): BigInteger {
        val bytesRequired = if (isContract) {
            // TODO: Only valid for TRC-20 transfer, for swaps implement actual serializer
            BYTES_PER_CONTRACT_TX
        } else {
            BYTES_PER_COIN_TX
        }

        val bandwidthPrice = getCacheTronChainParameters().bandwidthFeePrice

        // For contracts, always pay bandwidth fee
        if (isContract) {
            return BigInteger.valueOf(bytesRequired * bandwidthPrice)
        }

        // For native transfers, check available bandwidth
        val availableBandwidth = srcAccount?.calculateAvailableBandwidth() ?: 0L

        // Bandwidth apply all or nothing
        return if (availableBandwidth >= bytesRequired) {
            BigInteger.ZERO
        } else {
            BigInteger.valueOf(bytesRequired * bandwidthPrice)
        }
    }

    // https://developers.tron.network/docs/resource-model#account-bandwidth-balance-query
    private fun TronAccountResourceJson.calculateAvailableBandwidth(): Long {
        val freeBandwidth = freeNetLimit - freeNetUsed
        val stakingBandwidth = netLimit - netUsed

        return freeBandwidth + stakingBandwidth
    }

    private fun TronAccountResourceJson.calculateAvailableEnergy(): Long {
        return maxOf(energyLimit - energyUsed, 0L)
    }

    private suspend fun calculateActivationFee(): BigInteger {
        val createAccountFee = getCacheTronChainParameters().createAccountFeeEstimate
        val systemFee = getCacheTronChainParameters().createNewAccountFeeEstimateContract

        return BigInteger.valueOf(createAccountFee + systemFee)
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

        return BasicFee(totalFee)
    }


    /*

    private fun calculateTrc20Fee(
        srcAccount: TronAccountResponseJson?,
        dstAccount: TronAccountResponseJson?,
        chainParams: TronChainParametersJson,
        hasMemo: Boolean,
        contractAddress: String?
    ): BigInteger {
        var totalFee = BigInteger.ZERO

        // 1. Bandwidth fee (always paid for TRC20)
        val bandwidthFee = calculateBandwidthFee(
            srcAccount = srcAccount,
            isContract = true,
            chainParams = chainParams
        )
        totalFee = totalFee.add(bandwidthFee)

        // 2. Energy fee
        val energyFee = calculateEnergyFee(
            srcAccount = srcAccount,
            chainParams = chainParams,
            contractAddress = contractAddress
        )
        totalFee = totalFee.add(energyFee)

        // 3. Account activation fee (if destination is new)
        if (isNewAccount(dstAccount)) {
            val activationFee = calculateActivationFee(chainParams)
            totalFee = totalFee.add(activationFee)
        }

        // 4. Memo fee
        if (hasMemo) {
            val memoFee = chainParams.getMemoFee ?: DEFAULT_MEMO_FEE
            totalFee = totalFee.add(BigInteger.valueOf(memoFee))
        }

        return totalFee
    }

    private fun calculateEnergyFee(
        srcAccount: TronAccountResponseJson?,
        chainParams: TronChainParametersJson,
        contractAddress: String?
    ): BigInteger {
        // For now, use a reasonable default for TRC20 transfers
        val energyRequired = DEFAULT_TRC20_ENERGY
        val energyPrice = DEFAULT_ENERGY_PRICE // Could be fetched from chain params

        // Check if account has staked energy
        val availableEnergy = srcAccount?.accountResource?.energyLimit?.minus(
            srcAccount.accountResource.energyUsed ?: 0
        ) ?: 0

        val energyToPay = if (availableEnergy >= energyRequired) {
            0L
        } else {
            energyRequired - availableEnergy
        }
        return BigInteger.valueOf(energyToPay * energyPrice)
    }
 */

    companion object {
        // Bandwidth requirements
        private const val BYTES_PER_COIN_TX = 300L // Native TRX transfer
        private const val BYTES_PER_CONTRACT_TX = 345L // TRC20 token transfer

        private val DEFAULT_TOKEN_TRANSFER_FEE = "50000000".toBigInteger()
        private val DEFAULT_MEMO_TRANSFER_FEE = "1000000".toBigInteger()

        // Default inactive destination values
        private val DEFAULT_CREATE_ACCOUNT_FEE = "1000000".toBigInteger() // 1 TRX
        private val DEFAULT_CREATE_ACCOUNT_SYSTEM_FEE = "100000".toBigInteger() // 0.1 TRX
    }
}