package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.SubstrateSignerPayload
import com.vultisig.wallet.data.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Semantic identifier of a decoded signer-payload row. The data layer stores this key (never an
 * English display label) so the Compose layer can map it to a localized string.
 */
enum class SubstrateDappTxFieldKey {
    CALL_DATA,
    NONCE,
    TIP,
    ERA,
    SPEC_VERSION,
    TRANSACTION_VERSION,
    GENESIS_HASH,
    BLOCK_HASH,
    /** `CheckMetadataHash` mode, listed only when the runtime signs that extension. */
    METADATA_HASH_MODE,
    /** The `CheckMetadataHash` hash, listed only when the payload carries one. */
    METADATA_HASH,
}

data class SubstrateDappTxField(val key: SubstrateDappTxFieldKey, val value: String)

/**
 * A Balances transfer read out of the signed call: the recipient as SS58 and the value in planck.
 */
data class SubstrateDappTransfer(val recipient: String, val amount: BigInteger)

/**
 * Human-readable decode of a dApp's Substrate signer payload for the Verify and Done screens.
 *
 * [transfer] is set only when the call bytes are a Balances transfer, read from the bytes
 * themselves rather than the wire `toAddress` / `toAmount`, which the initiator fills for display
 * and a relay could rewrite. Every other call shows [callIndex] and its [fields]; the raw JSON is
 * always kept so a co-signer is never left with a blank screen.
 */
data class SubstrateDappTx(
    val callIndex: String?,
    val transfer: SubstrateDappTransfer?,
    /**
     * True when the call is a Balances transfer by its indices but its recipient / value could not
     * be read (an address form other than `MultiAddress::Id`, a non-canonical compact, trailing
     * bytes). The card then warns and leaves the raw call data as the only reading.
     */
    val isTransferUnreadable: Boolean,
    val fields: List<SubstrateDappTxField>,
    val rawJson: String,
)

object SubstrateDappTransactionDecoder {

    /** Polkadot relay SS58 prefix; Bittensor uses the generic Substrate prefix. */
    private val ss58PrefixByChain = mapOf(Chain.Polkadot to 0, Chain.Bittensor to 42)

    /**
     * Reads the transfer (if the call is one) and the fields of [payload]. A transfer whose bytes
     * the reader cannot follow is flagged, not thrown — see [SubstrateTransferCallReader].
     *
     * The fields are every value [SubstrateDappSigner] signs, so the two `CheckMetadataHash` values
     * appear exactly when the signer includes them: the mode whenever the extension is listed, the
     * hash when the payload carries one (polkadot.js sends `null` under mode 0).
     */
    fun decode(
        payload: SubstrateSignerPayload,
        chain: Chain,
        decimals: Int,
        ticker: String,
    ): SubstrateDappTx {
        val call = payload.methodBytes()
        val reading = SubstrateTransferCallReader.readForDisplay(call)
        val transfer =
            (reading as? SubstrateCallReading.Transfer)?.call?.let {
                SubstrateDappTransfer(
                    recipient = Ss58.encode(it.destination, ss58PrefixByChain.getValue(chain)),
                    amount = it.amount,
                )
            }
        val fields = buildList {
            add(SubstrateDappTxField(SubstrateDappTxFieldKey.CALL_DATA, Numeric.toHexString(call)))
            add(
                SubstrateDappTxField(SubstrateDappTxFieldKey.NONCE, payload.nonceValue().toString())
            )
            add(
                SubstrateDappTxField(
                    SubstrateDappTxFieldKey.TIP,
                    formatPlanck(payload.tipValue(), decimals, ticker),
                )
            )
            add(SubstrateDappTxField(SubstrateDappTxFieldKey.ERA, payload.era))
            add(
                SubstrateDappTxField(
                    SubstrateDappTxFieldKey.SPEC_VERSION,
                    payload.specVersionValue().toString(),
                )
            )
            add(
                SubstrateDappTxField(
                    SubstrateDappTxFieldKey.TRANSACTION_VERSION,
                    payload.transactionVersionValue().toString(),
                )
            )
            add(SubstrateDappTxField(SubstrateDappTxFieldKey.GENESIS_HASH, payload.genesisHash))
            add(SubstrateDappTxField(SubstrateDappTxFieldKey.BLOCK_HASH, payload.blockHash))
            if (payload.hasCheckMetadataHash) {
                add(
                    SubstrateDappTxField(
                        SubstrateDappTxFieldKey.METADATA_HASH_MODE,
                        payload.modeByte().toUByte().toString(),
                    )
                )
                if (payload.metadataHash.isNotEmpty()) {
                    add(
                        SubstrateDappTxField(
                            SubstrateDappTxFieldKey.METADATA_HASH,
                            payload.metadataHash,
                        )
                    )
                }
            }
        }
        return SubstrateDappTx(
            callIndex = payload.callIndexHex(),
            transfer = transfer,
            isTransferUnreadable = reading is SubstrateCallReading.Unreadable,
            fields = fields,
            rawJson = payload.rawJson,
        )
    }

    private fun formatPlanck(value: BigInteger, decimals: Int, ticker: String): String =
        "${BigDecimal(value).movePointLeft(decimals).stripTrailingZeros().toPlainString()} $ticker"
}
