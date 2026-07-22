package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.SuiCoin

/**
 * Covers [SecurityScannerTransactionFactory.buildSuiKeysignPayload] — the fix that a dApp-supplied
 * PTB (`Transaction.signSui`) must build a payload that signs it verbatim rather than a
 * reconstructed Pay/PaySui built from unrelated RPC coins (#5372). Asserted at the payload-building
 * level rather than through [SecurityScannerTransactionFactory.createSecurityScannerTransaction]
 * because the latter's last step, `SuiHelper.getZeroSignedTransaction`, calls into WalletCore's
 * native signer, which isn't loadable in a plain JVM unit test (see `SuiHelperTest`, which avoids
 * it the same way).
 */
class SecurityScannerTransactionFactoryTest {

    private val suiToken = Coins.Sui.SUI

    private fun baseTransaction(
        signSui: String? = null,
        blockChainSpecific: BlockChainSpecific =
            BlockChainSpecific.Sui(
                referenceGasPrice = BigInteger.valueOf(1000),
                gasBudget = BigInteger.valueOf(3_000_000),
                coins = emptyList(),
            ),
    ) =
        Transaction(
            id = "tx-1",
            vaultId = "vault-1",
            chainId = suiToken.chain.id,
            token = suiToken,
            srcAddress = "0xsender",
            dstAddress = "0xrecipient",
            tokenValue = TokenValue(value = BigInteger.valueOf(1_000_000_000), token = suiToken),
            fiatValue = FiatValue(value = BigDecimal.ONE, currency = "USD"),
            gasFee = TokenValue(value = BigInteger.ZERO, token = suiToken),
            totalGas = "0",
            memo = null,
            signSui = signSui,
            estimatedFee = "0",
            blockChainSpecific = blockChainSpecific,
        )

    private fun factory(suiApi: SuiApi = mockk()) =
        SecurityScannerTransactionFactory(mockk<SolanaApi>(), suiApi)

    @Test
    fun `dApp PTB builds a payload that signs the PTB verbatim and skips the RPC coin lookup`() =
        runBlocking {
            val ptb = "AAACAAgA4fUFAAAAAAAgWqQ5q8s0e0kq0a7s3w2QxJYwq7XmZ1pL0c1d8s2f3g4FAQEBAQABAAA="
            val suiApi = mockk<SuiApi>()
            val transaction = baseTransaction(signSui = ptb)

            val payload = factory(suiApi).buildSuiKeysignPayload(transaction)

            assertEquals(ptb, payload.signSui?.unsignedTxMsg)
            assertEquals(
                BlockChainSpecific.Sui(
                    referenceGasPrice = BigInteger.ZERO,
                    gasBudget = BigInteger.ZERO,
                    coins = emptyList(),
                ),
                payload.blockChainSpecific,
            )
            assertEquals(transaction.dstAddress, payload.toAddress)
            assertEquals(transaction.tokenValue.value, payload.toAmount)
            // The PTB is self-contained — no RPC coin lookup should ever happen for it.
            coVerify(exactly = 0) { suiApi.getAllCoins(any()) }
        }

    @Test
    fun `native SUI transfer without signSui reconstructs Pay-PaySui from the supplied coins`() =
        runBlocking {
            val coin =
                SuiCoin(
                    coinType = "0x2::sui::SUI",
                    coinObjectId = "0x1",
                    version = "10",
                    digest = "digest",
                    balance = "5000000",
                    previousTransaction = "",
                )
            val suiApi = mockk<SuiApi>()
            val transaction =
                baseTransaction(
                    signSui = null,
                    blockChainSpecific =
                        BlockChainSpecific.Sui(
                            referenceGasPrice = BigInteger.valueOf(1000),
                            gasBudget = BigInteger.valueOf(3_000_000),
                            coins = listOf(coin),
                        ),
                )

            val payload = factory(suiApi).buildSuiKeysignPayload(transaction)

            assertNull(payload.signSui)
            val blockChainSpecific = payload.blockChainSpecific as BlockChainSpecific.Sui
            assertEquals(listOf(coin), blockChainSpecific.coins)
            assertEquals(BigInteger.valueOf(1000), blockChainSpecific.referenceGasPrice)
            // Coins were already supplied on the transaction, so the RPC lookup is skipped.
            coVerify(exactly = 0) { suiApi.getAllCoins(any()) }
        }

    @Test
    fun `native SUI transfer with no supplied coins fetches them from suiApi`() = runBlocking {
        val fetchedCoin =
            SuiCoin(
                coinType = "0x2::sui::SUI",
                coinObjectId = "0x2",
                version = "20",
                digest = "digest-2",
                balance = "9000000",
                previousTransaction = "",
            )
        val suiApi = mockk<SuiApi> { coEvery { getAllCoins(any()) } returns listOf(fetchedCoin) }
        val transaction =
            baseTransaction(
                signSui = null,
                blockChainSpecific =
                    BlockChainSpecific.Sui(
                        referenceGasPrice = BigInteger.valueOf(1000),
                        gasBudget = BigInteger.valueOf(3_000_000),
                        coins = emptyList(),
                    ),
            )

        val payload = factory(suiApi).buildSuiKeysignPayload(transaction)

        val blockChainSpecific = payload.blockChainSpecific as BlockChainSpecific.Sui
        assertEquals(listOf(fetchedCoin), blockChainSpecific.coins)
        coVerify(exactly = 1) { suiApi.getAllCoins(transaction.srcAddress) }
    }

    @Test
    fun `native SUI transfer rejects a non-Sui blockChainSpecific`() = runBlocking {
        val transaction =
            baseTransaction(
                signSui = null,
                blockChainSpecific =
                    BlockChainSpecific.Ethereum(
                        maxFeePerGasWei = BigInteger.ZERO,
                        priorityFeeWei = BigInteger.ZERO,
                        nonce = BigInteger.ZERO,
                        gasLimit = BigInteger.ZERO,
                    ),
            )

        val exception =
            runCatching { factory().buildSuiKeysignPayload(transaction) }.exceptionOrNull()

        assertEquals(true, exception is IllegalArgumentException)
    }
}
