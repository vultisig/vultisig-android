package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.proto.Cosmos

@OptIn(ExperimentalStdlibApi::class)
class THORCHainHelper {

    fun getSwapPreSignedInputData(
        keysignPayload: KeysignPayload,
        input: Cosmos.SigningInput.Builder,
    ): ByteArray {
        val thorchainData = keysignPayload.blockChainSpecific as? BlockChainSpecific.THORChain
            ?: throw Exception("Invalid blockChainSpecific")
        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val inputData = input.apply {
            this.chainId = ThorChainHelper.THORCHAIN_NETWORK_ID
            this.publicKey = ByteString.copyFrom(publicKey.data())
            this.accountNumber = thorchainData.accountNumber.toLong()
            this.sequence = thorchainData.sequence.toLong()
            this.mode = Cosmos.BroadcastMode.SYNC
            this.fee = Cosmos.Fee.newBuilder().apply {
                this.gas = ThorChainHelper.THOR_CHAIN_GAS_UNIT
            }.build()
        }.build()
        return inputData.toByteArray()
    }

}