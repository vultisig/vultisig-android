package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import io.mockk.every
import io.mockk.spyk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers the ordering in `validateRecipient`. Chain validation itself is WalletCore JNI, so it is
 * stubbed here; what is under test is that an address failing it is reported as such, and that the
 * Solana curve rule is applied only after it and only to Solana.
 */
class ChainAccountAddressRepositoryRecipientTest {

    private val repository = spyk(ChainAccountAddressRepositoryImpl())

    @Test
    fun `a solana token account is well-formed but not a wallet`() {
        every { repository.isValid(Chain.Solana, TOKEN_ACCOUNT) } returns true

        assertEquals(
            RecipientValidity.NotAWalletAddress,
            repository.validateRecipient(Chain.Solana, TOKEN_ACCOUNT),
        )
    }

    @Test
    fun `a solana wallet address is a valid recipient`() {
        every { repository.isValid(Chain.Solana, WALLET) } returns true

        assertEquals(RecipientValidity.Valid, repository.validateRecipient(Chain.Solana, WALLET))
    }

    @Test
    fun `failing chain validation is reported as such, not as the curve rule`() {
        every { repository.isValid(Chain.Solana, "garbage") } returns false

        assertEquals(
            RecipientValidity.InvalidForChain,
            repository.validateRecipient(Chain.Solana, "garbage"),
        )
    }

    @Test
    fun `the curve rule is scoped to solana`() {
        // The same bytes on another chain must not be second-guessed: off-curve means nothing
        // outside ed25519, and every non-Solana chain here validates through its own rules.
        every { repository.isValid(any(), any()) } returns true

        listOf(Chain.Ethereum, Chain.Bitcoin, Chain.Ton, Chain.Sui, Chain.Polkadot).forEach {
            assertEquals(
                RecipientValidity.Valid,
                repository.validateRecipient(it, TOKEN_ACCOUNT),
                it.raw,
            )
        }
    }

    private companion object {
        /** The wallet's wrapped-SOL associated token account, pinned in the derivation test. */
        const val TOKEN_ACCOUNT = "GppmkdEmuqNgS7uY5SSN3gXEamJrcPG9197wBdQ37NLc"

        const val WALLET = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"
    }
}
