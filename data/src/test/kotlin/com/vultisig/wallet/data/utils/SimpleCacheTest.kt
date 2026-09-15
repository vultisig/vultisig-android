package com.vultisig.wallet.data.utils

import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class SimpleCacheTest {

    @Test
    fun `an entry is served until its expiration and gone after`() {
        val timeSource = TestTimeSource()
        val cache =
            SimpleCache<String, Int>(defaultExpiration = 10.seconds, timeSource = timeSource)
        cache.put("k", 1)

        timeSource += 10.seconds - 1.milliseconds
        cache.get("k") shouldBe 1
        cache.hasValidEntry("k") shouldBe true

        timeSource += 1.milliseconds
        cache.hasValidEntry("k") shouldBe false
        cache.get("k") shouldBe null
        cache.containsKey("k") shouldBe false
    }

    @Test
    fun `a custom expiration overrides the default for that entry`() {
        val timeSource = TestTimeSource()
        val cache =
            SimpleCache<String, Int>(defaultExpiration = 10.seconds, timeSource = timeSource)
        cache.put("short", 1, customExpiration = 1.seconds)
        cache.put("default", 2)

        timeSource += 1.seconds
        cache.get("short") shouldBe null
        cache.get("default") shouldBe 2
    }

    @Test
    fun `cleanUp drops only the expired entries`() {
        val timeSource = TestTimeSource()
        val cache =
            SimpleCache<String, Int>(defaultExpiration = 10.seconds, timeSource = timeSource)
        cache.put("old", 1)
        timeSource += 5.seconds
        cache.put("new", 2)
        timeSource += 5.seconds

        cache.cleanUp()

        cache.size() shouldBe 1
        cache.containsKey("new") shouldBe true
    }

    @Test
    fun `getOrPut computes once until the entry expires`() = runTest {
        val timeSource = TestTimeSource()
        val cache =
            SimpleCache<String, Int>(defaultExpiration = 10.seconds, timeSource = timeSource)
        var computed = 0

        cache.getOrPut("k") { ++computed } shouldBe 1
        cache.getOrPut("k") { ++computed } shouldBe 1

        timeSource += 10.seconds
        cache.getOrPut("k") { ++computed } shouldBe 2
    }
}
