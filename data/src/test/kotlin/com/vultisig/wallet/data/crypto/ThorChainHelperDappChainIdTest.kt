package com.vultisig.wallet.data.crypto

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import java.math.BigInteger
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.TransactionType
import wallet.core.jni.proto.Cosmos

/**
 * MayaChain is signed by a [ThorChainHelper] built with `CoinType.THORCHAIN`, so the chain id can
 * only come from the instance's own `networkId`. Taking it from the coin type produced
 * `thorchain-1` for every Maya dApp request, and the mismatch surfaced only when the node rejected
 * the broadcast — after a full keysign ceremony.
 */
class ThorChainHelperDappChainIdTest {

    private val originalThorNetworkId = ThorChainHelper.THORCHAIN_NETWORK_ID

    @AfterEach
    fun tearDown() {
        ThorChainHelper.THORCHAIN_NETWORK_ID = originalThorNetworkId
    }

    @Test
    fun `maya signDirect header carries the maya chain id`() {
        assertEquals(MAYA_NETWORK_ID, maya().dappHeader(Cosmos.SigningMode.Protobuf, "").chainId)
    }

    @Test
    fun `maya signAmino header carries the maya chain id`() {
        assertEquals(MAYA_NETWORK_ID, maya().dappHeader(Cosmos.SigningMode.JSON, null).chainId)
    }

    @Test
    fun `thor header carries the runtime network id, not the wallet-core constant`() {
        ThorChainHelper.THORCHAIN_NETWORK_ID = "thorchain-2"

        assertEquals(
            "thorchain-2",
            thor().dappHeader(Cosmos.SigningMode.Protobuf, "thorchain-2").chainId,
        )
    }

    @Test
    fun `a request carrying the resolved chain id is accepted`() {
        assertEquals(MAYA_NETWORK_ID, maya().resolveDappChainId(MAYA_NETWORK_ID))
    }

    @Test
    fun `a blank request chain id falls back to the resolved network id`() {
        assertEquals(MAYA_NETWORK_ID, maya().resolveDappChainId("  "))
    }

    @Test
    fun `a maya request carrying the thorchain id is rejected`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                maya().resolveDappChainId(THOR_NETWORK_ID)
            }

        assertEquals(
            "dApp requested chain id \"$THOR_NETWORK_ID\", but this vault signs \"$MAYA_NETWORK_ID\"",
            error.message,
        )
    }

    @Test
    fun `a thorchain request carrying the maya id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            thor().resolveDappChainId(MAYA_NETWORK_ID)
        }
    }

    @Test
    fun `the rest of the dapp header is unchanged`() {
        val header = maya().dappHeader(Cosmos.SigningMode.JSON, null)

        assertEquals(Cosmos.SigningMode.JSON, header.signingMode)
        assertEquals(ByteString.copyFrom(PUBLIC_KEY_DATA), header.publicKey)
        assertEquals(12345L, header.accountNumber)
        assertEquals(7L, header.sequence)
        assertEquals(Cosmos.BroadcastMode.SYNC, header.mode)
    }

    private fun ThorChainHelper.dappHeader(
        signingMode: Cosmos.SigningMode,
        requestChainId: String?,
    ): Cosmos.SigningInput =
        buildDappSigningInput(
                signingMode = signingMode,
                cosmosSpecific = COSMOS_SPECIFIC,
                publicKeyData = PUBLIC_KEY_DATA,
                requestChainId = requestChainId,
            )
            .build()

    private fun maya() = ThorChainHelper.maya(VAULT_PUBLIC_KEY, VAULT_CHAIN_CODE)

    private fun thor() = ThorChainHelper.thor(VAULT_PUBLIC_KEY, VAULT_CHAIN_CODE)

    private companion object {
        const val MAYA_NETWORK_ID = "mayachain-mainnet-v1"
        const val THOR_NETWORK_ID = "thorchain-1"
        const val VAULT_PUBLIC_KEY =
            "025476c2e83188368da1ff3e292e7acafcdb3566bb0ad253f62fc70f07aeee6357"
        const val VAULT_CHAIN_CODE = ""

        val PUBLIC_KEY_DATA = ByteArray(33) { it.toByte() }

        val COSMOS_SPECIFIC =
            BlockChainSpecific.Cosmos(
                accountNumber = BigInteger.valueOf(12345),
                sequence = BigInteger.valueOf(7),
                gas = BigInteger.valueOf(2_000_000_000),
                ibcDenomTraces = null,
                transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
            )
    }
}
