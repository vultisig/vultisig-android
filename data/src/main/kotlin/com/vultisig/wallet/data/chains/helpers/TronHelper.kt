@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.blockchain.tron.TRON_STAKING_MEMO_REGEX
import com.vultisig.wallet.data.blockchain.tron.TRON_WITHDRAW_EXPIRE_UNFREEZE_MEMO
import com.vultisig.wallet.data.common.toByteStringOrHex
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignatureWithRecoveryID
import com.vultisig.wallet.data.utils.Numeric
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Tron
import wallet.core.jni.proto.Tron.BlockHeader
import wallet.core.jni.proto.Tron.TransferTRC20Contract

class TronHelper(
    private val coinType: CoinType,
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {

    internal fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val tronSpecific =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Tron
                ?: throw IllegalArgumentException("Invalid blockChainSpecific")

        return when {
            keysignPayload.tronTransferContractPayload != null ->
                buildTronTransferContractPayload(keysignPayload, tronSpecific)
            keysignPayload.tronTriggerSmartContractPayload != null ->
                buildTronSmartContractPayload(keysignPayload, tronSpecific)
            keysignPayload.tronTransferAssetContractPayload != null ->
                buildTronTransferAssetSmartContractPayload(keysignPayload, tronSpecific)
            keysignPayload.coin.isNativeToken &&
                keysignPayload.coin.address == keysignPayload.toAddress &&
                keysignPayload.memo != null &&
                TRON_STAKING_MEMO_REGEX.matches(keysignPayload.memo) &&
                keysignPayload.memo.startsWith("FREEZE:") ->
                buildFreezeBalanceV2(keysignPayload, tronSpecific)
            keysignPayload.coin.isNativeToken &&
                keysignPayload.coin.address == keysignPayload.toAddress &&
                keysignPayload.memo != null &&
                TRON_STAKING_MEMO_REGEX.matches(keysignPayload.memo) &&
                keysignPayload.memo.startsWith("UNFREEZE:") ->
                buildUnfreezeBalanceV2(keysignPayload, tronSpecific)
            keysignPayload.coin.isNativeToken &&
                keysignPayload.coin.address == keysignPayload.toAddress &&
                keysignPayload.memo == TRON_WITHDRAW_EXPIRE_UNFREEZE_MEMO ->
                buildWithdrawExpireUnfreeze(keysignPayload, tronSpecific)
            keysignPayload.coin.isNativeToken &&
                keysignPayload.memo != null &&
                (TRON_STAKING_MEMO_REGEX.matches(keysignPayload.memo) ||
                    keysignPayload.memo == TRON_WITHDRAW_EXPIRE_UNFREEZE_MEMO) ->
                error("Staking memo requires destination address to match sender address")
            keysignPayload.coin.isNativeToken -> buildCoinTransfer(keysignPayload, tronSpecific)
            else -> buildTokenTransfer(keysignPayload, tronSpecific)
        }
    }

    private fun buildTronTransferAssetSmartContractPayload(
        keysignPayload: KeysignPayload,
        tronSpecific: BlockChainSpecific.Tron,
    ): ByteArray {
        val keySignTronContact = keysignPayload.tronTransferAssetContractPayload
        require(keySignTronContact != null) {
            "Empty payload for TronTransferAssetSmartContractPayload"
        }

        val contract =
            Tron.TransferAssetContract.newBuilder()
                .apply {
                    toAddress = keySignTronContact.toAddress
                    ownerAddress = keySignTronContact.ownerAddress
                    amount = keySignTronContact.amount.toLong()
                    assetName = keySignTronContact.assetName
                }
                .build()

        val txBuild =
            Tron.Transaction.newBuilder()
                .setFeeLimit(tronSpecific.gasFeeEstimation.toLong())
                .setTransferAsset(contract)
                .setTimestamp(tronSpecific.timestamp.toLong())
                .setBlockHeader(buildBlockHeader(tronSpecific))
                .setExpiration(tronSpecific.expiration.toLong())
        keysignPayload.memo?.let { memo -> txBuild.setMemo(memo) }

        val input = Tron.SigningInput.newBuilder().setTransaction(txBuild.build()).build()
        return input.toByteArray()
    }

    private fun buildTronSmartContractPayload(
        keysignPayload: KeysignPayload,
        tronSpecific: BlockChainSpecific.Tron,
    ): ByteArray {
        val keySignTronContact = keysignPayload.tronTriggerSmartContractPayload
        require(keySignTronContact != null) { "Empty payload for tronTriggerSmartContractPayload" }
        val contract =
            Tron.TriggerSmartContract.newBuilder()
                .apply {
                    this.contractAddress = keySignTronContact.contractAddress
                    this.ownerAddress = keySignTronContact.ownerAddress
                    keySignTronContact.data?.let {
                        data = keySignTronContact.data.toByteStringOrHex()
                    }
                    keySignTronContact.tokenId?.let {
                        tokenId = keySignTronContact.tokenId.toLong()
                    }
                    keySignTronContact.callValue?.let {
                        callValue = keySignTronContact.callValue.toLong()
                    }
                    keySignTronContact.callTokenValue?.let {
                        callTokenValue = keySignTronContact.callTokenValue.toLong()
                    }
                }
                .build()

        val txBuild =
            Tron.Transaction.newBuilder()
                .setFeeLimit(tronSpecific.gasFeeEstimation.toLong())
                .setTriggerSmartContract(contract)
                .setTimestamp(tronSpecific.timestamp.toLong())
                .setBlockHeader(buildBlockHeader(tronSpecific))
                .setExpiration(tronSpecific.expiration.toLong())
        keysignPayload.memo?.let { memo -> txBuild.setMemo(memo) }

        val input = Tron.SigningInput.newBuilder().setTransaction(txBuild.build()).build()
        return input.toByteArray()
    }

    private fun buildTokenTransfer(
        keysignPayload: KeysignPayload,
        tronSpecific: BlockChainSpecific.Tron,
    ): ByteArray {
        val contract =
            TransferTRC20Contract.newBuilder()
                .setToAddress(keysignPayload.toAddress)
                .setContractAddress(keysignPayload.coin.contractAddress)
                .setOwnerAddress(keysignPayload.coin.address)
                .setAmount(ByteString.copyFrom(keysignPayload.toAmount.toByteArray()))
                .build()
        val txBuild =
            Tron.Transaction.newBuilder()
                .setFeeLimit(tronSpecific.gasFeeEstimation.toLong())
                .setTransferTrc20Contract(contract)
                .setTimestamp(tronSpecific.timestamp.toLong())
                .setBlockHeader(
                    BlockHeader.newBuilder()
                        .setTimestamp(tronSpecific.blockHeaderTimestamp.toLong())
                        .setNumber(tronSpecific.blockHeaderNumber.toLong())
                        .setVersion(tronSpecific.blockHeaderVersion.toInt())
                        .setTxTrieRoot(
                            ByteString.copyFrom(
                                Numeric.hexStringToByteArray(tronSpecific.blockHeaderTxTrieRoot)
                            )
                        )
                        .setParentHash(
                            ByteString.copyFrom(
                                Numeric.hexStringToByteArray(tronSpecific.blockHeaderParentHash)
                            )
                        )
                        .setWitnessAddress(
                            ByteString.copyFrom(
                                Numeric.hexStringToByteArray(tronSpecific.blockHeaderWitnessAddress)
                            )
                        )
                        .build()
                )
                .setExpiration(tronSpecific.expiration.toLong())
        keysignPayload.memo?.let { memo -> txBuild.setMemo(memo) }
        val input = Tron.SigningInput.newBuilder().setTransaction(txBuild.build()).build()
        return input.toByteArray()
    }

    private fun buildTronTransferContractPayload(
        keysignPayload: KeysignPayload,
        tronSpecific: BlockChainSpecific.Tron,
    ): ByteArray {
        val keySignTronContact = keysignPayload.tronTransferContractPayload
        require(keySignTronContact != null) { "Empty payload for tronTransferContractPayload" }

        val contract =
            Tron.TransferContract.newBuilder()
                .setOwnerAddress(keySignTronContact.ownerAddress)
                .setToAddress(keySignTronContact.toAddress)
                .setAmount(keySignTronContact.amount.toLong())
                .build()

        val txBuild =
            Tron.Transaction.newBuilder()
                .setTransfer(contract)
                .setTimestamp(tronSpecific.timestamp.toLong())
                .setBlockHeader(buildBlockHeader(tronSpecific))
                .setExpiration(tronSpecific.expiration.toLong())
        keysignPayload.memo?.let { memo -> txBuild.setMemo(memo) }
        val input = Tron.SigningInput.newBuilder().setTransaction(txBuild.build()).build()
        return input.toByteArray()
    }

    // Kept uniquely for backwards compatibility with iOS
    // and other platforms/versions (since we have new payload)
    private fun buildCoinTransfer(
        keysignPayload: KeysignPayload,
        tronSpecific: BlockChainSpecific.Tron,
    ): ByteArray {
        val contract =
            Tron.TransferContract.newBuilder()
                .setOwnerAddress(keysignPayload.coin.address)
                .setToAddress(keysignPayload.toAddress)
                .setAmount(keysignPayload.toAmount.toLong())
                .build()
        val txBuild =
            Tron.Transaction.newBuilder()
                .setTransfer(contract)
                .setTimestamp(tronSpecific.timestamp.toLong())
                .setBlockHeader(
                    BlockHeader.newBuilder()
                        .setTimestamp(tronSpecific.blockHeaderTimestamp.toLong())
                        .setNumber(tronSpecific.blockHeaderNumber.toLong())
                        .setVersion(tronSpecific.blockHeaderVersion.toInt())
                        .setTxTrieRoot(
                            ByteString.copyFrom(
                                Numeric.hexStringToByteArray(tronSpecific.blockHeaderTxTrieRoot)
                            )
                        )
                        .setParentHash(
                            ByteString.copyFrom(
                                Numeric.hexStringToByteArray(tronSpecific.blockHeaderParentHash)
                            )
                        )
                        .setWitnessAddress(
                            ByteString.copyFrom(
                                Numeric.hexStringToByteArray(tronSpecific.blockHeaderWitnessAddress)
                            )
                        )
                        .build()
                )
                .setExpiration(tronSpecific.expiration.toLong())
        keysignPayload.memo?.let { memo -> txBuild.setMemo(memo) }
        val input = Tron.SigningInput.newBuilder().setTransaction(txBuild.build()).build()
        return input.toByteArray()
    }

    /**
     * Shared transaction builder for staking operations. Constructs a Tron transaction with common
     * timestamp, block header, expiration, and optional fee-limit fields, applying a
     * contract-setting lambda to the transaction builder. WithdrawExpireUnfreeze uses feeLimit = 0
     * since it consumes no chain resources; other staking operations use the estimated gas fee.
     */
    private inline fun buildStakingTransaction(
        tronSpecific: BlockChainSpecific.Tron,
        feeLimit: Long = tronSpecific.gasFeeEstimation.toLong(),
        contractSetter: (Tron.Transaction.Builder) -> Tron.Transaction.Builder,
    ): ByteArray {
        val txBuild =
            contractSetter(Tron.Transaction.newBuilder())
                .setTimestamp(tronSpecific.timestamp.toLong())
                .setBlockHeader(buildBlockHeader(tronSpecific))
                .setExpiration(tronSpecific.expiration.toLong())
                .setFeeLimit(feeLimit)

        val input = Tron.SigningInput.newBuilder().setTransaction(txBuild.build()).build()
        return input.toByteArray()
    }

    private fun buildFreezeBalanceV2(
        keysignPayload: KeysignPayload,
        tronSpecific: BlockChainSpecific.Tron,
    ): ByteArray {
        val memo = keysignPayload.memo ?: error("FREEZE memo required")
        val resource = memo.removePrefix("FREEZE:")
        require(resource == "BANDWIDTH" || resource == "ENERGY") {
            "Invalid TRON resource type: $resource"
        }

        val contract =
            Tron.FreezeBalanceV2Contract.newBuilder()
                .setOwnerAddress(keysignPayload.coin.address)
                .setFrozenBalance(keysignPayload.toAmount.toLong())
                .setResource(resource)
                .build()

        return buildStakingTransaction(tronSpecific) { it.setFreezeBalanceV2(contract) }
    }

    private fun buildUnfreezeBalanceV2(
        keysignPayload: KeysignPayload,
        tronSpecific: BlockChainSpecific.Tron,
    ): ByteArray {
        val memo = keysignPayload.memo ?: error("UNFREEZE memo required")
        val resource = memo.removePrefix("UNFREEZE:")
        require(resource == "BANDWIDTH" || resource == "ENERGY") {
            "Invalid TRON resource type: $resource"
        }

        val contract =
            Tron.UnfreezeBalanceV2Contract.newBuilder()
                .setOwnerAddress(keysignPayload.coin.address)
                .setUnfreezeBalance(keysignPayload.toAmount.toLong())
                .setResource(resource)
                .build()

        return buildStakingTransaction(tronSpecific) { it.setUnfreezeBalanceV2(contract) }
    }

    /**
     * Claims TRX whose unlock period has elapsed back into the spendable balance.
     * `UnfreezeBalanceV2` only moves it out of `frozenV2`; the chain returns it on this contract
     * alone.
     *
     * The contract carries nothing but the owner — it withdraws every expired entry at once — so
     * the payload's `toAmount` stays display-only and never reaches the signed bytes.
     */
    private fun buildWithdrawExpireUnfreeze(
        keysignPayload: KeysignPayload,
        tronSpecific: BlockChainSpecific.Tron,
    ): ByteArray {
        val contract =
            Tron.WithdrawExpireUnfreezeContract.newBuilder()
                .setOwnerAddress(keysignPayload.coin.address)
                .build()

        return buildStakingTransaction(tronSpecific, feeLimit = 0L) {
            it.setWithdrawExpireUnfreeze(contract)
        }
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val result = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, result)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val tronPublicKey =
            PublicKeyHelper.getDerivedPublicKey(
                vaultHexPublicKey,
                vaultHexChainCode,
                coinType.derivationPath(),
            )
        val inputData = getPreSignedInputData(keysignPayload)
        val publicKey =
            PublicKey(tronPublicKey.hexToByteArray(), PublicKeyType.SECP256K1).uncompressed()
        val preHashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(preHashes)
                .checkError()
        val allSignatures = DataVector()
        val allPublicKeys = DataVector()
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray())

        val signature =
            signatures[key]?.getSignatureWithRecoveryID() ?: error("Signature not found")
        if (!publicKey.verify(signature, preSigningOutput.dataHash.toByteArray())) {
            error("Signature verification failed")
        }
        allSignatures.add(signature)
        allPublicKeys.add(publicKey.data())

        val compileWithSignature =
            TransactionCompiler.compileWithSignatures(
                coinType,
                inputData,
                allSignatures,
                allPublicKeys,
            )
        val output = Tron.SigningOutput.parseFrom(compileWithSignature).checkError()

        return SignedTransactionResult(
            rawTransaction = output.json,
            transactionHash = Numeric.toHexStringNoPrefix(output.id.toByteArray()),
        )
    }

    private fun buildBlockHeader(tronSpecific: BlockChainSpecific.Tron): BlockHeader {
        return BlockHeader.newBuilder()
            .setTimestamp(tronSpecific.blockHeaderTimestamp.toLong())
            .setNumber(tronSpecific.blockHeaderNumber.toLong())
            .setVersion(tronSpecific.blockHeaderVersion.toInt())
            .setTxTrieRoot(
                ByteString.copyFrom(
                    Numeric.hexStringToByteArray(tronSpecific.blockHeaderTxTrieRoot)
                )
            )
            .setParentHash(
                ByteString.copyFrom(
                    Numeric.hexStringToByteArray(tronSpecific.blockHeaderParentHash)
                )
            )
            .setWitnessAddress(
                ByteString.copyFrom(
                    Numeric.hexStringToByteArray(tronSpecific.blockHeaderWitnessAddress)
                )
            )
            .build()
    }

    companion object {
        internal const val TRON_DEFAULT_ESTIMATION_FEE = 800_000L
    }
}
