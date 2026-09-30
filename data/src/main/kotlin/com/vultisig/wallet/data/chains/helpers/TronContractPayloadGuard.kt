package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.common.isHex
import com.vultisig.wallet.data.common.toByteStringOrHex
import com.vultisig.wallet.data.common.toHexBytes
import com.vultisig.wallet.data.crypto.Base58Codec
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import java.security.MessageDigest
import vultisig.keysign.v1.TronTransferAssetContractPayload
import vultisig.keysign.v1.TronTransferContractPayload
import vultisig.keysign.v1.TronTriggerSmartContractPayload

/**
 * Binds a dApp Tron contract payload to the payload's top-level fields before it is signed.
 *
 * The Verify screen shows `coin`, `toAddress` and `toAmount`, but [TronHelper] signs the contract
 * payload verbatim. Both devices run this check on the signing path, so a payload whose display
 * fields disagree with the contract it carries fails keysign instead of being co-signed. The
 * expected shapes mirror how the extension's TronWeb provider fills those fields:
 * - `TransferContract` / `TransferAssetContract`: recipient and amount of the contract.
 * - `TriggerSmartContract` with TRC-20 `transfer` data: the decoded recipient and amount, with the
 *   payload coin being that token and no TRX attached.
 * - Any other `TriggerSmartContract`: the contract address and the TRX `call_value`.
 *
 * In every case the contract owner must be the vault's own Tron address.
 */
internal object TronContractPayloadGuard {

    private const val TRC20_TRANSFER_SELECTOR = "a9059cbb"
    private const val ABI_WORD_BYTES = 32
    private const val TRON_ADDRESS_BYTES = 21
    private const val TRON_ADDRESS_PREFIX: Byte = 0x41
    private const val CHECKSUM_BYTES = 4

    fun check(payload: KeysignPayload, contract: TronTransferContractPayload) {
        requireOwner(payload, contract.ownerAddress)
        require(payload.coin.isNativeToken) { "Tron TransferContract must be signed as TRX" }
        requireRecipient(payload, contract.toAddress)
        requireAmount(payload, contract.amount.toBigIntegerOrNull())
    }

    fun check(payload: KeysignPayload, contract: TronTransferAssetContractPayload) {
        requireOwner(payload, contract.ownerAddress)
        requireRecipient(payload, contract.toAddress)
        requireAmount(payload, contract.amount.toBigIntegerOrNull())
    }

    fun check(payload: KeysignPayload, contract: TronTriggerSmartContractPayload) {
        requireOwner(payload, contract.ownerAddress)
        val callValue = contract.callValue?.toBigIntegerOrNull() ?: BigInteger.ZERO
        val transfer = contract.data?.let { decodeTrc20Transfer(it.toByteStringOrHex()) }
        if (transfer == null) {
            requireRecipient(payload, contract.contractAddress)
            requireAmount(payload, callValue)
            return
        }
        require(callValue.signum() == 0) { "TRC-20 transfer must not attach TRX" }
        require(
            !payload.coin.isNativeToken &&
                sameTronAddress(payload.coin.contractAddress, contract.contractAddress)
        ) {
            "TRC-20 transfer contract does not match the payload token"
        }
        requireRecipient(payload, transfer.recipient)
        requireAmount(payload, transfer.amount)
    }

    private fun requireOwner(payload: KeysignPayload, ownerAddress: String) {
        require(sameTronAddress(ownerAddress, payload.coin.address)) {
            "Tron contract owner is not the vault address"
        }
    }

    private fun requireRecipient(payload: KeysignPayload, signedRecipient: String) {
        require(sameTronAddress(signedRecipient, payload.toAddress)) {
            "Tron contract recipient does not match the payload recipient"
        }
    }

    private fun requireAmount(payload: KeysignPayload, signedAmount: BigInteger?) {
        require(signedAmount != null && signedAmount == payload.toAmount) {
            "Tron contract amount does not match the payload amount"
        }
    }

    private class Trc20Transfer(val recipient: String, val amount: BigInteger)

    private fun decodeTrc20Transfer(data: ByteString): Trc20Transfer? {
        val bytes = data.toByteArray()
        if (bytes.size < 4 + 2 * ABI_WORD_BYTES) return null
        val selector = bytes.copyOfRange(0, 4).joinToString("") { "%02x".format(it) }
        if (selector != TRC20_TRANSFER_SELECTOR) return null
        val recipientWord = bytes.copyOfRange(4, 4 + ABI_WORD_BYTES)
        val recipient =
            byteArrayOf(TRON_ADDRESS_PREFIX) +
                recipientWord.copyOfRange(ABI_WORD_BYTES - 20, ABI_WORD_BYTES)
        val amount =
            BigInteger(1, bytes.copyOfRange(4 + ABI_WORD_BYTES, 4 + 2 * ABI_WORD_BYTES))
        return Trc20Transfer(
            recipient = recipient.joinToString("") { "%02x".format(it) },
            amount = amount,
        )
    }

    /** Compares two Tron addresses given as Base58Check or as `41`-prefixed hex. */
    private fun sameTronAddress(a: String, b: String): Boolean {
        val left = tronAddressBytes(a) ?: return false
        val right = tronAddressBytes(b) ?: return false
        return left.contentEquals(right)
    }

    private fun tronAddressBytes(address: String): ByteArray? {
        val trimmed = address.trim()
        val hex = trimmed.removePrefix("0x")
        val bytes =
            if (hex.length == TRON_ADDRESS_BYTES * 2 && hex.isHex()) hex.toHexBytes()
            else decodeBase58Check(trimmed)
        return bytes?.takeIf {
            it.size == TRON_ADDRESS_BYTES && it[0] == TRON_ADDRESS_PREFIX
        }
    }

    private fun decodeBase58Check(address: String): ByteArray? {
        val decoded = Base58Codec.decode(address) ?: return null
        if (decoded.size <= CHECKSUM_BYTES) return null
        val body = decoded.copyOfRange(0, decoded.size - CHECKSUM_BYTES)
        val checksum = decoded.copyOfRange(decoded.size - CHECKSUM_BYTES, decoded.size)
        val sha256 = MessageDigest.getInstance("SHA-256")
        val expected = sha256.digest(sha256.digest(body)).copyOfRange(0, CHECKSUM_BYTES)
        return body.takeIf { checksum.contentEquals(expected) }
    }
}
