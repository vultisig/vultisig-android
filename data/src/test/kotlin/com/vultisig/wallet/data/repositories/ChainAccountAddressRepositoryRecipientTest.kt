package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.SolanaAccountOwnership
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.models.Chain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers the ordering in `validateRecipient`. Chain validation itself is WalletCore JNI, so it is
 * stubbed here; what is under test is that an address failing it is reported as such, that the
 * ownership question is asked only after it and only on Solana, that it is asked of every Solana
 * address rather than only the off-curve ones, and that the curve decides nothing but which way an
 * unreachable cluster resolves.
 */
class ChainAccountAddressRepositoryRecipientTest {

    private val solanaApi: SolanaApi = mockk()

    private val repository = spyk(ChainAccountAddressRepositoryImpl(solanaApi = solanaApi))

    @Test
    fun `a solana token account is well-formed but not a wallet`() = runTest {
        every { repository.isValid(Chain.Solana, TOKEN_ACCOUNT) } returns true
        coEvery { solanaApi.getAccountOwnership(TOKEN_ACCOUNT) } returns
            SolanaAccountOwnership.Owned(TOKEN_PROGRAM)

        assertEquals(
            RecipientValidity.NotAWalletAddress,
            repository.validateRecipient(Chain.Solana, TOKEN_ACCOUNT),
        )
    }

    @Test
    fun `a token-2022 account is not a wallet either`() = runTest {
        every { repository.isValid(Chain.Solana, TOKEN_ACCOUNT) } returns true
        coEvery { solanaApi.getAccountOwnership(TOKEN_ACCOUNT) } returns
            SolanaAccountOwnership.Owned(TOKEN_2022_PROGRAM)

        assertEquals(
            RecipientValidity.NotAWalletAddress,
            repository.validateRecipient(Chain.Solana, TOKEN_ACCOUNT),
        )
    }

    @Test
    fun `an off-curve address another program owns is a valid recipient`() = runTest {
        // A Squads vault or a DAO treasury: off the curve like every program address, but it holds
        // SPL through the ATAs it owns and spends them with invoke_signed.
        every { repository.isValid(Chain.Solana, TOKEN_ACCOUNT) } returns true
        coEvery { solanaApi.getAccountOwnership(TOKEN_ACCOUNT) } returns
            SolanaAccountOwnership.Owned(SYSTEM_PROGRAM)

        assertEquals(
            RecipientValidity.Valid,
            repository.validateRecipient(Chain.Solana, TOKEN_ACCOUNT),
        )
    }

    @Test
    fun `an off-curve address with no account on-chain is a valid recipient`() = runTest {
        // A vault that has only ever held SPL has no account of its own; the tokens sit in ATAs
        // derived from it. Refusing it would refuse the very destination the curve rule is accused
        // of blocking.
        every { repository.isValid(Chain.Solana, TOKEN_ACCOUNT) } returns true
        coEvery { solanaApi.getAccountOwnership(TOKEN_ACCOUNT) } returns
            SolanaAccountOwnership.Missing

        assertEquals(
            RecipientValidity.Valid,
            repository.validateRecipient(Chain.Solana, TOKEN_ACCOUNT),
        )
    }

    @Test
    fun `an off-curve address the cluster cannot be asked about is refused`() = runTest {
        every { repository.isValid(Chain.Solana, TOKEN_ACCOUNT) } returns true
        coEvery { solanaApi.getAccountOwnership(TOKEN_ACCOUNT) } returns
            SolanaAccountOwnership.Unavailable

        assertEquals(
            RecipientValidity.NotAWalletAddress,
            repository.validateRecipient(Chain.Solana, TOKEN_ACCOUNT),
        )
    }

    @Test
    fun `an on-curve wallet the cluster calls system-owned is a valid recipient`() = runTest {
        every { repository.isValid(Chain.Solana, WALLET) } returns true
        coEvery { solanaApi.getAccountOwnership(WALLET) } returns
            SolanaAccountOwnership.Owned(SYSTEM_PROGRAM)

        assertEquals(RecipientValidity.Valid, repository.validateRecipient(Chain.Solana, WALLET))
    }

    @Test
    fun `an on-curve auxiliary token account is not a wallet either`() = runTest {
        // Being on the curve is not what makes an address a wallet. A token account opened against
        // a fresh keypair rather than derived as an ATA looks exactly like one, and strands an SPL
        // transfer exactly like an ATA would, so the cluster is asked about these too.
        every { repository.isValid(Chain.Solana, AUXILIARY_TOKEN_ACCOUNT) } returns true
        coEvery { solanaApi.getAccountOwnership(AUXILIARY_TOKEN_ACCOUNT) } returns
            SolanaAccountOwnership.Owned(TOKEN_PROGRAM)

        assertEquals(
            RecipientValidity.NotAWalletAddress,
            repository.validateRecipient(Chain.Solana, AUXILIARY_TOKEN_ACCOUNT),
        )
    }

    @Test
    fun `an on-curve address the cluster cannot be asked about stays a valid recipient`() =
        runTest {
            // The opposite fallback to the off-curve case, and deliberately so: an address someone
            // can
            // hold the key for is what every user pastes, so a blinking RPC must not refuse them
            // all.
            every { repository.isValid(Chain.Solana, WALLET) } returns true
            coEvery { solanaApi.getAccountOwnership(WALLET) } returns
                SolanaAccountOwnership.Unavailable

            assertEquals(
                RecipientValidity.Valid,
                repository.validateRecipient(Chain.Solana, WALLET),
            )
        }

    @Test
    fun `failing chain validation is reported as such, not as the ownership rule`() = runTest {
        every { repository.isValid(Chain.Solana, "garbage") } returns false

        assertEquals(
            RecipientValidity.InvalidForChain,
            repository.validateRecipient(Chain.Solana, "garbage"),
        )
        coVerify(exactly = 0) { solanaApi.getAccountOwnership(any()) }
    }

    @Test
    fun `the ownership rule is scoped to solana`() = runTest {
        // The same bytes on another chain must not be second-guessed: account ownership means
        // nothing outside Solana, and every chain here validates through its own rules.
        every { repository.isValid(any(), any()) } returns true

        listOf(Chain.Ethereum, Chain.Bitcoin, Chain.Ton, Chain.Sui, Chain.Polkadot).forEach {
            assertEquals(
                RecipientValidity.Valid,
                repository.validateRecipient(it, TOKEN_ACCOUNT),
                it.raw,
            )
        }
        coVerify(exactly = 0) { solanaApi.getAccountOwnership(any()) }
    }

    private companion object {
        /** The wallet's wrapped-SOL associated token account, pinned in the derivation test. */
        const val TOKEN_ACCOUNT = "GppmkdEmuqNgS7uY5SSN3gXEamJrcPG9197wBdQ37NLc"

        const val WALLET = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"

        /** On the curve, unlike [TOKEN_ACCOUNT]: a token account opened against its own keypair. */
        const val AUXILIARY_TOKEN_ACCOUNT = "5tzFkiKscXHK5ZXCGbXZxdw7gTjjD1mBwuoFbhUvuAi9"

        const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"

        const val TOKEN_2022_PROGRAM = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"

        const val SYSTEM_PROGRAM = "11111111111111111111111111111111"
    }
}
