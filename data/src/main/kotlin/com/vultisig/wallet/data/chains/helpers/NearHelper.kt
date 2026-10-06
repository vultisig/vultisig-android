package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.blockchain.near.NEAR_BLOCK_HASH_BYTES
import com.vultisig.wallet.data.blockchain.near.NEAR_ED25519_PUBLIC_KEY_BYTES
import com.vultisig.wallet.data.blockchain.near.NearAccountId
import com.vultisig.wallet.data.blockchain.near.NearRefusal
import com.vultisig.wallet.data.blockchain.near.NearRefusalException
import com.vultisig.wallet.data.blockchain.near.requireNear
import com.vultisig.wallet.data.common.toHexByteArray
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import wallet.core.jni.Base58
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.Hash
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.NEAR
import wallet.core.jni.proto.TransactionCompiler.PreSigningOutput

/**
 * Native NEAR transfer signing: the frozen payload values go into WalletCore's `NEAR.SigningInput`
 * unchanged, and every device signs the digest of the same `TransactionV0` bytes. Nothing here
 * re-reads the chain.
 *
 * The signer is the vault's own EdDSA key: the payload's coin must be the implicit account of that
 * key, so a payload naming another key cannot redirect what this device signs for.
 */
class NearHelper(private val vaultHexPublicKey: String) {

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> =
        listOf(
            Numeric.toHexStringNoPrefix(
                preSigningOutput(signingInput(keysignPayload)).dataHash.toByteArray()
            )
        )

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val input = signingInput(keysignPayload)
        // NEAR's Ed25519 message is `dataHash` (sha256 of the Borsh body), not `data`.
        val dataHash = preSigningOutput(input).dataHash.toByteArray()
        val publicKey = vaultPublicKey()

        val signature =
            signatures[Numeric.toHexStringNoPrefix(dataHash)]?.getSignature()?.takeIf {
                publicKey.verify(it, dataHash)
            } ?: throw NearRefusalException(NearRefusal.SIGNATURE_VERIFICATION_FAILED)

        val compiled =
            TransactionCompiler.compileWithSignatures(
                CoinType.NEAR,
                input,
                DataVector().apply { add(signature) },
                DataVector().apply { add(publicKey.data()) },
            )
        val signedTransaction =
            NEAR.SigningOutput.parseFrom(compiled).checkError().signedTransaction.toByteArray()

        return SignedTransactionResult(
            rawTransaction = Base64.getEncoder().encodeToString(signedTransaction),
            transactionHash = transactionHash(signedTransaction),
        )
    }

    private fun vaultPublicKey(): PublicKey {
        val keyBytes = vaultHexPublicKey.toHexByteArray()
        requireNear(
            keyBytes.size == NEAR_ED25519_PUBLIC_KEY_BYTES,
            NearRefusal.INVALID_PUBLIC_KEY_LENGTH,
            vaultHexPublicKey,
        )
        return PublicKey(keyBytes, PublicKeyType.ED25519)
    }

    private fun preSigningOutput(input: ByteArray): PreSigningOutput =
        PreSigningOutput.parseFrom(TransactionCompiler.preImageHashes(CoinType.NEAR, input))
            .checkError()

    private fun signingInput(keysignPayload: KeysignPayload): ByteArray {
        val specific = validatedTransfer(keysignPayload)

        return NEAR.SigningInput.newBuilder()
            .setSignerId(keysignPayload.coin.address)
            .setNonce(specific.nonce.toLong())
            .setReceiverId(keysignPayload.toAddress)
            .setBlockHash(ByteString.copyFrom(specific.blockHash))
            .setPublicKey(ByteString.copyFrom(vaultPublicKey().data()))
            .addActions(
                NEAR.Action.newBuilder()
                    .setTransfer(
                        NEAR.Transfer.newBuilder()
                            .setDeposit(ByteString.copyFrom(depositBytes(keysignPayload.toAmount)))
                    )
            )
            .build()
            .toByteArray()
    }

    /** Every check the frozen payload must pass before it is signed; returns its NEAR specifics. */
    private fun validatedTransfer(keysignPayload: KeysignPayload): BlockChainSpecific.Near {
        val coin = keysignPayload.coin
        requireNear(coin.chain == Chain.Near && coin.isNativeToken, NearRefusal.TOKENS_UNSUPPORTED)
        // A payload decoded from the wire carries "" for an unset memo.
        requireNear(keysignPayload.memo.isNullOrEmpty(), NearRefusal.MEMO)
        assertSwapKitDepositOnly(keysignPayload)
        requireNear(
            keysignPayload.wasmExecuteContractPayload == null &&
                keysignPayload.tronTransferContractPayload == null &&
                keysignPayload.tronTriggerSmartContractPayload == null &&
                keysignPayload.tronTransferAssetContractPayload == null &&
                keysignPayload.approvePayload == null,
            NearRefusal.CONTRACT_PAYLOAD,
        )
        requireNear(
            keysignPayload.signAmino == null &&
                keysignPayload.signDirect == null &&
                keysignPayload.signSolana == null &&
                keysignPayload.signTon == null &&
                keysignPayload.signSui == null &&
                keysignPayload.signRipple == null &&
                keysignPayload.signBitcoin == null,
            NearRefusal.CUSTOM_SIGN_PAYLOAD,
        )
        requireNear(
            NearAccountId.isValid(keysignPayload.toAddress),
            NearRefusal.INVALID_RECIPIENT,
            keysignPayload.toAddress,
        )
        requireNear(
            keysignPayload.toAmount > BigInteger.ZERO && keysignPayload.toAmount <= MAX_U128,
            NearRefusal.INVALID_AMOUNT,
            keysignPayload.toAmount.toString(),
        )
        val specific =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Near
                ?: throw NearRefusalException(NearRefusal.MISSING_CHAIN_SPECIFIC)
        requireNear(
            specific.gasFee.signum() >= 0,
            NearRefusal.INVALID_GAS_FEE,
            specific.gasFee.toString(),
        )
        requireNear(
            specific.gasFee <= MAX_U128,
            NearRefusal.GAS_FEE_TOO_LARGE,
            specific.gasFee.toString(),
        )
        requireNear(
            specific.blockHash.size == NEAR_BLOCK_HASH_BYTES,
            NearRefusal.INVALID_BLOCK_HASH,
            NEAR_BLOCK_HASH_BYTES,
            specific.blockHash.size,
        )
        requireNear(specific.nonce > 0UL, NearRefusal.INVALID_NONCE)

        // The sender is the implicit account of the vault key; anything else would sign a
        // transaction funded by an account this device does not control.
        requireNear(
            NearAccountId.isImplicit(coin.address),
            NearRefusal.SENDER_NOT_IMPLICIT,
            coin.address,
        )
        val derived = CoinType.NEAR.deriveAddressFromPublicKey(vaultPublicKey())
        requireNear(derived == coin.address, NearRefusal.SENDER_KEY_MISMATCH, coin.address, derived)
        // The payload's own key must name that account too, so it cannot pair it with another key.
        requireNear(
            ED25519_PUBLIC_KEY_HEX.matches(coin.hexPublicKey),
            NearRefusal.INVALID_PUBLIC_KEY_LENGTH,
            coin.hexPublicKey,
        )
        val coinKey = PublicKey(coin.hexPublicKey.toHexByteArray(), PublicKeyType.ED25519)
        val coinKeyAccount = CoinType.NEAR.deriveAddressFromPublicKey(coinKey)
        requireNear(
            coinKeyAccount == coin.address,
            NearRefusal.SENDER_KEY_MISMATCH,
            coin.address,
            coinKeyAccount,
        )
        return specific
    }

    /**
     * A SwapKit deposit (NEAR Intents `simpleTransfer`) is signed as the plain transfer it
     * describes, so the swap metadata must name exactly that transfer.
     */
    private fun assertSwapKitDepositOnly(keysignPayload: KeysignPayload) {
        val swapPayload = keysignPayload.swapPayload ?: return
        val swap =
            (swapPayload as? SwapPayload.SwapKit)?.data
                ?: throw NearRefusalException(NearRefusal.SWAPKIT_DEPOSIT_ONLY)
        requireNear(
            swap.fromCoin.chain == Chain.Near && swap.fromCoin.isNativeToken,
            NearRefusal.SWAPKIT_NOT_NATIVE_NEAR,
        )
        // NEAR Intents deposits go to a fresh per-swap implicit account; a named target is never
        // one.
        requireNear(
            NearAccountId.isImplicit(swap.targetAddress),
            NearRefusal.SWAPKIT_DEPOSIT_NOT_IMPLICIT,
            swap.targetAddress,
        )
        requireNear(
            swap.targetAddress == keysignPayload.toAddress,
            NearRefusal.SWAPKIT_DEPOSIT_RECEIVER_MISMATCH,
            swap.targetAddress,
            keysignPayload.toAddress,
        )
        requireNear(
            swap.fromAmount == keysignPayload.toAmount,
            NearRefusal.SWAPKIT_DEPOSIT_AMOUNT_MISMATCH,
            swap.fromAmount.toString(),
            keysignPayload.toAmount.toString(),
        )
        requireNear(
            swap.txPayload.isEmpty() && swap.txType.isEmpty(),
            NearRefusal.SWAPKIT_DEPOSIT_PREBUILT,
        )
        requireNear(swap.memo.isNullOrEmpty(), NearRefusal.SWAPKIT_DEPOSIT_MEMO)
    }

    companion object {
        private const val ED25519_SIGNATURE_BYTES = 64
        private const val DEPOSIT_BYTES = 16
        private const val ACCOUNT_ID_LENGTH_BYTES = 4
        private const val ED25519_KEY_TYPE: Byte = 0
        private val ED25519_PUBLIC_KEY_HEX = Regex("^[0-9a-f]{64}$")
        private val MAX_U128: BigInteger = BigInteger.ONE.shiftLeft(128) - BigInteger.ONE
        private val MAX_U64: BigInteger = BigInteger.ONE.shiftLeft(64) - BigInteger.ONE

        /**
         * The transaction nonce an access-key nonce admits: nearcore's `verify_nonce` rejects
         * `tx_nonce <= ak_nonce`, so the successor is the only valid choice. The largest uint64 has
         * no successor in the field and fails closed instead of wrapping.
         */
        fun transactionNonce(accessKeyNonce: BigInteger): ULong {
            val next = accessKeyNonce + BigInteger.ONE
            requireNear(
                accessKeyNonce.signum() >= 0 && next <= MAX_U64,
                NearRefusal.NONCE_OVERFLOW,
                accessKeyNonce.toString(),
            )
            return next.toLong().toULong()
        }

        /**
         * The chain's transaction id: base58 of `sha256(borsh(TransactionV0))`, the digest that was
         * signed, not the hash of the signed envelope. WalletCore emits the envelope as the body
         * followed by the Ed25519 key-type byte and the 64-byte signature.
         */
        fun transactionHash(signedTransaction: ByteArray): String {
            val bodyLength = signedTransaction.size - ED25519_SIGNATURE_BYTES - 1
            requireNear(
                bodyLength > 0 && signedTransaction[bodyLength] == ED25519_KEY_TYPE,
                NearRefusal.MALFORMED_SIGNED_TRANSACTION,
            )
            return Base58.encodeNoCheck(Hash.sha256(signedTransaction.copyOfRange(0, bodyLength)))
        }

        /**
         * Signer account, read from the body's leading Borsh `AccountId` (u32-LE length + UTF-8).
         * NEAR's `tx` status lookup is sharded by sender, so the status call needs it.
         */
        fun signerId(signedTransaction: ByteArray): String {
            requireNear(
                signedTransaction.size >= ACCOUNT_ID_LENGTH_BYTES,
                NearRefusal.MALFORMED_SIGNED_TRANSACTION,
            )
            val length =
                ByteBuffer.wrap(signedTransaction, 0, ACCOUNT_ID_LENGTH_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int
            requireNear(
                length > 0 && ACCOUNT_ID_LENGTH_BYTES + length <= signedTransaction.size,
                NearRefusal.MALFORMED_SIGNED_TRANSACTION,
            )
            return String(signedTransaction, ACCOUNT_ID_LENGTH_BYTES, length, Charsets.UTF_8)
        }

        // Borsh `u128`: 16 little-endian bytes.
        private fun depositBytes(amount: BigInteger): ByteArray {
            val bigEndian = amount.toByteArray().dropWhile { it == 0.toByte() }
            return ByteArray(DEPOSIT_BYTES) { index ->
                bigEndian.getOrElse(bigEndian.size - 1 - index) { 0 }
            }
        }
    }
}
