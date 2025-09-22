package com.vultisig.wallet.data.blockchain.tron

import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.models.TronAccountJson
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.api.models.TronChainParametersJson
import com.vultisig.wallet.data.blockchain.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.Fee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.Transfer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.math.BigInteger
import javax.inject.Inject

/**
 * Service for calculating TRON transaction fees.
 *
 * TRON fee structure consists of:
 * 1. Bandwidth fee - for transaction data transmission
 * 2. Energy fee - for smart contract execution (TRC20 tokens)
 * 3. Memo fee - additional fee if memo is included
 * 4. Account activation fee - for sending to new accounts
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
        val srcAccountDeferred = async { tronApi.getAccount(fromAddress) }
        val dstAccountDeferred = async { tronApi.getAccount(toAddress) }

        val chainParams = chainParamsDeferred.await()
        val srcAccount = srcAccountDeferred.await()
        val dstAccount = dstAccountDeferred.await()

        val fee = if (coin.isNativeToken) {
            calculateNativeTrxFee(
                srcAccount = srcAccount,
                dstAccount = dstAccount,
                chainParams = chainParams,
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
        srcAccount: TronAccountJson?,
        dstAccount: TronAccountJson?,
        hasMemo: Boolean
    ): BigInteger {
        var totalFee = BigInteger.ZERO

        // 1. Bandwidth fee
        val bandwidthFee = calculateBandwidthFee(
            srcAccount = srcAccount,
            isContract = false,
        )

        totalFee = totalFee.add(bandwidthFee)

        // 2. Account activation fee (if destination is new)
        if (dstAccount.isNewAccount()) {
            val activationFee = calculateActivationFee(chainParams)
            totalFee = totalFee.add(activationFee)
            // New accounts don't pay bandwidth fee (it's included in activation)
            totalFee = totalFee.subtract(bandwidthFee)
        }

        // 3. Memo fee
        if (hasMemo) {
            val memoFee = getCacheTronChainParameters().memoFeeEstimate.toBigInteger()
            totalFee = totalFee + memoFee
        }

        return totalFee
    }

    private fun TronAccountJson?.isNewAccount(): Boolean = this == null

    private suspend fun calculateBandwidthFee(
        srcAccount: TronAccountJson?,
        isContract: Boolean,
    ): BigInteger {
        val bytesRequired = if (isContract) {
            BYTES_PER_CONTRACT_TX
        } else {
            BYTES_PER_COIN_TX
        }

        val bandwidthPrice =
            getCacheTronChainParameters().getTransactionFee ?: DEFAULT_BANDWIDTH_FEE_PRICE

        // For contracts, always pay bandwidth fee
        if (isContract) {
            return BigInteger.valueOf(bytesRequired * bandwidthPrice)
        }

        // For native transfers, check available bandwidth
        val availableBandwidth = srcAccount.calculateAvailableBandwidth()

        // Bandwidth apply all or nothing
        return if (availableBandwidth >= bytesRequired) {
            // Have enough free/staked bandwidth
            BigInteger.ZERO
        } else {
            // Need to pay for bandwidth
            BigInteger.valueOf(bytesRequired * bandwidthPrice)
        }
    }

    // https://developers.tron.network/docs/resource-model#account-bandwidth-balance-query
    private fun TronAccountResourceJson.calculateAvailableBandwidth(): Long {
        val freeBandwidth = freeNetLimit - freeNetUsed
        val stakingBandwidth = netLimit - netUsed

        return freeBandwidth + stakingBandwidth
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

    }

    /* override suspend fun calculateFee(
        coin: Coin,
        srcAddress: String,
        dstAddress: String,
        amount: BigInteger,
        memo: String?,
        gasFee: BigInteger,
        isMaxAmount: Boolean,
        blockChainSpecific: BlockChainSpecific.Tron?
    ): BigInteger = withContext(Dispatchers.IO) {
        try {
            Timber.Forest.d("Calculating TRON fee for ${coin.ticker} from $srcAddress to $dstAddress")

            // Fetch required data in parallel
            val chainParamsDeferred = async { getChainParameters() }
            val srcAccountDeferred = async { tronApi.getAccount(srcAddress) }
            val dstAccountDeferred = async { tronApi.getAccount(dstAddress) }

            val chainParams = chainParamsDeferred.await()
            val srcAccount = srcAccountDeferred.await()
            val dstAccount = dstAccountDeferred.await()

            // Calculate fee based on token type
            val fee = if (coin.isNativeToken) {
               calculateNativeTrxFee(
                    srcAccount = srcAccount,
                    dstAccount = dstAccount,
                    chainParams = chainParams,
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

            Timber.Forest.d(
                "TRON fee calculated: $fee SUN (${
                    fee.divide(
                        BigInteger.valueOf(
                            1_000_000
                        )
                    )
                } TRX)"
            )
            fee
        } catch (e: Exception) {
            Timber.Forest.e(e, "Error calculating TRON fee, using default")
            getDefaultFee(coin.isNativeToken)
        }
    }

    private fun calculateNativeTrxFee(
        srcAccount: TronAccountResponseJson?,
        dstAccount: TronAccountResponseJson?,
        chainParams: TronChainParametersJson,
        hasMemo: Boolean
    ): BigInteger {
        var totalFee = BigInteger.ZERO

        // 1. Bandwidth fee
        val bandwidthFee = calculateBandwidthFee(
            srcAccount = srcAccount,
            isContract = false,
            chainParams = chainParams
        )
        totalFee = totalFee.add(bandwidthFee)

        // 2. Account activation fee (if destination is new)
        if (isNewAccount(dstAccount)) {
            val activationFee = calculateActivationFee(chainParams)
            totalFee = totalFee.add(activationFee)
            // New accounts don't pay bandwidth fee (it's included in activation)
            totalFee = totalFee.subtract(bandwidthFee)
        }

        // 3. Memo fee
        if (hasMemo) {
            val memoFee = chainParams.getMemoFee ?: DEFAULT_MEMO_FEE
            totalFee = totalFee.add(BigInteger.valueOf(memoFee))
        }

        return totalFee
    }

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

    private fun calculateBandwidthFee(
        srcAccount: TronAccountResponseJson?,
        isContract: Boolean,
        chainParams: TronChainParametersJson
    ): BigInteger {
        val bytesRequired = if (isContract) BYTES_PER_CONTRACT_TX else BYTES_PER_COIN_TX
        val bandwidthPrice = chainParams.getTransactionFee ?: DEFAULT_BANDWIDTH_FEE_PRICE

        // For contracts, always pay bandwidth fee
        if (isContract) {
            return BigInteger.valueOf(bytesRequired * bandwidthPrice)
        }

        // For native transfers, check available bandwidth
        val availableBandwidth = calculateAvailableBandwidth(srcAccount)

        return if (availableBandwidth >= bytesRequired) {
            // Have enough free/staked bandwidth
            BigInteger.ZERO
        } else {
            // Need to pay for bandwidth
            BigInteger.valueOf(bytesRequired * bandwidthPrice)
        }
    }

    private fun calculateEnergyFee(
        srcAccount: TronAccountResponseJson?,
        chainParams: TronChainParametersJson,
        contractAddress: String?
    ): BigInteger {
        // TODO: Implement actual energy estimation based on contract
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

    private fun calculateAvailableBandwidth(account: TronAccountResponseJson?): Long {
        if (account == null) return 0L

        val freeNet = account.freeNetLimit?.minus(account.freeNetUsed ?: 0) ?: 0
        val stakedNet = account.accountResource?.netLimit?.minus(
            account.accountResource.netUsed ?: 0
        ) ?: 0

        return freeNet + stakedNet
    }

    private fun calculateActivationFee(chainParams: TronChainParametersJson): BigInteger {
        val createAccountFee = chainParams.getCreateAccountFee ?: DEFAULT_CREATE_ACCOUNT_FEE
        val systemFee = chainParams.getCreateNewAccountFeeInSystemContract ?: DEFAULT_CREATE_ACCOUNT_SYSTEM_FEE
        return BigInteger.valueOf(createAccountFee + systemFee)
    }

    private fun isNewAccount(account: TronAccountResponseJson?): Boolean {
        return account == null || account.createTime == null
    }

    private suspend fun getChainParameters(): TronChainParametersJson {
        cachedChainParameters?.let { return it }

        return tronApi.getChainParameters().also {
            cachedChainParameters = it
        }
    }

    private fun getDefaultFee(isNative: Boolean): BigInteger {
        return if (isNative) {
            // Default fee for native TRX: 1 TRX
            BigInteger.valueOf(1_000_000)
        } else {
            // Default fee for TRC20: 15 TRX (higher due to energy costs)
            BigInteger.valueOf(15_000_000)
        }
    } */

    companion object {
        // Bandwidth requirements
        private const val BYTES_PER_COIN_TX = 300L // Native TRX transfer
        private const val BYTES_PER_CONTRACT_TX = 345L // TRC20 token transfer

        // Default values
        private const val DEFAULT_BANDWIDTH_FEE_PRICE = 1000L // 1000 SUN per byte
        private const val DEFAULT_MEMO_FEE = 1_000_000L // 1 TRX
        private const val DEFAULT_CREATE_ACCOUNT_FEE = 1_000_000L // 1 TRX
        private const val DEFAULT_CREATE_ACCOUNT_SYSTEM_FEE = 100_000L // 0.1 TRX
        private const val DEFAULT_ENERGY_PRICE = 280L // 280 SUN per energy unit
        private const val DEFAULT_TRC20_ENERGY = 65_000L // Typical TRC20 transfer energy
    }
}