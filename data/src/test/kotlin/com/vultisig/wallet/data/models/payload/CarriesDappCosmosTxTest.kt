package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.SignAmino

/**
 * Pins the routing predicate that decides whether a Terra / TerraClassic keysign carries a
 * dApp-supplied Cosmos transaction (so [com.vultisig.wallet.data.chains.helpers.SigningHelper]
 * signs it verbatim via CosmosHelper) or a native bank send (rebuilt by TerraHelper).
 *
 * Regression guard for the cosmosrescue.com Terra-delegation bug: a Keplr-style dApp that signs via
 * legacy amino (`signAmino`, `signDirect == null`) used to fall through to TerraHelper, which
 * rebuilt the MsgDelegate as a bank MsgSend to the `terravaloper…` validator — rejected by the
 * chain with "invalid to address: hrp does not match bech32 prefix".
 */
class CarriesDappCosmosTxTest {

    @Test
    fun `signDirect-bearing payload carries a dApp cosmos tx`() {
        assertTrue(payload(signDirect = SIGN_DIRECT).carriesDappCosmosTx)
    }

    @Test
    fun `signAmino-bearing payload carries a dApp cosmos tx`() {
        assertTrue(payload(signAmino = SignAmino()).carriesDappCosmosTx)
    }

    @Test
    fun `payload with neither signDirect nor signAmino is a native send`() {
        assertFalse(payload().carriesDappCosmosTx)
    }

    private fun payload(signDirect: SignDirectProto? = null, signAmino: SignAmino? = null) =
        KeysignPayload(
            coin = LUNC,
            toAddress = "terravaloper1l3zgemxwql5fpa6p9z6h00000000000abcd",
            toAmount = BigInteger("249770649"),
            blockChainSpecific =
                BlockChainSpecific.Cosmos(
                    accountNumber = BigInteger.valueOf(12345),
                    sequence = BigInteger.valueOf(7),
                    gas = BigInteger.valueOf(100_000_000),
                    ibcDenomTraces = null,
                    transactionType =
                        vultisig.keysign.v1.TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                ),
            memo = null,
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "local",
            libType = null,
            wasmExecuteContractPayload = null,
            signDirect = signDirect,
            signAmino = signAmino,
        )

    private companion object {
        val SIGN_DIRECT =
            SignDirectProto(
                bodyBytes = "Cg0vY29zbW9zLk1zZ0RlbGVnYXRl",
                authInfoBytes = "EgQKAggB",
                chainId = "columbus-5",
                accountNumber = "12345",
            )

        val LUNC =
            Coin(
                chain = Chain.TerraClassic,
                ticker = "LUNC",
                logo = "lunc",
                address = "terra1pxpxmdrnv66w0000000000000000000000abcd",
                decimal = 6,
                hexPublicKey = "020202020202020202020202020202020202020202020202020202020202020202",
                priceProviderID = "terra-luna",
                contractAddress = "",
                isNativeToken = true,
            )
    }
}
