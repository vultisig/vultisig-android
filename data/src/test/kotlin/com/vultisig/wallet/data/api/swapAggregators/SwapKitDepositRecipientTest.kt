package com.vultisig.wallet.data.api.swapAggregators

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Test

internal class SwapKitDepositRecipientTest {

    @Test
    fun `a NEAR deposit shows the account the transfer is signed to`() {
        payload(Chain.Near, txType = "").swapKitDepositRecipient(NEAR_DEPOSIT) shouldBe NEAR_DEPOSIT
    }

    @Test
    fun `TON, XRP and deposit-only Cardano show the signed toAddress`() {
        listOf(
                Chain.Ton to SwapKitSwapPayloadJson.TX_TYPE_TON,
                Chain.Ripple to SwapKitSwapPayloadJson.TX_TYPE_XRP,
                Chain.Cardano to SwapKitSwapPayloadJson.TX_TYPE_CARDANO,
            )
            .forEach { (chain, txType) ->
                payload(chain, txType).swapKitDepositRecipient(SIGNED_TO) shouldBe SIGNED_TO
            }
    }

    @Test
    fun `a BTC or LTC PSBT shows the targetAddress its deposit output is pinned to`() {
        listOf(Chain.Bitcoin, Chain.Litecoin).forEach { chain ->
            payload(chain, SwapKitSwapPayloadJson.TX_TYPE_PSBT)
                .swapKitDepositRecipient(SIGNED_TO) shouldBe TARGET
        }
    }

    @Test
    fun `routes that sign SwapKit's bytes without pinning a recipient show none`() {
        listOf(
                Chain.Tron to SwapKitSwapPayloadJson.TX_TYPE_TRON,
                Chain.Sui to SwapKitSwapPayloadJson.TX_TYPE_SUI,
                Chain.Cardano to SwapKitSwapPayloadJson.TX_TYPE_CARDANO_PREBUILT,
            )
            .forEach { (chain, txType) ->
                payload(chain, txType).swapKitDepositRecipient(SIGNED_TO).shouldBeNull()
            }
    }

    @Test
    fun `a legacy UTXO or ZEC PSBT that doesn't parse shows none`() {
        listOf(Chain.Dogecoin, Chain.BitcoinCash, Chain.Dash, Chain.Zcash).forEach { chain ->
            payload(chain, SwapKitSwapPayloadJson.TX_TYPE_PSBT, txPayload = byteArrayOf(1, 2, 3))
                .swapKitDepositRecipient(SIGNED_TO)
                .shouldBeNull()
        }
    }

    private fun payload(chain: Chain, txType: String, txPayload: ByteArray = ByteArray(0)) =
        SwapKitSwapPayloadJson(
            fromCoin = coin(chain),
            toCoin = coin(Chain.Ethereum),
            fromAmount = BigInteger.TEN,
            toAmountDecimal = BigDecimal.ONE,
            txType = txType,
            txPayload = txPayload,
            targetAddress = TARGET,
        )

    private fun coin(chain: Chain) =
        Coin(
            chain = chain,
            ticker = chain.raw,
            logo = "",
            address = "",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private companion object {
        const val NEAR_DEPOSIT = "9b9fd5479ca09357845e2e48157106ad9b147efac801030ffb670b736607f3c9"
        const val TARGET = "deposit-target"
        const val SIGNED_TO = "signed-to"
    }
}
