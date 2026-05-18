package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.R
import com.vultisig.wallet.data.services.PushNotificationError

internal fun pushNotificationErrorUiText(e: Throwable): UiText {
    val err = e as? PushNotificationError
    return if (err is PushNotificationError.PartialFailure) {
        UiText.PluralText(
            resId = R.plurals.push_notification_partial_failure,
            quantity = err.failureCount,
            formatArgs =
                listOf(err.successCount, err.successCount + err.failureCount, err.failureCount),
        )
    } else {
        UiText.StringResource(err?.toStringRes() ?: R.string.push_notifications_failed)
    }
}

private fun PushNotificationError.toStringRes(): Int =
    when (this) {
        is PushNotificationError.VaultNotFound -> R.string.push_notification_vault_not_found
        is PushNotificationError.VaultNotSupported ->
            R.string.push_notification_error_vault_not_supported
        is PushNotificationError.TokenNotAvailable ->
            R.string.push_notification_error_token_not_available
        is PushNotificationError.ApiFailure -> R.string.push_notification_error_api_failure
        is PushNotificationError.PartialFailure -> R.string.push_notification_error_api_failure
    }
