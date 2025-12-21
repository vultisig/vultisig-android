package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.api.models.TronChainParametersJson
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_ARBITRUM_TRANSFER
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_COIN_TRANSFER_LIMIT
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_SWAP_LIMIT
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_TOKEN_TRANSFER_LIMIT
import com.vultisig.wallet.data.chains.helpers.PolkadotHelper
import com.vultisig.wallet.data.chains.helpers.SolanaHelper.Companion.DefaultFeeInLamports
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.crypto.TonHelper.RECOMMENDED_JETTONS_AMOUNT
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.utils.decimals
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

interface GasFeeRepository {
    suspend fun getGasFee(
        chain: Chain,
        address: String,
        isSwap: Boolean = false,
        isNativeToken: Boolean = false,
        to: String? = null,
        memo: String? = null,
    ): TokenValue
}

@Deprecated("Migrate to Properly Fee Service After PR #2412")
internal class GasFeeRepositoryImpl @Inject constructor(
    private val evmApiFactory: EvmApiFactory,
    private val blockChairApi: BlockChairApi,
    private val solanaApi: SolanaApi,
    private val tokenRepository: TokenRepository,
    private val thorChainApi: ThorChainApi,
    private val tronApi: TronApi,
    private val polkadotApi: PolkadotApi,
    private val ethereumFeeService: EthereumFeeService,
) : GasFeeRepository {

    var chainParameters: TronChainParametersJson? = null

    override suspend fun getGasFee(
        chain: Chain,
        address: String,
        isSwap: Boolean,
        isNativeToken: Boolean,
        to: String?,
        memo: String?,
    ): TokenValue = when (chain.standard) {
        TokenStandard.EVM -> {
             if (!isSwap) {
                getDefaultEVMFee(chain, isSwap, isNativeToken)
            } else {
                val evmApi = evmApiFactory.createEvmApi(chain)
                TokenValue(
                    evmApi.getGasPrice().multiply(BigInteger("3")).divide(BigInteger("2")),
                    chain.feeUnit,
                    9
                )
            }
        }

        TokenStandard.UTXO -> {
            val nativeToken = tokenRepository.getNativeToken(chain.id)

            val gas = when (chain) {
                Chain.Zcash -> "1000".toBigInteger()
                Chain.Cardano -> "180000".toBigInteger()

                else -> {
                    val gas = blockChairApi.getBlockChairStats(chain)
                    gas.multiply(BigInteger("5")).divide(BigInteger("2"))
                }
            }

            TokenValue(
                gas,
                chain.feeUnit,
                nativeToken.decimal
            )
        }

        else -> when (chain) {
            Chain.ThorChain -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id)
                TokenValue(
                    value = thorChainApi.getTHORChainNativeTransactionFee(),
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.MayaChain -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id)
                TokenValue(
                    value = ThorChainHelper.MAYA_CHAIN_GAS_UNIT.toBigInteger(),
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.GaiaChain, Chain.Kujira, Chain.Osmosis, Chain.Terra, Chain.Akash -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id)
                TokenValue(
                    value = 7500.toBigInteger(),
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.Noble -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id)
                TokenValue(
                    value = 20000L.toBigInteger(),
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.TerraClassic -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id)
                TokenValue(
                    value = 10000000L.toBigInteger(),
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.Dydx -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id)
                TokenValue(
                    value = 2500000000000000L.toBigInteger(),
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.Solana -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id)
                val fee = maxOf(
                    BigInteger(solanaApi.getHighPriorityFee(address)),
                    DefaultFeeInLamports
                )
                TokenValue(
                    value = fee,
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.Polkadot -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id)

                TokenValue(
                    value = PolkadotHelper.DEFAULT_FEE_PLANCKS.toBigInteger(),
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.Sui -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id)
                TokenValue(
                    value = BigInteger("3000000"),
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.Ton -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id)
                val msgFees = if (!isNativeToken) {
                    RECOMMENDED_JETTONS_AMOUNT.toBigInteger()
                } else {
                    BigInteger.ZERO
                }
                TokenValue(
                    value = BigInteger("10000000") + msgFees,
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.Ripple -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id)
                TokenValue(
                    value = BigInteger("180000"),
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.Tron -> {
                supervisorScope {
                    try {
                        if (isSwap) {
                            return@supervisorScope TokenValue(
                                value = (8000000).toBigInteger(),
                                unit = chain.feeUnit,
                                decimals = 6,
                            )
                        }

                        val nativeToken =
                            async { tokenRepository.getNativeToken(chain.id) }
                        val bandwidth =
                            async {
                                tronApi.getAccountResource(address).calculateAvailableBandwidth()
                            }
                        val memoFee =
                            async { getTronFeeMemo(memo, isSwap) }

                        // Check if destination needs activation (new account)
                        val activationFee = getTronInactiveDestinationFee(to)
                        val isNewAccount = activationFee > BigInteger.ZERO

                        // New accounts don't pay bandwidth fee (included in activation)
                        val bandwidthFee = if (isNewAccount) {
                            BigInteger.ZERO
                        } else {
                            getBandwidthFeeDiscount(isNativeToken, bandwidth)
                        }

                        val totalFee = bandwidthFee + memoFee.await() + activationFee

                        TokenValue(
                            value = totalFee,
                            unit = chain.feeUnit,
                            decimals = nativeToken.await().decimal,
                        )
                    } catch (t: Throwable) {
                        Timber.e(t)
                        TokenValue(
                            value = (BYTES_PER_CONTRACT_TX * 1000).toBigInteger(),
                            unit = chain.feeUnit,
                            decimals = 6,
                        )
                    }
                }
            }

            else -> throw IllegalArgumentException("Can't estimate gas fee. Chain $chain is unsupported")
        }
    }

    private suspend fun getBandwidthFeeDiscount(
        isNativeToken: Boolean,
        availableBandwidth: Deferred<Long>,
    ): BigInteger {
        val feeBandwidthRequired = if (isNativeToken) {
            BYTES_PER_COIN_TX
        } else {
            BYTES_PER_CONTRACT_TX
        }
        val bandwidthPrice = getCacheTronChainParameters().bandwidthFeePrice

        return when {
            // Native transfer with sufficient bandwidth => free tx
            isNativeToken && availableBandwidth.await() >= feeBandwidthRequired -> BigInteger.ZERO
            // TRC20 always pays fee (no free bandwidth for smart contracts)
            !isNativeToken -> (feeBandwidthRequired * bandwidthPrice).toBigInteger()
            // Native transfer without sufficient bandwidth
            else -> (feeBandwidthRequired * bandwidthPrice).toBigInteger()
        }
    }

    private suspend fun getTronFeeMemo(memo: String?, isSwap: Boolean): BigInteger =
        if (!memo.isNullOrEmpty() || isSwap) {
            getCacheTronChainParameters().memoFeeEstimate.toBigInteger()
        } else {
            BigInteger.ZERO
        }

    private suspend fun getTronInactiveDestinationFee(to: String?): BigInteger {
        if (to.isNullOrEmpty()) {
            return BigInteger.ZERO
        }

        val accountExists = try {
            tronApi.getAccount(to).address.isNotEmpty()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e)
            false
        }

        if (accountExists){
            return BigInteger.ZERO
        }

        val createAccountFee =
            getCacheTronChainParameters().createAccountFeeEstimate
        val createAccountContractFee =
            getCacheTronChainParameters().createNewAccountFeeEstimateContract

        return createAccountFee.toBigInteger() + createAccountContractFee.toBigInteger()
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

    // https://developers.tron.network/docs/resource-model#account-bandwidth-balance-query
    private fun TronAccountResourceJson.calculateAvailableBandwidth(): Long {
        val freeBandwidth = freeNetLimit - freeNetUsed
        val stakingBandwidth = netLimit - netUsed

        return freeBandwidth + stakingBandwidth
    }

    private suspend fun getDefaultEVMFee(
        chain: Chain,
        isSwap: Boolean,
        isNativeToken: Boolean
    ): TokenValue {
        val defaultGasLimit =
            when {
                chain == Chain.Arbitrum -> DEFAULT_ARBITRUM_TRANSFER
                isNativeToken -> DEFAULT_COIN_TRANSFER_LIMIT
                else -> DEFAULT_TOKEN_TRANSFER_LIMIT
            }

        val fees = ethereumFeeService.calculateFees(chain, defaultGasLimit, isSwap, null)
        
        return TokenValue(
            value = fees.amount,
            unit = chain.feeUnit,
            decimals = chain.coinType.decimals,
        )
    }

    internal companion object {
        private const val BYTES_PER_COIN_TX = 300L
        private const val BYTES_PER_CONTRACT_TX = 345L
    }
}