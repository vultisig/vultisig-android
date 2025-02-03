package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.TonApi
import com.vultisig.wallet.data.chains.helpers.TronHelper.Companion.TRON_DEFAULT_ESTIMATION_FEE
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.data.utils.Numeric.max
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import timber.log.Timber
import vultisig.keysign.v1.CosmosIbcDenomTrace
import wallet.core.jni.Base58
import java.math.BigInteger
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

data class BlockChainSpecificAndUtxo(
    val blockChainSpecific: BlockChainSpecific,
    val utxos: List<UtxoInfo> = emptyList(),
)

interface BlockChainSpecificRepository {

    suspend fun getSpecific(
        chain: Chain,
        address: String,
        token: Coin,
        gasFee: TokenValue,
        isSwap: Boolean,
        isMaxAmountEnabled: Boolean,
        isDeposit: Boolean,
        gasLimit: BigInteger? = null,
        dstAddress: String? = null,
        tokenAmountValue: BigInteger? = null,
        memo: String? = null,
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
    private val suiApi: SuiApi,
    private val tonApi: TonApi,
    private val rippleApi: RippleApi,
    private val tronApi: TronApi,
) : BlockChainSpecificRepository {

    override suspend fun getSpecific(
        chain: Chain,
        address: String,
        token: Coin,
        gasFee: TokenValue,
        isSwap: Boolean,
        isMaxAmountEnabled: Boolean,
        isDeposit: Boolean,
        gasLimit: BigInteger?,
        dstAddress: String?,
        tokenAmountValue: BigInteger?,
        memo: String?,
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
                        isDeposit = isDeposit,
                    )
                } else {
                    BlockChainSpecific.THORChain(
                        accountNumber = BigInteger(
                            account.accountNumber
                                ?: "0"
                        ),
                        sequence = BigInteger(account.sequence ?: "0"),
                        fee = gasFee.value,
                        isDeposit = isDeposit,
                    )
                }
            )
        }

        TokenStandard.EVM -> {
            val evmApi = evmApiFactory.createEvmApi(chain)
            if (chain == Chain.ZkSync) {
                val memoDataHex = "0xffffffff".toByteArray()
                    .joinToString(separator = "") { byte -> String.format("%02x", byte) }

                val data = "0x$memoDataHex"
                val nonce = evmApi.getNonce(address)

                val feeEstimate = evmApi.zkEstimateFee(
                    srcAddress = token.address,
                    dstAddress = address,
                    data = data
                )

                BlockChainSpecificAndUtxo(
                    BlockChainSpecific.Ethereum(
                        maxFeePerGasWei = feeEstimate.maxFeePerGas,
                        priorityFeeWei = feeEstimate.maxPriorityFeePerGas,
                        nonce = nonce,
                        gasLimit = feeEstimate.gasLimit,
                    )
                )
            } else {

                val defaultGasLimit = BigInteger(
                    when {
                        isSwap -> "600000"
                        chain == Chain.Arbitrum -> {
                                "160000" // arbitrum has higher gas limit
                        }

                        token.isNativeToken -> "23000"

                        else -> "120000"
                    }
                )

                val estimateGasLimit = if (token.isNativeToken) evmApi.estimateGasForEthTransaction(
                    senderAddress = token.address,
                    recipientAddress = address,
                    value = tokenAmountValue ?: BigInteger.ZERO,
                    memo = memo,
                ) else evmApi.estimateGasForERC20Transfer(
                    senderAddress = token.address,
                    recipientAddress = address,
                    contractAddress = token.contractAddress,
                    value = tokenAmountValue ?: BigInteger.ZERO,
                )

                var maxPriorityFee = evmApi.getMaxPriorityFeePerGas()
                if (chain in listOf(Chain.Ethereum, Chain.Avalanche)) {
                    maxPriorityFee = ensureOneGweiPriorityFee(maxPriorityFee)
                }
                val nonce = evmApi.getNonce(address)
                BlockChainSpecificAndUtxo(
                    BlockChainSpecific.Ethereum(
                        maxFeePerGasWei = gasFee.value,
                        priorityFeeWei = maxPriorityFee,
                        nonce = nonce,
                        gasLimit = gasLimit ?: max(defaultGasLimit, estimateGasLimit),
                    )
                )
            }
        }

        TokenStandard.UTXO -> {
            val utxos = blockChairApi.getAddressInfo(chain, address)

            val byteFee = gasFee.value

            BlockChainSpecificAndUtxo(
                blockChainSpecific = BlockChainSpecific.UTXO(
                    byteFee = byteFee,
                    sendMaxAmount = isMaxAmountEnabled,
                ),
                utxos = utxos
                    ?.utxos
                    ?.sortedBy { it.value }
                    ?.toList()
                    ?.map {
                        UtxoInfo(
                            hash = it.transactionHash,
                            amount = it.value,
                            index = it.index.toUInt(),
                        )
                    } ?: emptyList(),
            )
        }

        TokenStandard.SOL -> coroutineScope {
            val blockHash = async {
                solanaApi.getRecentBlockHash()
            }
            val fromAddressPubKey = async {
                solanaApi.getTokenAssociatedAccountByOwner(
                    token.address,
                    token.contractAddress
                ).takeIf { !token.isNativeToken }
            }
            val toAddressPubKey = async {
                dstAddress?.let {
                    solanaApi.getTokenAssociatedAccountByOwner(
                        dstAddress,
                        token.contractAddress
                    ).takeIf { !token.isNativeToken }
                }
            }
            val recentBlockHashResult = blockHash.await()
            val fromAddressPubKeyResult = fromAddressPubKey.await()
            val toAddressPubKeyResult = toAddressPubKey.await()
            Timber.d("solana blockhash: $recentBlockHashResult")
            BlockChainSpecificAndUtxo(
                BlockChainSpecific.Solana(
                    recentBlockHash = recentBlockHashResult,
                    priorityFee = gasFee.value,
                    fromAddressPubKey = fromAddressPubKeyResult?.first,
                    toAddressPubKey = toAddressPubKeyResult?.first,
                    programId = fromAddressPubKeyResult?.second ?: false
                )
            )
        }

        TokenStandard.COSMOS -> {
            val api = cosmosApiFactory.createCosmosApi(chain)
            val account = api.getAccountNumber(address)

            val denomTrace = if (token.contractAddress.startsWith("ibc/")) {
                val denomTrace = api.getIbcDenomTraces(token.contractAddress)
                val timeout = Clock.System.now().plus(10.minutes)
                    .nanosecondsOfSecond
                CosmosIbcDenomTrace(
                    path = denomTrace.path,
                    baseDenom = denomTrace.baseDenom,
                    latestBlock = "${api.getLatestBlock()}_$timeout",
                )
            } else {
                null
            }

            BlockChainSpecificAndUtxo(
                BlockChainSpecific.Cosmos(
                    accountNumber = BigInteger(
                        account.accountNumber
                            ?: "0"
                    ),
                    sequence = BigInteger(account.sequence ?: "0"),
                    gas = gasFee.value,
                    ibcDenomTraces = denomTrace,
                )
            )
        }

        TokenStandard.SUBSTRATE -> {
            if (chain == Chain.Polkadot) {
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

        TokenStandard.SUI -> {
            BlockChainSpecificAndUtxo(
                BlockChainSpecific.Sui(
                    referenceGasPrice = suiApi.getReferenceGasPrice(),
                    coins = suiApi.getAllCoins(address),
                ),
                utxos = emptyList(),
            )
        }

        TokenStandard.TON -> {
            BlockChainSpecificAndUtxo(
                blockChainSpecific = BlockChainSpecific.Ton(
                    sequenceNumber = tonApi.getSpecificTransactionInfo(address)
                        .toString().toULong(),
                    expireAt = (Clock.System.now()
                        .epochSeconds + 600L).toULong(),
                    bounceable = false,
                    isDeposit = isDeposit,
                ),
            )
        }

        TokenStandard.RIPPLE -> {

            BlockChainSpecificAndUtxo(
                blockChainSpecific = BlockChainSpecific.Ripple(
                    sequence = rippleApi.fetchAccountsInfo(address)?.result?.accountData?.sequence?.toULong()
                        ?: 0UL,
                    gas = gasFee.value.toLong().toULong(),
                ),
            )

        }

        TokenStandard.TRC20 -> {
            val specific = tronApi.getSpecific()
            val now = Clock.System.now()
            val expiration = now + 1.hours
            val rawData = specific.blockHeader.rawData

            // Tron does not have a 0x... it can be any address
            // We will only simulate the transaction fee with below address
            val recipientAddressHex = Numeric.toHexString(Base58.decode(address))

            val estimation = TRON_DEFAULT_ESTIMATION_FEE.takeIf { token.isNativeToken }
                ?: run {
                    val rawBalance = tronApi.getBalance(token)
                    tronApi.getTriggerConstantContractFee(
                        ownerAddressBase58 = token.address,
                        contractAddressBase58 = token.contractAddress,
                        recipientAddressHex = recipientAddressHex,
                        amount = rawBalance
                    )
                }

            BlockChainSpecificAndUtxo(
                blockChainSpecific = BlockChainSpecific.Tron(
                    timestamp = now.toEpochMilliseconds().toULong(),
                    expiration = expiration.toEpochMilliseconds().toULong(),
                    blockHeaderTimestamp = rawData.timeStamp,
                    blockHeaderNumber = rawData.number,
                    blockHeaderVersion = rawData.version,
                    blockHeaderTxTrieRoot = rawData.txTrieRoot,
                    blockHeaderParentHash = rawData.parentHash,
                    blockHeaderWitnessAddress = rawData.witnessAddress,
                    gasFeeEstimation = estimation.toULong()
                )
            )
        }
    }

    private fun ensureOneGweiPriorityFee(priorityFee: BigInteger): BigInteger {
        // Let's make sure we pay at least 1GWei as priority fee
        val oneGwei = 1000000000.toBigInteger()
        return priorityFee.coerceAtLeast(oneGwei)
    }

}