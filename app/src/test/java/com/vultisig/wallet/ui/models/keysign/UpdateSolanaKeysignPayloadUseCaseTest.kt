package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class UpdateSolanaKeysignPayloadUseCaseTest {

    private val solanaApi: SolanaApi = mockk()
    private val useCase = UpdateSolanaKeysignPayloadUseCase(solanaApi)

    private val fastVault =
        Vault(
            id = "fast-vault",
            name = "Fast Vault",
            localPartyID = "iPhone-XYZ",
            signers = listOf("iPhone-XYZ", "Server-12345"),
        )

    private val secureVault =
        Vault(
            id = "secure-vault",
            name = "Secure Vault",
            localPartyID = "iPhone-XYZ",
            signers = listOf("iPhone-XYZ", "iPad-ABC"),
        )

    private val solanaCoin =
        Coin(
            chain = Chain.Solana,
            ticker = "SOL",
            logo = "sol",
            address = "SolSender1111",
            decimal = 9,
            hexPublicKey = "",
            priceProviderID = "solana",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun solanaKeysignPayload(recentBlockHash: String) =
        KeysignPayload(
            coin = solanaCoin,
            toAddress = "SolRecipient1111",
            toAmount = BigInteger.ONE,
            blockChainSpecific =
                BlockChainSpecific.Solana(
                    recentBlockHash = recentBlockHash,
                    priorityFee = BigInteger.ZERO,
                    priorityLimit = BigInteger.ZERO,
                ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    @Test
    fun `Fast Vault Solana payload is refreshed with the finalized-commitment block hash`() =
        runTest {
            coEvery { solanaApi.getFinalizedBlockHash() } returns "FinalizedBlockHash111"

            val result =
                useCase(solanaKeysignPayload(recentBlockHash = "StaleBlockHash111"), fastVault)

            val specific = result?.blockChainSpecific
            assertEquals(
                "FinalizedBlockHash111",
                (specific as? BlockChainSpecific.Solana)?.recentBlockHash,
            )
            coVerify(exactly = 0) { solanaApi.getRecentBlockHash() }
        }

    @Test
    fun `Secure Vault Solana payload keeps the confirmed-commitment block hash`() = runTest {
        coEvery { solanaApi.getRecentBlockHash() } returns "ConfirmedBlockHash111"

        val result =
            useCase(solanaKeysignPayload(recentBlockHash = "StaleBlockHash111"), secureVault)

        val specific = result?.blockChainSpecific
        assertEquals(
            "ConfirmedBlockHash111",
            (specific as? BlockChainSpecific.Solana)?.recentBlockHash,
        )
        coVerify(exactly = 0) { solanaApi.getFinalizedBlockHash() }
    }

    @Test
    fun `non-Solana payload is returned unchanged without calling SolanaApi`() = runTest {
        val ethCoin =
            solanaCoin.copy(chain = Chain.Ethereum, ticker = "ETH", priceProviderID = "ethereum")
        val ethPayload =
            KeysignPayload(
                coin = ethCoin,
                toAddress = "0xdest",
                toAmount = BigInteger.ONE,
                blockChainSpecific =
                    BlockChainSpecific.Ethereum(
                        maxFeePerGasWei = BigInteger.ZERO,
                        priorityFeeWei = BigInteger.ZERO,
                        nonce = BigInteger.ZERO,
                        gasLimit = BigInteger.valueOf(21000),
                    ),
                vaultPublicKeyECDSA = "",
                vaultLocalPartyID = "",
                libType = null,
                wasmExecuteContractPayload = null,
            )

        val result = useCase(ethPayload, fastVault)

        assertSame(ethPayload, result)
        coVerify(exactly = 0) { solanaApi.getFinalizedBlockHash() }
        coVerify(exactly = 0) { solanaApi.getRecentBlockHash() }
    }

    @Test
    fun `null payload returns null without calling SolanaApi`() = runTest {
        val result = useCase(null, fastVault)

        assertNull(result)
        coVerify(exactly = 0) { solanaApi.getFinalizedBlockHash() }
        coVerify(exactly = 0) { solanaApi.getRecentBlockHash() }
    }
}
