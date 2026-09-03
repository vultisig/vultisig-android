package com.vultisig.wallet.ui.models.sign

import com.vultisig.wallet.data.common.normalizeMessageFormat
import com.vultisig.wallet.data.common.remove0x
import com.vultisig.wallet.data.repositories.FourByteRepository
import javax.inject.Inject
import javax.inject.Singleton

/** What a custom sign-message payload turned out to be, once read. */
internal sealed interface DecodedCustomMessage {

    /** Text the payload actually carries, after hex and any chain-specific framing. */
    data class Text(val value: String) : DecodedCustomMessage

    /** Well-formed calldata: the resolved function, and its arguments when those decode too. */
    data class ContractCall(val function: String, val arguments: String?) : DecodedCustomMessage

    /**
     * A bare digest. Nothing was decoded and the bytes are all there is, which is worth saying
     * rather than leaving an unexplained hex string on a signing screen.
     */
    data object Hash : DecodedCustomMessage
}

/**
 * Reads a custom sign-message payload so the verify screen can say what is being signed.
 *
 * Mirrors the iOS `CustomMessageDecoder` with two deliberate departures, both because the payload
 * this app actually receives is usually a digest rather than content:
 * - `eth_signTypedData_v4` is not JSON-decoded. The extension sends the message and domain
 *   pre-hashed, so the original data cannot be recovered from the hex — the same reason
 *   `JoinKeysignViewModel.getNormalizedCustomMessage` already refuses it.
 * - A payload is only read as a contract call when it is shaped like one. iOS treats the first four
 *   bytes of any hex payload as a selector, which turns a 32-byte digest into a confident "Contract
 *   Function Call (…)" for a function nobody is calling. See [contractCall].
 */
@Singleton
internal class CustomMessageDecoder
@Inject
constructor(private val fourByteRepository: FourByteRepository) {

    /**
     * Returns what [message] is, or null when it is already plain text and the screen's own
     * rendering of it is the whole truth.
     */
    suspend fun decode(method: String, message: String, chain: String?): DecodedCustomMessage? {
        // Only a 0x payload hides anything. Anything else is already the message.
        if (!message.startsWith(HEX_PREFIX)) return null

        // Pre-hashed by the extension: there is no typed data left in these bytes to show.
        if (method.equals(ETH_SIGN_TYPED_DATA_V4, ignoreCase = true)) return digest(message)

        if (
            method.equals(SIGN_MESSAGE, ignoreCase = true) &&
                chain.equals(TRON_CHAIN, ignoreCase = true)
        ) {
            tronSignedMessage(message)?.let {
                return DecodedCustomMessage.Text(it)
            }
        }

        readableText(message)?.let {
            return DecodedCustomMessage.Text(it)
        }

        contractCall(message)?.let {
            return it
        }

        return digest(message)
    }

    /**
     * The payload as text, or null when it does not strictly decode as UTF-8.
     *
     * [normalizeMessageFormat] returns its input unchanged on anything it cannot decode — malformed
     * hex, or bytes that are not valid UTF-8 — so a changed value is exactly the signal that a real
     * string came out. It is reused rather than reimplemented so this screen cannot disagree with
     * what the co-signer already shows.
     */
    private fun readableText(message: String): String? =
        message.normalizeMessageFormat().takeIf { it != message && it.isNotBlank() }

    /**
     * TRON TIP-191: the signed bytes are `\x19TRON Signed Message:\n<length><body>`. The framing is
     * the wallet's, not the user's, so only the body is shown.
     *
     * The leading `\x19` may already be gone: [normalizeMessageFormat] trims control characters off
     * both ends, so the prefix is matched from `TRON` onward.
     */
    private fun tronSignedMessage(message: String): String? {
        val decoded = readableText(message) ?: return null
        val unframed = decoded.trimStart(TRON_MESSAGE_FRAMING)
        if (!unframed.startsWith(TRON_MESSAGE_PREFIX)) return decoded

        val afterPrefix = unframed.removePrefix(TRON_MESSAGE_PREFIX)
        val digits = afterPrefix.takeWhile { it.isDigit() }
        val length = digits.toIntOrNull() ?: return decoded

        // The length counts bytes, not characters, so it is applied to the encoded body.
        val body = afterPrefix.removePrefix(digits).toByteArray(Charsets.UTF_8)
        if (body.size < length) return decoded
        return String(body, 0, length, Charsets.UTF_8)
    }

    /**
     * The payload read as an EVM call, or null when it is not shaped like one.
     *
     * ABI calldata is a 4-byte selector followed by whole 32-byte words, so a well-formed call is
     * 4, 36, 68, … bytes. The shape check is what keeps a digest from being announced as a
     * function: a 32-byte hash leaves 28 bytes after the selector, which is not a word boundary, so
     * it is refused here rather than named after its first four bytes.
     */
    private suspend fun contractCall(message: String): DecodedCustomMessage.ContractCall? {
        val hex = message.remove0x()
        if (hex.length % 2 != 0) return null

        val bytes = hex.length / 2
        if (bytes < SELECTOR_BYTES || (bytes - SELECTOR_BYTES) % ABI_WORD_BYTES != 0) return null

        val signature = fourByteRepository.decodeFunction(message) ?: return null
        return DecodedCustomMessage.ContractCall(
            function = signature,
            arguments = fourByteRepository.decodeFunctionArgs(signature, message),
        )
    }

    /** Names a payload that is exactly one digest, so its opacity is stated rather than implied. */
    private fun digest(message: String): DecodedCustomMessage? =
        DecodedCustomMessage.Hash.takeIf { message.remove0x().length == DIGEST_BYTES * 2 }

    private companion object {
        private const val HEX_PREFIX = "0x"
        private const val ETH_SIGN_TYPED_DATA_V4 = "eth_signTypedData_v4"
        private const val SIGN_MESSAGE = "sign_message"
        private const val TRON_CHAIN = "tron"
        private const val TRON_MESSAGE_PREFIX = "TRON Signed Message:\n"

        /** TIP-191's leading byte, which [normalizeMessageFormat] may already have trimmed. */
        private const val TRON_MESSAGE_FRAMING = '\u0019'

        private const val SELECTOR_BYTES = 4
        private const val ABI_WORD_BYTES = 32
        private const val DIGEST_BYTES = 32
    }
}
