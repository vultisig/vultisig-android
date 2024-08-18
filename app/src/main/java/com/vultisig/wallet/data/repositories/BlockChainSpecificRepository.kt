package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.chains.UtxoInfo
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.presenter.keysign.BlockChainSpecific
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

internal data class BlockChainSpecificAndUtxo(
    val blockChainSpecific: BlockChainSpecific,
    val utxos: List<UtxoInfo> = emptyList(),
)

internal interface BlockChainSpecificRepository {

    suspend fun getSpecific(
        chain: Chain,
        address: String,
        token: Coin,
        gasFee: TokenValue,
        isSwap: Boolean,
        isMaxAmountEnabled: Boolean,
    ): BlockChainSpecificAndUtxo

}

internal class BlockChainSpecificRepositoryImpl @Inject constructor(
    private val thorChainApi: ThorChainApi,
    private val mayaChainApi: MayaChainApi,
    private val evmApiFactory: EvmApiFactory,
    private val solanaApi: SolanaApi,
    private val cosmosApiFactory: CosmosApiFactory,
    private val blockChairApi: BlockChairApi,
    private val polkadotApi: PolkadotApi,
) : BlockChainSpecificRepository {

    override suspend fun getSpecific(
        chain: Chain,
        address: String,
        token: Coin,
        gasFee: TokenValue,
        isSwap: Boolean,
        isMaxAmountEnabled: Boolean,
    ): BlockChainSpecificAndUtxo = when (chain.standard) {
        TokenStandard.THORCHAIN -> {
            val account = if (chain == Chain.MayaChain) {
                mayaChainApi.getAccountNumber(address)
            } else {
                thorChainApi.getAccountNumber(address)
            }

            BlockChainSpecificAndUtxo(
                if (chain == Chain.MayaChain) {
                    BlockChainSpecific.MayaChain(
                        accountNumber = BigInteger(
                            account.accountNumber
                                ?: "0"
                        ),
                        sequence = BigInteger(account.sequence ?: "0"),
                    )
                } else {
                    BlockChainSpecific.THORChain(
                        accountNumber = BigInteger(
                            account.accountNumber
                                ?: "0"
                        ),
                        sequence = BigInteger(account.sequence ?: "0"),
                        fee = gasFee.value,
                    )
                }
            )
        }

        TokenStandard.EVM -> {
            val evmApi = evmApiFactory.createEvmApi(chain)
            val gasLimit = BigInteger(
                when {
                    isSwap -> "600000"
                    token.isNativeToken -> {
                        if (chain == Chain.Arbitrum)
                            "120000" // arbitrum has higher gas limit
                        else
                            "23000"
                    }

                    else -> "120000"
                }
            )

            var maxPriorityFee = evmApi.getMaxPriorityFeePerGas()
            if (chain in listOf(Chain.ethereum, Chain.avalanche)) {
                maxPriorityFee = ensureOneGweiPriorityFee(maxPriorityFee)
            }
            val nonce = evmApi.getNonce(address)
            BlockChainSpecificAndUtxo(
                BlockChainSpecific.Ethereum(
                    maxFeePerGasWei = gasFee.value,
                    priorityFeeWei = maxPriorityFee,
                    nonce = nonce,
                    gasLimit = gasLimit,
                )
            )
        }

        TokenStandard.UTXO -> {
            val utxos = blockChairApi.getAddressInfo(chain, address)

            BlockChainSpecificAndUtxo(
                blockChainSpecific = BlockChainSpecific.UTXO(
                    byteFee = gasFee.value,
                    sendMaxAmount = isMaxAmountEnabled,
                ),
                utxos = utxos?.utxos?.sortedBy { it.value }?.toList()?.map {
                    UtxoInfo(
                        hash = it.transactionHash,
                        amount = it.value,
                        index = it.index.toUInt(),
                    )
                } ?: emptyList(),
            )
        }

        TokenStandard.SOL -> {
            val blockhash = solanaApi.getRecentBlockHash()
            Timber.d("solana blockhash: $blockhash")
            BlockChainSpecificAndUtxo(
                BlockChainSpecific.Solana(
                    recentBlockHash = blockhash,
                    priorityFee = gasFee.value
                )
            )
        }

        TokenStandard.COSMOS -> {
            val api = cosmosApiFactory.createCosmosApi(chain)
            val account = api.getAccountNumber(address)

            BlockChainSpecificAndUtxo(
                BlockChainSpecific.Cosmos(
                    accountNumber = BigInteger(
                        account.accountNumber
                            ?: "0"
                    ),
                    sequence = BigInteger(account.sequence ?: "0"),
                    gas = gasFee.value,
                )
            )
        }

        TokenStandard.SUBSTRATE -> {
            if (chain == Chain.polkadot) {
                val version: Pair<BigInteger, BigInteger> = polkadotApi.getRuntimeVersion()
                BlockChainSpecificAndUtxo(
                    BlockChainSpecific.Polkadot(
                        recentBlockHash = polkadotApi.getBlockHash(),
                        nonce = polkadotApi.getNonce(address),
                        currentBlockNumber = polkadotApi.getBlockHeader(),
                        specVersion = version.first.toLong().toUInt(),
                        transactionVersion = version.second.toLong().toUInt(),
                        genesisHash = polkadotApi.getGenesisBlockHash()
                    )
                )
            } else {
                error("Unsupported chain: $chain")
            }
        }
        else -> {
            error("Unsupported chain: $chain")
        }
    }

    private fun ensureOneGweiPriorityFee(priorityFee: BigInteger): BigInteger {
        // Let's make sure we pay at least 1GWei as priority fee
        val oneGwei = 1000000000.toBigInteger()
        return priorityFee.coerceAtLeast(oneGwei)
    }

}