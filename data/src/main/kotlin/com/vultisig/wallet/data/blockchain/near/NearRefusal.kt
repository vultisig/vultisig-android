package com.vultisig.wallet.data.blockchain.near

import java.util.Locale

/**
 * Why a NEAR transaction is refused before it is built or signed. [english] is the exact log text;
 * the app maps each reason onto a short user message.
 */
enum class NearRefusal(internal val english: String) {
    TOKENS_UNSUPPORTED("NEAR tokens are not supported by the native send path"),
    MEMO("NEAR native transfers cannot carry a memo"),
    CONTRACT_PAYLOAD("NEAR native transfers do not support contract payloads"),
    CUSTOM_SIGN_PAYLOAD("NEAR native transfers do not support custom sign payloads"),
    INVALID_RECIPIENT("Invalid NEAR recipient account id: %1\$s"),
    BURN_RECIPIENT("NEAR recipient is the all-zero implicit account, which no key controls"),
    INVALID_AMOUNT("Invalid NEAR transfer amount: %1\$s"),
    MISSING_CHAIN_SPECIFIC("NEAR payload carries no NEAR chain specific data"),
    INVALID_GAS_FEE("Invalid NEAR gas fee: %1\$s is not an unsigned decimal integer"),
    GAS_FEE_TOO_LARGE("Invalid NEAR gas fee: %1\$s exceeds the chain's field width"),
    INVALID_BLOCK_HASH("Invalid NEAR block hash: expected %1\$d bytes, received %2\$d"),
    INVALID_NONCE("Invalid NEAR nonce: a signed transaction needs a positive nonce"),
    NONCE_OVERFLOW("NEAR access key nonce %1\$s has no successor in the uint64 field"),
    INVALID_PUBLIC_KEY_LENGTH("Invalid NEAR public key: %1\$s is not a 32-byte Ed25519 key"),
    SENDER_NOT_IMPLICIT("NEAR sender %1\$s is not an implicit account"),
    SENDER_KEY_MISMATCH(
        "NEAR sender address does not match the signing public key: %1\$s != %2\$s"
    ),
    UNKNOWN_ACCESS_KEY("NEAR access key is not on the account: %1\$s"),
    FUNCTION_CALL_KEY(
        "NEAR signing key for %1\$s is a function-call key; a native transfer needs full access"
    ),
    SIGNATURE_VERIFICATION_FAILED("NEAR signature verification failed"),
    MALFORMED_SIGNED_TRANSACTION("NEAR signed transaction is malformed"),
    SWAP_PAYLOAD("NEAR native transfers cannot carry a swap"),
}

/** A NEAR transaction this device refuses to build or sign; [args] fill the log text. */
class NearRefusalException(val reason: NearRefusal, vararg args: Any) :
    IllegalArgumentException(reason.english.format(Locale.ROOT, *args))

/** Throws [NearRefusalException] for [reason] unless [condition] holds. */
internal fun requireNear(condition: Boolean, reason: NearRefusal, vararg args: Any) {
    if (!condition) throw NearRefusalException(reason, *args)
}
