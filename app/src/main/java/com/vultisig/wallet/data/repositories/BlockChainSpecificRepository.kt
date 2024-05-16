package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.presenter.keysign.BlockChainSpecific
import java.math.BigInteger
import javax.inject.Inject

internal interface BlockChainSpecificRepository {

    suspend fun getSpecific(
        chain: Chain,
        address: String,
        token: Coin,
        gasFee: TokenValue,
    ): BlockChainSpecific

}

internal class BlockChainSpecificRepositoryImpl @Inject constructor(
    private val thorChainApi: ThorChainApi,
    private val mayaChainApi: MayaChainApi,
    private val evmApiFactory: EvmApiFactory,
    private val solanaApi: SolanaApi,
    private val cosmosApiFactory: CosmosApiFactory,
) : BlockChainSpecificRepository {

    override suspend fun getSpecific(
        chain: Chain,
        address: String,
        token: Coin,
        gasFee: TokenValue,
    ): BlockChainSpecific = when (chain.standard) {
        TokenStandard.THORCHAIN -> {
            val account = if (chain == Chain.mayaChain) {
                mayaChainApi.getAccountNumber(address)
            } else {
                thorChainApi.getAccountNumber(address)
            }

            BlockChainSpecific.THORChain(
                accountNumber = BigInteger(account.accountNumber),
                sequence = BigInteger(account.sequence ?: "0"),
            )
        }

        TokenStandard.EVM -> {
            val evmApi = evmApiFactory.createEvmApi(chain)

            val gasLimit = BigInteger(
                if (token.isNativeToken) "23000"
                else "120000"
            )
            val maxPriorityFee = evmApi.getMaxPriorityFeePerGas()
            val nonce = evmApi.getNonce(address)

            BlockChainSpecific.Ethereum(
                maxFeePerGasWei = gasFee.value,
                priorityFeeWei = maxPriorityFee,
                nonce = nonce,
                gasLimit = gasLimit,
            )
        }

        TokenStandard.UTXO -> {
            BlockChainSpecific.UTXO(
                byteFee = gasFee.value,
                sendMaxAmount = false,
            )
        }

        TokenStandard.SOL -> {
            val blockhash = solanaApi.getRecentBlockHash()

            BlockChainSpecific.Solana(
                recentBlockHash = blockhash,
                priorityFee = gasFee.value
            )
        }

        TokenStandard.COSMOS -> {
            val api = cosmosApiFactory.createCosmosApi(chain)
            val account = api.getAccountNumber(address)

            BlockChainSpecific.Cosmos(
                accountNumber = BigInteger(account.accountNumber),
                sequence = BigInteger(account.sequence ?: "0"),
                gas = gasFee.value,
            )
        }

        else -> throw IllegalArgumentException("Unsupported chain $chain")
    }

}