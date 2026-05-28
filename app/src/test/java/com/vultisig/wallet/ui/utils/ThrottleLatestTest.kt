package com.vultisig.wallet.ui.utils

import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ThrottleLatestTest {

    @Test
    fun `emits the leading value immediately and drops values within the window`() = runTest {
        val time = TestTimeSource()
        val source = flow {
            emit(1) // leading edge -> emitted
            emit(2) // same instant, within window -> dropped
            time += 300.milliseconds
            emit(3) // window elapsed -> emitted
            emit(4) // same instant, within window -> dropped
        }

        val result = source.throttleLatest(window = 250.milliseconds, timeSource = time).toList()

        assertEquals(listOf(1, 3), result)
    }

    @Test
    fun `always passes terminal values even within the window`() = runTest {
        val time = TestTimeSource()
        val source = flow {
            emit(1 to false) // leading edge -> emitted
            emit(2 to false) // within window -> dropped
            emit(3 to true) // terminal -> emitted despite the window
        }

        val result =
            source
                .throttleLatest(window = 250.milliseconds, timeSource = time) { it.second }
                .toList()

        assertEquals(listOf(1 to false, 3 to true), result)
    }
}
