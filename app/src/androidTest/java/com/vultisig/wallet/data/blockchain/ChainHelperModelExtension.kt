package com.vultisig.wallet.data.blockchain

import BlockchainSpecific
import Coin
import KeysignPayload
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import vultisig.keysign.v1.CosmosIbcDenomTrace
import vultisig.keysign.v1.TransactionType
import java.math.BigInteger

fun KeysignPayload.toInternalKeySignPayload(): com.vultisig.wallet.data.models.payload.KeysignPayload {
    val coin = this.coin.toInternalCoinPayload()

    return com.vultisig.wallet.data.models.payload.KeysignPayload(
        coin = coin,
        toAddress = this.toAddress,
        toAmount = this.toAmount.toBigInteger(),
        blockChainSpecific = this.blockchainSpecific.toBlockChainSpecific(coin,this.toAddress),
        utxos = emptyList(),
        memo = this.memo,
        vaultPublicKeyECDSA = this.vaultPublicKeyEcdsa,
        vaultLocalPartyID = "",
        libType = SigningLibType.valueOf(this.libType),
        wasmExecuteContractPayload = null, // Not present in source, handled as null
        skipBroadcast = false // Not present in source, handled as default
    )
}

fun Coin.toInternalCoinPayload(): com.vultisig.wallet.data.models.Coin {
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
                gasLimit = BigInteger(ethereumSpecific.gasLimit)
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
                sendMaxAmount = tonSpecific.senMaxAmount,
                expireAt = tonSpecific.expireAt.toULong(),
                bounceable = tonSpecific.bounceable,
                sequenceNumber = tonSpecific.sequenceNumber.toULong(),
            )
        }

        TokenStandard.SOL -> {
            val solanaSpecific = this.solanaSpecific ?: error("Specific empty $this")
            BlockChainSpecific.Solana(
                recentBlockHash = solanaSpecific.recentBlockHash,
                priorityFee = solanaSpecific.priorityFee.toBigInteger(),
                fromAddressPubKey = coin.address,
                toAddressPubKey = toAddress,
                programId = solanaSpecific.hasProgramId,
            )
        }

        TokenStandard.THORCHAIN -> {

        }

        TokenStandard.UTXO -> TODO()
        TokenStandard.SUBSTRATE -> TODO()
        TokenStandard.SUI -> TODO()
        TokenStandard.TRC20 -> TODO()
    }
}