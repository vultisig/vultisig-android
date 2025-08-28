package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.chains.helpers.PolkadotHelper
import com.vultisig.wallet.data.chains.helpers.SolanaHelper.Companion.DefaultFeeInLamports
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.crypto.TonHelper.RECOMMENDED_JETTONS_AMOUNT
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.toUnit
import java.math.BigInteger
import javax.inject.Inject

interface GasFeeRepository {
    suspend fun getGasFee(
        chain: Chain,
        address: String,
        isNativeToken: Boolean = false,
    ): TokenValue
}

internal class GasFeeRepositoryImpl @Inject constructor(
    private val evmApiFactory: EvmApiFactory,
    private val blockChairApi: BlockChairApi,
    private val solanaApi: SolanaApi,
    private val tokenRepository: TokenRepository,
    private val thorChainApi: ThorChainApi,
    private val suiApi: SuiApi,
) : GasFeeRepository {

    override suspend fun getGasFee(
        chain: Chain,
        address: String,
        isNativeToken: Boolean,
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
                val nativeToken = tokenRepository.getNativeToken(chain.id)
                val feeAmount = chain.toUnit("0.1".toBigDecimal())

                TokenValue(
                    value = feeAmount,
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            else -> throw IllegalArgumentException("Can't estimate gas fee. Chain $chain is unsupported")
        }
    }

}