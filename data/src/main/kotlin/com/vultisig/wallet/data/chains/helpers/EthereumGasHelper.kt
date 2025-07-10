package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import wallet.core.jni.CoinType
import wallet.core.jni.proto.Ethereum
import java.math.BigInteger

object EthereumGasHelper {
    fun requireEthereumSpec(spec: BlockChainSpecific): BlockChainSpecific.Ethereum =
        spec as? BlockChainSpecific.Ethereum
            ?: error("BlockChainSpecific is not Ethereum for EVM chain")

    fun setGasParameters(
        gas: BigInteger,
        gasPrice: BigInteger,
        signingInput: Ethereum.SigningInput.Builder,
        keysignPayload: KeysignPayload,
        nonceIncrement: BigInteger = BigInteger.ZERO,
        coinType: CoinType
    ): Ethereum.SigningInput.Builder {
        val ethSpecifc = requireEthereumSpec(keysignPayload.blockChainSpecific)
        val signingInputBuilder = signingInput.apply {
            chainId = ByteString.copyFrom(BigInteger(coinType.chainId()).toByteArray())
            nonce = ByteString.copyFrom((ethSpecifc.nonce + nonceIncrement).toByteArray())
        }
        if(keysignPayload.coin.chain == Chain.BscChain) {
            signingInputBuilder.apply  {
                txMode = Ethereum.TransactionMode.Legacy
                setGasPrice(ByteString.copyFrom(gasPrice.toByteArray()))
                gasLimit =ByteString.copyFrom(gas.toByteArray())
            }
        } else {
            signingInputBuilder.apply {
                txMode = Ethereum.TransactionMode.Enveloped
                gasLimit = ByteString.copyFrom(ethSpecifc.gasLimit.toByteArray())
                maxFeePerGas = ByteString.copyFrom(ethSpecifc.maxFeePerGasWei.toByteArray())
                maxInclusionFeePerGas = ByteString.copyFrom(ethSpecifc.priorityFeeWei.toByteArray())
            }
        }
        return signingInputBuilder
    }
}