package com.vultisig.wallet.data.keygen

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * `godkls` returns `LIB_ABORT_PROTOCOL_AND_BAN_PARTY_1..10` (100-109) when it aborts a session
 * after catching a co-signer misbehaving. That must abort the keysign with
 * [MaliciousPartyException], not the generic runtime error other `lib_error` codes get retried
 * against. See #5648 / iOS #5226.
 */
class DklsBannedPartyDetectionTest {

    private val committee = listOf("partyA", "partyB")

    @Test
    fun `ban range throws with committee id`() {
        shouldThrow<MaliciousPartyException> { checkForBannedParty(100, committee) }
            .partyID shouldBe "partyA"
        shouldThrow<MaliciousPartyException> { checkForBannedParty(101, committee) }
            .partyID shouldBe "partyB"
    }

    @Test
    fun `ban range beyond committee size falls back to index`() {
        shouldThrow<MaliciousPartyException> { checkForBannedParty(102, committee) }
            .partyID shouldBe "#3"
    }

    @Test
    fun `non-ban codes do not throw`() {
        // LIB_OK, an ordinary error, and the neighboring non-ban LIB_ABORT_PROTOCOL_PARTY_*
        // range (200-209) must not be mistaken for a ban.
        listOf(0, 13, 99, 110, 200, 209).forEach { value ->
            shouldNotThrowAny { checkForBannedParty(value, committee) }
        }
    }
}
