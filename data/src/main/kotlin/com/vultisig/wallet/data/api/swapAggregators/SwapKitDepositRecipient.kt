package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.chains.helpers.SwapKitLegacyP2PKHSigner
import com.vultisig.wallet.data.chains.helpers.SwapKitLegacyP2PKHSignerException
import com.vultisig.wallet.data.chains.helpers.SwapKitPsbtException
import com.vultisig.wallet.data.chains.helpers.SwapKitZcashSigner
import com.vultisig.wallet.data.chains.helpers.SwapKitZcashSignerException
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.coinType
import timber.log.Timber

/**
 * The address the deposit this non-EVM SwapKit payload signs pays, read the way `SigningHelper`
 * signs it; null when the signed transaction names no recipient this device checks.
 * - NEAR, TON, XRP and deposit-only Cardano sign a plain transfer to [signedToAddress], the keysign
 *   payload's `toAddress`.
 * - BTC and LTC PSBTs are refused unless their deposit output pays
 *   [SwapKitSwapPayloadJson.targetAddress].
 * - DOGE, BCH, DASH and ZEC PSBTs pay whatever their first output carries, decoded from the bytes.
 * - TRON, SUI and pre-built Cardano sign SwapKit's bytes without pinning a recipient: null.
 *
 * A PSBT that doesn't parse also gives null; signing refuses the same bytes.
 */
fun SwapKitSwapPayloadJson.swapKitDepositRecipient(signedToAddress: String): String? {
    val chain = fromCoin.chain
    return when {
        chain == Chain.Near -> signedToAddress
        SwapKitSwapPayloadJson.isUtxoPsbtTxType(txType) ->
            when (chain) {
                Chain.Bitcoin,
                Chain.Litecoin -> targetAddress
                Chain.BitcoinCash,
                Chain.Dogecoin,
                Chain.Dash ->
                    decodedPsbtDeposit {
                        SwapKitLegacyP2PKHSigner.depositAddress(
                            txPayload,
                            chain.coinType,
                            targetAddress,
                        )
                    }
                Chain.Zcash ->
                    decodedPsbtDeposit {
                        SwapKitZcashSigner.depositAddress(txPayload, targetAddress)
                    }
                else -> null
            }
        txType == SwapKitSwapPayloadJson.TX_TYPE_TON ||
            txType == SwapKitSwapPayloadJson.TX_TYPE_XRP ||
            txType == SwapKitSwapPayloadJson.TX_TYPE_CARDANO -> signedToAddress
        else -> null
    }
}

private inline fun decodedPsbtDeposit(decode: () -> String): String? =
    try {
        decode()
    } catch (e: Exception) {
        when (e) {
            is SwapKitPsbtException,
            is SwapKitLegacyP2PKHSignerException,
            is SwapKitZcashSignerException -> {
                Timber.w(e, "SwapKit PSBT deposit output doesn't decode")
                null
            }
            else -> throw e
        }
    }
