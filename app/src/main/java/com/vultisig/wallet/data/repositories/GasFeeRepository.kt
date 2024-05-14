package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.chains.SolanaHelper
import com.vultisig.wallet.chains.SolanaHelper.Companion.DefaultFeeInLamports
import com.vultisig.wallet.chains.THORCHainHelper
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.GasFee
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.models.Chain
import kotlinx.coroutines.flow.first
import javax.inject.Inject

internal interface GasFeeRepository {

    suspend fun getGasFee(
        chain: Chain,
    ): GasFee

}

internal class GasFeeRepositoryImpl @Inject constructor(
    private val evmApiFactory: EvmApiFactory,
    private val blockChairApi: BlockChairApi,
    private val tokenRepository: TokenRepository,
) : GasFeeRepository {

    override suspend fun getGasFee(chain: Chain): GasFee = when {
        chain.standard == TokenStandard.ERC20 -> {
            val evmApi = evmApiFactory.createEvmApi(chain)
            GasFee(
                unit = chain.feeUnit,
                value = TokenValue(evmApi.getGasPrice(), 9)
            )
        }

        chain.standard == TokenStandard.BITCOIN -> {
            val gas = blockChairApi.getBlockchairStats(chain)

            val nativeToken = tokenRepository.getNativeToken(chain.id).first()
            GasFee(
                unit = chain.feeUnit,
                value = TokenValue(gas, nativeToken.decimal)
            )
        }

        else -> when (chain) {
            Chain.thorChain, Chain.mayaChain -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id).first()
                GasFee(
                    unit = chain.feeUnit,
                    value = TokenValue(
                        value = THORCHainHelper.THORChainGasUnit.toBigInteger(),
                        decimals = nativeToken.decimal,
                    )
                )
            }

            Chain.gaiaChain, Chain.kujira -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id).first()
                GasFee(
                    unit = chain.feeUnit,
                    value = TokenValue(
                        value = 7500.toBigInteger(),
                        decimals = nativeToken.decimal,
                    )
                )
            }

            Chain.solana -> {
                val nativeToken = tokenRepository.getNativeToken(chain.id).first()
                SolanaHelper
                GasFee(
                    unit = chain.feeUnit,
                    value = TokenValue(
                        value = DefaultFeeInLamports,
                        decimals = nativeToken.decimal,
                    )
                )
            }

            else -> throw IllegalArgumentException("Can't estimate gas fee. Chain $chain is unsupported")
        }
    }

}