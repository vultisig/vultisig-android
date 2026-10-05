package com.vultisig.wallet.data.blockchain.near

import java.util.Locale

/**
 * Why a NEAR transaction is refused before it is built or signed. The app shows each reason in the
 * user's language; [english] is the log text, with the same placeholders in the same order.
 */
enum class NearRefusal(internal val english: String) {
    TOKENS_UNSUPPORTED("NEAR tokens are not supported by the native send path"),
    MEMO("NEAR native transfers cannot carry a memo"),
    CONTRACT_PAYLOAD("NEAR native transfers do not support contract payloads"),
    CUSTOM_SIGN_PAYLOAD("NEAR native transfers do not support custom sign payloads"),
    INVALID_RECIPIENT("Invalid NEAR recipient account id: %1\$s"),
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
    SWAPKIT_DEPOSIT_ONLY("NEAR native transfers support SwapKit deposit swaps only"),
    SWAPKIT_NOT_NATIVE_NEAR("NEAR SwapKit deposit must sell native NEAR"),
    SWAPKIT_DEPOSIT_NOT_IMPLICIT("NEAR SwapKit deposit address %1\$s is not an implicit account"),
    SWAPKIT_DEPOSIT_RECEIVER_MISMATCH(
        "NEAR SwapKit deposit address %1\$s is not the transfer receiver %2\$s"
    ),
    SWAPKIT_DEPOSIT_AMOUNT_MISMATCH(
        "NEAR SwapKit deposit amount %1\$s is not the transfer amount %2\$s"
    ),
    SWAPKIT_DEPOSIT_PREBUILT(
        "NEAR SwapKit deposits are plain transfers and cannot carry a pre-built transaction"
    ),
    SWAPKIT_DEPOSIT_MEMO("NEAR SwapKit deposits cannot carry a memo"),
}

/**
 * A NEAR transaction this device refuses to build or sign; [args] fill the reason's placeholders.
 */
class NearRefusalException(val reason: NearRefusal, vararg args: Any) :
    IllegalArgumentException(reason.english.format(Locale.ROOT, *args)) {
    val args: List<Any> = args.toList()
}

/** Throws [NearRefusalException] for [reason] unless [condition] holds. */
internal fun requireNear(condition: Boolean, reason: NearRefusal, vararg args: Any) {
    if (!condition) throw NearRefusalException(reason, *args)
}
