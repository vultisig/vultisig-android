package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.R
import com.vultisig.wallet.data.services.PushNotificationError
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

internal class PushNotificationErrorTextTest {

    @Test
    fun `partial failure maps to plural with success-total-failure args`() {
        val result =
            pushNotificationErrorUiText(
                PushNotificationError.PartialFailure(successCount = 2, failureCount = 3)
            )

        val plural = result.shouldBeInstanceOf<UiText.PluralText>()
        plural.resId shouldBe R.plurals.push_notification_partial_failure
        plural.quantity shouldBe 3
        plural.formatArgs shouldBe listOf<Any>(2, 5, 3)
    }

    @Test
    fun `vault not found maps to dedicated string resource`() {
        val result = pushNotificationErrorUiText(PushNotificationError.VaultNotFound())

        val stringRes = result.shouldBeInstanceOf<UiText.StringResource>()
        stringRes.resId shouldBe R.string.push_notification_vault_not_found
    }

    @Test
    fun `vault not supported maps to dedicated string resource`() {
        val result = pushNotificationErrorUiText(PushNotificationError.VaultNotSupported())

        val stringRes = result.shouldBeInstanceOf<UiText.StringResource>()
        stringRes.resId shouldBe R.string.push_notification_error_vault_not_supported
    }

    @Test
    fun `token not available maps to dedicated string resource`() {
        val result = pushNotificationErrorUiText(PushNotificationError.TokenNotAvailable())

        val stringRes = result.shouldBeInstanceOf<UiText.StringResource>()
        stringRes.resId shouldBe R.string.push_notification_error_token_not_available
    }

    @Test
    fun `api failure maps to api failure string resource`() {
        val result =
            pushNotificationErrorUiText(PushNotificationError.ApiFailure(RuntimeException("boom")))

        val stringRes = result.shouldBeInstanceOf<UiText.StringResource>()
        stringRes.resId shouldBe R.string.push_notification_error_api_failure
    }

    @Test
    fun `non-PushNotificationError falls back to generic string`() {
        val result = pushNotificationErrorUiText(IllegalStateException("unrelated"))

        val stringRes = result.shouldBeInstanceOf<UiText.StringResource>()
        stringRes.resId shouldBe R.string.push_notifications_failed
    }

    @Test
    fun `null-safe cast does not throw when error type is unrelated`() {
        pushNotificationErrorUiText(Throwable("plain")).shouldBeInstanceOf<UiText.StringResource>()
    }
}
