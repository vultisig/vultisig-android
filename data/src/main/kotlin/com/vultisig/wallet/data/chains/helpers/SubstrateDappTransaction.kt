package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.SubstrateSignerPayload
import com.vultisig.wallet.data.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType

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
    val fields: List<SubstrateDappTxField>,
    val rawJson: String,
)

object SubstrateDappTransactionDecoder {

    /** Polkadot relay SS58 prefix; Bittensor uses the generic Substrate prefix. */
    private val ss58PrefixByChain = mapOf(Chain.Polkadot to 0, Chain.Bittensor to 42)

    /**
     * Reads the transfer (if the call is one) and the fields of [payload]. Throws when the call
     * claims to be a transfer but does not decode — see [SubstrateTransferCallReader].
     */
    fun decode(
        payload: SubstrateSignerPayload,
        chain: Chain,
        decimals: Int,
        ticker: String,
        ss58Encode: (ByteArray, Chain) -> String = ::walletCoreSs58,
    ): SubstrateDappTx {
        val call = payload.methodBytes()
        val transfer =
            SubstrateTransferCallReader.read(call)?.let {
                SubstrateDappTransfer(
                    recipient = ss58Encode(it.destination, chain),
                    amount = it.amount,
                )
            }
        val fields =
            listOf(
                SubstrateDappTxField(SubstrateDappTxFieldKey.CALL_DATA, Numeric.toHexString(call)),
                SubstrateDappTxField(
                    SubstrateDappTxFieldKey.NONCE,
                    payload.nonceValue().toString(),
                ),
                SubstrateDappTxField(
                    SubstrateDappTxFieldKey.TIP,
                    formatPlanck(payload.tipValue(), decimals, ticker),
                ),
                SubstrateDappTxField(SubstrateDappTxFieldKey.ERA, payload.era),
                SubstrateDappTxField(
                    SubstrateDappTxFieldKey.SPEC_VERSION,
                    payload.specVersionValue().toString(),
                ),
                SubstrateDappTxField(
                    SubstrateDappTxFieldKey.TRANSACTION_VERSION,
                    payload.transactionVersionValue().toString(),
                ),
                SubstrateDappTxField(SubstrateDappTxFieldKey.GENESIS_HASH, payload.genesisHash),
                SubstrateDappTxField(SubstrateDappTxFieldKey.BLOCK_HASH, payload.blockHash),
            )
        return SubstrateDappTx(
            callIndex = payload.callIndexHex(),
            transfer = transfer,
            fields = fields,
            rawJson = payload.rawJson,
        )
    }

    /** The SS58 spelling of a 32-byte AccountId under [chain]'s prefix, via WalletCore. */
    fun walletCoreSs58(accountId: ByteArray, chain: Chain): String =
        AnyAddress(
                PublicKey(accountId, PublicKeyType.ED25519),
                CoinType.POLKADOT,
                ss58PrefixByChain.getValue(chain),
            )
            .description()

    private fun formatPlanck(value: BigInteger, decimals: Int, ticker: String): String =
        "${BigDecimal(value).movePointLeft(decimals).stripTrailingZeros().toPlainString()} $ticker"
}
