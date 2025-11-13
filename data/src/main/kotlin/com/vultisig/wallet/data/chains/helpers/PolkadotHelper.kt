package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.common.toHexByteArray
import com.vultisig.wallet.data.common.toHexBytesInByteString
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PrivateKey
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Polkadot

class PolkadotHelper(
    private val vaultHexPublicKey: String,
) {
    private val coinType = CoinType.POLKADOT

    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val polkadotSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Polkadot
            ?: throw IllegalArgumentException("Invalid blockChainSpecific")
        val toAddress = AnyAddress(keysignPayload.toAddress, coinType)

        // After Asset Hub update, even native DOT transfers use assetTransfer
        // with assetID 0 and feeAssetID 0 for native DOT
        // When asset_id is 0, WalletCore encodes it as TransferAllowDeath (Balances.transfer)
        // So we need Balances pallet call indices, not Assets pallet
        // For Asset Hub, Balances pallet is typically module 10, method 0 (transfer_allow_death)
        val assetTransfer = Polkadot.Balance.AssetTransfer.newBuilder()
            .setAssetId(0)
            .setFeeAssetId(0)
            .setToAddress(toAddress.description())
            .setValue(ByteString.copyFrom(keysignPayload.toAmount.toByteArray()))
            .setCallIndices(
                Polkadot.CallIndices
                    .newBuilder()
                    .setCustom(
                        Polkadot.CustomCallIndices.newBuilder()
                            .setMethodIndex(0)
                            .setModuleIndex(10)
                            .build()
                    )
                    .build()
            ).build()

        val balanceTransfer = Polkadot.Balance.newBuilder()
            .setAssetTransfer(assetTransfer)
            .build()

        val input = Polkadot.SigningInput.newBuilder()
            .setGenesisHash(polkadotSpecific.genesisHash.toHexBytesInByteString())
            .setBlockHash(polkadotSpecific.recentBlockHash.toHexBytesInByteString())
            .setNonce(polkadotSpecific.nonce.toLong())
            .setSpecVersion(polkadotSpecific.specVersion.toInt())
            .setNetwork(coinType.ss58Prefix())
            .setTransactionVersion(polkadotSpecific.transactionVersion.toInt())
            .setEra(
                Polkadot.Era.newBuilder()
                    .setBlockNumber(polkadotSpecific.currentBlockNumber.toLong())
                    .setPeriod(64)
                    .build()
            )
            .setBalanceCall(balanceTransfer)
            .build()
        return input.toByteArray()
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val result = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, result)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        if (!preSigningOutput.errorMessage.isNullOrEmpty()) {
            throw Exception(preSigningOutput.errorMessage)
        }
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
    }

    fun getZeroSignedTransaction(keysignPayload: KeysignPayload): String {
        val dummyPublicKey = PrivateKey().publicKeyEd25519

        val inputData = getPreSignedInputData(keysignPayload)

        val allSignatures = DataVector()
        val publicKeys = DataVector()

        // Add a dummy ED25519 signature (64 bytes of zeros) - we still use zeros for the signature
        // but use the dummy public key so the transaction structure is valid
        allSignatures.add("0".repeat(128).toHexByteArray())
        publicKeys.add(dummyPublicKey.data())

        // Compile with the dummy signature
        val compiledWithSignature =
            TransactionCompiler.compileWithSignatures(
                coinType,
                inputData,
                allSignatures,
                publicKeys
            )
        val output = Polkadot.SigningOutput.parseFrom(compiledWithSignature)
            .checkError()

        return Numeric.toHexStringNoPrefix(output.encoded.toByteArray())
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val publicKey = PublicKey(vaultHexPublicKey.toHexByteArray(), PublicKeyType.ED25519)
        val input = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, input)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray())
        val allSignatures = DataVector()
        val publicKeys = DataVector()
        val signature = signatures[key]?.getSignature() ?: throw Exception("Signature not found")
        if (!publicKey.verify(signature, preSigningOutput.data.toByteArray())) {
            throw Exception("Signature verification failed")
        }
        allSignatures.add(signature)
        publicKeys.add(publicKey.data())
        val compiledWithSignature =
            TransactionCompiler.compileWithSignatures(coinType, input, allSignatures, publicKeys)
        val output = Polkadot.SigningOutput.parseFrom(compiledWithSignature)
            .checkError()

        return SignedTransactionResult(
            rawTransaction = Numeric.toHexStringNoPrefix(
                output.encoded.toByteArray()
            ),
            transactionHash = Numeric.toHexString(
                Utils.blake2bHash(
                    output.encoded.toByteArray().take(32).toByteArray()
                )
            )
        )
    }

    companion object {
        const val DEFAULT_FEE_PLANCKS = 250_000_000L
        const val DEFAULT_EXISTENTIAL_DEPOSIT = 10_000_000_000L // 1 DOT
    }
}