package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers [SecurityScannerTransactionFactory.createRecipientSecurityScannerTransaction] — the
 * recipient-only fallback that screens a swap's external recipient on the destination chain when
 * the source-chain swap transaction itself can't be scanned (#5348).
 */
class SecurityScannerRecipientTransactionTest {

    private val factory = SecurityScannerTransactionFactory(mockk<SolanaApi>(), mockk<SuiApi>())

    private val solana =
        Coin(
            chain = Chain.Solana,
            ticker = "SOL",
            logo = "sol",
            address = "8sLbNZoA1cfnvMJLPfp98ZLAnFSYCFApfJKMbiXNLwxj",
            decimal = 9,
            hexPublicKey = "",
            priceProviderID = "solana",
            contractAddress = "",
            isNativeToken = true,
        )

    private val ethereum =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "eth",
            address = VAULT_ETH_ADDRESS,
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun swap(dstToken: Coin, externalRecipient: String?) =
        SwapTransaction.RegularSwapTransaction(
            id = "tx-1",
            vaultId = "vault-1",
            srcToken = solana,
            srcTokenValue = TokenValue(value = BigInteger.ONE, token = solana),
            dstToken = dstToken,
            dstAddress = "router-address",
            expectedDstTokenValue = TokenValue(value = BigInteger.ONE, token = dstToken),
            blockChainSpecific = mockk<BlockChainSpecificAndUtxo>(),
            estimatedFees = TokenValue(value = BigInteger.ZERO, token = dstToken),
            gasFees = TokenValue(value = BigInteger.ZERO, token = solana),
            memo = null,
            payload = mockk<SwapPayload>(),
            isApprovalRequired = false,
            gasFeeFiatValue = FiatValue(value = BigDecimal.ZERO, currency = "USD"),
            externalRecipient = externalRecipient,
        )

    @Test
    fun `EVM destination screens the external recipient as a native transfer from the vault`() {
        val transaction = swap(dstToken = ethereum, externalRecipient = EXTERNAL_RECIPIENT)

        val scan = factory.createRecipientSecurityScannerTransaction(transaction)

        assertEquals(
            SecurityScannerTransaction(
                chain = Chain.Ethereum,
                type = SecurityTransactionType.COIN_TRANSFER,
                from = VAULT_ETH_ADDRESS,
                to = EXTERNAL_RECIPIENT,
                amount = BigInteger.ZERO,
                data = "0x",
            ),
            scan,
        )
    }

    @Test
    fun `a swap back into the vault has no recipient to screen`() {
        val transaction = swap(dstToken = ethereum, externalRecipient = null)

        val exception =
            runCatching { factory.createRecipientSecurityScannerTransaction(transaction) }
                .exceptionOrNull()

        assertTrue(exception is SecurityScannerException)
    }

    @Test
    fun `blank recipient is treated as absent rather than scanned`() {
        val transaction = swap(dstToken = ethereum, externalRecipient = "   ")

        val exception =
            runCatching { factory.createRecipientSecurityScannerTransaction(transaction) }
                .exceptionOrNull()

        assertTrue(exception is SecurityScannerException)
    }

    /**
     * Non-EVM destinations have no address-shaped scan at the provider — their endpoints take a
     * serialized transaction, which doesn't exist for an outbound leg the vault never signs.
     */
    @Test
    fun `non-EVM destination is rejected so the caller skips the fallback`() {
        val transaction = swap(dstToken = solana, externalRecipient = EXTERNAL_RECIPIENT)

        val exception =
            runCatching { factory.createRecipientSecurityScannerTransaction(transaction) }
                .exceptionOrNull()

        assertTrue(exception is SecurityScannerException)
        assertEquals(Chain.Solana, (exception as SecurityScannerException).chain)
    }

    /** Blockaid scans an address pair; a missing vault address would send an empty `from`. */
    @Test
    fun `missing vault address on the destination chain is rejected`() {
        val transaction =
            swap(dstToken = ethereum.copy(address = ""), externalRecipient = EXTERNAL_RECIPIENT)

        val exception =
            runCatching { factory.createRecipientSecurityScannerTransaction(transaction) }
                .exceptionOrNull()

        assertTrue(exception is IllegalArgumentException)
    }

    private companion object {
        const val VAULT_ETH_ADDRESS = "0xAbCdEf1234567890AbCdEf1234567890AbCdEf12"
        const val EXTERNAL_RECIPIENT = "0x9876543210FeDcBa9876543210FeDcBa98765432"
    }
}
