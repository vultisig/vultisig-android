package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.chains.DydxHelper
import com.vultisig.wallet.chains.MayaChainHelper
import com.vultisig.wallet.chains.SolanaHelper.Companion.DefaultFeeInLamports
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigInteger
import javax.inject.Inject

internal interface GasFeeRepository {

    suspend fun getGasFee(
        chain: Chain,
        address: String,
    ): TokenValue

}

internal class GasFeeRepositoryImpl @Inject constructor(
    private val evmApiFactory: EvmApiFactory,
    private val blockChairApi: BlockChairApi,
    private val solanaApi: SolanaApi,
    private val tokenRepository: TokenRepository,
    private val thorChainApi: ThorChainApi,
) : GasFeeRepository {

    override suspend fun getGasFee(
        chain: Chain,
        address: String,
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
            val gas = blockChairApi.getBlockChairStats(chain)
            val nativeToken = tokenRepository.getNativeToken(chain.id)
            TokenValue(
                gas.multiply(BigInteger("3")).divide(BigInteger("2")),
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
                    value = MayaChainHelper.MAYA_CHAIN_GAS_UNIT.toBigInteger(),
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.GaiaChain, Chain.Kujira -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id)
                TokenValue(
                    value = 7500.toBigInteger(),
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }
            Chain.Dydx -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id)
                TokenValue(
                    value = DydxHelper.DYDX_CHAIN_GAS_LIMIT.toBigInteger(),
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
                    value = 0.toBigInteger(),
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            else -> throw IllegalArgumentException("Can't estimate gas fee. Chain $chain is unsupported")
        }
    }

}