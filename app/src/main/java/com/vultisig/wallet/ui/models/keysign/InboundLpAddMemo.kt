package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload

/**
 * Whether [this] is the asset side of a THORChain or MayaChain LP add: a plain native transfer from
 * a UTXO or EVM chain into a protocol inbound vault, whose memo `+:POOL[:PAIREDADDR]` (or
 * `ADD:…`) names a `CHAIN.ASSET` pool.
 *
 * Nothing in the specific of such a transfer says "deposit" — it is a send to an address with a
 * memo, and the memo is what the protocol reads to decide what it is. Without this a co-signer
 * reviews the add on the send screen, with no pool and no paired address beside the amount.
 *
 * Payloads that carry their own description of what they sign — a swap, an approval, a dApp
 * request, a pre-built Bitcoin transaction — are left to the screens built for them.
 */
internal fun KeysignPayload.isInboundLpAddMemo(): Boolean {
    when (blockChainSpecific) {
        is BlockChainSpecific.UTXO,
        is BlockChainSpecific.Ethereum -> Unit
        else -> return false
    }
    if (
        swapPayload != null ||
            approvePayload != null ||
            signBitcoin != null ||
            dappMetadata != null
    ) {
        return false
    }

    val fields = memo?.trim()?.split(":") ?: return false
    if (fields.size !in LP_ADD_FIELD_COUNTS) return false
    if (fields[0].uppercase() !in LP_ADD_HEADS) return false

    val pool = fields[1]
    val chainPrefix = pool.substringBefore('.', missingDelimiterValue = "")
    val asset = pool.substringAfter('.', missingDelimiterValue = "")
    return chainPrefix.isNotEmpty() && asset.isNotEmpty()
}

private val LP_ADD_HEADS = setOf("+", "ADD")

// `+:POOL` (single-sided) or `+:POOL:PAIREDADDR`.
private val LP_ADD_FIELD_COUNTS = 2..3
