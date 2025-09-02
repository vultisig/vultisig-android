package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.models.TronAccountResourceJson
import com.vultisig.wallet.data.api.models.TronChainParametersJson
import com.vultisig.wallet.data.chains.helpers.PolkadotHelper
import com.vultisig.wallet.data.chains.helpers.SolanaHelper.Companion.DefaultFeeInLamports
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.crypto.TonHelper.RECOMMENDED_JETTONS_AMOUNT
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.utils.toUnit
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import wallet.core.jni.CoinType
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

interface GasFeeRepository {
    suspend fun getGasFee(
        chain: Chain,
        address: String,
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
) : GasFeeRepository {

    var chainParameters: TronChainParametersJson? = null

    override suspend fun getGasFee(
        chain: Chain,
        address: String,
        isNativeToken: Boolean,
        to: String?,
        memo: String?,
    ): TokenValue = when (chain.standard) {
        TokenStandard.EVM -> {
            val evmApi = evmApiFactory.createEvmApi(chain)
            TokenValue(
                evmApi.getGasPrice().multiply(BigInteger("3")).divide(BigInteger("2")),
                chain.feeUnit,
                9
            )
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
                    val nativeToken =
                        async { tokenRepository.getNativeToken(chain.id) }
                    val bandwidth =
                        async { tronApi.getAccountResource(address).calculateAvailableBandwidth() }

                    val feeAmount = if (isNativeToken) {
                        MAX_BANDWIDTH_PER_COIN_TRANSFER
                    } else {
                        MAX_BANDWIDTH_TRANSACTION
                    }

                    val finalFeeAmount = getBaseFeeWithDiscount(isNativeToken, bandwidth, feeAmount)

                    val extraFeeMemo = async { getTronFeeMemo(memo) }
                    val activateDestinationFee = async { getTronInactiveDestinationFee(to) }

                    val totalFee =
                        CoinType.TRON.toUnit(finalFeeAmount) + extraFeeMemo.await() + activateDestinationFee.await()

                    TokenValue(
                        value = totalFee,
                        unit = chain.feeUnit,
                        decimals = nativeToken.await().decimal,
                    )
                }
            }

            else -> throw IllegalArgumentException("Can't estimate gas fee. Chain $chain is unsupported")
        }
    }

    private suspend fun getBaseFeeWithDiscount(
        isNativeToken: Boolean,
        availableBandwidth: Deferred<Long>,
        feeAmount: BigDecimal
    ): BigDecimal {
        val feeAmountUnit = CoinType.TRON.toUnit(feeAmount).toLong()
        return when {
            // Native transfer with sufficient bandwidth => free tx
            isNativeToken && availableBandwidth.await() >= feeAmountUnit -> BigDecimal.ZERO
            // TRC20 always pays fee (no free bandwidth for smart contracts)
            !isNativeToken -> feeAmount
            // Native transfer without sufficient bandwidth
            else -> feeAmount
        }
    }

    private suspend fun getTronFeeMemo(memo: String?): BigInteger =
        if (!memo.isNullOrEmpty()) {
            getCacheTronChainParameters().memoFeeEstimate.toBigInteger()
        } else {
            BigInteger.ZERO
        }

    private suspend fun getTronInactiveDestinationFee(to: String?): BigInteger =
        if (!to.isNullOrEmpty()) {
            val isDestinationInactive = tronApi.getAccount(to).address.isEmpty()
            if (isDestinationInactive) {
                (getCacheTronChainParameters().createAccountFeeEstimate +
                        getCacheTronChainParameters().createNewAccountFeeEstimateContract).toBigInteger()
            } else {
                BigInteger.ZERO
            }
        } else {
            BigInteger.ZERO
        }

    private suspend fun getCacheTronChainParameters(): TronChainParametersJson {
        return if (chainParameters == null) {
            tronApi.getChainParameters()
        } else {
            chainParameters!!
        }
    }

    private fun TronAccountResourceJson.calculateAvailableBandwidth(): Long {
        val freeBandwidth = freeNetLimit - freeNetUsed
        val stakingBandwidth = netLimit - netUsed

        return freeBandwidth + stakingBandwidth
    }

    internal companion object {
        private val MAX_BANDWIDTH_PER_COIN_TRANSFER = "0.3".toBigDecimal()
        private val MAX_BANDWIDTH_TRANSACTION  = "0.345".toBigDecimal()
    }
}