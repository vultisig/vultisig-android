package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.TransactionType
import wallet.core.jni.CoinType
import wallet.core.jni.proto.Cosmos

/**
 * Regression test for issue #5821: the default `MsgSend` arm picked the denom by a
 * `factory/`/`ibc/` contract-address prefix, so Osmosis ION (`contractAddress = "uion"`, which
 * matches neither prefix) fell through to the chain's fee denom `uosmo` and signed a transfer of
 * OSMO instead of ION.
 */
@OptIn(ExperimentalStdlibApi::class)
class CosmosHelperTest {

    private val helper = CosmosHelper(coinType = CoinType.OSMOSIS, denom = "uosmo")

    private fun payload(coin: Coin) =
        KeysignPayload(
            coin = coin,
            toAddress = "osmo1to000000000000000000000000000000abcd",
            toAmount = BigInteger.valueOf(2_000_000),
            blockChainSpecific =
                BlockChainSpecific.Cosmos(
                    accountNumber = BigInteger.valueOf(7),
                    sequence = BigInteger.valueOf(3),
                    gas = BigInteger.valueOf(7_500),
                    ibcDenomTraces = null,
                    transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                ),
            memo = null,
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "local",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private fun sentDenom(coin: Coin): String {
        val input = Cosmos.SigningInput.parseFrom(helper.getPreSignedInputData(payload(coin)))
        return input.messagesList.single().sendCoinsMessage.amountsList.single().denom
    }

    // WalletCore's JNI native library targets Android ABIs only, so it cannot load on a
    // desktop JVM; skip rather than fail when that's the environment we're running in.
    private fun skipIfJniUnavailable(e: Throwable) {
        if (
            e is UnsatisfiedLinkError ||
                e is ExceptionInInitializerError ||
                e is NoClassDefFoundError
        ) {
            assumeTrue(false, "WalletCore JNI not available: ${e.message}")
        } else throw e
    }

    @Test
    fun `plain send of a non-native token with a bare contract address uses the contract address as denom`() {
        try {
            val ion =
                Coin(
                    chain = Chain.Osmosis,
                    ticker = "ION",
                    logo = "ion",
                    address = "osmo1from00000000000000000000000000000abcd",
                    decimal = 6,
                    hexPublicKey =
                        "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                    priceProviderID = "",
                    contractAddress = "uion",
                    isNativeToken = false,
                )

            assertEquals("uion", sentDenom(ion))
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }

    @Test
    fun `plain send of the native token still uses the chain fee denom`() {
        try {
            val osmo =
                Coin(
                    chain = Chain.Osmosis,
                    ticker = "OSMO",
                    logo = "osmo",
                    address = "osmo1from00000000000000000000000000000abcd",
                    decimal = 6,
                    hexPublicKey =
                        "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                    priceProviderID = "osmosis",
                    contractAddress = "",
                    isNativeToken = true,
                )

            assertEquals("uosmo", sentDenom(osmo))
        } catch (e: Throwable) {
            skipIfJniUnavailable(e)
        }
    }
}
