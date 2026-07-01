package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.BittensorApi
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CardanoApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.DashApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.TronApiImpl.Companion.TRANSFER_FUNCTION_SELECTOR
import com.vultisig.wallet.data.api.ZcashApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_ARBITRUM_TRANSFER
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_COIN_TRANSFER_LIMIT
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_SWAP_LIMIT
import com.vultisig.wallet.data.blockchain.ethereum.EthereumFeeService.Companion.DEFAULT_TOKEN_TRANSFER_LIMIT_WITH_MARGIN
import com.vultisig.wallet.data.blockchain.model.Eip1559
import com.vultisig.wallet.data.blockchain.model.GasFees
import com.vultisig.wallet.data.blockchain.model.Swap
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.blockchain.sui.SuiFeeService.Companion.SUI_DEFAULT_GAS_BUDGET
import com.vultisig.wallet.data.chains.helpers.CardanoHelper
import com.vultisig.wallet.data.chains.helpers.SOLANA_PRIORITY_FEE_LIMIT
import com.vultisig.wallet.data.chains.helpers.SOLANA_PRIORITY_FEE_PRICE
import com.vultisig.wallet.data.chains.helpers.TronHelper.Companion.TRON_DEFAULT_ESTIMATION_FEE
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.data.utils.Numeric.max
import com.vultisig.wallet.data.utils.increaseByPercent
import java.math.BigInteger
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import timber.log.Timber
import vultisig.keysign.v1.CosmosIbcDenomTrace
import vultisig.keysign.v1.TransactionType
import wallet.core.jni.Base58

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
        transactionType: TransactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
        isThorchainRouterDeposit: Boolean = false,
    ): BlockChainSpecificAndUtxo
}

internal class BlockChainSpecificRepositoryImpl
@Inject
constructor(
    private val thorChainApi: ThorChainApi,
    private val mayaChainApi: MayaChainApi,
    private val evmApiFactory: EvmApiFactory,
    private val solanaApi: SolanaApi,
    private val cosmosApiFactory: CosmosApiFactory,
    private val blockChairApi: BlockChairApi,
    private val dashApi: DashApi,
    private val zcashApi: ZcashApi,
    private val polkadotApi: PolkadotApi,
    private val bittensorApi: BittensorApi,
    private val suiApi: SuiApi,
    private val tonApi: TonApi,
    private val rippleApi: RippleApi,
    private val tronApi: TronApi,
    private val cardanoApi: CardanoApi,
    private val feeServiceComposite: FeeServiceComposite,
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
        transactionType: TransactionType,
        isThorchainRouterDeposit: Boolean,
    ): BlockChainSpecificAndUtxo =
        when (chain.standard) {
            TokenStandard.THORCHAIN -> {
                val account =
                    if (chain == Chain.MayaChain) {
                        mayaChainApi.getAccountNumber(address)
                    } else {
                        thorChainApi.getAccountNumber(address)
                    }

                BlockChainSpecificAndUtxo(
                    if (chain == Chain.MayaChain) {
                        BlockChainSpecific.MayaChain(
                            accountNumber = BigInteger(account.accountNumber ?: "0"),
                            sequence = BigInteger(account.sequence ?: "0"),
                            isDeposit = isDeposit,
                        )
                    } else {
                        BlockChainSpecific.THORChain(
                            accountNumber = BigInteger(account.accountNumber ?: "0"),
                            sequence = BigInteger(account.sequence ?: "0"),
                            fee = gasFee.value,
                            isDeposit = isDeposit,
                            transactionType = transactionType,
                        )
                    }
                )
            }

            TokenStandard.EVM -> {
                val evmApi = evmApiFactory.createEvmApi(chain)
                val recipientAddress = dstAddress ?: address
                if (chain == Chain.ZkSync) {
                    val memoDataHex =
                        "0xffffffff".toByteArray().joinToString(separator = "") { byte ->
                            String.format("%02x", byte)
                        }

                    val data = "0x$memoDataHex"
                    val nonce = evmApi.getNonce(address)

                    val feeEstimate =
                        evmApi.zkEstimateFee(
                            srcAddress = token.address,
                            dstAddress = recipientAddress,
                            data = data,
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
                    val defaultGasLimit =
                        when {
                            isSwap -> DEFAULT_SWAP_LIMIT
                            chain == Chain.Arbitrum -> DEFAULT_ARBITRUM_TRANSFER
                            token.isNativeToken -> DEFAULT_COIN_TRANSFER_LIMIT
                            // ERC-20 floor mirrors EthereumFeeService.getDefaultLimit so the
                            // signed gasLimit equals the displayed fee bond (issue #4857).
                            else -> DEFAULT_TOKEN_TRANSFER_LIMIT_WITH_MARGIN
                        }

                    // ERC-20 router deposits need depositWithExpiry headroom, but
                    // eth_estimateGas reverts when the router hasn't been approved yet —
                    // so we hardcode the limit and skip the estimate RPC.
                    val routerDepositGasLimit =
                        if (
                            isThorchainRouterDeposit &&
                                !token.isNativeToken &&
                                isThorchainRouterChain(chain)
                        )
                            THORCHAIN_ROUTER_DEPOSIT_GAS_LIMIT
                        else null

                    val estimateGasLimit =
                        when {
                            routerDepositGasLimit != null -> routerDepositGasLimit
                            token.isNativeToken ->
                                evmApi.estimateGasForEthTransaction(
                                    senderAddress = token.address,
                                    recipientAddress = recipientAddress,
                                    value = tokenAmountValue ?: BigInteger.ZERO,
                                    memo = memo,
                                )
                            else ->
                                evmApi
                                    .estimateGasForERC20Transfer(
                                        senderAddress = token.address,
                                        recipientAddress = recipientAddress,
                                        contractAddress = token.contractAddress,
                                        value = tokenAmountValue ?: BigInteger.ZERO,
                                    )
                                    .increaseByPercent(
                                        50
                                    ) // keep it consistent with how we calculate default gas
                        // limit in EthereumFeeService
                        }

                    val nonce = evmApi.getNonce(address)

                    // Router deposits use their hardcoded headroom verbatim — the raised ERC-20
                    // default floor must not bump it up via max(). Everything else floors the
                    // on-chain estimate at the chain default.
                    val gasLimitFee =
                        gasLimit ?: routerDepositGasLimit ?: max(defaultGasLimit, estimateGasLimit)
                    val fees =
                        if (isSwap) {
                            feeServiceComposite.calculateDefaultFees(
                                Swap(
                                    coin = token,
                                    vault = VaultData("", ""),
                                    amount = tokenAmountValue ?: BigInteger.ZERO,
                                    to = dstAddress ?: address,
                                    callData = "",
                                    approvalData = null,
                                )
                            )
                        } else {
                            feeServiceComposite.calculateFees(
                                Transfer(
                                    coin = token,
                                    vault = VaultData("", ""),
                                    amount = tokenAmountValue ?: BigInteger.ZERO,
                                    to = recipientAddress,
                                    memo = memo,
                                )
                            )
                        }

                    val (maxFeePerGas, priorityFeeWei) =
                        when (fees) {
                            is Eip1559 -> fees.maxFeePerGas to fees.maxPriorityFeePerGas
                            is GasFees -> fees.price to BigInteger.ZERO
                            else ->
                                error(
                                    "Unsupported fee type ${fees::class.simpleName} for chain=$chain"
                                )
                        }

                    BlockChainSpecificAndUtxo(
                        BlockChainSpecific.Ethereum(
                            maxFeePerGasWei = maxFeePerGas,
                            priorityFeeWei = priorityFeeWei,
                            nonce = nonce,
                            gasLimit = gasLimitFee,
                        )
                    )
                }
            }

            TokenStandard.UTXO -> {
                if (chain == Chain.Cardano) {
                    val utxos = cardanoApi.getUTXOs(token)
                    val ttl = cardanoApi.calculateDynamicTTL()
                    val flatFee = gasFee.value.toLong()
                    // The initiator derives the size-based fee once and transmits it as byteFee;
                    // every co-signer then forces this exact value so the MPC sighash matches
                    // regardless of WalletCore version. Falls back to the flat fee when the
                    // transaction details aren't known yet (e.g. before an amount is entered) or
                    // when planning fails.
                    val byteFee =
                        if (dstAddress != null && tokenAmountValue != null) {
                            try {
                                CardanoHelper.estimateFee(
                                    toAmount = tokenAmountValue.toLong(),
                                    toAddress = dstAddress,
                                    changeAddress = address,
                                    sendMaxAmount = isMaxAmountEnabled,
                                    ttl = ttl.toLong(),
                                    utxos = utxos,
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.e(e, "Cardano fee derivation failed, using flat fee")
                                flatFee
                            }
                        } else {
                            flatFee
                        }
                    BlockChainSpecificAndUtxo(
                        blockChainSpecific =
                            BlockChainSpecific.Cardano(
                                byteFee = byteFee,
                                sendMaxAmount = isMaxAmountEnabled,
                                ttl = ttl,
                            ),
                        utxos = utxos,
                    )
                } else if (chain == Chain.Dash) {
                    val dashUtxos =
                        try {
                            dashApi.getAddressUtxos(address)
                        } catch (e: Exception) {
                            Timber.e(e, "Dash RPC failed, falling back to Blockchair")
                            null
                        }
                    if (dashUtxos != null) {
                        BlockChainSpecificAndUtxo(
                            blockChainSpecific =
                                BlockChainSpecific.UTXO(
                                    byteFee = gasFee.value,
                                    sendMaxAmount = isMaxAmountEnabled,
                                ),
                            utxos = dashUtxos.sortedBy(UtxoInfo::amount),
                        )
                    } else {
                        // Fallback to Blockchair with block_id filtering
                        val utxos = blockChairApi.getAddressInfo(chain = chain, address = address)
                        BlockChainSpecificAndUtxo(
                            blockChainSpecific =
                                BlockChainSpecific.UTXO(
                                    byteFee = gasFee.value,
                                    sendMaxAmount = isMaxAmountEnabled,
                                ),
                            utxos =
                                utxos
                                    ?.utxos
                                    ?.filter { it.blockId > 0 }
                                    ?.sortedBy { it.value }
                                    ?.map {
                                        UtxoInfo(
                                            hash = it.transactionHash,
                                            amount = it.value,
                                            index = it.index.toUInt(),
                                        )
                                    } ?: emptyList(),
                        )
                    }
                } else {
                    val utxos = blockChairApi.getAddressInfo(chain = chain, address = address)

                    val byteFee = gasFee.value

                    BlockChainSpecificAndUtxo(
                        blockChainSpecific =
                            BlockChainSpecific.UTXO(
                                byteFee = byteFee,
                                sendMaxAmount = isMaxAmountEnabled,
                                // Resolve the live ZIP-243 branch id for ZEC at build time so it
                                // travels with the payload to the signing helpers; null (constant
                                // fallback) for the other UTXO chains and when the RPC is down.
                                zcashBranchId =
                                    if (chain == Chain.Zcash) zcashApi.getConsensusBranchIdHex()
                                    else null,
                            ),
                        utxos =
                            utxos
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
            }

            TokenStandard.SOL ->
                coroutineScope {
                    val blockHash = async { solanaApi.getRecentBlockHash() }
                    val fromAddressPubKey = async {
                        solanaApi
                            .getTokenAssociatedAccountByOwner(token.address, token.contractAddress)
                            .takeIf { !token.isNativeToken }
                    }
                    val toAddressPubKey = async {
                        dstAddress?.let {
                            solanaApi
                                .getTokenAssociatedAccountByOwner(dstAddress, token.contractAddress)
                                .takeIf { !token.isNativeToken }
                        }
                    }
                    val recentBlockHashResult = blockHash.await()
                    val fromAddressPubKeyResult = fromAddressPubKey.await()
                    val toAddressPubKeyResult = toAddressPubKey.await()
                    Timber.d("solana blockhash: $recentBlockHashResult")
                    // priorityFee is a per-compute-unit price (microlamports/CU); priorityLimit is
                    // the CU limit. SolanaHelper charges price * limit / 1e6 as the priority fee.
                    // Swaps sign the aggregator's prebuilt tx, which carries its own compute
                    // budget,
                    // so these fields are ignored for swaps — skip the RPC and use the fallback.
                    val priorityFeePrice =
                        if (isSwap) {
                            SOLANA_PRIORITY_FEE_PRICE.toBigInteger()
                        } else {
                            val priorityAccounts = buildList {
                                add(token.address)
                                fromAddressPubKeyResult?.first?.let { add(it) }
                                toAddressPubKeyResult?.first?.let { add(it) }
                            }
                            solanaApi.getMedianPriorityFee(priorityAccounts)
                        }
                    BlockChainSpecificAndUtxo(
                        BlockChainSpecific.Solana(
                            recentBlockHash = recentBlockHashResult,
                            priorityFee = priorityFeePrice,
                            fromAddressPubKey = fromAddressPubKeyResult?.first,
                            toAddressPubKey = toAddressPubKeyResult?.first,
                            programId = fromAddressPubKeyResult?.second == true,
                            priorityLimit = SOLANA_PRIORITY_FEE_LIMIT.toBigInteger(),
                        )
                    )
                }

            TokenStandard.COSMOS -> {
                val api = cosmosApiFactory.createCosmosApi(chain)
                val account = api.getAccountNumber(address)

                val denomTrace =
                    when (chain) {
                        Chain.Kujira,
                        Chain.Terra ->
                            if (token.contractAddress.startsWith("ibc/")) {
                                val denomTrace = api.getIbcDenomTraces(token.contractAddress)
                                val timeout =
                                    Clock.System.now()
                                        .plus(10.minutes)
                                        .toEpochMilliseconds()
                                        .milliseconds
                                        .inWholeNanoseconds

                                CosmosIbcDenomTrace(
                                    path = denomTrace.path,
                                    baseDenom = denomTrace.baseDenom,
                                    latestBlock = "${api.getLatestBlock()}_$timeout",
                                )
                            } else {
                                val timeout =
                                    Clock.System.now()
                                        .plus(10.minutes)
                                        .toEpochMilliseconds()
                                        .milliseconds
                                        .inWholeNanoseconds
                                CosmosIbcDenomTrace(
                                    path = "",
                                    baseDenom = "",
                                    latestBlock = "${api.getLatestBlock()}_$timeout",
                                )
                            }

                        Chain.GaiaChain,
                        Chain.Osmosis -> {
                            if (transactionType == TransactionType.TRANSACTION_TYPE_IBC_TRANSFER) {
                                val timeout =
                                    Clock.System.now()
                                        .plus(10.minutes)
                                        .toEpochMilliseconds()
                                        .milliseconds
                                        .inWholeNanoseconds
                                CosmosIbcDenomTrace(
                                    path = "",
                                    baseDenom = "",
                                    latestBlock = "${api.getLatestBlock()}_$timeout",
                                )
                            } else null
                        }

                        else -> null
                    }

                BlockChainSpecificAndUtxo(
                    BlockChainSpecific.Cosmos(
                        accountNumber = BigInteger(account.accountNumber ?: "0"),
                        sequence = BigInteger(account.sequence ?: "0"),
                        gas = gasFee.value,
                        ibcDenomTraces = denomTrace,
                        transactionType = transactionType,
                    )
                )
            }

            TokenStandard.SUBSTRATE -> {
                when (chain) {
                    Chain.Polkadot ->
                        coroutineScope {
                            val runtimeVersionDeferred = async { polkadotApi.getRuntimeVersion() }
                            val blockHashDeferred = async { polkadotApi.getBlockHash() }
                            val nonceDeferred = async { polkadotApi.getNonce(address) }
                            val blockHeaderDeferred = async { polkadotApi.getBlockHeader() }
                            val genesisHashDeferred = async { polkadotApi.getGenesisBlockHash() }

                            val (specVersion, transactionVersion) = runtimeVersionDeferred.await()

                            BlockChainSpecificAndUtxo(
                                BlockChainSpecific.Polkadot(
                                    recentBlockHash = blockHashDeferred.await(),
                                    nonce = nonceDeferred.await(),
                                    currentBlockNumber = blockHeaderDeferred.await(),
                                    specVersion = specVersion.toLong().toUInt(),
                                    transactionVersion = transactionVersion.toLong().toUInt(),
                                    genesisHash = genesisHashDeferred.await(),
                                    gas = gasFee.value.toString().toULong(),
                                )
                            )
                        }
                    Chain.Bittensor ->
                        coroutineScope {
                            val runtimeVersionDeferred = async { bittensorApi.getRuntimeVersion() }
                            val blockHashDeferred = async { bittensorApi.getBlockHash() }
                            val nonceDeferred = async { bittensorApi.getNonce(address) }
                            val blockHeaderDeferred = async { bittensorApi.getBlockHeader() }
                            val genesisHashDeferred = async { bittensorApi.getGenesisBlockHash() }

                            val (specVersion, transactionVersion) = runtimeVersionDeferred.await()

                            BlockChainSpecificAndUtxo(
                                BlockChainSpecific.Polkadot(
                                    recentBlockHash = blockHashDeferred.await(),
                                    nonce = nonceDeferred.await(),
                                    currentBlockNumber = blockHeaderDeferred.await(),
                                    specVersion = specVersion.toLong().toUInt(),
                                    transactionVersion = transactionVersion.toLong().toUInt(),
                                    genesisHash = genesisHashDeferred.await(),
                                    gas = gasFee.value.toString().toULong(),
                                )
                            )
                        }
                    else -> error("Unsupported SUBSTRATE chain: $chain")
                }
            }

            TokenStandard.SUI ->
                coroutineScope {
                    val coinsDeferred = async { suiApi.getAllCoins(address) }
                    val transfer =
                        Transfer(
                            coin = token,
                            vault = VaultData("", ""),
                            amount = tokenAmountValue ?: BigInteger.ZERO,
                            to = dstAddress ?: address,
                        )

                    suspend fun paddedDefaultSuiFees(): GasFees {
                        val price = suiApi.getReferenceGasPrice()
                        val padded = SUI_DEFAULT_GAS_BUDGET.increaseByPercent(15)
                        return GasFees(price = price, limit = padded, amount = padded)
                    }

                    val suiFeesDeferred = async {
                        try {
                            when (val fees = feeServiceComposite.calculateFees(transfer)) {
                                is GasFees -> fees
                                else -> {
                                    Timber.w(
                                        "Unexpected fee type %s for SUI; using padded default gas budget",
                                        fees::class.simpleName,
                                    )
                                    paddedDefaultSuiFees()
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.w(
                                e,
                                "SUI fee estimation failed; using padded default gas budget",
                            )
                            paddedDefaultSuiFees()
                        }
                    }

                    val suiFees = suiFeesDeferred.await()
                    BlockChainSpecificAndUtxo(
                        BlockChainSpecific.Sui(
                            referenceGasPrice = suiFees.price,
                            gasBudget = suiFees.limit,
                            coins = coinsDeferred.await(),
                        ),
                        utxos = emptyList(),
                    )
                }

            TokenStandard.TON -> {
                coroutineScope {
                    val sequenceNumberDeferred = async { tonApi.getSeqno(address) }
                    val isBounceable = async {
                        if (dstAddress == null) return@async false

                        val isUninitialized =
                            tonApi.getWalletState(dstAddress) == TON_WALLET_STATE_UNINITIALIZED
                        if (isUninitialized) return@async false

                        dstAddress.startsWith("E")
                    }
                    val (destinationActive, jettonsAddress) =
                        if (!token.isNativeToken) {
                            val destinationIsActiveDeferred = async {
                                if (dstAddress == null) {
                                    false
                                } else {
                                    tonApi.getWalletState(dstAddress) !=
                                        TON_WALLET_STATE_UNINITIALIZED
                                }
                            }

                            val jettonsAddressDeferred = async {
                                tonApi
                                    .getJettonWallet(address, token.contractAddress)
                                    .getJettonsAddress()
                            }

                            destinationIsActiveDeferred.await() to jettonsAddressDeferred.await()
                        } else {
                            false to ""
                        }
                    BlockChainSpecificAndUtxo(
                        blockChainSpecific =
                            BlockChainSpecific.Ton(
                                sequenceNumber =
                                    sequenceNumberDeferred.await().toString().toULong(),
                                expireAt = (Clock.System.now().epochSeconds + 600L).toULong(),
                                bounceable = isBounceable.await(),
                                isDeposit = isDeposit,
                                sendMaxAmount = isMaxAmountEnabled,
                                isActiveDestination = destinationActive,
                                jettonAddress = jettonsAddress ?: "",
                            )
                    )
                }
            }

            TokenStandard.RIPPLE -> {
                val accountInfo = rippleApi.fetchAccountsInfo(address)
                BlockChainSpecificAndUtxo(
                    blockChainSpecific =
                        BlockChainSpecific.Ripple(
                            sequence = accountInfo?.result?.accountData?.sequence?.toULong() ?: 0UL,
                            lastLedgerSequence =
                                accountInfo?.result?.ledgerCurrentIndex?.toULong()?.plus(60UL)
                                    ?: 0UL,
                            gas = gasFee.value.toLong().toULong(),
                        )
                )
            }

            TokenStandard.TRC20 -> {
                val specific = tronApi.getSpecific()
                val energyPrice =
                    try {
                        tronApi.getChainParameters().energyFee.takeIf { it > 0L }
                    } catch (_: Exception) {
                        null
                    } ?: ENERGY_TO_SUN_FACTOR.toLong()
                val now = Clock.System.now()
                val expiration = now + 1.hours
                val rawData = specific.blockHeader.rawData

                val recipientAddressHex = Numeric.toHexString(Base58.decode(dstAddress ?: address))

                val estimation =
                    TRON_DEFAULT_ESTIMATION_FEE.takeIf { token.isNativeToken }
                        ?: run {
                            val rawBalance = tronApi.getBalance(token)
                            val triggerResult =
                                tronApi.getTriggerConstantContractFee(
                                    ownerAddressBase58 = token.address,
                                    contractAddressBase58 = token.contractAddress,
                                    recipientAddressHex = recipientAddressHex,
                                    functionSelector = TRANSFER_FUNCTION_SELECTOR,
                                    amount = rawBalance,
                                )

                            val totalEnergy = triggerResult.energyUsed + triggerResult.energyPenalty
                            totalEnergy * energyPrice
                        }

                BlockChainSpecificAndUtxo(
                    blockChainSpecific =
                        BlockChainSpecific.Tron(
                            timestamp = now.toEpochMilliseconds().toULong(),
                            expiration = expiration.toEpochMilliseconds().toULong(),
                            blockHeaderTimestamp = rawData.timeStamp,
                            blockHeaderNumber = rawData.number,
                            blockHeaderVersion = rawData.version,
                            blockHeaderTxTrieRoot = rawData.txTrieRoot,
                            blockHeaderParentHash = rawData.parentHash,
                            blockHeaderWitnessAddress = rawData.witnessAddress,
                            gasFeeEstimation = estimation.toString().toULong(),
                        )
                )
            }
        }

    private fun isThorchainRouterChain(chain: Chain): Boolean =
        chain == Chain.Ethereum ||
            chain == Chain.Base ||
            chain == Chain.Avalanche ||
            chain == Chain.BscChain ||
            chain == Chain.Arbitrum ||
            chain == Chain.Optimism

    companion object {
        private const val TON_WALLET_STATE_UNINITIALIZED = "uninit"

        private const val ENERGY_TO_SUN_FACTOR = 280

        private val THORCHAIN_ROUTER_DEPOSIT_GAS_LIMIT = BigInteger.valueOf(200_000)
    }
}
