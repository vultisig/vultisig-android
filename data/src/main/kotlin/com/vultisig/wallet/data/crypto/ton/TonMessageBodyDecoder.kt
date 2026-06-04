package com.vultisig.wallet.data.crypto.ton

/**
 * Decodes a TonConnect message body (base64 or hex BOC) into a structured [TonMessageBodyIntent].
 *
 * Mirrors `decodeTonMessageBody` from the Vultisig SDK / iOS `TonMessageBodyDecoder`, scoped to the
 * transfer opcodes surfaced on the keysign verify screen. Returns `null` for anything it cannot
 * confidently classify — empty input, malformed/truncated BOCs, or unrecognised opcodes — so the
 * keysign UI falls back to the raw message display rather than showing wrong information.
 *
 * Some dApps prefix a transfer body with an empty 32-bit "text comment" header (op = 0); in that
 * case the real opcode is the next 32 bits.
 */
object TonMessageBodyDecoder {

    // Opcode = first 32 bits of the message body.
    private const val OP_JETTON_TRANSFER = 0x0f8a7ea5L // TEP-74
    private const val OP_NFT_TRANSFER = 0x5fcc3d14L // TEP-62
    private const val OP_EXCESSES = 0xd53276dbL // TEP-74 gas return

    fun decode(payload: String?): TonMessageBodyIntent? {
        val base64 = TonBocParser.payloadToBase64(payload) ?: return null
        return try {
            val slice = TonBocParser.parse(base64).beginParse()
            if (slice.remainingBits < 32) return null
            val op = slice.loadUInt(32)
            if (op == 0L) {
                // Peel the text-comment header and dispatch on the nested opcode.
                if (slice.remainingBits < 32) return null
                dispatch(slice.loadUInt(32), slice)
            } else {
                dispatch(op, slice)
            }
        } catch (_: TonCellException) {
            null
        }
    }

    private fun dispatch(op: Long, slice: TonSlice): TonMessageBodyIntent? =
        when (op) {
            OP_JETTON_TRANSFER -> parseJettonTransfer(slice)
            OP_NFT_TRANSFER -> parseNftTransfer(slice)
            OP_EXCESSES -> parseExcesses(slice)
            else -> null
        }

    private fun parseJettonTransfer(slice: TonSlice): TonMessageBodyIntent {
        val queryId = slice.loadUIntBig(64)
        val amount = slice.loadCoins()
        val destination = slice.loadAddress()
        val responseDestination = slice.loadMaybeAddress()
        slice.loadMaybeRef() // custom_payload:(Maybe ^Cell) — discarded
        val forwardTonAmount = slice.loadCoins()
        // forward_payload:(Either Cell ^Cell) — consumed (not surfaced) so a
        // body truncated before this field is rejected.
        consumeForwardPayload(slice)
        return TonMessageBodyIntent.JettonTransfer(
            queryId = queryId,
            amount = amount,
            destination = destination,
            responseDestination = responseDestination,
            forwardTonAmount = forwardTonAmount,
        )
    }

    private fun parseNftTransfer(slice: TonSlice): TonMessageBodyIntent {
        val queryId = slice.loadUIntBig(64)
        val newOwner = slice.loadAddress()
        val responseDestination = slice.loadMaybeAddress()
        slice.loadMaybeRef() // custom_payload:(Maybe ^Cell) — discarded
        val forwardAmount = slice.loadCoins()
        consumeForwardPayload(slice)
        return TonMessageBodyIntent.NftTransfer(
            queryId = queryId,
            newOwner = newOwner,
            responseDestination = responseDestination,
            forwardAmount = forwardAmount,
        )
    }

    private fun parseExcesses(slice: TonSlice): TonMessageBodyIntent =
        TonMessageBodyIntent.Excesses(queryId = slice.loadUIntBig(64))

    /**
     * Consume a `forward_payload:(Either Cell ^Cell)`. The content is not surfaced here; reading
     * the discriminator bit (and the ref it claims) rejects bodies truncated at this field. An
     * inline payload (the `Either` left case) is the slice's remaining tail and needs no further
     * reads.
     */
    private fun consumeForwardPayload(slice: TonSlice) {
        if (slice.loadBit()) slice.loadRef()
    }
}
