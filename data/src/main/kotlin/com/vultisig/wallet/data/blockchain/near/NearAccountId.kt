package com.vultisig.wallet.data.blockchain.near

/**
 * NEAR account-id grammar (nomicon `DataStructures/Account`). WalletCore validates only implicit
 * and legacy forms and refuses every named account, so recipients are checked here instead.
 */
object NearAccountId {

    /**
     * Alphanumeric groups joined by one separator, with `.` delimiting the domain-like segments.
     */
    private val NAMED = Regex("""(([a-z0-9]+[-_])*[a-z0-9]+\.)*([a-z0-9]+[-_])*[a-z0-9]+""")

    /** Implicit accounts are the lowercase hex form of an Ed25519 public key. */
    private val IMPLICIT = Regex("[0-9a-f]{64}")

    /** `0x…` (NEP-518) and `0s…` (NEP-616) families this transfer path cannot address. */
    private val UNSUPPORTED_HEX_PREFIXED = Regex("(0x|0s)[0-9a-f]{40}")

    private const val MIN_LENGTH = 2
    private const val MAX_LENGTH = 64

    fun isImplicit(accountId: String): Boolean = IMPLICIT.matches(accountId)

    /**
     * True when [accountId] reads as NEAR without other context: a 64-hex key or a dotted name. A
     * bare word passes the grammar but is as likely a typo or another chain's address.
     */
    fun isUnambiguous(accountId: String): Boolean =
        isImplicit(accountId) || ('.' in accountId && isValid(accountId))

    fun isValid(accountId: String): Boolean {
        if (accountId.length !in MIN_LENGTH..MAX_LENGTH) return false
        if (isImplicit(accountId)) return true
        if (UNSUPPORTED_HEX_PREFIXED.matches(accountId)) return false
        return NAMED.matches(accountId)
    }
}
