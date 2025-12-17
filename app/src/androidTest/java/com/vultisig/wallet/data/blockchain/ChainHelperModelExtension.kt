package com.vultisig.wallet.data.blockchain

import BlockchainSpecific
import Coin
import KeysignPayload
import OneinchQuote
import OneinchSwapPayload
import OneinchTransaction
import SwapPayload
import ThorchainSwapPayload
import WasmExecuteContractPayload
import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.UtxoInfo
import vultisig.keysign.v1.CosmosCoin
import vultisig.keysign.v1.CosmosIbcDenomTrace
import vultisig.keysign.v1.TransactionType
import java.math.BigInteger

fun KeysignPayload.toInternalKeySignPayload(): com.vultisig.wallet.data.models.payload.KeysignPayload {
    val coin = this.coin.toInternalCoinPayload()

    return com.vultisig.wallet.data.models.payload.KeysignPayload(
        coin = coin,
        toAddress = this.toAddress,
        toAmount = this.toAmount.toBigInteger(),
        blockChainSpecific = this.blockchainSpecific.toBlockChainSpecific(coin, this.toAddress),
        utxos = this.utxoInfo?.map {
            UtxoInfo(
                hash = it.hash,
                amount = it.amount,
                index = it.index.toUInt(),
            )
        } ?: emptyList(),
        memo = this.memo,
        vaultPublicKeyECDSA = this.vaultPublicKeyEcdsa,
        vaultLocalPartyID = "",
        libType = SigningLibType.valueOf(this.libType),
        wasmExecuteContractPayload = this.wasmExecuteContractPayload?.toWasmPayload(),
        swapPayload = this.swapPayload?.toInternalSwapPayload(),
        approvePayload = this.approvePayload?.let {
            com.vultisig.wallet.data.models.payload.ERC20ApprovePayload(
                spender = it.spender,
                amount = it.amount.toBigInteger(),
            )
        },
        skipBroadcast = false // Not present in source, handled as default
    )
}

internal fun WasmExecuteContractPayload.toWasmPayload(): vultisig.keysign.v1.WasmExecuteContractPayload {
    return vultisig.keysign.v1.WasmExecuteContractPayload(
        senderAddress = this.senderAddress,
        contractAddress = this.contractAddress,
        executeMsg = this.executeMsg,
        coins = this.coins.map {
            CosmosCoin(
                denom = it.denom,
                amount = it.amount,
            )
        }
    )
}

internal fun Coin.toInternalCoinPayload(): com.vultisig.wallet.data.models.Coin {
    return com.vultisig.wallet.data.models.Coin(
        chain = Chain.entries.find { it.raw.equals(this.chain, true) } ?: Chain.Ethereum,
        ticker = this.ticker,
        logo = this.logo,
        address = this.address,
        decimal = this.decimals,
        hexPublicKey = this.hexPublicKey,
        priceProviderID = this.priceProviderId,
        contractAddress = this.contractAddress ?: "",
        isNativeToken = this.isNativeToken,
    )
}

fun BlockchainSpecific.toBlockChainSpecific(
    coin: com.vultisig.wallet.data.models.Coin,
    toAddress: String
): BlockChainSpecific {
    val standard = coin.chain.standard
    return when (standard) {
        TokenStandard.EVM -> {
            val ethereumSpecific = this.ethereumSpecific ?: error("Specific empty $this")
            BlockChainSpecific.Ethereum(
                maxFeePerGasWei = BigInteger(ethereumSpecific.maxFeePerGasWei),
                priorityFeeWei = BigInteger(ethereumSpecific.priorityFee),
                nonce = BigInteger.valueOf(ethereumSpecific.nonce.toLong()),
                gasLimit = BigInteger(ethereumSpecific.gasLimit),
                isDeposit = false
            )
        }

        TokenStandard.COSMOS -> {
            val cosmosSpecific = this.cosmosSpecific ?: error("Specific empty $this")
            BlockChainSpecific.Cosmos(
                accountNumber = cosmosSpecific.accountNumber.toBigInteger(),
                sequence = cosmosSpecific.sequence.toBigInteger(),
                gas = cosmosSpecific.gas.toBigInteger(),
                ibcDenomTraces = CosmosIbcDenomTrace(
                    path = cosmosSpecific.ibcDenomTrace?.path ?: "",
                    baseDenom = cosmosSpecific.ibcDenomTrace?.baseDenom ?: "",
                    latestBlock = cosmosSpecific.ibcDenomTrace?.height ?: "",
                ),
                transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
            )
        }

        TokenStandard.RIPPLE -> {
            val rippleSpecific = this.rippleSpecific ?: error("Specific empty $this")
            BlockChainSpecific.Ripple(
                sequence = rippleSpecific.sequence.toULong(),
                gas = rippleSpecific.gas.toULong(),
                lastLedgerSequence = rippleSpecific.lastLedgerSequence.toULong(),
            )
        }

        TokenStandard.TON -> {
            val tonSpecific = this.tonSpecific ?: error("Specific empty $this")
            BlockChainSpecific.Ton(
                sendMaxAmount = tonSpecific.sendMaxAmount,
                expireAt = tonSpecific.expireAt.toULong(),
                bounceable = tonSpecific.bounceable,
                sequenceNumber = tonSpecific.sequenceNumber.toULong(),
                jettonAddress = tonSpecific.jettonsAddress,
                isActiveDestination = tonSpecific.activeDestination,
            )
        }

        TokenStandard.SUBSTRATE -> {
            val polkadotSpecific = this.polkadotSpecific ?: error("Specific empty $this")
            BlockChainSpecific.Polkadot(
                recentBlockHash = polkadotSpecific.recentBlockHash,
                nonce = polkadotSpecific.nonce.toBigInteger(),
                currentBlockNumber = polkadotSpecific.currentBlockNumber.toBigInteger(),
                specVersion = polkadotSpecific.specVersion.toUInt(),
                transactionVersion = polkadotSpecific.transactionVersion.toUInt(),
                genesisHash = polkadotSpecific.genesisHash,
                gas = polkadotSpecific.gas.toULong(),
            )
        }

        TokenStandard.SOL -> {
            val solanaSpecific = this.solanaSpecific ?: error("Specific empty $this")
            BlockChainSpecific.Solana(
                recentBlockHash = solanaSpecific.recentBlockHash,
                priorityFee = solanaSpecific.priorityFee.toBigInteger(),
                fromAddressPubKey = solanaSpecific.fromAddressPubKey,
                toAddressPubKey = solanaSpecific.toAddressPubKey,
                programId = solanaSpecific.hasProgramId,
                priorityLimit = solanaSpecific.priorityLimit?.toBigInteger() ?: BigInteger.ZERO
            )
        }

        TokenStandard.THORCHAIN -> {
            val thorchainSpecific = this.thorchainSpecific
            thorchainSpecific?.let {
                return BlockChainSpecific.THORChain(
                    accountNumber = it.accountNumber.toBigInteger(),
                    sequence = it.sequence.toBigInteger(),
                    fee = it.fee.toBigInteger(),
                    isDeposit = it.isDeposit,
                    transactionType = getTransactionType(it.transactionType)
                )
            }
            val mayachainSpecific = this.mayachainSpecific
            mayachainSpecific?.let {
                return BlockChainSpecific.MayaChain(
                    accountNumber = it.accountNumber.toBigInteger(),
                    sequence = it.sequence.toBigInteger(),
                    isDeposit = it.isDeposit,
                )
            }
            error("Specific empty $this")
        }

        TokenStandard.UTXO -> {
            val utxoSpecific = this.utxoSpecific ?: error("Specific empty $this")
            BlockChainSpecific.UTXO(
                byteFee = utxoSpecific.byteFee.toBigInteger(),
                sendMaxAmount = utxoSpecific.sendMaxAmount,
            )
        }

        TokenStandard.SUI -> {
            val suiSpecific = this.suiSpecific ?: error("Specific empty $this")
            BlockChainSpecific.Sui(
                referenceGasPrice = suiSpecific.referenceGasPrice.toBigInteger(),
                gasBudget = suiSpecific.gasBudget.toBigInteger(),
                coins = suiSpecific.coins.map {
                    vultisig.keysign.v1.SuiCoin(
                        coinType = it.coinType,
                        coinObjectId = it.coinObjectId,
                        version = it.version,
                        balance = it.balance,
                        digest = it.digest,
                        previousTransaction = it.previousTransaction ?: "",
                    )
                }
            )
        }

        TokenStandard.TRC20 -> {
            val trc20Specific = this.tronSpecific ?: error("Specific empty $this")
            BlockChainSpecific.Tron(
                timestamp = trc20Specific.timestamp.toULong(),
                expiration = trc20Specific.expiration.toULong(),
                blockHeaderTimestamp = trc20Specific.blockHeaderTimestamp.toULong(),
                blockHeaderNumber = trc20Specific.blockHeaderNumber.toULong(),
                blockHeaderVersion = trc20Specific.blockHeaderVersion.toULong(),
                blockHeaderTxTrieRoot = trc20Specific.blockHeaderTxTrieRoot,
                blockHeaderParentHash = trc20Specific.blockHeaderParentHash,
                blockHeaderWitnessAddress = trc20Specific.blockHeaderWitnessAddress,
                gasFeeEstimation = trc20Specific.gasFeeEstimation.toULong()
            )
        }

    }
}

fun SwapPayload.toInternalSwapPayload(): com.vultisig.wallet.data.models.payload.SwapPayload {
    this.thorchainSwapPayload?.let {
        return com.vultisig.wallet.data.models.payload.SwapPayload.ThorChain(it.toInternalThorChainSwapPayload())
    }
    this.mayachainSwapPayload?.let {
        return com.vultisig.wallet.data.models.payload.SwapPayload.MayaChain(it.toInternalThorChainSwapPayload())
    }
    this.oneinchSwapPayload?.let{
        return com.vultisig.wallet.data.models.payload.SwapPayload.EVM(it.toInternalOneInchSwapPayload())
    }
    error("SwapPayload is nil")
}

fun ThorchainSwapPayload.toInternalThorChainSwapPayload(): com.vultisig.wallet.data.models.THORChainSwapPayload {
    return com.vultisig.wallet.data.models.THORChainSwapPayload(
        fromAddress = this.fromAddress,
        fromCoin = this.fromCoin.toInternalCoinPayload(),
        toCoin = this.toCoin.toInternalCoinPayload(),
        vaultAddress = this.vaultAddress,
        routerAddress = this.routerAddress,
        fromAmount = this.fromAmount.toBigInteger(),
        toAmountDecimal = this.toAmountDecimal.toBigDecimal(),
        toAmountLimit = this.toAmountLimit,
        streamingInterval = this.streamingInterval,
        streamingQuantity = this.streamingQuantity,
        expirationTime = this.expirationTime.toULong(),
        isAffiliate = this.isAffiliate,
    )
}
fun OneinchSwapPayload.toInternalOneInchSwapPayload(): com.vultisig.wallet.data.models.EVMSwapPayloadJson {
    return com.vultisig.wallet.data.models.EVMSwapPayloadJson(
        fromCoin = this.fromCoin.toInternalCoinPayload(),
        toCoin = this.toCoin.toInternalCoinPayload(),
        fromAmount = this.fromAmount.toBigInteger(),
        toAmountDecimal = this.toAmountDecimal.toBigDecimal(),
        quote = this.quote.toInternalOneInchQuote(),
        provider = this.provider,
    )
}
fun OneinchQuote.toInternalOneInchQuote(): EVMSwapQuoteJson {
    return EVMSwapQuoteJson(
        dstAmount = this.dstAmount,
        tx = this.tx.toInternalOneInchTransaction()
    )
}
fun OneinchTransaction.toInternalOneInchTransaction(): OneInchSwapTxJson {
    return OneInchSwapTxJson(
        from = this.from,
        to = this.to,
        gas = this.gas,
        data = this.data,
        value = this.value,
        gasPrice = this.gasPrice,
    )
}
fun getTransactionType(txType: Int): TransactionType {
    return when (txType) {
        0 -> TransactionType.TRANSACTION_TYPE_UNSPECIFIED
        1 -> TransactionType.TRANSACTION_TYPE_VOTE
        2 -> TransactionType.TRANSACTION_TYPE_PROPOSAL
        3 -> TransactionType.TRANSACTION_TYPE_IBC_TRANSFER
        4 -> TransactionType.TRANSACTION_TYPE_THOR_MERGE
        5 -> TransactionType.TRANSACTION_TYPE_THOR_UNMERGE
        6 -> TransactionType.TRANSACTION_TYPE_TON_DEPOSIT
        7 -> TransactionType.TRANSACTION_TYPE_TON_WITHDRAW
        8 -> TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT
        else -> error("Unknown transaction type: $txType")
    }
}