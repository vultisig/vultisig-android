package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.R
import com.vultisig.wallet.data.services.PushNotificationError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PushNotificationErrorTextTest {

    @Test
    fun `partial failure maps to plural with success-total-failure args`() {
        val result =
            pushNotificationErrorUiText(
                PushNotificationError.PartialFailure(successCount = 2, failureCount = 3)
            )

        val plural = result as UiText.PluralText
        assertEquals(R.plurals.push_notification_partial_failure, plural.resId)
        assertEquals(3, plural.quantity)
        assertEquals(listOf<Any>(2, 5, 3), plural.formatArgs)
    }

    @Test
    fun `vault not found maps to dedicated string resource`() {
        val result = pushNotificationErrorUiText(PushNotificationError.VaultNotFound())

        val stringRes = result as UiText.StringResource
        assertEquals(R.string.push_notification_vault_not_found, stringRes.resId)
    }

    @Test
    fun `vault not supported maps to dedicated string resource`() {
        val result = pushNotificationErrorUiText(PushNotificationError.VaultNotSupported())

        val stringRes = result as UiText.StringResource
        assertEquals(R.string.push_notification_error_vault_not_supported, stringRes.resId)
    }

    @Test
    fun `token not available maps to dedicated string resource`() {
        val result = pushNotificationErrorUiText(PushNotificationError.TokenNotAvailable())

        val stringRes = result as UiText.StringResource
        assertEquals(R.string.push_notification_error_token_not_available, stringRes.resId)
    }

    @Test
    fun `api failure maps to api failure string resource`() {
        val result =
            pushNotificationErrorUiText(PushNotificationError.ApiFailure(RuntimeException("boom")))

        val stringRes = result as UiText.StringResource
        assertEquals(R.string.push_notification_error_api_failure, stringRes.resId)
    }

    @Test
    fun `non-PushNotificationError falls back to generic string`() {
        val result = pushNotificationErrorUiText(IllegalStateException("unrelated"))

        val stringRes = result as UiText.StringResource
        assertEquals(R.string.push_notifications_failed, stringRes.resId)
    }

    @Test
    fun `null-safe cast does not throw when error type is unrelated`() {
        // Smoke test: branching uses safe cast `as?` — must not propagate ClassCastException.
        assertTrue(pushNotificationErrorUiText(Throwable("plain")) is UiText.StringResource)
    }
}
