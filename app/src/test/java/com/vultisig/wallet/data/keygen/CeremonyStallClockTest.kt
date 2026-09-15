package com.vultisig.wallet.data.keygen

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import org.junit.jupiter.api.Test

/**
 * Pins the progress-based ceremony stall clock from issue #5813.
 *
 * Three signers in far apart regions hit `timeout: failed to create vault within 60 seconds` nine
 * times in a row. The clock they tripped ran from the start of the attempt, so a ceremony that was
 * still exchanging messages — just slowly — failed anyway. These tests fix the difference: silence
 * still fails at 60 s, but applied messages keep pushing the deadline out.
 */
class CeremonyStallClockTest {

    private fun clockOf(fake: TestTimeSource, limit: kotlin.time.Duration = 60.seconds) =
        CeremonyStallClock(limit = limit, timeSource = fake)

    @Test
    fun `a fresh clock is not stalled`() {
        val fake = TestTimeSource()

        clockOf(fake).isStalled() shouldBe false
    }

    @Test
    fun `silence short of the limit is not a stall`() {
        val fake = TestTimeSource()
        val stall = clockOf(fake)

        fake += 59.seconds

        stall.isStalled() shouldBe false
    }

    @Test
    fun `silence past the limit is a stall`() {
        val fake = TestTimeSource()
        val stall = clockOf(fake)

        fake += 61.seconds

        stall.isStalled() shouldBe true
    }

    /**
     * The fix itself. Under the old attempt-start clock this ceremony failed at 60 s despite a
     * message landing every 30 s; the deadline now moves with the last applied message.
     */
    @Test
    fun `applied messages keep a slow but healthy ceremony alive`() {
        val fake = TestTimeSource()
        val stall = clockOf(fake)

        repeat(10) {
            fake += 30.seconds
            withClue("30 s between messages must not trip the stall") {
                stall.isStalled() shouldBe false
            }
            stall.markProgress()
        }

        // Five minutes of ceremony, none of it silent for 60 s.
        stall.isStalled() shouldBe false
    }

    @Test
    fun `the clock still fires once progress actually stops`() {
        val fake = TestTimeSource()
        val stall = clockOf(fake)

        fake += 30.seconds
        stall.markProgress()
        fake += 61.seconds

        withClue("a real stall must still fail inside the limit") {
            stall.isStalled() shouldBe true
        }
    }

    @Test
    fun `reset restarts the window for a new attempt`() {
        val fake = TestTimeSource()
        val stall = clockOf(fake)

        fake += 61.seconds
        stall.isStalled() shouldBe true

        stall.reset()

        withClue("a retry gets a fresh peer-wait window") { stall.isStalled() shouldBe false }
    }

    @Test
    fun `sinceProgress reports the gap the failure message quotes`() {
        val fake = TestTimeSource()
        val stall = clockOf(fake)

        fake += 45.seconds

        stall.sinceProgress() shouldBe 45.seconds
    }

    @Test
    fun `limitSeconds exposes the configured limit`() {
        clockOf(TestTimeSource()).limitSeconds shouldBe 60L
        clockOf(TestTimeSource(), limit = 90.seconds).limitSeconds shouldBe 90L
    }
}
