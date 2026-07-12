package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingOpType
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingPayload
import com.vultisig.wallet.data.common.toHexByteArray
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import io.ktor.util.decodeBase64Bytes
import java.math.BigInteger
import wallet.core.jni.AnyAddress
import wallet.core.jni.Base58
import wallet.core.jni.Base64
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.SolanaAddress
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Solana

internal const val SOLANA_PRIORITY_FEE_PRICE = 1000000L
internal const val SOLANA_PRIORITY_FEE_LIMIT = 100000

const val SOLANA_DEFAULT_CONTRACT_ADDRESS = "So11111111111111111111111111111111111111112"

/**
 * Prefix of the error thrown when the sender has no associated token account for an SPL transfer.
 * The coin ticker follows the prefix so the keysign error screen can name the token in a localized
 * message; keep it in sync with the `KeysignErrorScreen` branch that matches on it.
 */
const val SOLANA_MISSING_TOKEN_ACCOUNT_PREFIX =
    "SPL token transfer failed: missing associated token account for "

class SolanaHelper(private val vaultHexPublicKey: String) {

    private val coinType = CoinType.SOLANA

    companion object {
        val DefaultFeeInLamports: BigInteger = 1000000.toBigInteger()

        /** Byte length of an ed25519 signature, and of each slot in a Solana signature array. */
        private const val SIGNATURE_LENGTH = 64

        /**
         * The Solana transaction id is the first signature in the signed transaction. WalletCore
         * populates [Solana.SigningOutput.getSignaturesList] (base58) on the
         * `compileWithSignatures` path, so read it directly instead of re-deriving it from the
         * encoded transaction.
         */
        internal fun Solana.SigningOutput.transactionHash(): String =
            signaturesList.firstOrNull()?.signature
                ?: error("Signed Solana transaction has no signature")
    }

    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val solanaSpecific =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Solana
                ?: error("Invalid blockChainSpecific")
        if (keysignPayload.coin.chain != Chain.Solana) {
            error("Chain is not Solana")
        }
        if (
            !keysignPayload.coin.isNativeToken && solanaSpecific.fromAddressPubKey.isNullOrEmpty()
        ) {
            error("$SOLANA_MISSING_TOKEN_ACCOUNT_PREFIX${keysignPayload.coin.ticker}")
        }
        val toAddress = AnyAddress(keysignPayload.toAddress, coinType)

        val input =
            Solana.SigningInput.newBuilder()
                .setV0Msg(true)
                .setRecentBlockhash(solanaSpecific.recentBlockHash)
                .setSender(keysignPayload.coin.address)
                .applyPriorityFee(solanaSpecific.priorityFee, solanaSpecific.priorityLimit)

        if (keysignPayload.coin.isNativeToken) {
            val transfer =
                Solana.Transfer.newBuilder()
                    .setRecipient(toAddress.description())
                    .setValue(keysignPayload.toAmount.toLong())
            keysignPayload.memo?.let { transfer.setMemo(it) }

            return input.setTransferTransaction(transfer.build()).build().toByteArray()
        } else {
            if (!solanaSpecific.toAddressPubKey.isNullOrEmpty()) {
                val transfer =
                    Solana.TokenTransfer.newBuilder()
                        .setTokenMintAddress(keysignPayload.coin.contractAddress)
                        .setSenderTokenAddress(solanaSpecific.fromAddressPubKey)
                        .setRecipientTokenAddress(solanaSpecific.toAddressPubKey)
                        .setAmount(keysignPayload.toAmount.toLong())
                        .setDecimals(keysignPayload.coin.decimal)
                        .setTokenProgramIdValue(if (solanaSpecific.programId == true) 1 else 0)
                keysignPayload.memo?.let { transfer.setMemo(it) }

                return input.setTokenTransferTransaction(transfer.build()).build().toByteArray()
            } else {
                val receiverAddress = SolanaAddress(toAddress.description())
                val generatedRecipientAssociatedAddress =
                    if (solanaSpecific.programId == true) {
                        receiverAddress.token2022Address(keysignPayload.coin.contractAddress)
                    } else {
                        receiverAddress.defaultTokenAddress(keysignPayload.coin.contractAddress)
                    }
                val transferTokenMessage =
                    Solana.CreateAndTransferToken.newBuilder()
                        .setRecipientMainAddress(toAddress.description())
                        .setTokenMintAddress(keysignPayload.coin.contractAddress)
                        .setRecipientTokenAddress(generatedRecipientAssociatedAddress)
                        .setSenderTokenAddress(solanaSpecific.fromAddressPubKey)
                        .setAmount(keysignPayload.toAmount.toLong())
                        .setDecimals(keysignPayload.coin.decimal)
                        .setTokenProgramIdValue(if (solanaSpecific.programId == true) 1 else 0)
                keysignPayload.memo?.let { transferTokenMessage.setMemo(it) }

                return input
                    .setCreateAndTransferTokenTransaction(transferTokenMessage.build())
                    .build()
                    .toByteArray()
            }
        }
    }

    /**
     * Builds the unsigned bytes for a native-staking op (delegate / deactivate / withdraw) and
     * returns them base64-encoded — the encoding `SignSolana.rawTransactions` and the raw-signing
     * path ([signRawTransaction]) consume.
     *
     * This is the MPC byte-parity contract for native staking: the initiating device builds these
     * bytes ONCE — pinning [recentBlockHash] and, for delegate, letting wallet-core derive the
     * stake-account address deterministically from sender + blockhash (so [SolanaStakingPayload]
     * omits it) — then relays the bytes to the peer. Both devices sign the byte-identical message
     * through the raw-transaction path, so the local-only [payload] never has to reach the peer.
     *
     * @param payload the local-only staking intent (validator + amount, or stake account)
     * @param senderAddress base58 SOL address of the signer (the stake authority / funder)
     * @param coinHexPublicKey the signer's ed25519 public key (hex), for the zero-signature
     *   envelope
     * @param recentBlockHash the pinned recent blockhash
     * @param priorityFeePrice micro-lamports-per-CU price
     * @param priorityFeeLimit compute-unit limit
     */
    fun buildStakingUnsignedTransaction(
        payload: SolanaStakingPayload,
        senderAddress: String,
        coinHexPublicKey: String,
        recentBlockHash: String,
        priorityFeePrice: BigInteger,
        priorityFeeLimit: BigInteger,
    ): String {
        val input =
            getStakingPreSignedInputData(
                payload = payload,
                senderAddress = senderAddress,
                recentBlockHash = recentBlockHash,
                priorityFeePrice = priorityFeePrice,
                priorityFeeLimit = priorityFeeLimit,
            )
        val publicKey = PublicKey(coinHexPublicKey.toHexByteArray(), PublicKeyType.ED25519)
        val allSignatures = DataVector()
        val publicKeys = DataVector()
        allSignatures.add("0".repeat(128).toHexByteArray())
        publicKeys.add(publicKey.data())
        val compiled =
            TransactionCompiler.compileWithSignatures(coinType, input, allSignatures, publicKeys)
        val output = Solana.SigningOutput.parseFrom(compiled).checkError()
        // WalletCore emits base58 (the signing input never sets txEncoding). Normalize to base64 —
        // the encoding the raw-transaction signing path consumes.
        return Base64.encode(Base58.decodeNoCheck(output.encoded))
    }

    /**
     * Applies the clamped priority-fee price + compute-unit limit shared by the transfer and
     * staking signing inputs. Price is floored at [SOLANA_PRIORITY_FEE_PRICE] and both values are
     * clamped to their proto field widths (price → Long, limit → Int).
     */
    private fun Solana.SigningInput.Builder.applyPriorityFee(
        price: BigInteger,
        limit: BigInteger,
    ): Solana.SigningInput.Builder =
        setPriorityFeePrice(
                Solana.PriorityFeePrice.newBuilder()
                    .setPrice(
                        maxOf(
                            price.min(BigInteger.valueOf(Long.MAX_VALUE)).toLong(),
                            SOLANA_PRIORITY_FEE_PRICE,
                        )
                    )
                    .build()
            )
            .setPriorityFeeLimit(
                Solana.PriorityFeeLimit.newBuilder()
                    .setLimit(limit.min(BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt())
                    .build()
            )

    private fun getStakingPreSignedInputData(
        payload: SolanaStakingPayload,
        senderAddress: String,
        recentBlockHash: String,
        priorityFeePrice: BigInteger,
        priorityFeeLimit: BigInteger,
    ): ByteArray {
        val input =
            Solana.SigningInput.newBuilder()
                .setV0Msg(true)
                .setRecentBlockhash(recentBlockHash)
                .setSender(senderAddress)
                .applyPriorityFee(priorityFeePrice, priorityFeeLimit)

        return when (payload.opType) {
            SolanaStakingOpType.Delegate -> {
                val votePubkey =
                    payload.votePubkey?.takeIf { it.isNotEmpty() }
                        ?: error("solana delegate: missing validator vote pubkey")
                require(AnyAddress.isValid(votePubkey, coinType)) {
                    "solana delegate: invalid validator vote pubkey"
                }
                val existingAccount = payload.stakeAccount?.takeIf { it.isNotEmpty() }
                val delegate =
                    Solana.DelegateStake.newBuilder()
                        .setValidatorPubkey(votePubkey)
                        .apply {
                            if (existingAccount != null) {
                                // Move-stake "Finish Move": re-delegate the existing (cooled-down)
                                // account in place. It already holds its lamports on-chain, so
                                // `value` is NOT a funding amount — leave it 0 so wallet-core can
                                // never interpret it as lamports to move from the wallet. Matches
                                // the resolver's finish-move funding guard, which reserves only the
                                // fee.
                                setStakeAccount(existingAccount)
                            } else {
                                // Fresh stake: wallet-core derives the account deterministically
                                // and
                                // emits create + initialize + delegate in one tx, funding the new
                                // account with `value` lamports.
                                val lamports =
                                    payload.lamports?.takeIf { it.signum() > 0 }
                                        ?: error(
                                            "solana delegate: missing or zero delegation amount"
                                        )
                                setValue(lamports.toLong())
                            }
                        }
                        .build()
                input.setDelegateStakeTransaction(delegate).build().toByteArray()
            }
            SolanaStakingOpType.Unstake -> {
                val stakeAccount = validatedStakeAccount(payload.stakeAccount, "deactivate")
                val deactivate =
                    Solana.DeactivateStake.newBuilder().setStakeAccount(stakeAccount).build()
                input.setDeactivateStakeTransaction(deactivate).build().toByteArray()
            }
            SolanaStakingOpType.Withdraw -> {
                val stakeAccount = validatedStakeAccount(payload.stakeAccount, "withdraw")
                val lamports =
                    payload.lamports?.takeIf { it.signum() > 0 }
                        ?: error("solana withdraw: missing or zero withdrawal amount")
                val withdraw =
                    Solana.WithdrawStake.newBuilder()
                        .setStakeAccount(stakeAccount)
                        .setValue(lamports.toLong())
                        .build()
                input.setWithdrawTransaction(withdraw).build().toByteArray()
            }
        }
    }

    private fun validatedStakeAccount(stakeAccount: String?, op: String): String {
        val account =
            stakeAccount?.takeIf { it.isNotEmpty() } ?: error("solana $op: missing stake account")
        require(AnyAddress.isValid(account, coinType)) {
            "solana $op: invalid stake account address"
        }
        return account
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        keysignPayload.signSolana?.let { signSolana ->
            val allHashes = mutableListOf<String>()
            for (base64Tx in signSolana.rawTransactions) {
                val hashes = getPreSignedImageHashForRaw(base64Tx)
                allHashes.addAll(hashes)
            }
            return allHashes
        }

        val result = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, result)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        if (!preSigningOutput.errorMessage.isNullOrEmpty()) {
            error(preSigningOutput.errorMessage)
        }
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray()))
    }

    /**
     * Assembles one signed transaction per raw transaction in a dApp `signSolana` payload. A
     * `signAndSendAllTransactions` batch is hashed and signed in a single keysign ceremony, so
     * assembly must deliver every transaction rather than just the first (issue #5238). A payload
     * without `signSolana` assembles the single WalletCore-built transaction.
     */
    fun getSignedTransactions(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): List<SignedTransactionResult> {
        val signSolana =
            keysignPayload.signSolana
                ?: return listOf(getSignedTransaction(keysignPayload, signatures))
        require(signSolana.rawTransactions.isNotEmpty()) {
            "signSolana payload carries no raw transactions"
        }
        return signSolana.rawTransactions.map { base64Tx ->
            signRawTransaction(
                coinHexPubKey = keysignPayload.coin.hexPublicKey,
                base64Transaction = base64Tx,
                signatures = signatures,
            )
        }
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        keysignPayload.signSolana?.let { signSolana ->
            require(signSolana.rawTransactions.size == 1) {
                "Expected exactly one Solana raw transaction"
            }

            return signRawTransaction(
                coinHexPubKey = keysignPayload.coin.hexPublicKey,
                base64Transaction = signSolana.rawTransactions.first(),
                signatures = signatures,
            )
        }

        val publicKey = PublicKey(vaultHexPublicKey.toHexByteArray(), PublicKeyType.ED25519)
        val input = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, input)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray())
        val allSignatures = DataVector()
        val publicKeys = DataVector()
        val signature = signatures[key]?.getSignature() ?: error("Signature not found")
        if (!publicKey.verify(signature, preSigningOutput.data.toByteArray())) {
            error("Signature verification failed")
        }
        allSignatures.add(signature)
        publicKeys.add(publicKey.data())
        val compiledWithSignature =
            TransactionCompiler.compileWithSignatures(coinType, input, allSignatures, publicKeys)
        val output = Solana.SigningOutput.parseFrom(compiledWithSignature).checkError()
        return SignedTransactionResult(
            rawTransaction = output.encoded,
            transactionHash = output.transactionHash(),
        )
    }

    fun getSwapPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        require(!keysignPayload.memo.isNullOrEmpty()) {
            "THORChain swap memo must not be null or empty for Solana swap transactions"
        }
        return getPreSignedInputData(keysignPayload)
    }

    fun getSwapSignedTransaction(
        inputData: ByteArray,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val publicKey = PublicKey(vaultHexPublicKey.toHexByteArray(), PublicKeyType.ED25519)

        val preHashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(preHashes)
                .checkError()

        val allSignatures = DataVector()
        val allPublicKeys = DataVector()
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray())

        val signature = signatures[key]?.getSignature() ?: error("Signature not found")

        val verified = publicKey.verify(signature, (preSigningOutput.data).toByteArray())
        if (!verified) {
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

        val output = Solana.SigningOutput.parseFrom(compileWithSignature).checkError()
        return SignedTransactionResult(
            rawTransaction = output.encoded,
            transactionHash = output.transactionHash(),
        )
    }

    fun getZeroSignedTransaction(keysignPayload: KeysignPayload): String {
        val publicKey = PublicKey(vaultHexPublicKey.toHexByteArray(), PublicKeyType.ED25519)
        val input = getPreSignedInputData(keysignPayload)
        val allSignatures = DataVector()
        val publicKeys = DataVector()
        allSignatures.add("0".repeat(128).toHexByteArray())
        publicKeys.add(publicKey.data())
        val compiledWithSignature =
            TransactionCompiler.compileWithSignatures(coinType, input, allSignatures, publicKeys)
        val output = Solana.SigningOutput.parseFrom(compiledWithSignature).checkError()
        return output.encoded
    }

    fun getVersionedMessage(keysignPayload: KeysignPayload): String {
        val input = getPreSignedInputData(keysignPayload)
        val preHashes = TransactionCompiler.preImageHashes(coinType, input)
        val dataMessage =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(preHashes)
                .checkError()
                .data
                .toByteArray()

        return Base64.encode(dataMessage)
    }

    private fun getPreSignedImageHashForRaw(base64Transaction: String): List<String> =
        listOf(Numeric.toHexStringNoPrefix(parseRawTransaction(base64Transaction).message))

    private fun signRawTransaction(
        coinHexPubKey: String,
        base64Transaction: String,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val pubkeyData = coinHexPubKey.toHexByteArray()
        val publicKey = PublicKey(pubkeyData, PublicKeyType.ED25519)

        val transaction = parseRawTransaction(base64Transaction)
        val key = Numeric.toHexStringNoPrefix(transaction.message)
        val signature = signatures[key]?.getSignature() ?: error("Signature not found")
        if (!publicKey.verify(signature, transaction.message)) {
            error("Signature verification failed")
        }

        // Splice signer 0's signature into signer 0's slot in the original bytes; the message and
        // any further signer slots stay exactly as the dApp built them, so the broadcast
        // transaction matches the pre-image that was signed.
        check(signature.size == SIGNATURE_LENGTH) { "Unexpected Solana signature length" }
        val signedTransaction = transaction.bytes.copyOf()
        signature.copyInto(signedTransaction, destinationOffset = transaction.firstSignatureOffset)

        // Android broadcasts Solana transactions with the RPC's default base58
        // encoding (see SolanaApi.broadcastTransaction), unlike iOS which pins base64,
        // so the signed transaction and its hash are base58 here.
        return SignedTransactionResult(
            rawTransaction = Base58.encodeNoCheck(signedTransaction),
            transactionHash = Base58.encodeNoCheck(signature),
        )
    }

    /**
     * A dApp-supplied raw Solana transaction split into its wire envelope `[compact-u16 signature
     * count][count × 64-byte signature slot][message]`.
     *
     * [message] is the pre-image ed25519 signs verbatim; [firstSignatureOffset] is where signer 0's
     * slot begins, so the produced signature can be spliced back into [bytes] without disturbing
     * anything else.
     */
    private class RawSolanaTransaction(
        val bytes: ByteArray,
        val firstSignatureOffset: Int,
        val message: ByteArray,
    )

    /**
     * Parses the `[shortvec(numSignatures)][numSignatures × 64-byte slot][message]` envelope of a
     * dApp-supplied raw transaction and returns its message bytes verbatim.
     *
     * Signing over these original bytes — rather than decoding into WalletCore's representation and
     * re-serializing it (the `TransactionDecoder` → `SigningInput.rawMessage` →
     * `TransactionCompiler` round trip) — keeps the pre-image hash independent of WalletCore's
     * encoder. That re-encode is not guaranteed to reproduce the original bytes for a v0 message
     * referencing an Address Lookup Table (the standard shape for DEX/aggregator swaps), which
     * would make co-signing devices compute mismatching hashes and stall the ceremony.
     */
    private fun parseRawTransaction(base64Transaction: String): RawSolanaTransaction {
        val bytes = base64Transaction.decodeBase64Bytes()

        val (signatureCount, firstSignatureOffset) = readCompactU16(bytes)
        check(signatureCount >= 1) { "Solana transaction declares no signatures" }

        val messageOffset = firstSignatureOffset + signatureCount * SIGNATURE_LENGTH
        check(messageOffset < bytes.size) {
            "Solana transaction too short for its $signatureCount declared signature(s)"
        }
        return RawSolanaTransaction(
            bytes = bytes,
            firstSignatureOffset = firstSignatureOffset,
            message = bytes.copyOfRange(messageOffset, bytes.size),
        )
    }

    /**
     * Decodes the Solana compact-u16 (shortvec) at the start of [bytes] — up to three bytes, 7
     * payload bits each with the high bit signalling "more bytes follow" — and returns the decoded
     * value together with the offset just past it (where the signature slots begin).
     */
    private fun readCompactU16(bytes: ByteArray): Pair<Int, Int> {
        var value = 0
        var offset = 0
        var shift = 0
        while (offset < bytes.size) {
            val byte = bytes[offset].toInt() and 0xFF
            value = value or ((byte and 0x7F) shl shift)
            offset++
            if (byte and 0x80 == 0) return value to offset
            shift += 7
            if (shift > 14) error("Malformed compact-u16 in Solana transaction")
        }
        error("Truncated compact-u16 in Solana transaction")
    }
}
