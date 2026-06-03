package com.vultisig.wallet.data.blockchain.cosmos.staking

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.testutils.MockHttpClient
import io.ktor.http.HttpStatusCode
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * DTO parse coverage for the Cosmos x/staking + x/distribution LCD reader. Mirrors iOS
 * `CosmosStakingServiceTests.swift`. Each test exercises a single endpoint via a fixture JSON copy
 * of the on-the-wire shape — the SDK / agent app produce the same shapes so these tests double as a
 * wire-format contract.
 *
 * Two additional tests exercise the [CosmosStakingServiceImpl] HTTP round-trip end-to-end against a
 * Ktor [MockHttpClient] to confirm the bodyOrThrow wiring.
 */
class CosmosStakingServiceTests {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // MARK: - Delegations

    @Test
    fun `delegations response parses all entries and preserves order`() {
        val response: CosmosDelegationResponse = loadFixture("delegations")
        val delegations = response.toDelegations()
        assertEquals(2, delegations.size)
        assertEquals(
            "terravaloper1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            delegations[0].validatorAddress,
        )
        assertEquals(CosmosStakingCoin(denom = "uluna", amount = "1000000"), delegations[0].balance)
        assertEquals("1000000.000000000000000000", delegations[0].shares)
        assertEquals(
            "terravaloper1bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            delegations[1].validatorAddress,
        )
        assertEquals("500000", delegations[1].balance.amount)
    }

    // MARK: - Unbonding delegations

    @Test
    fun `unbonding delegations parse all entries and decode dates`() {
        val response: CosmosUnbondingDelegationResponse = loadFixture("unbonding-delegations")
        val unbonding = response.toUnbondingDelegations()
        assertEquals(1, unbonding.size)
        val entries = unbonding[0].entries
        assertEquals(2, entries.size)

        // First entry: RFC3339 with fractional seconds.
        assertEquals(12_345_678L, entries[0].creationHeight)
        assertEquals(BigDecimal("100000"), entries[0].initialBalance)
        assertEquals(BigDecimal("100000"), entries[0].balance)
        assertNotNull(entries[0].completionTime)

        // Second entry: whole-second completion time — both formats must parse without the
        // fractional component being mandatory.
        assertEquals(12_345_700L, entries[1].creationHeight)
        assertNotNull(entries[1].completionTime)
        assertTrue(entries[1].completionTime.isAfter(entries[0].completionTime))
    }

    // MARK: - Rewards

    @Test
    fun `delegator rewards parses multi-validator rewards and total`() {
        val response: CosmosDelegatorRewardsResponse = loadFixture("rewards")
        val rewards = response.toRewards()
        assertEquals(2, rewards.rewards.size)
        assertEquals(1, rewards.total.size)
        assertEquals(
            CosmosStakingCoin(denom = "uluna", amount = "178456.789000000000000000"),
            rewards.total[0],
        )
    }

    @Test
    fun `delegator rewards falls back to empty on null payload`() {
        // `rewards: null` and `total: null` arrive from some LCD firmwares when the delegator has
        // never accrued. The SDK falls back to []; we do the same.
        val response: CosmosDelegatorRewardsResponse = loadFixture("rewards-empty")
        val rewards = response.toRewards()
        assertTrue(rewards.rewards.isEmpty())
        assertTrue(rewards.total.isEmpty())
    }

    // MARK: - Validators

    @Test
    fun `validator list maps status flags`() {
        val response: CosmosValidatorListResponse = loadFixture("validators")
        val validators = response.toValidators()
        assertEquals(3, validators.size)

        assertEquals("Validator A", validators[0].moniker)
        assertEquals(CosmosValidator.Status.Bonded, validators[0].status)
        assertFalse(validators[0].jailed)
        assertEquals(BigDecimal("0.050000000000000000"), validators[0].commission)
        assertEquals(BigDecimal("50000000000000"), validators[0].votingPower)

        assertEquals("Validator B (Jailed)", validators[1].moniker)
        assertTrue(validators[1].jailed)
        assertEquals(CosmosValidator.Status.Bonded, validators[1].status)

        assertEquals(CosmosValidator.Status.Unbonded, validators[2].status)
    }

    @Test
    fun `validator jailed defaults false when missing`() {
        // The `jailed` field is missing from the unbonded validator (Validator C). Cosmos LCDs
        // sometimes omit `false` booleans; the wire DTO must default to `false` rather than
        // failing decode.
        val response: CosmosValidatorListResponse = loadFixture("validators")
        val unbonded = response.toValidators().first { it.moniker.contains("Unbonded") }
        assertFalse(unbonded.jailed)
    }

    @Test
    fun `validator identity decodes when present and collapses empty to null`() {
        // Validator A advertises a Keybase identity; B + C omit the field. Decoder must preserve
        // the value when present, default to null when absent, and collapse the empty string to
        // null so the avatar lookup doesn't fire on garbage.
        val response: CosmosValidatorListResponse = loadFixture("validators")
        val validators = response.toValidators()
        assertEquals("1234567890ABCDEF", validators[0].identity)
        assertNull(validators[1].identity)
        assertNull(validators[2].identity)
    }

    // MARK: - Redelegations

    @Test
    fun `redelegations response flattens all entries and decodes dates`() {
        val response: CosmosRedelegationResponse = loadFixture("redelegations")
        val entries = response.toRedelegations()
        assertEquals(1, entries.size)
        assertEquals("terravaloper1srcsrcsrcsrcsrcsrcsrcsrcsrcsrcsrcsrcs", entries[0].srcValidator)
        assertEquals("terravaloper1dstdstdstdstdstdstdstdstdstdstdstdsts", entries[0].dstValidator)
        assertNotNull(entries[0].completionTime)
    }

    // MARK: - Service HTTP round-trip

    @Test
    fun `service decodes delegations via HTTP round-trip`() = runTest {
        val body = readResource("/cosmos_staking_fixtures/delegations.json")
        val service =
            CosmosStakingServiceImpl(MockHttpClient.respondingWith(HttpStatusCode.OK, body))
        val delegations = service.fetchDelegations(Chain.Terra, "terra1delegator")
        assertEquals(2, delegations.size)
        assertEquals("uluna", delegations[0].balance.denom)
    }

    @Test
    fun `service decodes validators via HTTP round-trip`() = runTest {
        val body = readResource("/cosmos_staking_fixtures/validators.json")
        val service =
            CosmosStakingServiceImpl(MockHttpClient.respondingWith(HttpStatusCode.OK, body))
        val validators = service.fetchValidators(Chain.Terra)
        assertEquals(3, validators.size)
    }

    // MARK: - Fixture loader

    private inline fun <reified T> loadFixture(name: String): T {
        val text = readResource("/cosmos_staking_fixtures/$name.json")
        return json.decodeFromString<T>(text)
    }

    private fun readResource(path: String): String =
        checkNotNull(javaClass.getResourceAsStream(path)) { "Missing test resource $path" }
            .bufferedReader()
            .use { it.readText() }
}
