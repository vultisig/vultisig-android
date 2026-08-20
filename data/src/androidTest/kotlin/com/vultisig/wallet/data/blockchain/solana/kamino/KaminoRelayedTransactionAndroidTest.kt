package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.WalletCoreNative
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import wallet.core.jni.SolanaAddress

/**
 * The co-signing device's half of the prepare pipeline, on the same live transactions: what the
 * initiating device builds is what a joining device recognises (issue #5644).
 *
 * The unit coverage next door pins the rules against hand-built instruction lists; this pins them
 * against real Kamino bytes, which is what proves the kVault discriminators and the share-account
 * derivation are the ones those transactions actually carry.
 */
class KaminoRelayedTransactionAndroidTest {

    private val vault = KaminoVaultRegistry.STEAKHOUSE_USDC

    @Before
    fun loadWalletCore() {
        WalletCoreNative.ensureLoaded()
    }

    @Test
    fun a_prepared_deposit_is_recognised_as_a_deposit_into_its_vault() {
        val intent =
            ResolveKaminoRelayedIntentUseCase()(listOf(preparedDeposit()), KaminoFixtures.WALLET)

        assertEquals(vault, intent?.vault)
        assertEquals(KaminoAction.DEPOSIT, intent?.action)
        // The fixture was built for 1 USDC, and the instruction carries it in base units — the same
        // figure the payload's toAmount states, which is what lets the two devices be compared.
        assertEquals(BigInteger.valueOf(1_000_000), intent?.amount)
    }

    @Test
    fun another_wallets_transaction_cannot_be_named_by_this_signer() {
        // The vault is named by the signer's own share account, so the same bytes read against a
        // different wallet resolve to nothing rather than to somebody else's position.
        val intent = ResolveKaminoRelayedIntentUseCase()(listOf(preparedDeposit()), OTHER_WALLET)

        assertNull(intent)
    }

    @Test
    fun a_live_withdraw_reads_as_a_withdraw_of_shares() {
        // Decoded straight from the captured response rather than prepared: this withdraw carries
        // the withdraw-everything sentinel, so preparing it is refused before it can be relayed.
        // The reader is what is under test here, and it needs no budget or memo to do its job.
        val decoded = KaminoTransactionDecoder.decode(KaminoFixtures.WITHDRAW)
        val shareAccount =
            SolanaAddress(KaminoFixtures.WALLET).defaultTokenAddress(vault.sharesMint)

        val intent = KaminoRelayedTransactionReader.read(decoded, mapOf(shareAccount to vault))

        assertEquals(vault, intent?.vault)
        assertEquals(KaminoAction.WITHDRAW, intent?.action)
    }

    @Test
    fun a_recognised_deposit_carries_the_budget_wallet_core_encoded_into_it() {
        // The limit instruction is encoded inside WalletCore's `setComputeUnitLimit`, so this is
        // where the reader meets the real encoding rather than one the tests wrote themselves. The
        // joining device prices its fee row off this pair, and refuses the payload when the fields
        // relayed beside the bytes disagree with it.
        val intent =
            ResolveKaminoRelayedIntentUseCase()(
                listOf(preparedDeposit(price = BigInteger.valueOf(250_000))),
                KaminoFixtures.WALLET,
            )

        assertEquals(
            KaminoPriorityFee(
                limit = KaminoComputeBudget.unitLimitFor(vault, KaminoAction.DEPOSIT),
                price = BigInteger.valueOf(250_000),
            ),
            intent?.priorityFee,
        )
    }

    /** The bytes the initiating device would relay: the live response, budgeted and memo'd. */
    private fun preparedDeposit(price: BigInteger? = null): String = runBlocking {
        KaminoTransactionPreparer(KaminoFixtureApi(KaminoFixtures.DEPOSIT))
            .prepare(
                vault = vault,
                action = KaminoAction.DEPOSIT,
                walletAddress = KaminoFixtures.WALLET,
                amount = "1",
                networkUnitPrice = price,
            )
    }

    private companion object {
        /** A valid Solana address that is not the one these fixtures were built for. */
        const val OTHER_WALLET = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"
    }
}
