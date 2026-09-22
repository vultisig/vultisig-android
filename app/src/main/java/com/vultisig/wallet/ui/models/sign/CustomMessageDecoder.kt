package com.vultisig.wallet.ui.models.sign

import com.vultisig.wallet.data.common.normalizeMessageFormat
import com.vultisig.wallet.data.common.remove0x
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.evmChainId
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.repositories.FourByteRepository
import com.vultisig.wallet.data.repositories.KnownEvmContracts
import com.vultisig.wallet.data.repositories.TokenMetadataResolver
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.keysign.DecodedFunctionParam
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** What a custom sign-message payload turned out to be, once read. */
internal sealed interface DecodedCustomMessage {

    /** Text the payload actually carries, after hex and any chain-specific framing. */
    data class Text(val value: String) : DecodedCustomMessage

    /** Well-formed calldata: the resolved function, and its arguments when those decode too. */
    data class ContractCall(val function: String, val arguments: String?) : DecodedCustomMessage

    /**
     * EIP-712 typed data read into labelled rows: a token approval when the primary type is a
     * known permit, otherwise the domain, primary type and each field of the message.
     */
    data class TypedData(val rows: List<DecodedFunctionParam>) : DecodedCustomMessage

    /**
     * A bare digest. Nothing was decoded and the bytes are all there is, which is worth saying
     * rather than leaving an unexplained hex string on a signing screen.
     */
    data object Hash : DecodedCustomMessage
}

/**
 * Reads a custom sign-message payload so the verify screen can say what is being signed.
 *
 * Mirrors the iOS `CustomMessageDecoder` with two deliberate departures:
 * - `eth_signTypedData_v4` is read the way the extension's `Eip712PermitDisplay` reads it — into
 *   the rows of a token approval, or the fields of the message — rather than pretty-printed. The
 *   raw JSON stays on screen underneath either way. A hex payload under this method is a digest
 *   the extension already hashed, with no typed data left in it to show.
 * - A payload is only read as a contract call when it is shaped like one. iOS treats the first four
 *   bytes of any hex payload as a selector, which turns a 32-byte digest into a confident "Contract
 *   Function Call (…)" for a function nobody is calling. See [contractCall].
 */
@Singleton
internal class CustomMessageDecoder
@Inject
constructor(
    private val fourByteRepository: FourByteRepository,
    private val vaultRepository: VaultRepository,
    private val tokenMetadataResolver: TokenMetadataResolver,
    private val json: Json,
) {

    /**
     * Returns what [message] is, or null when it is already plain text and the screen's own
     * rendering of it is the whole truth.
     */
    suspend fun decode(method: String, message: String, chain: String?): DecodedCustomMessage? {
        if (method.equals(ETH_SIGN_TYPED_DATA_V4, ignoreCase = true)) {
            return if (message.startsWith(HEX_PREFIX)) digest(message)
            else typedData(message, chain)
        }

        // Only a 0x payload hides anything. Anything else is already the message.
        if (!message.startsWith(HEX_PREFIX)) return null

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

    /**
     * The typed data as rows, or null when [message] is not typed data at all. A permit's tokens
     * are looked up so the rows can say "Unlimited USDT" rather than an address and a 49-digit
     * number; a token that cannot be resolved is left as its address, never dropped.
     */
    private suspend fun typedData(message: String, chainRaw: String?): DecodedCustomMessage? {
        val data = Eip712TypedData.parse(json, message) ?: return null
        val permit = data.permitOrNull() ?: return DecodedCustomMessage.TypedData(typedDataRows(data))

        val chain = evmChain(data.domainChainId, chainRaw)
        val tokens =
            if (chain == null) permit.tokens.map { null }
            else {
                val knownCoins = vaultRepository.getAll().flatMap { it.coins } + Coins.coins[chain].orEmpty()
                permit.tokens.map { token -> permitToken(chain, token.address, knownCoins) }
            }
        val spenderLabel = chain?.let { KnownEvmContracts.lookup(it, permit.spender) }
        return DecodedCustomMessage.TypedData(permitRows(permit, tokens, spenderLabel))
    }

    /**
     * The chain the typed data is bound to. The domain's chain id is authoritative: the extension
     * files every EVM request under Ethereum, so the payload's own chain only says "some EVM chain"
     * and is used only when the domain does not name one.
     */
    private fun evmChain(domainChainId: BigInteger?, chainRaw: String?): Chain? {
        val byId =
            domainChainId?.toString()?.let { id ->
                Chain.entries.firstOrNull { it.evmChainId() == id }
            }
        if (byId != null) return byId
        return chainRaw
            ?.let(Chain::fromRawOrNull)
            ?.takeIf { it.standard == TokenStandard.EVM }
    }

    /**
     * Symbol, decimals and logo for [address] on [chain]: from [knownCoins] — what some vault
     * already holds, plus the built-in list — first, since those carry a logo, and from the
     * contract itself otherwise.
     */
    private suspend fun permitToken(
        chain: Chain,
        address: String,
        knownCoins: List<Coin>,
    ): PermitTokenInfo? {
        val known =
            knownCoins.firstOrNull { coin ->
                coin.chain == chain &&
                    coin.contractAddress.equals(address, ignoreCase = true) &&
                    coin.ticker.isNotBlank()
            }
        if (known != null) {
            return PermitTokenInfo(
                symbol = known.ticker,
                decimals = known.decimal,
                logo = getCoinLogo(known.logo),
            )
        }
        val resolved = tokenMetadataResolver.resolve(chain, address) ?: return null
        return PermitTokenInfo(symbol = resolved.symbol, decimals = resolved.decimals, logo = null)
    }

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
