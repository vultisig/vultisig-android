package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.crypto.ton.TonMessageBodyDecoder
import com.vultisig.wallet.data.crypto.ton.TonMessageBodyIntent
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.ui.components.hero.HeroCoinAmount
import java.math.BigDecimal
import java.math.BigInteger
import vultisig.keysign.v1.SignTon
import vultisig.keysign.v1.TonMessage

private val NANOTON_DIVISOR: BigInteger = BigInteger.TEN.pow(9)

/**
 * Decode each TonConnect message into a [TonMessageUiModel] for the keysign verify screen. Bodies
 * that don't decode fall back to a plain transfer view. Decoded recipient addresses (raw
 * `workchain:hex`) are converted to user-friendly form via [formatAddress]; the outer destination
 * of a plain transfer is already user-friendly and is shown as-is.
 */
internal fun mapTonMessages(
    signTon: SignTon?,
    formatAddress: (String) -> String,
): List<TonMessageUiModel> =
    signTon?.tonMessages?.filterNotNull().orEmpty().map { message ->
        val rawPayload = message.payload?.takeIf { it.isNotEmpty() }
        val hasStateInit = !message.stateInit.isNullOrEmpty()
        when (val intent = TonMessageBodyDecoder.decode(message.payload)) {
            is TonMessageBodyIntent.JettonTransfer ->
                TonMessageUiModel(
                    operation = TonMessageOperation.JettonTransfer,
                    recipient = formatAddress(intent.destination),
                    amount = formatTon(intent.forwardTonAmount),
                    rawPayload = rawPayload,
                    hasStateInit = hasStateInit,
                )

            is TonMessageBodyIntent.NftTransfer ->
                TonMessageUiModel(
                    operation = TonMessageOperation.NftTransfer,
                    recipient = formatAddress(intent.newOwner),
                    amount = formatTon(intent.forwardAmount),
                    rawPayload = rawPayload,
                    hasStateInit = hasStateInit,
                )

            is TonMessageBodyIntent.Excesses ->
                TonMessageUiModel(
                    operation = TonMessageOperation.ExcessGasRefund,
                    recipient = null,
                    amount = null,
                    rawPayload = rawPayload,
                    hasStateInit = hasStateInit,
                )

            null ->
                TonMessageUiModel(
                    operation = TonMessageOperation.Transfer,
                    recipient = message.to.takeIf { it.isNotEmpty() },
                    amount =
                        message.amount
                            .toBigIntegerOrNull()
                            ?.takeIf { it.signum() >= 0 }
                            ?.let(::formatTon),
                    rawPayload = rawPayload,
                    hasStateInit = hasStateInit,
                )
        }
    }

/**
 * Resolve the headline jetton amount for the keysign hero. Scans [messages] for the first jetton
 * transfer whose token is held in the vault and returns its scaled amount + ticker + logo, or
 * `null` when nothing resolves.
 *
 * On Android a vault jetton coin keeps the jetton master in [Coin.contractAddress] (its
 * [Coin.address] is the owner wallet), so the message destination — the sender's jetton wallet — is
 * mapped to a master via [resolveJettonMaster] before matching.
 */
internal suspend fun resolveTonJettonHero(
    messages: List<TonMessage>,
    vaultCoins: List<Coin>,
    resolveJettonMaster: suspend (jettonWalletAddress: String) -> String?,
): HeroCoinAmount? {
    for (message in messages) {
        val transfer =
            TonMessageBodyDecoder.decode(message.payload) as? TonMessageBodyIntent.JettonTransfer
                ?: continue
        val master = resolveJettonMaster(message.to) ?: continue
        val coin =
            vaultCoins.firstOrNull {
                // TON addresses are base64url and case-sensitive, so match exactly.
                it.chain == Chain.Ton && !it.isNativeToken && it.contractAddress == master
            } ?: continue
        return HeroCoinAmount(
            amount = formatJettonAmount(transfer.amount, coin.decimal),
            ticker = coin.ticker,
            logo = coin.logo,
        )
    }
    return null
}

private fun formatTon(nanotons: BigInteger): String {
    val whole = nanotons / NANOTON_DIVISOR
    val fraction = (nanotons % NANOTON_DIVISOR).toString().padStart(9, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$whole TON" else "$whole.$fraction TON"
}

private fun formatJettonAmount(raw: BigInteger, decimals: Int): String =
    if (decimals <= 0) {
        raw.toString()
    } else {
        BigDecimal(raw).movePointLeft(decimals).stripTrailingZeros().toPlainString()
    }
