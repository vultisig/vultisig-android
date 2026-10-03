package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wallet.core.jni.CoinType

/**
 * Pins the signing-time invariant that an ERC-20 transfer is never addressed to the token's own
 * contract. This runs on every signer (initiator and co-signer) from the relayed payload, so it
 * guards a co-signer whose initiator misresolved the recipient (e.g. an EIP-681 `transfer` link
 * whose `address` parameter was dropped and the token contract used instead). The `require` throws
 * in `getPreSignedInputData` before any WalletCore JNI call, so it is exercised headlessly.
 */
class ERC20HelperSelfTransferTest {

    private val helper = ERC20Helper(CoinType.ETHEREUM, vaultHexPublicKey = "", vaultHexChainCode = "")

    private fun payload(toAddress: String) =
        KeysignPayload(
            coin =
                Coin(
                    chain = Chain.Ethereum,
                    ticker = "USDC",
                    logo = "usdc",
                    address = "0x1234567890123456789012345678901234567890",
                    decimal = 6,
                    hexPublicKey = "",
                    priceProviderID = "usd-coin",
                    contractAddress = USDC,
                    isNativeToken = false,
                ),
            toAddress = toAddress,
            toAmount = BigInteger.valueOf(8000),
            blockChainSpecific =
                BlockChainSpecific.Ethereum(
                    maxFeePerGasWei = BigInteger("1000000000"),
                    priorityFeeWei = BigInteger("100000000"),
                    nonce = BigInteger.valueOf(1),
                    gasLimit = BigInteger.valueOf(210_000),
                ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    @Test
    fun `refuses an ERC-20 transfer to the token's own contract`() {
        val e =
            assertThrows(IllegalArgumentException::class.java) {
                helper.getPreSignedImageHash(payload(USDC))
            }
        assertTrue(e.message!!.contains("token's own contract"), e.message)
    }

    @Test
    fun `refuses it regardless of address casing`() {
        val e =
            assertThrows(IllegalArgumentException::class.java) {
                helper.getPreSignedImageHash(payload(USDC.uppercase()))
            }
        assertTrue(e.message!!.contains("token's own contract"), e.message)
    }

    private companion object {
        // USDC on Ethereum mainnet (the real revert case on etherscan).
        const val USDC = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
    }
}
