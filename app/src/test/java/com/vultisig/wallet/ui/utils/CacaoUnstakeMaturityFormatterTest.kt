package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.R
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

internal class CacaoUnstakeMaturityFormatterTest {

    @Test
    fun `multi-day remaining formats with days and hours`() {
        val seconds = (5L * 24L * 3_600L) + (3L * 3_600L) // 5d 3h

        val result = cacaoUnlocksInUiText(seconds)

        val formatted = result.shouldBeInstanceOf<UiText.FormattedText>()
        formatted.resId shouldBe R.string.unstake_cacao_unlocks_in_days_hours_format
        formatted.formatArgs shouldBe listOf<Any>(5, 3)
    }

    @Test
    fun `sub-day remaining formats with hours only`() {
        val seconds = 7L * 3_600L

        val result = cacaoUnlocksInUiText(seconds)

        val formatted = result.shouldBeInstanceOf<UiText.FormattedText>()
        formatted.resId shouldBe R.string.unstake_cacao_unlocks_in_hours_format
        formatted.formatArgs shouldBe listOf<Any>(7)
    }

    @Test
    fun `sub-hour remaining falls back to unlocks-soon copy`() {
        val seconds = 45L * 60L

        val result = cacaoUnlocksInUiText(seconds)

        val stringRes = result.shouldBeInstanceOf<UiText.StringResource>()
        stringRes.resId shouldBe R.string.unstake_cacao_unlocks_soon
    }

    @Test
    fun `exactly one day rounds into days bucket with zero hours`() {
        val seconds = 24L * 3_600L

        val result = cacaoUnlocksInUiText(seconds)

        val formatted = result.shouldBeInstanceOf<UiText.FormattedText>()
        formatted.resId shouldBe R.string.unstake_cacao_unlocks_in_days_hours_format
        formatted.formatArgs shouldBe listOf<Any>(1, 0)
    }
}
