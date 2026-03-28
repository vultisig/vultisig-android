@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tss.KeysignResponse
import vultisig.keysign.v1.TransactionType

class QBTCTransactionHelper {

    companion object {
        // This is the actual on-chain ID; the network launched with this name
        private const val CHAIN_ID = "qbtc-testnet"
        private const val DENOM = "qbtc"
        // Higher than typical Cosmos chains — ML-DSA-44 signatures are ~2.4 KB
        internal const val GAS_LIMIT = 300_000L
        private const val PUB_KEY_TYPE_URL = "/cosmos.crypto.mldsa.PubKey"
        private const val MSG_SEND_TYPE_URL = "/cosmos.bank.v1beta1.MsgSend"
        private const val MSG_IBC_TRANSFER_TYPE_URL = "/ibc.applications.transfer.v1.MsgTransfer"
        private const val MSG_VOTE_TYPE_URL = "/cosmos.gov.v1beta1.MsgVote"
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

        val result = mutableListOf<Byte>()
        result.addAll(protoBytes(1, bodyBytes))
        result.addAll(protoBytes(2, authInfoBytes))
        result.addAll(protoString(3, CHAIN_ID))
        result.addAll(protoVarint(4, cosmosSpecific.accountNumber.toLong()))
        return result.toByteArray()
    }

    private fun buildTxBody(keysignPayload: KeysignPayload): ByteArray {
        val cosmosSpecific =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Cosmos
                ?: error("Invalid blockChainSpecific for QBTC")

        val (anyMsg, memo) = buildMessageAny(keysignPayload, cosmosSpecific)

        val result = mutableListOf<Byte>()
        result.addAll(protoBytes(1, anyMsg))
        if (!memo.isNullOrEmpty()) {
            result.addAll(protoString(2, memo))
        }
        return result.toByteArray()
    }

    private fun buildMessageAny(
        keysignPayload: KeysignPayload,
        cosmosSpecific: BlockChainSpecific.Cosmos,
    ): Pair<ByteArray, String?> {
        return when (cosmosSpecific.transactionType) {
            TransactionType.TRANSACTION_TYPE_IBC_TRANSFER -> {
                val memoParts = keysignPayload.memo?.split(":")
                val strippedMemo =
                    if (memoParts != null && memoParts.size == 4) memoParts[3] else null
                val anyMsg = buildIBCTransferAny(keysignPayload, cosmosSpecific)
                anyMsg to strippedMemo
            }

            TransactionType.TRANSACTION_TYPE_VOTE -> {
                val anyMsg = buildVoteAny(keysignPayload)
                anyMsg to null
            }

            else -> {
                val anyMsg = buildMsgSendAny(keysignPayload)
                anyMsg to keysignPayload.memo
            }
        }
    }

    // MsgSend

    private fun buildMsgSendAny(keysignPayload: KeysignPayload): ByteArray {
        val msgBytes = buildMsgSend(keysignPayload)
        return wrapAny(MSG_SEND_TYPE_URL, msgBytes)
    }

    private fun buildMsgSend(keysignPayload: KeysignPayload): ByteArray {
        val coinDenom =
            if (keysignPayload.coin.isNativeToken) DENOM else keysignPayload.coin.contractAddress

        val result = mutableListOf<Byte>()
        result.addAll(protoString(1, keysignPayload.coin.address))
        result.addAll(protoString(2, keysignPayload.toAddress))
        result.addAll(protoBytes(3, buildCoin(coinDenom, keysignPayload.toAmount.toString())))
        return result.toByteArray()
    }

    // IBC Transfer

    private fun buildIBCTransferAny(
        keysignPayload: KeysignPayload,
        cosmosSpecific: BlockChainSpecific.Cosmos,
    ): ByteArray {
        val msgBytes = buildMsgTransfer(keysignPayload, cosmosSpecific)
        return wrapAny(MSG_IBC_TRANSFER_TYPE_URL, msgBytes)
    }

    private fun buildMsgTransfer(
        keysignPayload: KeysignPayload,
        cosmosSpecific: BlockChainSpecific.Cosmos,
    ): ByteArray {
        val memoParts =
            keysignPayload.memo?.split(":")
                ?: error("IBC transfer requires memo with source channel")
        val sourceChannel = memoParts.getOrElse(1) { "" }
        require(sourceChannel.isNotBlank()) {
            "IBC transfer requires memo with source channel at index 1"
        }

        val timeouts = cosmosSpecific.ibcDenomTraces?.latestBlock?.split("_") ?: emptyList()
        val timeout = timeouts.lastOrNull()?.toLongOrNull() ?: 0L

        val tokenDenom =
            if (keysignPayload.coin.isNativeToken) DENOM else keysignPayload.coin.contractAddress

        val token = buildCoin(tokenDenom, keysignPayload.toAmount.toString())

        val result = mutableListOf<Byte>()
        result.addAll(protoString(1, "transfer"))
        result.addAll(protoString(2, sourceChannel))
        result.addAll(protoBytes(3, token))
        result.addAll(protoString(4, keysignPayload.coin.address))
        result.addAll(protoString(5, keysignPayload.toAddress))
        // field 6: timeout_height omitted (use timeout_timestamp)
        if (timeout > 0) {
            result.addAll(protoVarint(7, timeout))
        }
        return result.toByteArray()
    }

    // Governance Vote

    private fun buildVoteAny(keysignPayload: KeysignPayload): ByteArray {
        val msgBytes = buildMsgVote(keysignPayload)
        return wrapAny(MSG_VOTE_TYPE_URL, msgBytes)
    }

    private fun buildMsgVote(keysignPayload: KeysignPayload): ByteArray {
        val voteStr = keysignPayload.memo?.removePrefix("QBTC_VOTE:") ?: error("Vote requires memo")
        val parts = voteStr.split(":")
        require(parts.size == 2) { "Invalid vote memo format, expected OPTION:PROPOSAL_ID" }

        val option = voteOptionValue(parts[0])
        val proposalId = parts[1].toLongOrNull() ?: error("Invalid proposal ID")

        val result = mutableListOf<Byte>()
        result.addAll(protoVarint(1, proposalId))
        result.addAll(protoString(2, keysignPayload.coin.address))
        result.addAll(protoVarint(3, option))
        return result.toByteArray()
    }

    private fun voteOptionValue(description: String): Long =
        when (description.uppercase()) {
            "YES" -> 1L
            "ABSTAIN" -> 2L
            "NO" -> 3L
            "NO_WITH_VETO",
            "NOWITHVETO" -> 4L
            else -> error("Unrecognized vote option: $description")
        }

    // Common builders

    private fun wrapAny(typeUrl: String, value: ByteArray): ByteArray {
        val result = mutableListOf<Byte>()
        result.addAll(protoString(1, typeUrl))
        result.addAll(protoBytes(2, value))
        return result.toByteArray()
    }

    private fun buildCoin(denom: String, amount: String): ByteArray {
        val result = mutableListOf<Byte>()
        result.addAll(protoString(1, denom))
        result.addAll(protoString(2, amount))
        return result.toByteArray()
    }

    private fun buildAuthInfo(keysignPayload: KeysignPayload): ByteArray {
        val cosmosSpecific =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Cosmos
                ?: error("Invalid blockChainSpecific for QBTC")
        val pubKeyBytes = keysignPayload.coin.hexPublicKey.hexToByteArray()

        val signerInfo = buildSignerInfo(pubKeyBytes, cosmosSpecific.sequence.toLong())
        val feeBytes = buildFee(cosmosSpecific.gas.toLong())

        val result = mutableListOf<Byte>()
        result.addAll(protoBytes(1, signerInfo))
        result.addAll(protoBytes(2, feeBytes))
        return result.toByteArray()
    }

    private fun buildSignerInfo(pubKeyBytes: ByteArray, sequence: Long): ByteArray {
        val pubKeyAny = buildPubKeyAny(pubKeyBytes)
        val modeInfo = buildModeInfo()

        val result = mutableListOf<Byte>()
        result.addAll(protoBytes(1, pubKeyAny))
        result.addAll(protoBytes(2, modeInfo))
        result.addAll(protoVarint(3, sequence))
        return result.toByteArray()
    }

    private fun buildPubKeyAny(pubKeyBytes: ByteArray): ByteArray {
        val innerValue = mutableListOf<Byte>()
        innerValue.addAll(protoBytes(1, pubKeyBytes))

        val result = mutableListOf<Byte>()
        result.addAll(protoString(1, PUB_KEY_TYPE_URL))
        result.addAll(protoBytes(2, innerValue.toByteArray()))
        return result.toByteArray()
    }

    private fun buildModeInfo(): ByteArray {
        val single = mutableListOf<Byte>()
        single.addAll(protoVarint(1, 1)) // SIGN_MODE_DIRECT
        val result = mutableListOf<Byte>()
        result.addAll(protoBytes(1, single.toByteArray()))
        return result.toByteArray()
    }

    private fun buildFee(gasAmount: Long): ByteArray {
        val coinBytes = buildCoin(DENOM, gasAmount.toString())
        val result = mutableListOf<Byte>()
        result.addAll(protoBytes(1, coinBytes))
        result.addAll(protoVarint(2, GAS_LIMIT))
        return result.toByteArray()
    }

    private fun buildTxRaw(
        bodyBytes: ByteArray,
        authInfoBytes: ByteArray,
        signatureBytes: ByteArray,
    ): ByteArray {
        val result = mutableListOf<Byte>()
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

    // Protobuf wire format encoding

    private fun protoVarint(fieldNumber: Int, value: Long): List<Byte> {
        if (value == 0L) return emptyList() // proto3: omit default values
        val result = mutableListOf<Byte>()
        val tag = (fieldNumber shl 3) or 0
        result.addAll(encodeVarint(tag.toLong()))
        result.addAll(encodeVarint(value))
        return result
    }

    private fun protoBytes(fieldNumber: Int, data: ByteArray): List<Byte> {
        if (data.isEmpty()) return emptyList() // proto3: omit empty bytes
        val result = mutableListOf<Byte>()
        val tag = (fieldNumber shl 3) or 2
        result.addAll(encodeVarint(tag.toLong()))
        result.addAll(encodeVarint(data.size.toLong()))
        result.addAll(data.toList())
        return result
    }

    private fun protoString(fieldNumber: Int, value: String): List<Byte> {
        if (value.isEmpty()) return emptyList() // proto3: omit empty strings
        return protoBytes(fieldNumber, value.toByteArray(Charsets.UTF_8))
    }

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
