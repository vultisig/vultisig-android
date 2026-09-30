package com.vultisig.wallet.app.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.vultisig.wallet.data.services.PendingPushPayload
import com.vultisig.wallet.data.services.VultisigFirebaseMessagingService
import timber.log.Timber

/**
 * Target of the keysign notification's tap intent. It is not exported, so other apps can't start
 * it; it moves the payload into [PendingPushPayload] and brings up [MainActivity] with no extras.
 */
class PushNotificationTapActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val payload = intent.getStringExtra(VultisigFirebaseMessagingService.QR_CODE_DATA)
        if (payload != null) {
            PendingPushPayload.set(payload)
            try {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
            } catch (e: Exception) {
                // The handoff never reached MainActivity, so clear the payload rather than leave it
                // for an unrelated later launch to consume out of sequence.
                PendingPushPayload.clear()
                Timber.e(e, "Failed to hand off push payload to MainActivity")
            }
        }
        finish()
    }
}
