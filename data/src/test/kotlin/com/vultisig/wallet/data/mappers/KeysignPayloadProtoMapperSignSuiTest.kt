package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.proto.v1.CoinProto
import com.vultisig.wallet.data.models.proto.v1.KeysignPayloadProto
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.SignSui
import vultisig.keysign.v1.SuiSpecific

/**
 * Pins the inbound mapping of a dApp-supplied Sui PTB (`signSui`).
 *
 * A `SignSui` payload is signed verbatim from its base64 `TransactionData` BCS bytes via
 * WalletCore's SignDirect path, so the initiator omits the SUI RPC-derived `suicheSpecific` (coins
 * / gas price). The inbound mapper must (1) carry `signSui` onto the domain payload so [SuiHelper]
 * takes the SignDirect branch, and (2) stand in an empty [BlockChainSpecific.Sui] placeholder so
 * the helper — which casts `blockChainSpecific` to `Sui` — has a value to read. This mirrors how
 * `signBitcoin` short-circuits to [BlockChainSpecific.BitcoinPSBT].
 */
class KeysignPayloadProtoMapperSignSuiTest {

    private val inbound = KeysignPayloadProtoMapperImpl()

    @Test
    fun `signSui is copied and blockChainSpecific becomes an empty Sui placeholder`() {
        val ptb = "AAACAAgA4fUFAAAAAA==" // arbitrary base64, opaque to the mapper

        val payload = inbound(basePayload(signSui = SignSui(unsignedTxMsg = ptb)))

        assertEquals(ptb, payload.signSui?.unsignedTxMsg)
        val specific = payload.blockChainSpecific
        check(specific is BlockChainSpecific.Sui) { "expected empty Sui placeholder" }
        assertEquals(BigInteger.ZERO, specific.referenceGasPrice)
        assertEquals(BigInteger.ZERO, specific.gasBudget)
        assertEquals(emptyList<Any>(), specific.coins)
    }

    @Test
    fun `signSui takes precedence over a present suicheSpecific`() {
        // Defensive: even if an initiator wrongly attaches both, the SignDirect bytes win so the
        // helper never rebuilds a Pay/PaySui from RPC coins it shouldn't use.
        val payload =
            inbound(
                basePayload(
                    signSui = SignSui(unsignedTxMsg = "AAEC"),
                    suicheSpecific =
                        SuiSpecific(
                            referenceGasPrice = "750",
                            gasBudget = "3000000",
                            coins = emptyList(),
                        ),
                )
            )

        val specific = payload.blockChainSpecific
        check(specific is BlockChainSpecific.Sui)
        assertEquals(BigInteger.ZERO, specific.referenceGasPrice)
        assertEquals(BigInteger.ZERO, specific.gasBudget)
    }

    @Test
    fun `absent signSui maps the suicheSpecific normally`() {
        val payload =
            inbound(
                basePayload(
                    suicheSpecific =
                        SuiSpecific(
                            referenceGasPrice = "750",
                            gasBudget = "3000000",
                            coins = emptyList(),
                        )
                )
            )

        assertNull(payload.signSui)
        val specific = payload.blockChainSpecific
        check(specific is BlockChainSpecific.Sui)
        assertEquals(BigInteger("750"), specific.referenceGasPrice)
        assertEquals(BigInteger("3000000"), specific.gasBudget)
    }

    private fun basePayload(signSui: SignSui? = null, suicheSpecific: SuiSpecific? = null) =
        KeysignPayloadProto(
            coin = SUI_COIN,
            toAddress = "0x9a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef",
            toAmount = "0",
            vaultPublicKeyEcdsa = "pubkey",
            vaultLocalPartyId = "party-1",
            signSui = signSui,
            suicheSpecific = suicheSpecific,
        )

    private companion object {
        val SUI_COIN =
            CoinProto(
                chain = "Sui",
                ticker = "SUI",
                address = "0x9a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef",
                contractAddress = "",
                decimals = 9,
                priceProviderId = "sui",
                isNativeToken = true,
                hexPublicKey = "",
                logo = "sui",
            )
    }
}
