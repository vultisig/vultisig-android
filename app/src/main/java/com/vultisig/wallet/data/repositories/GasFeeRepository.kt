package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.chains.MayaChainHelper
import com.vultisig.wallet.chains.THORCHainHelper
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.models.Chain
import kotlinx.coroutines.flow.first
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
) : GasFeeRepository {

    override suspend fun getGasFee(
        chain: Chain,
        address: String,
    ): TokenValue = when (chain.standard) {
        TokenStandard.EVM -> {
            val evmApi = evmApiFactory.createEvmApi(chain)
            TokenValue(evmApi.getGasPrice(), chain.feeUnit, 9)
        }

        TokenStandard.UTXO -> {
            val gas = blockChairApi.getBlockchairStats(chain)

            val nativeToken = tokenRepository.getNativeToken(chain.id).first()
            TokenValue(gas, chain.feeUnit, nativeToken.decimal)
        }

        else -> when (chain) {
            Chain.thorChain -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id).first()
                TokenValue(
                    value = THORCHainHelper.THORChainGasUnit.toBigInteger(),
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.mayaChain -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id).first()
                TokenValue(
                    value = MayaChainHelper.MayaChainGasUnit.toBigInteger(),
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.gaiaChain, Chain.kujira -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id).first()
                TokenValue(
                    value = 7500.toBigInteger(),
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.solana -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id).first()
                val fee = BigInteger(solanaApi.getHighPriorityFee(address))
                TokenValue(
                    value = fee,
                    unit = chain.feeUnit,
                    decimals = nativeToken.decimal,
                )
            }

            Chain.polkadot -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id).first()
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