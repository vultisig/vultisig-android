package com.vultisig.wallet.ui.screens.v2.defi

enum class BondNodeState(val state: String) {
    WHITELISTED("whitelisted"),
    STANDBY("standby"),
    READY("ready"),
    ACTIVE("active"),
    DISABLED("disabled"),
    UNKNOWN("unknown");

    val canUnbond: Boolean
        get() = when (this) {
            WHITELISTED, STANDBY, UNKNOWN -> true
            READY, ACTIVE, DISABLED -> false
        }

    val canBond: Boolean
        get() = when (this) {
            WHITELISTED, STANDBY, READY, ACTIVE -> true
            DISABLED, UNKNOWN -> false
        }

    val isEarningRewards: Boolean
        get() = this == ACTIVE

    companion object {
        /** Initialize from API response status string */
        fun String?.fromApiStatus(): BondNodeState {
            return when (this?.lowercase()) {
                "whitelisted" -> WHITELISTED
                "standby" -> STANDBY
                "ready" -> READY
                "active" -> ACTIVE
                "disabled" -> DISABLED
                else -> UNKNOWN
            }
        }
    }
}
