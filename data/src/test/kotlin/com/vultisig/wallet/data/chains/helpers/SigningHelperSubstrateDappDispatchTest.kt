package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SubstrateSignerPayload
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * A Substrate dApp payload must be hashed as the signer payload in its memo. The native helpers
 * would build a `transfer_keep_alive` around the empty wire `toAddress` (and refuse it), so
 * reaching the dApp signer's bytes here proves the dispatcher took the dApp route for both chains.
 */
class SigningHelperSubstrateDappDispatchTest {

    private val vault = Vault(id = "v1", name = "Test Vault")

    @Test
    fun `a Polkadot dApp payload signs the memo's extrinsic payload`() {
        val payload = dappPayload(Chain.Polkadot, "DOT", 10, POLKADOT_GENESIS)

        SigningHelper.getKeysignMessages(payload, vault) shouldBe
            SubstrateDappSigner.getPreSignedImageHash(signerPayload(POLKADOT_GENESIS))
    }

    @Test
    fun `a Bittensor dApp payload signs the memo's extrinsic payload`() {
        val payload = dappPayload(Chain.Bittensor, "TAO", 9, BITTENSOR_GENESIS)

        SigningHelper.getKeysignMessages(payload, vault) shouldBe
            SubstrateDappSigner.getPreSignedImageHash(signerPayload(BITTENSOR_GENESIS))
    }

    // Both chains sign with the same ed25519 key, so a payload labelled Polkadot but carrying
    // Bittensor's genesis would yield a signature valid on Bittensor while Verify says Polkadot.
    @Test
    fun `a payload whose genesis hash belongs to the other chain is refused`() {
        shouldThrow<IllegalStateException> {
            SigningHelper.getKeysignMessages(
                dappPayload(Chain.Polkadot, "DOT", 10, BITTENSOR_GENESIS),
                vault,
            )
        }
        shouldThrow<IllegalStateException> {
            SigningHelper.getKeysignMessages(
                dappPayload(Chain.Bittensor, "TAO", 9, POLKADOT_GENESIS),
                vault,
            )
        }
    }

    private fun signerPayload(genesisHash: String) =
        requireNotNull(SubstrateSignerPayload.fromMemo(memo(genesisHash)))

    private fun dappPayload(chain: Chain, ticker: String, decimals: Int, genesisHash: String) =
        KeysignPayload(
            coin =
                Coin(
                    chain = chain,
                    ticker = ticker,
                    logo = "",
                    address = "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5",
                    decimal = decimals,
                    hexPublicKey = "",
                    priceProviderID = "",
                    contractAddress = "",
                    isNativeToken = true,
                ),
            // What the extension puts on the wire for a non-transfer call.
            toAddress = "",
            toAmount = BigInteger.ZERO,
            blockChainSpecific =
                BlockChainSpecific.Polkadot(
                    recentBlockHash = "0x" + "00".repeat(32),
                    nonce = BigInteger.ZERO,
                    currentBlockNumber = BigInteger.ZERO,
                    specVersion = 0u,
                    transactionVersion = 0u,
                    genesisHash = genesisHash,
                    gas = 250_000_000uL,
                ),
            memo = memo(genesisHash),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = SigningLibType.DKLS,
            wasmExecuteContractPayload = null,
        )

    private companion object {
        const val POLKADOT_GENESIS =
            "0x91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val BITTENSOR_GENESIS =
            "0x2f0555cc76fc2840a25a6ea3b9637146806f1f44b090c175ffde2a7e5ab36c03"

        fun memo(genesisHash: String) =
            """{"address":"","blockHash":"0x${"00".repeat(32)}","blockNumber":"0x00000000",""" +
                """"era":"0x0000","genesisHash":"$genesisHash","method":"0x0000",""" +
                """"nonce":"0x00000000","specVersion":"0x00000000","tip":"0x${"00".repeat(16)}",""" +
                """"transactionVersion":"0x00000000","signedExtensions":[],"version":4}"""
    }
}
