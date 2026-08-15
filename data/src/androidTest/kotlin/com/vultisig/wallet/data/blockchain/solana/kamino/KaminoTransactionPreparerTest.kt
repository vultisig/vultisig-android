package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.WalletCoreNative
import com.vultisig.wallet.data.api.KaminoApi
import com.vultisig.wallet.data.api.KaminoPnlJson
import com.vultisig.wallet.data.api.KaminoUserPositionJson
import com.vultisig.wallet.data.api.KaminoVaultMetricsJson
import java.math.BigInteger
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
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
 * The ordering here is load-bearing rather than incidental, in two directions. The memo is
 * appended, so injecting the budget after it would leave the memo mid-list and the validator would
 * refuse it. And the budget pair has to lead the transaction as limit-then-price, because an iPhone
 * co-signer emits the same two at 0 and 1 and matches everything after them by position. These
 * tests pin both.
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

    /**
     * A live `POST /ktx/kvault/withdraw` response for the same vault: create the destination token
     * account, then `kVault::withdraw`.
     *
     * Kamino built it for a wallet holding no position at all — the clearest evidence that the
     * endpoint validates nothing about the amount it is handed, and also why there is nothing
     * staked to release here.
     *
     * **This is therefore the UNSTAKED shape, which is not the one most holders will send.** A
     * withdraw against a staked position carries two extra `farms` instructions and a second
     * account creation ahead of the vault withdraw. Capturing that needs a wallet actually holding
     * a staked position, which these vaults only produce by depositing real funds, so the limit
     * sized for it rests on the iOS mainnet measurements (283,786 / 289,486 / 309,310) rather than
     * on anything this suite proves.
     */
    private val withdrawFixture =
        "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACA" +
            "AQAFCH//JB25KLX6LxwUe5Pw/FGXqeETK7Jj6GQYPDbOSuemOEsQm7A2IghRdbzU0ar6Q7dIhEJA6xfcD32XM4Yw" +
            "fPTlbTyv/0d12pxZLb6myiG7I2gKyRGc5alr3GsSn7RxYQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "D9ps6fZhmN2HDYqYbzBnXEb4Pi9Sg0h5UvXVBWPj73qMlyWPTiSJ8bs9ECkUjg2DC1oTmdr/EIQEjnvY2+n4WZlx" +
            "KXooAEJJPkJxkMd7ZH6G99GZch/RVimWXJ1rZZT4BNkK8duJOew1/5TZZA1X3MMnlLs+G7Zv066EmoTc25TcFsAN" +
            "s3q6CtgzQiIGiofmyao3Sh0Dq7RowlSc92jsQgIFBgABAA0DFgEBBxYADwYLEgENAggWFhUEBw4MCQoTERQQEBOD" +
            "cJuq3CI5//////////8BgunUZsNGfS6ysNnMvrR72/SV7hwbtHfQ2q+tA1+n6+QIBTUlLxMCCQEHJxUECzcHAw=="

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
        api: KaminoApi? = null,
        vault: KaminoVault = KaminoVaultRegistry.STEAKHOUSE_USDC,
        action: KaminoAction = KaminoAction.DEPOSIT,
        networkUnitPrice: BigInteger? = null,
    ): String = runBlocking {
        val resolved =
            api
                ?: FixtureApi(
                    if (action == KaminoAction.WITHDRAW) withdrawFixture else depositFixture
                )
        KaminoTransactionPreparer(resolved)
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
        val instructions = KaminoTransactionDecoder.decode(prepared).instructions

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
    fun the_compute_budget_leads_the_transaction_as_limit_then_price() {
        // The layout an iPhone co-signer matches against positionally: limit at 0, price at 1,
        // everything Kamino built after them, memo last. WalletCore's own `setComputeUnitPrice`
        // appends instead, which put the price after the ATA creation and the vault deposit and
        // made
        // every Android-initiated Kamino transaction unjoinable from iOS.
        val instructions = KaminoTransactionDecoder.decode(prepare()).instructions

        assertEquals(KaminoComputeBudget.PROGRAM_ID, instructions[0].programId)
        assertEquals(
            KaminoComputeBudget.SET_UNIT_LIMIT_DISCRIMINATOR,
            instructions[0].data.first().toInt() and 0xFF,
        )

        assertEquals(KaminoComputeBudget.PROGRAM_ID, instructions[1].programId)
        assertEquals(
            KaminoComputeBudget.SET_UNIT_PRICE_DISCRIMINATOR,
            instructions[1].data.first().toInt() and 0xFF,
        )
        // The exact bytes the app encoded, so a re-encoding regression cannot pass by carrying some
        // other price at the right position.
        assertArrayEquals(
            KaminoComputeBudget.setUnitPriceData(KaminoComputeBudget.FALLBACK_UNIT_PRICE),
            instructions[1].data,
        )

        // Nothing else invokes ComputeBudget: a stray third would mean the append is still
        // happening
        // somewhere.
        assertEquals(2, instructions.count { it.programId == KaminoComputeBudget.PROGRAM_ID })
        assertEquals(KaminoAttributionMemo.MEMO_PROGRAM_ID, instructions.last().programId)
        // The instructions Kamino built sit between the budget and the memo, in the order it
        // returned them — which is what makes matching them by position meaningful at all.
        assertTrue(
            "Kamino's own instructions must sit between the budget and the memo",
            instructions.subList(2, instructions.size - 1).none {
                it.programId == KaminoComputeBudget.PROGRAM_ID ||
                    it.programId == KaminoAttributionMemo.MEMO_PROGRAM_ID
            },
        )
    }

    @Test
    fun the_compute_unit_limit_is_well_above_the_app_s_default_which_these_transactions_exceed() {
        val prepared = prepare()

        val limit = SolanaTransaction.getComputeUnitLimit(prepared)?.toLong() ?: 0L
        assertEquals(320_000L, limit)
        // The app-wide Solana limit is 100,000 units; a USDC deposit measures ~252,000 and would
        // abort on compute exhaustion if that constant were reused here.
        assertTrue("limit must exceed the 100,000 default (was $limit)", limit > 100_000L)
    }

    @Test
    fun a_SOL_vault_deposit_gets_the_larger_budget_it_needs_to_wrap_first() {
        val prepared = prepare(vault = KaminoVaultRegistry.ALLEZ_SOL)
        assertEquals(350_000L, SolanaTransaction.getComputeUnitLimit(prepared)?.toLong())
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
    fun the_unit_price_is_capped_at_the_ceiling_an_iPhone_co_signer_will_accept() {
        // iOS clamps the sample into the same range and its decoder refuses a transaction priced
        // outside it, so an uncapped price is one no iPhone will join. The sample reaching here
        // comes from `getMedianPriorityFee`, which already floors at 1,000,000 and caps at
        // 100,000,000 — every congested-network reading is above the ceiling, not an edge case.
        val capped = prepare(networkUnitPrice = BigInteger.valueOf(100_000_000))
        assertEquals(
            KaminoComputeBudget.MAX_UNIT_PRICE.toString(),
            SolanaTransaction.getComputeUnitPrice(capped),
        )
    }

    @Test
    fun a_response_that_already_carries_a_compute_budget_is_refused_rather_than_double_priced() {
        // The price is now inserted rather than set, and an insert cannot overwrite. If Kamino ever
        // starts returning its own `SetComputeUnitPrice`, ours would be a second one and the chain
        // would reject the transaction — after a full signing ceremony had been spent on it.
        val alreadyPriced =
            checkNotNull(
                SolanaTransaction.insertInstruction(
                    depositFixture,
                    -1,
                    KaminoComputeBudget.setUnitPriceInstructionJson(BigInteger.valueOf(7_000)),
                )
            )

        val rejection =
            assertThrows(IllegalStateException::class.java) {
                prepare(api = FixtureApi(alreadyPriced))
            }
        assertTrue(
            "unexpected reason: ${rejection.message}",
            rejection.message.orEmpty().contains("ComputeBudget instructions at"),
        )
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
            "message must start right after the signature slot, marked versioned v0",
            0x80,
            after[messageOffset].toInt() and 0xFF,
        )
        assertTrue(
            "a memo and compute budget were added, so it must have grown",
            after.size > before.size,
        )
    }

    @Test
    fun the_captured_withdraw_is_refused_because_it_carries_the_sentinel() {
        // Kamino built this fixture for a wallet holding nothing, and rather than rejecting the
        // request it rewrote the amount to u64::MAX — "withdraw everything". That is the rewrite
        // the
        // whole withdraw design exists to stay away from, so the pipeline must refuse to prepare it
        // on any device. This is also direct evidence the rewrite is real, not merely documented.
        val rejection =
            assertThrows(KaminoTransactionRejected::class.java) {
                prepare(action = KaminoAction.WITHDRAW)
            }
        assertTrue(
            "unexpected reason: ${rejection.message}",
            rejection.message.orEmpty().contains("withdraw-everything sentinel"),
        )
    }

    @Test
    fun the_captured_withdraw_is_otherwise_the_shape_the_validator_expects() {
        // Everything except the amount: the instructions decode, invoke the kVault program, and
        // carry
        // no farms instruction (nothing was staked, so nothing had to be released). Asserted on the
        // decode rather than through prepare, which correctly refuses the sentinel above.
        val instructions =
            KaminoTransactionDecoder.decode(KaminoAttributionMemo.append(withdrawFixture))
                .instructions

        assertTrue(
            "a withdraw must invoke the kVault program",
            instructions.any { it.programId == KaminoVaultRegistry.PROGRAM_ID },
        )
        assertTrue(
            "this fixture is the unstaked shape",
            instructions.none { it.programId == KaminoVaultRegistry.FARMS_PROGRAM_ID },
        )
        assertEquals(KaminoAttributionMemo.MEMO_PROGRAM_ID, instructions.last().programId)
    }

    @Test
    fun a_withdraw_is_refused_when_it_is_not_the_wallet_s_own_transaction() {
        // The fixture's fee payer is ; validating it against a different signer must fail,
        // which is what stops the app signing on another account's behalf.
        val other = "HDsayqAsDWy3QvANGqh2yNraqcD8Fnjgh73Mhb3WRS5E"
        val rejection =
            assertThrows(KaminoTransactionRejected::class.java) {
                KaminoTransactionValidator.validate(
                    decoded =
                        KaminoTransactionDecoder.decode(
                            KaminoAttributionMemo.append(withdrawFixture)
                        ),
                    vault = KaminoVaultRegistry.STEAKHOUSE_USDC,
                    action = KaminoAction.WITHDRAW,
                    signerAddress = other,
                )
            }
        assertTrue(
            "unexpected reason: ${rejection.message}",
            rejection.message.orEmpty().contains("authorised by"),
        )
    }

    @Test
    fun a_malformed_response_from_Kamino_fails_loudly_instead_of_reaching_keysign() {
        assertThrows(IllegalStateException::class.java) {
            prepare(api = FixtureApi("not-base64-tx"))
        }
    }
}
