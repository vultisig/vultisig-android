package com.vultisig.wallet.data.blockchain

import BlockchainSpecific
import Coin
import KeysignPayload
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import java.math.BigInteger

fun KeysignPayload.toInternalKeySignPayload(): com.vultisig.wallet.data.models.payload.KeysignPayload {
    return com.vultisig.wallet.data.models.payload.KeysignPayload(
        coin = this.coin.toInternalCoinPayload(),
        toAddress = this.toAddress,
        toAmount = this.toAmount.toBigInteger(),
        blockChainSpecific = this.blockchainSpecific.toBlockChainSpecific(),
        utxos = emptyList(),
        memo = this.swapPayload,
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
        isNativeToken = this.contractAddress.isNullOrEmpty(),
    )
}

fun BlockchainSpecific.toBlockChainSpecific(): BlockChainSpecific {
    return BlockChainSpecific.Ethereum(
        maxFeePerGasWei = BigInteger(this.ethereumSpecific.maxFeePerGasWei),
        priorityFeeWei = BigInteger(this.ethereumSpecific.priorityFee),
        nonce = BigInteger.valueOf(this.ethereumSpecific.nonce.toLong()),
        gasLimit = BigInteger(this.ethereumSpecific.gasLimit)
    )
}