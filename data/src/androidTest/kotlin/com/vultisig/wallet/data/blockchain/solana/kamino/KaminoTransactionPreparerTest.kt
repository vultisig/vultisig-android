package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.WalletCoreNative
import com.vultisig.wallet.data.api.KaminoApi
import com.vultisig.wallet.data.api.KaminoPnlJson
import com.vultisig.wallet.data.api.KaminoUserPositionJson
import com.vultisig.wallet.data.api.KaminoVaultMetricsJson
import java.math.BigInteger
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import wallet.core.jni.SolanaTransaction

/**
 * End-to-end coverage of the prepare pipeline on a real Kamino deposit: compute budget in, memo on
 * top, decode, validate.
 *
 * The ordering here is load-bearing rather than incidental. WalletCore appends a compute-budget
 * instruction when none exists — the same append the memo uses — so injecting the budget after the
 * memo would leave the memo mid-list and the validator would refuse it. These tests pin that.
 */
class KaminoTransactionPreparerTest {

    private val depositFixture =
        "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACA" +
            "AQAFCX//JB25KLX6LxwUe5Pw/FGXqeETK7Jj6GQYPDbOSuemOEsQm7A2IghRdbzU0ar6Q7dIhEJA6xfcD32X" +
            "M4YwfPSF38N5IeLVVX5/3BJfld0IR08X1RB7fe6uQkeVw/nk3uVtPK//R3XanFktvqbKIbsjaArJEZzlqWvc" +
            "axKftHFhAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP2mzp9mGY3YcNiphvMGdcRvg+L1KDSHlS" +
            "9dUFY+PveoyXJY9OJInxuz0QKRSODYMLWhOZ2v8QhASOe9jb6fhZ2LAQF2PT5R8SbmFW3oXejGEwWbhEaNDa" +
            "P+iioiUcxwEE2Qrx24k57DX/lNlkDVfcwyeUuz4btm/TroSahNzblDJCgcwkFVHRKh2nMxb4pfSsIMN/m4f+" +
            "R1HblBX15CRABAYGAAMACQQaAQEIFQARDRYUCQEDGBoaBQgQDwoMFRMXEhDyI8aJUuHytkBCDwAAAAAABwgA" +
            "AAAAAgsEGQhvEbn6PHom/gcIAAILDgMJBxoQzrDKEsjRs2z//////////wGC6dRmw0Z9LrKw2cy+tHvb9JXu" +
            "HBu0d9Dar60DX6fr5AkFNTElLzITCQEJJxUECwI3BwYD"

    private val wallet = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"

    /**
     * Returns the captured transaction for whatever is asked, so the pipeline is what's under test.
     */
    private inner class FixtureApi(private val transaction: String = depositFixture) : KaminoApi {
        var lastAmount: String? = null
        var lastVault: String? = null

        override suspend fun getVaultState(vaultAddress: String) = error("not used")

        override suspend fun getVaultMetrics(vaultAddress: String) = KaminoVaultMetricsJson()

        override suspend fun getUserPositions(walletAddress: String): List<KaminoUserPositionJson> =
            emptyList()

        override suspend fun getPositionPnl(walletAddress: String, vaultAddress: String) =
            KaminoPnlJson()

        override suspend fun buildDeposit(
            walletAddress: String,
            vaultAddress: String,
            amount: String,
        ): String {
            lastAmount = amount
            lastVault = vaultAddress
            return transaction
        }

        override suspend fun buildWithdraw(
            walletAddress: String,
            vaultAddress: String,
            amount: String,
        ): String {
            lastAmount = amount
            lastVault = vaultAddress
            return transaction
        }
    }

    @Before
    fun loadWalletCore() {
        WalletCoreNative.ensureLoaded()
    }

    private fun prepare(
        api: KaminoApi = FixtureApi(),
        vault: KaminoVault = KaminoVaultRegistry.STEAKHOUSE_USDC,
        action: KaminoAction = KaminoAction.DEPOSIT,
        networkUnitPrice: BigInteger? = null,
    ): String = runBlocking {
        KaminoTransactionPreparer(api)
            .prepare(
                vault = vault,
                action = action,
                walletAddress = wallet,
                amount = "1",
                networkUnitPrice = networkUnitPrice,
            )
    }

    @Test
    fun a_prepared_deposit_validates_and_its_memo_is_the_final_instruction() {
        val prepared = prepare()
        val instructions = KaminoTransactionDecoder.decode(prepared)

        // Not merely "passes validation" — assert the property the validator depends on, so a
        // reordering regression cannot hide behind a passing validator.
        assertEquals(KaminoAttributionMemo.MEMO_PROGRAM_ID, instructions.last().programId)
        assertEquals(
            1,
            instructions.count { it.programId == KaminoAttributionMemo.MEMO_PROGRAM_ID },
        )
        assertEquals(
            KaminoAttributionMemo.TAG,
            instructions.last().data.toString(Charsets.US_ASCII),
        )
        assertTrue(
            "the kVault program must still be invoked",
            instructions.any { it.programId == KaminoVaultRegistry.PROGRAM_ID },
        )
    }

    @Test
    fun the_compute_unit_limit_is_well_above_the_app_s_default_which_these_transactions_exceed() {
        val prepared = prepare()

        val limit = SolanaTransaction.getComputeUnitLimit(prepared)?.toLong()
        assertEquals(320_000L, limit)
        // The app-wide Solana limit is 100,000 units; a USDC deposit measures ~252,000 and would
        // abort on compute exhaustion if that constant were reused here.
        assertTrue("limit must exceed the 100,000 default (was $limit)", limit!! > 100_000L)
    }

    @Test
    fun a_SOL_vault_deposit_gets_the_larger_budget_it_needs_to_wrap_first() {
        val prepared = prepare(vault = KaminoVaultRegistry.ALLEZ_SOL)
        assertEquals(350_000L, SolanaTransaction.getComputeUnitLimit(prepared)?.toLong())
    }

    @Test
    fun a_withdraw_gets_the_withdraw_budget() {
        val prepared = prepare(action = KaminoAction.WITHDRAW)
        assertEquals(220_000L, SolanaTransaction.getComputeUnitLimit(prepared)?.toLong())
    }

    @Test
    fun the_unit_price_floors_rather_than_trusting_a_low_network_sample() {
        val flooredByNull = prepare(networkUnitPrice = null)
        assertEquals(
            KaminoComputeBudget.FALLBACK_UNIT_PRICE.toString(),
            SolanaTransaction.getComputeUnitPrice(flooredByNull),
        )

        val flooredByLowSample = prepare(networkUnitPrice = BigInteger.ONE)
        assertEquals(
            KaminoComputeBudget.FALLBACK_UNIT_PRICE.toString(),
            SolanaTransaction.getComputeUnitPrice(flooredByLowSample),
        )

        val honoured = prepare(networkUnitPrice = BigInteger.valueOf(999_999))
        assertEquals("999999", SolanaTransaction.getComputeUnitPrice(honoured))
    }

    @Test
    fun preparing_stays_within_the_transaction_size_limit() {
        val size = Base64.getDecoder().decode(prepare()).size
        assertTrue("prepared transaction was $size bytes", size <= 1232)
    }

    @Test
    fun the_decimal_amount_reaches_Kamino_unscaled() {
        // Kamino wants decimals on the way in; sending base units would deposit a millionth of the
        // intended amount.
        val api = FixtureApi()
        prepare(api = api)
        assertEquals("1", api.lastAmount)
        assertEquals(KaminoVaultRegistry.STEAKHOUSE_USDC.address, api.lastVault)
    }

    @Test
    fun a_transaction_that_already_carries_a_foreign_memo_is_refused_rather_than_signed() {
        // Simulates Kamino returning something with a memo of its own: appending ours would make
        // two,
        // and the app must not sign a transaction carrying a memo it did not author.
        val foreign =
            checkNotNull(
                SolanaTransaction.insertInstruction(
                    depositFixture,
                    -1,
                    """{"programId":"${KaminoAttributionMemo.MEMO_PROGRAM_ID}","accounts":[],"data":"3yZe7d"}""",
                )
            )

        val rejection =
            assertThrows(KaminoTransactionRejected::class.java) {
                prepare(api = FixtureApi(foreign))
            }
        assertTrue(
            "unexpected reason: ${rejection.message}",
            rejection.message.orEmpty().contains("exactly one memo"),
        )
    }

    @Test
    fun the_unsigned_signature_envelope_survives_every_mutation() {
        // `SolanaHelper.signRawTransaction` splices the MPC signature in at the offset it derives
        // from the leading compact-u16 signature count, and treats everything past the signature
        // slots as the message to hash. Three WalletCore mutations happen before that, so the
        // envelope has to come out the far side with the same shape or signing writes to the wrong
        // bytes and the co-signers hash different messages.
        val before = Base64.getDecoder().decode(depositFixture)
        val after = Base64.getDecoder().decode(prepare())

        assertEquals("signature count must stay 1", 1, before[0].toInt())
        assertEquals("signature count must stay 1", 1, after[0].toInt())

        val signature = after.copyOfRange(1, 1 + 64)
        assertTrue(
            "the signature slot must still be zeroed, ready for the MPC signature",
            signature.all { it == 0.toByte() },
        )

        // The message must begin immediately after the signature slots, still marked versioned v0.
        val messageOffset = 1 + 64
        assertEquals(
            "message must start right after the signature slot",
            0x80,
            after[messageOffset].toInt() and 0xFF and 0x80,
        )
        assertTrue(
            "message must be longer than the original (a memo and compute budget were added)",
            after.size - messageOffset > before.size - messageOffset,
        )
    }

    @Test
    fun a_malformed_response_from_Kamino_fails_loudly_instead_of_reaching_keysign() {
        assertThrows(IllegalStateException::class.java) {
            prepare(api = FixtureApi("not-base64-tx"))
        }
    }
}
