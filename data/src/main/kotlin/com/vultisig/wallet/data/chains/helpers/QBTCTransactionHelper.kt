@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tss.KeysignResponse

class QBTCTransactionHelper {

    companion object {
        private const val CHAIN_ID = "qbtc-testnet"
        private const val DENOM = "qbtc"
        private const val GAS_LIMIT = 300000L
        private const val PUB_KEY_TYPE_URL = "/cosmos.crypto.mldsa.PubKey"
        private const val MSG_SEND_TYPE_URL = "/cosmos.bank.v1beta1.MsgSend"
        private const val MSG_IBC_TRANSFER_TYPE_URL = "/ibc.applications.transfer.v1.MsgTransfer"
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val signDoc = buildSignDoc(keysignPayload)
        val hash = sha256(signDoc)
        return listOf(hash.toHexString())
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val signDoc = buildSignDoc(keysignPayload)
        val hash = sha256(signDoc).toHexString()
        val sig = signatures[hash] ?: error("Missing MLDSA signature for hash $hash")
        val signatureBytes = sig.derSignature.hexToByteArray()

        val bodyBytes = buildTxBody(keysignPayload)
        val authInfoBytes = buildAuthInfo(keysignPayload)
        val txRaw = buildTxRaw(bodyBytes, authInfoBytes, signatureBytes)
        val broadcastJson = buildBroadcastJson(txRaw)
        val txHash = sha256(txRaw).toHexString().uppercase()

        return SignedTransactionResult(broadcastJson, txHash)
    }

    private fun buildSignDoc(keysignPayload: KeysignPayload): ByteArray {
        val cosmosSpecific =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Cosmos
                ?: error("Invalid blockChainSpecific for QBTC")
        val bodyBytes = buildTxBody(keysignPayload)
        val authInfoBytes = buildAuthInfo(keysignPayload)

        val result = ByteArray(0).toMutableList()
        // field 1: body_bytes (bytes)
        result.addAll(protoBytes(1, bodyBytes))
        // field 2: auth_info_bytes (bytes)
        result.addAll(protoBytes(2, authInfoBytes))
        // field 3: chain_id (string)
        result.addAll(protoString(3, CHAIN_ID))
        // field 4: account_number (uint64)
        result.addAll(protoVarint(4, cosmosSpecific.accountNumber.toLong()))
        return result.toByteArray()
    }

    private fun buildTxBody(keysignPayload: KeysignPayload): ByteArray {
        val message = buildMessage(keysignPayload)
        val result = ByteArray(0).toMutableList()
        // field 1: messages (repeated Any)
        result.addAll(protoBytes(1, message))
        // field 2: memo (string)
        keysignPayload.memo?.let {
            if (it.isNotEmpty()) {
                result.addAll(protoString(2, it))
            }
        }
        return result.toByteArray()
    }

    private fun buildMessage(keysignPayload: KeysignPayload): ByteArray {
        val cosmosSpecific =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Cosmos
                ?: error("Invalid blockChainSpecific")

        val msgBytes = buildMsgSend(keysignPayload)
        val typeUrl = MSG_SEND_TYPE_URL

        val result = ByteArray(0).toMutableList()
        // field 1: type_url
        result.addAll(protoString(1, typeUrl))
        // field 2: value
        result.addAll(protoBytes(2, msgBytes))
        return result.toByteArray()
    }

    private fun buildMsgSend(keysignPayload: KeysignPayload): ByteArray {
        val result = ByteArray(0).toMutableList()
        // field 1: from_address
        result.addAll(protoString(1, keysignPayload.coin.address))
        // field 2: to_address
        result.addAll(protoString(2, keysignPayload.toAddress))
        // field 3: amount (repeated Coin)
        val coinBytes = buildCoin(DENOM, keysignPayload.toAmount.toString())
        result.addAll(protoBytes(3, coinBytes))
        return result.toByteArray()
    }

    private fun buildCoin(denom: String, amount: String): ByteArray {
        val result = ByteArray(0).toMutableList()
        result.addAll(protoString(1, denom))
        result.addAll(protoString(2, amount))
        return result.toByteArray()
    }

    private fun buildAuthInfo(keysignPayload: KeysignPayload): ByteArray {
        val cosmosSpecific =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Cosmos
                ?: error("Invalid blockChainSpecific")
        val pubKeyBytes = keysignPayload.coin.hexPublicKey.hexToByteArray()

        // Build signer_info
        val signerInfo = buildSignerInfo(pubKeyBytes, cosmosSpecific.sequence.toLong())

        // Build fee
        val feeBytes = buildFee(cosmosSpecific.gas.toLong())

        val result = ByteArray(0).toMutableList()
        // field 1: signer_infos
        result.addAll(protoBytes(1, signerInfo))
        // field 2: fee
        result.addAll(protoBytes(2, feeBytes))
        return result.toByteArray()
    }

    private fun buildSignerInfo(pubKeyBytes: ByteArray, sequence: Long): ByteArray {
        // PublicKey Any
        val pubKeyAny = buildPubKeyAny(pubKeyBytes)

        // ModeInfo (SIGN_MODE_DIRECT = 1)
        val modeInfo = buildModeInfo()

        val result = ByteArray(0).toMutableList()
        // field 1: public_key (Any)
        result.addAll(protoBytes(1, pubKeyAny))
        // field 2: mode_info
        result.addAll(protoBytes(2, modeInfo))
        // field 3: sequence
        result.addAll(protoVarint(3, sequence))
        return result.toByteArray()
    }

    private fun buildPubKeyAny(pubKeyBytes: ByteArray): ByteArray {
        // Inner value: key field
        val innerValue = ByteArray(0).toMutableList()
        innerValue.addAll(protoBytes(1, pubKeyBytes))

        val result = ByteArray(0).toMutableList()
        result.addAll(protoString(1, PUB_KEY_TYPE_URL))
        result.addAll(protoBytes(2, innerValue.toByteArray()))
        return result.toByteArray()
    }

    private fun buildModeInfo(): ByteArray {
        // Single mode with SIGN_MODE_DIRECT (1)
        val single = ByteArray(0).toMutableList()
        single.addAll(protoVarint(1, 1))
        val result = ByteArray(0).toMutableList()
        result.addAll(protoBytes(1, single.toByteArray()))
        return result.toByteArray()
    }

    private fun buildFee(gasAmount: Long): ByteArray {
        val coinBytes = buildCoin(DENOM, gasAmount.toString())
        val result = ByteArray(0).toMutableList()
        // field 1: amount
        result.addAll(protoBytes(1, coinBytes))
        // field 2: gas_limit
        result.addAll(protoVarint(2, GAS_LIMIT))
        return result.toByteArray()
    }

    private fun buildTxRaw(
        bodyBytes: ByteArray,
        authInfoBytes: ByteArray,
        signatureBytes: ByteArray,
    ): ByteArray {
        val result = ByteArray(0).toMutableList()
        result.addAll(protoBytes(1, bodyBytes))
        result.addAll(protoBytes(2, authInfoBytes))
        result.addAll(protoBytes(3, signatureBytes))
        return result.toByteArray()
    }

    private fun buildBroadcastJson(txRaw: ByteArray): String {
        val txBytesBase64 = java.util.Base64.getEncoder().encodeToString(txRaw)
        return Json.encodeToString(
            buildJsonObject {
                put("tx_bytes", txBytesBase64)
                put("mode", "BROADCAST_MODE_SYNC")
            }
        )
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun protoVarint(fieldNumber: Int, value: Long): List<Byte> {
        val result = mutableListOf<Byte>()
        val tag = (fieldNumber shl 3) or 0 // wire type 0 = varint
        result.addAll(encodeVarint(tag.toLong()))
        result.addAll(encodeVarint(value))
        return result
    }

    private fun protoBytes(fieldNumber: Int, data: ByteArray): List<Byte> {
        val result = mutableListOf<Byte>()
        val tag = (fieldNumber shl 3) or 2 // wire type 2 = length-delimited
        result.addAll(encodeVarint(tag.toLong()))
        result.addAll(encodeVarint(data.size.toLong()))
        result.addAll(data.toList())
        return result
    }

    private fun protoString(fieldNumber: Int, value: String): List<Byte> =
        protoBytes(fieldNumber, value.toByteArray(Charsets.UTF_8))

    private fun encodeVarint(value: Long): List<Byte> {
        val result = mutableListOf<Byte>()
        var v = value
        while (v > 0x7F) {
            result.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        result.add((v and 0x7F).toByte())
        return result
    }
}
