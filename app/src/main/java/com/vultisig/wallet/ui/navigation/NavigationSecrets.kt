package com.vultisig.wallet.ui.navigation

import com.vultisig.wallet.data.utils.TransientStore
import java.util.UUID

/** Only the opaque handle is eligible for Android navigation state. */
internal object NavigationSecrets {
    private val values = TransientStore<String, String>(capacity = 24, lifetimeMillis = 60_000)

    fun handoff(value: String): String =
        UUID.randomUUID().toString().also { id -> values.put(id, value) }

    fun take(handle: String?): String? = handle?.let(values::remove)
}
