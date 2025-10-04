package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteData
import com.vultisig.wallet.data.api.models.quotes.KyberSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.KyberSwapPayloadJson
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.models.proto.v1.CoinProto
import com.vultisig.wallet.data.models.proto.v1.KeysignPayloadProto
import com.vultisig.wallet.data.models.proto.v1.ThorChainSwapPayloadProto
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

interface KeysignPayloadProtoMapper :
    MapperFunc<KeysignPayloadProto, KeysignPayload>

internal class KeysignPayloadProtoMapperImpl @Inject constructor() : KeysignPayloadProtoMapper {

    override fun invoke(from: KeysignPayloadProto): KeysignPayload {
        return KeysignPayload(
            vaultLocalPartyID = from.vaultLocalPartyId,
            vaultPublicKeyECDSA = from.vaultPublicKeyEcdsa,
            toAddress = from.toAddress,
            toAmount = BigInteger(from.toAmount),
            memo = from.memo,
            coin = requireNotNull(from.coin).toCoin(),
            libType = SigningLibType.from(from.libType),
            utxos = from.utxoInfo
                .asSequence()
                .filterNotNull()
                .map {
                    UtxoInfo(
                        hash = it.hash,
                        amount = it.amount,
                        index = it.index,
                    )
                }
                .toList(),
            approvePayload = from.erc20ApprovePayload?.let {
                ERC20ApprovePayload(
                    amount = BigInteger(it.amount),
                    spender = it.spender,
                )
            },
            wasmExecuteContractPayload = from.wasmExecuteContractPayload,
            skipBroadcast = from.skipBroadcast ?: false,
            swapPayload = when {
                from.oneinchSwapPayload != null -> from.oneinchSwapPayload.let { it ->
                    SwapPayload.EVM(
                        EVMSwapPayloadJson(
                            fromCoin = requireNotNull(it.fromCoin).toCoin(),
                            toCoin = requireNotNull(it.toCoin).toCoin(),
                            fromAmount = BigInteger(it.fromAmount),
                            toAmountDecimal = BigDecimal(it.toAmountDecimal),
                            quote = requireNotNull(it.quote).let { it ->
                                EVMSwapQuoteJson(
                                    dstAmount = it.dstAmount,
                                    tx = requireNotNull(it.tx).let {
                                        OneInchSwapTxJson(
                                            from = it.from,
                                            to = it.to,
                                            gas = it.gas,
                                            data = it.data,
                                            value = it.value,
                                            gasPrice = it.gasPrice,
                                            swapFee = it.swapFee,
                                        )
                                    },
                                )
                            },
                            provider = it.provider,
                        )
                    )
                }
                // TODO: Migrate mapping once all clients use 1inch payload
                from.kyberswapSwapPayload != null -> from.kyberswapSwapPayload.let { it ->
                    SwapPayload.Kyber(
                        KyberSwapPayloadJson(
                            fromCoin = requireNotNull(it.fromCoin).toCoin(),
                            toCoin = requireNotNull(it.toCoin).toCoin(),
                            fromAmount = BigInteger(it.fromAmount),
                            toAmountDecimal = BigDecimal(it.toAmountDecimal),
                            quote = requireNotNull(it.quote).let { it ->
                                KyberSwapQuoteJson(
                                    code = 0,
                                    message = "Success",
                                    data = KyberSwapQuoteData(
                                        amountIn = from.toAmount,
                                        amountInUsd = "0",
                                        amountOut = it.dstAmount,
                                        amountOutUsd = "0",
                                        gas = it.tx?.gas.toString(),
                                        gasUsd = "0",
                                        data = it.tx?.data ?: "",
                                        routerAddress = it.tx?.to ?: "",
                                        transactionValue = it.tx?.value ?: "",
                                        gasPrice = it.tx?.gasPrice ?: "",
                                        fee = it.tx?.fee?.toBigInteger() ?: BigInteger.ZERO,
                                    ),
                                    requestId = ""
                                )
                            }
                        ))
                }

                from.thorchainSwapPayload != null -> from.thorchainSwapPayload.let {
                    SwapPayload.ThorChain(
                        it.toThorChainSwapPayload()
                    )
                }

                from.mayachainSwapPayload != null -> from.mayachainSwapPayload.let {
                    SwapPayload.MayaChain(
                        it.toThorChainSwapPayload()
                    )
                }

                else -> null
            },

            blockChainSpecific = when {
                from.ethereumSpecific != null -> from.ethereumSpecific.let {
                    BlockChainSpecific.Ethereum(
                        maxFeePerGasWei = BigInteger(it.maxFeePerGasWei),
                        priorityFeeWei = BigInteger(it.priorityFee),
                        nonce = it.nonce.toBigInteger(),
                        gasLimit = BigInteger(it.gasLimit),
                    )
                }

                from.thorchainSpecific != null -> from.thorchainSpecific.let {
                    BlockChainSpecific.THORChain(
                        accountNumber = BigInteger(it.accountNumber.toString()),
                        sequence = BigInteger(it.sequence.toString()),
                        fee = BigInteger(it.fee.toString()),
                        isDeposit = it.isDeposit,
                        transactionType = it.transactionType,
                    )
                }

                from.utxoSpecific != null -> from.utxoSpecific.let {
                    BlockChainSpecific.UTXO(
                        byteFee = BigInteger(it.byteFee),
                        sendMaxAmount = it.sendMaxAmount,
                    )
                }

                from.mayaSpecific != null -> from.mayaSpecific.let {
                    BlockChainSpecific.MayaChain(
                        accountNumber = BigInteger(it.accountNumber.toString()),
                        sequence = BigInteger(it.sequence.toString()),
                        isDeposit = it.isDeposit,
                    )
                }

                from.cosmosSpecific != null -> from.cosmosSpecific.let {
                    BlockChainSpecific.Cosmos(
                        accountNumber = BigInteger(it.accountNumber.toString()),
                        sequence = BigInteger(it.sequence.toString()),
                        gas = BigInteger(it.gas.toString()),
                        ibcDenomTraces = it.ibcDenomTraces,
                        transactionType = it.transactionType,
                    )
                }

                from.solanaSpecific != null -> from.solanaSpecific.let {
                    BlockChainSpecific.Solana(
                        recentBlockHash = it.recentBlockHash,
                        priorityFee = it.priorityFee.toBigIntegerOrNull() ?: BigInteger.ZERO,
                        computeLimit = it.computeLimit?.toBigIntegerOrNull() ?: BigInteger.ZERO,
                        fromAddressPubKey = it.fromTokenAssociatedAddress,
                        toAddressPubKey = it.toTokenAssociatedAddress,
                        programId = it.programId == true,
                    )
                }

                from.polkadotSpecific != null -> from.polkadotSpecific.let {
                    BlockChainSpecific.Polkadot(
                        recentBlockHash = it.recentBlockHash,
                        nonce = BigInteger(it.nonce.toString()),
                        currentBlockNumber = BigInteger(it.currentBlockNumber),
                        specVersion = it.specVersion,
                        transactionVersion = it.transactionVersion,
                        genesisHash = it.genesisHash,
                    )
                }

                from.suicheSpecific != null -> from.suicheSpecific.let {
                    BlockChainSpecific.Sui(
                        referenceGasPrice = BigInteger(it.referenceGasPrice),
                        gasBudget = BigInteger(it.gasBudget),
                        coins = it.coins.filterNotNull(),
                    )
                }

                from.tonSpecific != null -> from.tonSpecific.let {
                    BlockChainSpecific.Ton(
                        sequenceNumber = it.sequenceNumber,
                        expireAt = it.expireAt,
                        bounceable = it.bounceable,
                        sendMaxAmount = it.sendMaxAmount,
                        jettonAddress = it.jettonAddress,
                        isActiveDestination = it.isActiveDestination,
                    )
                }
                from.rippleSpecific != null -> from.rippleSpecific.let {
                    BlockChainSpecific.Ripple(
                        sequence = it.sequence,
                        lastLedgerSequence = it.lastLedgerSequence,
                        gas = it.gas,
                    )
                }

                from.tronSpecific != null -> from.tronSpecific.let {
                    BlockChainSpecific.Tron(
                        timestamp = it.timestamp,
                        expiration = it.expiration,
                        blockHeaderTimestamp = it.blockHeaderTimestamp,
                        blockHeaderNumber = it.blockHeaderNumber,
                        blockHeaderVersion = it.blockHeaderVersion,
                        blockHeaderTxTrieRoot = it.blockHeaderTxTrieRoot,
                        blockHeaderParentHash = it.blockHeaderParentHash,
                        blockHeaderWitnessAddress = it.blockHeaderWitnessAddress,
                        gasFeeEstimation = it.gasEstimation,
                    )
                }
                from.cardano !=null ->  from.cardano.let {
                    BlockChainSpecific.Cardano(
                        byteFee = it.byteFee,
                        sendMaxAmount = it.sendMaxAmount,
                        ttl = it.ttl
                    )
                }

                else -> error("No supported BlockChainSpecific in proto $from")
            },
        )
    }

    private fun CoinProto.toCoin(): Coin = Coin(
        chain = Chain.fromRaw(chain),
        ticker = ticker,
        address = address,
        contractAddress = contractAddress,
        hexPublicKey = hexPublicKey,
        decimal = decimals,
        priceProviderID = priceProviderId,
        logo = logo,
        isNativeToken = isNativeToken,
    )

    private fun ThorChainSwapPayloadProto.toThorChainSwapPayload() =
        THORChainSwapPayload(
            fromAddress = fromAddress,
            fromCoin = requireNotNull(fromCoin).toCoin(),
            toCoin = requireNotNull(toCoin).toCoin(),
            vaultAddress = vaultAddress,
            routerAddress = routerAddress,
            fromAmount = BigInteger(fromAmount),
            toAmountDecimal = BigDecimal(toAmountDecimal),
            toAmountLimit = toAmountLimit,
            streamingInterval = streamingInterval,
            streamingQuantity = streamingQuantity,
            expirationTime = expirationTime,
            isAffiliate = isAffiliate,
        )
}