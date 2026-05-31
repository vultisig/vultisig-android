package com.vultisig.wallet.data.blockchain.cosmos.staking

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Pure stateless byte-builder for Cosmos-SDK x/staking + x/distribution messages.
 *
 * Byte-equal port of iOS `CosmosStakingHelper.swift` (vultisig-ios PR #4432). Each `encode*` method
 * returns the `Any`-wrapped message bytes (`{ type_url, value }`) ready to drop into a TxBody.
 * [buildTxBodyMulti] packs N `Any`-wrapped messages into a single TxBody — used both by the
 * single-msg flows (delegate / undelegate / redelegate) and by the multi-msg batched-claim flow.
 *
 * Proto3 default-skip semantics match the SDK reference encoder, so the wire format is identical.
 */
object CosmosStakingHelper {

    const val MSG_DELEGATE_TYPE_URL = "/cosmos.staking.v1beta1.MsgDelegate"
    const val MSG_UNDELEGATE_TYPE_URL = "/cosmos.staking.v1beta1.MsgUndelegate"
    const val MSG_BEGIN_REDELEGATE_TYPE_URL = "/cosmos.staking.v1beta1.MsgBeginRedelegate"
    const val MSG_WITHDRAW_DELEGATOR_REWARD_TYPE_URL =
        "/cosmos.distribution.v1beta1.MsgWithdrawDelegatorReward"
    const val PUBKEY_TYPE_URL = "/cosmos.crypto.secp256k1.PubKey"

    /**
     * Proto SignMode value for SIGN_MODE_DIRECT — the only mode the staking path uses. Amino is
     * reserved for legacy hardware paths and not exercised here.
     */
    private const val SIGN_MODE_DIRECT: Long = 1L

    data class SignDocArtifacts(val bytes: ByteArray, val hashHex: String) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SignDocArtifacts) return false
            return bytes.contentEquals(other.bytes) && hashHex == other.hashHex
        }

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + hashHex.hashCode()
    }

    /**
     * Encodes a `MsgDelegate` into `Any { type_url, value }`. Wire shape of `value`: `{
     * delegator_address(1), validator_address(2), Coin { denom(1), amount(2) }(3) }`.
     */
    fun encodeDelegate(
        delegator: String,
        validator: String,
        amount: String,
        denom: String,
    ): ByteArray {
        val coin = encodeCoin(denom, amount)
        val msg = ByteArrayOutputStream()
        msg.appendProtoString(1, delegator)
        msg.appendProtoString(2, validator)
        msg.appendProtoBytes(3, coin)
        return wrapAny(MSG_DELEGATE_TYPE_URL, msg.toByteArray())
    }

    /** Identical wire shape to [encodeDelegate]; only the `Any` typeUrl differs. */
    fun encodeUndelegate(
        delegator: String,
        validator: String,
        amount: String,
        denom: String,
    ): ByteArray {
        val coin = encodeCoin(denom, amount)
        val msg = ByteArrayOutputStream()
        msg.appendProtoString(1, delegator)
        msg.appendProtoString(2, validator)
        msg.appendProtoBytes(3, coin)
        return wrapAny(MSG_UNDELEGATE_TYPE_URL, msg.toByteArray())
    }

    /**
     * Encodes a `MsgBeginRedelegate`. Wire shape of `value`: `{ delegator_address(1),
     * validator_src_address(2), validator_dst_address(3), Coin(4) }`.
     *
     * Field 2 is the SOURCE validator, field 3 is the DESTINATION. Swapping silently produces a tx
     * that redelegates the wrong way — pinned as a regression guard against that.
     */
    fun encodeBeginRedelegate(
        delegator: String,
        validatorSrc: String,
        validatorDst: String,
        amount: String,
        denom: String,
    ): ByteArray {
        val coin = encodeCoin(denom, amount)
        val msg = ByteArrayOutputStream()
        msg.appendProtoString(1, delegator)
        msg.appendProtoString(2, validatorSrc)
        msg.appendProtoString(3, validatorDst)
        msg.appendProtoBytes(4, coin)
        return wrapAny(MSG_BEGIN_REDELEGATE_TYPE_URL, msg.toByteArray())
    }

    /**
     * Wire shape: `{ delegator_address(1), validator_address(2) }` — no Coin field. The
     * distribution-module typeUrl carries the discriminator.
     */
    fun encodeWithdrawDelegatorReward(delegator: String, validator: String): ByteArray {
        val msg = ByteArrayOutputStream()
        msg.appendProtoString(1, delegator)
        msg.appendProtoString(2, validator)
        return wrapAny(MSG_WITHDRAW_DELEGATOR_REWARD_TYPE_URL, msg.toByteArray())
    }

    /**
     * Packs N `Any`-wrapped messages into a single TxBody, preserving order. Single-msg flows pass
     * a one-element list; batched claim passes one `Any`-wrapped `MsgWithdrawDelegatorReward` per
     * validator.
     *
     * Wire shape: `{ messages(1, repeated Any), memo(2, optional) }`. `timeout_height` (field 3)
     * and extension options (4/5) are intentionally omitted — they default to 0 / unset, matching
     * the SDK encoder.
     */
    fun buildTxBodyMulti(msgsAny: List<ByteArray>, memo: String = ""): ByteArray {
        val txBody = ByteArrayOutputStream()
        for (anyMsg in msgsAny) {
            txBody.appendProtoBytes(1, anyMsg)
        }
        if (memo.isNotEmpty()) {
            txBody.appendProtoString(2, memo)
        }
        return txBody.toByteArray()
    }

    /**
     * Builds the `AuthInfo` for a single-signer secp256k1 tx in SIGN_MODE_DIRECT.
     *
     * AuthInfo: `{ signer_infos(1, repeated), fee(2) }`. SignerInfo: `{ public_key(1, Any),
     * mode_info(2), sequence(3) }`. ModeInfo: `{ single(1) }` → Single: `{ mode(1) }`. Fee: `{
     * amount(1, repeated Coin), gas_limit(2) }`.
     */
    fun buildAuthInfo(
        pubKey: ByteArray,
        sequence: Long,
        gasLimit: Long,
        feeDenom: String,
        feeAmount: Long,
    ): ByteArray {
        val pubKeyInner = ByteArrayOutputStream()
        pubKeyInner.appendProtoBytes(1, pubKey)

        val pubKeyAny = ByteArrayOutputStream()
        pubKeyAny.appendProtoString(1, PUBKEY_TYPE_URL)
        pubKeyAny.appendProtoBytes(2, pubKeyInner.toByteArray())

        val single = ByteArrayOutputStream()
        single.appendProtoVarint(1, SIGN_MODE_DIRECT)
        val modeInfo = ByteArrayOutputStream()
        modeInfo.appendProtoBytes(1, single.toByteArray())

        val signerInfo = ByteArrayOutputStream()
        signerInfo.appendProtoBytes(1, pubKeyAny.toByteArray())
        signerInfo.appendProtoBytes(2, modeInfo.toByteArray())
        signerInfo.appendProtoVarint(3, sequence)

        val feeCoin = encodeCoin(feeDenom, feeAmount.toString())

        val fee = ByteArrayOutputStream()
        fee.appendProtoBytes(1, feeCoin)
        fee.appendProtoVarint(2, gasLimit)

        val authInfo = ByteArrayOutputStream()
        authInfo.appendProtoBytes(1, signerInfo.toByteArray())
        authInfo.appendProtoBytes(2, fee.toByteArray())
        return authInfo.toByteArray()
    }

    /**
     * Builds the SignDoc bytes and returns them alongside their SHA-256 hex digest — the digest is
     * the pre-image hash that the MPC layer signs.
     *
     * SignDoc wire shape: `{ body_bytes(1), auth_info_bytes(2), chain_id(3), account_number(4) }`.
     */
    fun buildSignDoc(
        bodyBytes: ByteArray,
        authInfoBytes: ByteArray,
        chainId: String,
        accountNumber: Long,
    ): SignDocArtifacts {
        val signDoc = ByteArrayOutputStream()
        signDoc.appendProtoBytes(1, bodyBytes)
        signDoc.appendProtoBytes(2, authInfoBytes)
        signDoc.appendProtoString(3, chainId)
        signDoc.appendProtoVarint(4, accountNumber)
        val bytes = signDoc.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return SignDocArtifacts(bytes = bytes, hashHex = digest.toHexLowercase())
    }

    /** `Coin` wire shape: `{ denom(1), amount(2) }`. */
    private fun encodeCoin(denom: String, amount: String): ByteArray {
        val coin = ByteArrayOutputStream()
        coin.appendProtoString(1, denom)
        coin.appendProtoString(2, amount)
        return coin.toByteArray()
    }

    /** Wraps an inner message body in `google.protobuf.Any`: `{ type_url(1), value(2) }`. */
    private fun wrapAny(typeUrl: String, value: ByteArray): ByteArray {
        val anyMsg = ByteArrayOutputStream()
        anyMsg.appendProtoString(1, typeUrl)
        anyMsg.appendProtoBytes(2, value)
        return anyMsg.toByteArray()
    }

    private fun ByteArray.toHexLowercase(): String =
        buildString(size * 2) {
            for (byte in this@toHexLowercase) {
                val v = byte.toInt() and 0xFF
                append(HEX_CHARS[v ushr 4])
                append(HEX_CHARS[v and 0x0F])
            }
        }

    private const val HEX_CHARS = "0123456789abcdef"
}

/**
 * Proto3 default-skip semantics: an empty string, zero varint, or empty bytes is omitted entirely.
 * Pinned by [CosmosStakingHelper] so the encoder stays byte-equal with the SDK reference encoder.
 */
private fun ByteArrayOutputStream.appendProtoVarint(fieldNumber: Int, value: Long) {
    if (value == 0L) return
    val tag = (fieldNumber shl 3) or 0
    writeVarint(tag.toLong())
    writeVarint(value)
}

private fun ByteArrayOutputStream.appendProtoBytes(fieldNumber: Int, data: ByteArray) {
    if (data.isEmpty()) return
    val tag = (fieldNumber shl 3) or 2
    writeVarint(tag.toLong())
    writeVarint(data.size.toLong())
    write(data, 0, data.size)
}

private fun ByteArrayOutputStream.appendProtoString(fieldNumber: Int, value: String) {
    if (value.isEmpty()) return
    appendProtoBytes(fieldNumber, value.toByteArray(Charsets.UTF_8))
}

private fun ByteArrayOutputStream.writeVarint(value: Long) {
    var v = value
    while (v ushr 7 != 0L) {
        write(((v and 0x7F) or 0x80).toInt())
        v = v ushr 7
    }
    write((v and 0x7F).toInt())
}
