package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.WalletCoreNative
import com.vultisig.wallet.data.api.KaminoApi
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
                ?: KaminoFixtureApi(
                    if (action == KaminoAction.WITHDRAW) KaminoFixtures.WITHDRAW
                    else KaminoFixtures.DEPOSIT
                )
        KaminoTransactionPreparer(resolved)
            .prepare(
                vault = vault,
                action = action,
                walletAddress = KaminoFixtures.WALLET,
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
                    KaminoFixtures.DEPOSIT,
                    -1,
                    KaminoComputeBudget.setUnitPriceInstructionJson(BigInteger.valueOf(7_000)),
                )
            )

        val rejection =
            assertThrows(IllegalStateException::class.java) {
                prepare(api = KaminoFixtureApi(alreadyPriced))
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
        val api = KaminoFixtureApi()
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
                    KaminoFixtures.DEPOSIT,
                    -1,
                    """{"programId":"${KaminoAttributionMemo.MEMO_PROGRAM_ID}","accounts":[],"data":"3yZe7d"}""",
                )
            )

        val rejection =
            assertThrows(KaminoTransactionRejected::class.java) {
                prepare(api = KaminoFixtureApi(foreign))
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
        val before = Base64.getDecoder().decode(KaminoFixtures.DEPOSIT)
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
            KaminoTransactionDecoder.decode(KaminoAttributionMemo.append(KaminoFixtures.WITHDRAW))
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
                            KaminoAttributionMemo.append(KaminoFixtures.WITHDRAW)
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
            prepare(api = KaminoFixtureApi("not-base64-tx"))
        }
    }
}
