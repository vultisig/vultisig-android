package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.NearMalformedResponseException
import com.vultisig.wallet.data.api.NearRpcException
import com.vultisig.wallet.data.blockchain.near.NearRefusal
import com.vultisig.wallet.data.blockchain.near.NearRefusalException

/**
 * The user-facing text of a NEAR refusal, node error or unreadable node answer behind [this]
 * failure, or null for any other failure. Reasons the user can act on keep their own message;
 * integrity failures share one, and the exact reason stays in the exception for the log.
 */
fun Throwable.nearErrorTextOrNull(): UiText? =
    when (this) {
        is NearRefusalException -> UiText.StringResource(reason.stringRes)
        is NearRpcException,
        is NearMalformedResponseException -> UiText.StringResource(R.string.send_error_near_network)
        else -> null
    }

private val NearRefusal.stringRes: Int
    get() =
        when (this) {
            NearRefusal.INVALID_RECIPIENT -> R.string.send_error_near_invalid_recipient
            NearRefusal.BURN_RECIPIENT -> R.string.error_recipient_burn_address
            NearRefusal.UNKNOWN_ACCESS_KEY,
            NearRefusal.FUNCTION_CALL_KEY -> R.string.send_error_near_key_not_full_access
            NearRefusal.TOKENS_UNSUPPORTED,
            NearRefusal.MEMO,
            NearRefusal.CONTRACT_PAYLOAD,
            NearRefusal.CUSTOM_SIGN_PAYLOAD,
            NearRefusal.SWAP_PAYLOAD -> R.string.send_error_near_unsupported_payload
            NearRefusal.INVALID_AMOUNT,
            NearRefusal.MISSING_CHAIN_SPECIFIC,
            NearRefusal.INVALID_GAS_FEE,
            NearRefusal.GAS_FEE_TOO_LARGE,
            NearRefusal.INVALID_BLOCK_HASH,
            NearRefusal.INVALID_NONCE,
            NearRefusal.NONCE_OVERFLOW,
            NearRefusal.INVALID_PUBLIC_KEY_LENGTH,
            NearRefusal.SENDER_NOT_IMPLICIT,
            NearRefusal.SENDER_KEY_MISMATCH,
            NearRefusal.SIGNATURE_VERIFICATION_FAILED,
            NearRefusal.MALFORMED_SIGNED_TRANSACTION -> R.string.send_error_near_safety_check
        }
