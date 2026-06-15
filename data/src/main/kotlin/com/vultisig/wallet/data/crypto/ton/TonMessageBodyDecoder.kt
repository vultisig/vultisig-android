package com.vultisig.wallet.data.crypto.ton

import java.math.BigInteger

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
    private const val OP_PTON_TRANSFER = 0x01f3835dL // STON.fi pTON (TON-side swap)
    private const val OP_STONFI_V2_SWAP = 0x6664de2aL // STON.fi v2 swap (forward payload)
    private const val OP_DEDUST_NATIVE_SWAP = 0xea06185dL // DeDust native-TON-in swap
    // DeDust jetton-in swap. Defined for completeness but intentionally not dispatched: DeDust uses
    // one vault per jetton, so the vault set is not statically enumerable and cannot be
    // allow-listed.
    // Such bodies fall through to the raw transfer display rather than a (ungated) swap card.
    private const val OP_DEDUST_JETTON_SWAP = 0xe3a0d482L

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
            OP_PTON_TRANSFER -> parsePtonTransfer(slice)
            OP_DEDUST_NATIVE_SWAP -> parseDedustNativeSwap(slice)
            // OP_DEDUST_JETTON_SWAP is deliberately absent — see its declaration.
            else -> null
        }

    /**
     * Parse a TEP-74 jetton transfer. When the forward payload carries a STON.fi v2 swap
     * (`0x6664de2a`) this returns a [TonMessageBodyIntent.Swap] with the jetton-transfer
     * destination as [TonMessageBodyIntent.Swap.inputRouterAddress] for the runtime allow-list
     * gate; otherwise it returns a plain [TonMessageBodyIntent.JettonTransfer]. A forward payload
     * that begins with the swap opcode but is then malformed throws (→ `null`) rather than
     * degrading to a transfer, so a valid-prefix/garbage-tail body never renders a fake swap.
     */
    private fun parseJettonTransfer(slice: TonSlice): TonMessageBodyIntent {
        val queryId = slice.loadUIntBig(64)
        val amount = slice.loadCoins()
        val destination = slice.loadAddress()
        val responseDestination = slice.loadMaybeAddress()
        slice.loadMaybeRef() // custom_payload:(Maybe ^Cell) — discarded
        val forwardTonAmount = slice.loadCoins()
        // forward_payload:(Either Cell ^Cell) — a STON.fi swap rides in here.
        val forward = slice.loadForwardPayload()
        if (forward.remainingBits >= 32 && forward.loadUInt(32) == OP_STONFI_V2_SWAP) {
            return parseStonfiSwapForward(
                forward,
                offerAsset = TonMessageBodyIntent.OfferAsset.JETTON,
                offerAmount = amount,
                inputRouterAddress = destination,
            )
        }
        return TonMessageBodyIntent.JettonTransfer(
            queryId = queryId,
            amount = amount,
            destination = destination,
            responseDestination = responseDestination,
            forwardTonAmount = forwardTonAmount,
        )
    }

    /**
     * STON.fi pTON wallet transfer (`0x01f3835d`) — the TON-side leg of a STON.fi swap. The TON
     * offer amount is read here; the swap parameters ride in the forward payload. Gated in the
     * runtime layer on the outer message destination ∈ STON.fi pTON wallets.
     */
    private fun parsePtonTransfer(slice: TonSlice): TonMessageBodyIntent? {
        slice.loadUIntBig(64) // query_id — discarded
        val offerAmount = slice.loadCoins()
        slice.loadAddress() // refund_address — discarded
        val forward = slice.loadForwardPayload()
        if (forward.remainingBits < 32 || forward.loadUInt(32) != OP_STONFI_V2_SWAP) return null
        return parseStonfiSwapForward(
            forward,
            offerAsset = TonMessageBodyIntent.OfferAsset.TON,
            offerAmount = offerAmount,
            inputRouterAddress = null,
        )
    }

    /**
     * Body of a STON.fi v2 swap (`0x6664de2a`), with the opcode already consumed. Every field of
     * the `additional_data` ref is read so a truncated tail throws and the swap fails closed.
     */
    private fun parseStonfiSwapForward(
        slice: TonSlice,
        offerAsset: TonMessageBodyIntent.OfferAsset,
        offerAmount: BigInteger,
        inputRouterAddress: String?,
    ): TonMessageBodyIntent {
        val targetAddress = slice.loadAddress()
        val refundAddress = slice.loadAddress()
        val excessesAddress = slice.loadAddress()
        slice.loadUIntBig(64) // tx_deadline / query_id — discarded
        val additional = slice.loadRef().beginParse()
        val minOut = additional.loadCoins()
        val receiverAddress = additional.loadAddress()
        additional.loadCoins() // custom_payload_fwd_gas — discarded
        additional.loadMaybeRef() // custom_payload — discarded
        additional.loadCoins() // refund_fwd_gas — discarded
        additional.loadMaybeRef() // refund_payload — discarded
        additional.loadUInt(16) // referral_value (bps) — discarded
        // referral_address:(Maybe MsgAddress) — must be optional; a no-referral swap encodes
        // addr_none here and a non-optional read would throw and misclassify it.
        additional.loadMaybeAddress()
        return TonMessageBodyIntent.Swap(
            provider = TonMessageBodyIntent.Provider.STONFI,
            offerAsset = offerAsset,
            offerAmount = offerAmount,
            minOut = minOut,
            targetAddress = targetAddress,
            inputRouterAddress = inputRouterAddress,
            receiverAddress = receiverAddress,
            refundAddress = refundAddress,
            excessesAddress = excessesAddress,
        )
    }

    /**
     * DeDust native-TON-in swap (`0xea06185d`), opcode already consumed. Only `given_in` (swap-kind
     * bit `0`) is supported; `given_out` throws (→ `null`). Gated in the runtime layer on the outer
     * message destination ∈ DeDust native vaults.
     */
    private fun parseDedustNativeSwap(slice: TonSlice): TonMessageBodyIntent {
        slice.loadUIntBig(64) // query_id — discarded
        val offerAmount = slice.loadCoins()
        // SwapStep
        val targetAddress = slice.loadAddress()
        if (slice.loadBit()) throw TonCellException("dedust given_out swap not supported")
        val minOut = slice.loadCoins()
        slice.loadMaybeRef() // next:(Maybe ^SwapStep) — discarded
        // SwapParams
        slice.loadUInt(32) // deadline — discarded
        val receiverAddress = slice.loadMaybeAddress()
        slice.loadMaybeAddress() // referral_address — discarded
        slice.loadMaybeRef() // fulfill_payload — discarded
        slice.loadMaybeRef() // reject_payload — discarded
        return TonMessageBodyIntent.Swap(
            provider = TonMessageBodyIntent.Provider.DEDUST,
            offerAsset = TonMessageBodyIntent.OfferAsset.TON,
            offerAmount = offerAmount,
            minOut = minOut,
            targetAddress = targetAddress,
            inputRouterAddress = null,
            receiverAddress = receiverAddress,
            refundAddress = null,
            excessesAddress = null,
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
