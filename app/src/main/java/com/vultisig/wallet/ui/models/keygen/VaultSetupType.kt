package com.vultisig.wallet.ui.models.keygen

internal enum class VaultSetupType(
    val raw: Int,
    val isFast: Boolean,
) {
    SECURE(2, false), // m to n devices
    // with vultiserver
    FAST(3, true), // 1 to 1
    ACTIVE(4, true), // 2 to 1
    ;

    companion object {
        const val FAST_PARTICIPANTS_KICKOFF_THRESHOLD = 2

        fun fromInt(value: Int): VaultSetupType = entries.first { it.raw == value }
        fun VaultSetupType.asString(): String =
            when (this) {
                SECURE -> "Secure"
                FAST -> "Fast"
                ACTIVE -> "Active"
            }
    }
}