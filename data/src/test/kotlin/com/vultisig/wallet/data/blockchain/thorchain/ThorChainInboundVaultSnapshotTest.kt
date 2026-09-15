package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.api.models.thorchain.THORChainInboundAddress
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource
import org.junit.jupiter.api.Test

/**
 * Pins the five-minute window a recorded inbound set stays usable for: the sign-time corroboration
 * in [ThorChainInboundVaults] must not vouch for a vault address the network may have rotated.
 */
class ThorChainInboundVaultSnapshotTest {

    private val addresses =
        listOf(THORChainInboundAddress(chain = "BTC", address = "bc1qvault", halted = false))

    @Test
    fun `nothing is held before a fetch is recorded`() {
        ThorChainInboundVaultSnapshot(TestTimeSource()).current() shouldBe null
    }

    @Test
    fun `an empty answer is not held`() {
        val snapshot = ThorChainInboundVaultSnapshot(TestTimeSource())

        snapshot.record(emptyList())

        snapshot.current() shouldBe null
    }

    @Test
    fun `a recorded set is served until the window closes`() {
        val timeSource = TestTimeSource()
        val snapshot = ThorChainInboundVaultSnapshot(timeSource)
        snapshot.record(addresses)

        timeSource += 5.minutes
        snapshot.current() shouldBe addresses

        timeSource += 1.milliseconds
        snapshot.current() shouldBe null
    }

    @Test
    fun `a new fetch reopens the window`() {
        val timeSource = TestTimeSource()
        val snapshot = ThorChainInboundVaultSnapshot(timeSource)
        snapshot.record(addresses)
        timeSource += 10.minutes

        snapshot.record(addresses)

        snapshot.current() shouldBe addresses
    }
}
