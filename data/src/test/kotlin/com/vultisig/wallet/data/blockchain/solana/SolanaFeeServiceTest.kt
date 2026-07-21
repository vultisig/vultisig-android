@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.blockchain.solana

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.blockchain.model.GasFees
import com.vultisig.wallet.data.blockchain.model.Swap
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.chains.helpers.SOLANA_PRIORITY_FEE_LIMIT
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Regression guard for #5115 / PR #5156 — the Solana send fee estimate double-counted the priority
 * fee (~2× the real on-chain fee).
 *
 * `getFeeForMessage` already folds the ComputeBudget priority fee into the returned message fee
 * (base signature fee + price × limit / 1e6). The fix stopped adding a separate priority term on
 * top, so the displayed send fee is `messageFee + rentExemption`, not `messageFee + priority + …`.
 *
 * These tests stub the versioned-message serializer (a WalletCore JNI call not available in JVM
 * unit tests) via the service's test seam so the fee-composition arithmetic can be locked without
 * the native library.
 */
class SolanaFeeServiceTest {

    private val solanaApi: SolanaApi = mockk()
    private val service =
        SolanaFeeService(solanaApi, serializeVersionedMessage = { _, _ -> "serialized-msg" })

    private val messageFee = BigInteger.valueOf(105_000L) // 5000 base + 100000 folded-in priority
    private val rentExemption = BigInteger.valueOf(2_039_280L)
    private val dynamicPriorityFee = BigInteger.valueOf(1_234L)

    @BeforeEach
    fun setUp() {
        coEvery { solanaApi.getRecentBlockHash() } returns "blockhash"
        coEvery { solanaApi.getFeeForMessage(any()) } returns messageFee
        coEvery { solanaApi.getMedianPriorityFee(any()) } returns dynamicPriorityFee
    }

    @Test
    fun `native SOL send fee is message fee only, priority not double-counted`() = runTest {
        val fee = service.calculateFees(nativeTransfer()) as GasFees

        // The whole point of #5156: amount equals the RPC message fee (which already contains the
        // priority fee) with no extra priority term added on top. A double-count would exceed this.
        assertEquals(messageFee, fee.amount)
        assertEquals(dynamicPriorityFee, fee.price)
        assertEquals(SOLANA_PRIORITY_FEE_LIMIT.toBigInteger(), fee.limit)

        // Native transfers never touch a token account, so no rent-exemption RPC is made.
        coVerify(exactly = 0) { solanaApi.getMinimumBalanceForRentExemption() }
    }

    @Test
    fun `SPL send with no recipient token account adds rent exemption once on top of message fee`() =
        runTest {
            coEvery { solanaApi.getTokenAssociatedAccountByOwner(SENDER, MINT) } returns
                Pair("senderAta", false)
            // Recipient has no associated token account -> sender pays rent exemption to create it.
            coEvery { solanaApi.getTokenAssociatedAccountByOwner(RECIPIENT, MINT) } returns
                Pair("", false)
            coEvery { solanaApi.getMinimumBalanceForRentExemption() } returns rentExemption

            val fee = service.calculateFees(tokenTransfer()) as GasFees

            // Priority counted once (inside messageFee) + rent counted once. No priority
            // double-count.
            assertEquals(messageFee + rentExemption, fee.amount)
            assertEquals(dynamicPriorityFee, fee.price)
            assertEquals(SOLANA_PRIORITY_FEE_LIMIT.toBigInteger(), fee.limit)
        }

    @Test
    fun `SPL send with existing recipient token account charges no rent exemption`() = runTest {
        coEvery { solanaApi.getTokenAssociatedAccountByOwner(SENDER, MINT) } returns
            Pair("senderAta", false)
        // Recipient already has an associated token account -> no rent to pay.
        coEvery { solanaApi.getTokenAssociatedAccountByOwner(RECIPIENT, MINT) } returns
            Pair("recipientAta", false)

        val fee = service.calculateFees(tokenTransfer()) as GasFees

        assertEquals(messageFee, fee.amount)
        coVerify(exactly = 0) { solanaApi.getMinimumBalanceForRentExemption() }
    }

    @Test
    fun `swap default fee converts micro-lamports-per-CU to lamports and adds base once`() =
        runTest {
            // price is in micro-lamports per compute unit; the estimate must scale by the CU limit
            // and divide by 1e6 to reach lamports (the per-CU-price vs total-lamports distinction
            // from #5101/#5114), then add the flat base fee exactly once.
            val pricePerCu = BigInteger.valueOf(1_000_000L)
            coEvery { solanaApi.getMedianPriorityFee(any()) } returns pricePerCu

            val fee = service.calculateFees(swap()) as GasFees

            // priorityAmount = 1_000_000 * 100_000 / 1_000_000 = 100_000 lamports
            // amount = base(5_000) + 100_000 = 105_000 (priority counted once, not doubled)
            assertEquals(BigInteger.valueOf(105_000L), fee.amount)
            assertEquals(pricePerCu, fee.price)
            assertEquals(SOLANA_PRIORITY_FEE_LIMIT.toBigInteger(), fee.limit)
        }

    private fun nativeTransfer() =
        Transfer(
            coin = solCoin(),
            vault = VaultData(vaultHexPublicKey = VAULT_PUBKEY, vaultHexChainCode = "chain"),
            amount = BigInteger.valueOf(1_000_000L),
            to = RECIPIENT,
        )

    private fun tokenTransfer() =
        Transfer(
            coin = usdcCoin(),
            vault = VaultData(vaultHexPublicKey = VAULT_PUBKEY, vaultHexChainCode = "chain"),
            amount = BigInteger.valueOf(1_000_000L),
            to = RECIPIENT,
        )

    private fun swap() =
        Swap(
            coin = solCoin(),
            vault = VaultData(vaultHexPublicKey = VAULT_PUBKEY, vaultHexChainCode = "chain"),
            amount = BigInteger.valueOf(1_000_000L),
            to = RECIPIENT,
            callData = "",
            approvalData = null,
        )

    private fun solCoin() =
        Coin(
            chain = Chain.Solana,
            ticker = "SOL",
            logo = "",
            address = SENDER,
            decimal = 9,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun usdcCoin() =
        Coin(
            chain = Chain.Solana,
            ticker = "USDC",
            logo = "",
            address = SENDER,
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = MINT,
            isNativeToken = false,
        )

    private companion object {
        const val VAULT_PUBKEY = "vaultpub"
        const val SENDER = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"
        const val RECIPIENT = "3xM8c79mk7fvcz5ENZgMbChPJGWZAjFqwdDzZp4R2gHR"
        const val MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
    }
}
