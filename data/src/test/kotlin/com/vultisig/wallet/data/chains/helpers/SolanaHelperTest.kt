package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SolanaHelperTest {

    /**
     * An SPL send whose sender holds no associated token account must fail with a clear,
     * ticker-bearing error rather than passing `null` into `CreateAndTransferToken` and crashing
     * with an NPE. The guard runs before any WalletCore call, so it is exercised without the native
     * library.
     */
    @Test
    fun `SPL transfer without a sender token account fails with a ticker-bearing error`() {
        val usdc =
            Coin(
                chain = Chain.Solana,
                ticker = "USDC",
                logo = "",
                address = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM",
                decimal = 6,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
                isNativeToken = false,
            )
        val payload =
            KeysignPayload(
                coin = usdc,
                toAddress = "3xM8c79mk7fvcz5ENZgMbChPJGWZAjFqwdDzZp4R2gHR",
                toAmount = BigInteger.valueOf(1_000_000L),
                blockChainSpecific =
                    BlockChainSpecific.Solana(
                        recentBlockHash = "",
                        priorityFee = BigInteger.ZERO,
                        priorityLimit = SOLANA_PRIORITY_FEE_LIMIT.toBigInteger(),
                        fromAddressPubKey = null,
                        toAddressPubKey = null,
                        programId = false,
                    ),
                vaultPublicKeyECDSA = "",
                vaultLocalPartyID = "",
                libType = SigningLibType.GG20,
                wasmExecuteContractPayload = null,
            )

        val error =
            assertThrows<IllegalStateException> { SolanaHelper("").getPreSignedImageHash(payload) }
        assertEquals(SOLANA_MISSING_TOKEN_ACCOUNT_PREFIX + usdc.ticker, error.message)
    }
}
