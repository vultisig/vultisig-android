package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.proto.Cosmos
import java.math.BigInteger

@OptIn(ExperimentalStdlibApi::class)
class ThorchainSwapHelper {
    private fun getTicker(coin: Coin): String {
        return  if(coin.ticker.startsWith("x/",true)) {
                coin.ticker.drop(2)
            } else {
                coin.ticker
            }
    }
    fun getSwapPreSignedInputData(
        keysignPayload: KeysignPayload
    ): ByteArray {
        val thorchainData = keysignPayload.blockChainSpecific as? BlockChainSpecific.THORChain
            ?: throw Exception("Invalid blockChainSpecific")
        val publicKey =
            PublicKey(keysignPayload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        require(!keysignPayload.memo.isNullOrEmpty()) {
            "Memo is required for THORChain swap"
        }

        val thorChainSwapPayload = keysignPayload.swapPayload as? SwapPayload.ThorChain
            ?: throw Exception("Invalid swap payload for THORChain")

        val fromAddress = AnyAddress(thorChainSwapPayload.srcToken.address, CoinType.THORCHAIN).data()
        val coin = Cosmos.THORChainCoin.newBuilder()
            .setAsset(
                Cosmos.THORChainAsset.newBuilder()
                    .setChain("THOR")
                    .setSymbol(getTicker(keysignPayload.coin))
                    .setTicker(getTicker(keysignPayload.coin))
                    .setSynth(false)
                    .build()
            )
            .let {
                if (thorChainSwapPayload.data.fromAmount > BigInteger.ZERO) {
                    it.setAmount(thorChainSwapPayload.data.fromAmount.toString())
                        .setDecimals(keysignPayload.coin.decimal.toLong())
                } else it
            }.build()

        val depositMsg = Cosmos.Message.newBuilder().apply {
            thorchainDepositMessage = Cosmos.Message.THORChainDeposit.newBuilder().apply {
                this.signer = ByteString.copyFrom(fromAddress)
                this.memo = keysignPayload.memo
                this.addCoins(coin)
            }.build()
        }.build()
        val inputData = Cosmos.SigningInput.newBuilder().apply {
            this.chainId = ThorChainHelper.THORCHAIN_NETWORK_ID
            this.publicKey = ByteString.copyFrom(publicKey.data())
            this.accountNumber = thorchainData.accountNumber.toLong()
            this.sequence = thorchainData.sequence.toLong()
            this.mode = Cosmos.BroadcastMode.SYNC
            this.signingMode = Cosmos.SigningMode.Protobuf
            this.fee = Cosmos.Fee.newBuilder().apply {
                this.gas = ThorChainHelper.THOR_CHAIN_GAS_UNIT
            }.build()
            this.addAllMessages(listOf(depositMsg))
        }.build()
        return inputData.toByteArray()
    }
}