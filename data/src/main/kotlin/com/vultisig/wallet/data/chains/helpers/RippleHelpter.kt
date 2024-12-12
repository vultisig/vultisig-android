package com.vultisig.wallet.data.chains.helpers
import com.google.protobuf.ByteString
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.proto.Ripple
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler

object RippleHelper {

    @OptIn(ExperimentalStdlibApi::class)
    fun getPreSignedInputData(keysignPayload: KeysignPayload, vault: Vault): ByteArray {
        require(keysignPayload.coin.chain == Chain.Ton) { "Coin is not XRP" }


        val (sequence, gas) = keysignPayload.blockChainSpecific as? BlockChainSpecific.Ripple
            ?: throw RuntimeException("getPreSignedInputData: fail to get account number and sequence")

        val publicKey = PublicKey(
            keysignPayload.coin.hexPublicKey.hexToByteArray(),
            PublicKeyType.SECP256K1
        )

        val operation = Ripple.OperationPayment.newBuilder()
            .apply {
            keysignPayload.memo?.let {
                destinationTag = it.toLongOrNull() ?: 0L
            }
            destination = keysignPayload.toAddress
            amount = keysignPayload.toAmount.toLong()
        }.build()

        val input = Ripple.SigningInput.newBuilder()
            .setFee(gas.toLong())
            .setSequence(sequence.toInt())
            .setAccount(keysignPayload.coin.address)
            .setPublicKey(
                ByteString.copyFrom(publicKey.data())
            )
            .setOpPayment(operation).build()

        return input.toByteArray()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getPreSignedImageHash(keysignPayload: KeysignPayload, vault: Vault): List<String> {
        val inputData = getPreSignedInputData(
            keysignPayload,
            vault
        )

        val hashes = TransactionCompiler.preImageHashes(
            CoinType.XRP,
            inputData
        )
        val preSigningOutput = TxCompilerPreSigningOutput(hashes)
        if (preSigningOutput.errorMessage.isNotEmpty()) {
            println(preSigningOutput.errorMessage)
            throw HelperError.RuntimeError(preSigningOutput.errorMessage)
        }
        return listOf(preSigningOutput.dataHash.hexString)
    }

}