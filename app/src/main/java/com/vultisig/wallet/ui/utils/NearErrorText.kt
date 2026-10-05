package com.vultisig.wallet.ui.utils

import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.NearMalformedResponseException
import com.vultisig.wallet.data.api.NearRpcException
import com.vultisig.wallet.data.blockchain.near.NearRefusal
import com.vultisig.wallet.data.blockchain.near.NearRefusalException

/**
 * The user-facing text of a NEAR refusal, node error or unreadable node answer behind [this]
 * failure, or null for any other failure.
 */
fun Throwable.nearErrorTextOrNull(): UiText? =
    when (this) {
        is NearRefusalException -> UiText.FormattedText(reason.stringRes, args)
        is NearRpcException ->
            UiText.FormattedText(R.string.near_error_rpc, listOf(method, name, detail))
        is NearMalformedResponseException ->
            UiText.FormattedText(R.string.near_error_malformed_response, listOf(detail))
        else -> null
    }

private val NearRefusal.stringRes: Int
    get() =
        when (this) {
            NearRefusal.TOKENS_UNSUPPORTED -> R.string.near_error_tokens_unsupported
            NearRefusal.MEMO -> R.string.near_error_memo
            NearRefusal.CONTRACT_PAYLOAD -> R.string.near_error_contract_payload
            NearRefusal.CUSTOM_SIGN_PAYLOAD -> R.string.near_error_custom_sign_payload
            NearRefusal.INVALID_RECIPIENT -> R.string.near_error_invalid_recipient
            NearRefusal.INVALID_AMOUNT -> R.string.near_error_invalid_amount
            NearRefusal.MISSING_CHAIN_SPECIFIC -> R.string.near_error_missing_chain_specific
            NearRefusal.INVALID_GAS_FEE -> R.string.near_error_invalid_gas_fee
            NearRefusal.GAS_FEE_TOO_LARGE -> R.string.near_error_gas_fee_too_large
            NearRefusal.INVALID_BLOCK_HASH -> R.string.near_error_invalid_block_hash
            NearRefusal.INVALID_NONCE -> R.string.near_error_invalid_nonce
            NearRefusal.NONCE_OVERFLOW -> R.string.near_error_nonce_overflow
            NearRefusal.INVALID_PUBLIC_KEY_LENGTH -> R.string.near_error_invalid_public_key_length
            NearRefusal.SENDER_NOT_IMPLICIT -> R.string.near_error_sender_not_implicit
            NearRefusal.SENDER_KEY_MISMATCH -> R.string.near_error_sender_key_mismatch
            NearRefusal.UNKNOWN_ACCESS_KEY -> R.string.near_error_unknown_access_key
            NearRefusal.FUNCTION_CALL_KEY -> R.string.near_error_function_call_key
            NearRefusal.SIGNATURE_VERIFICATION_FAILED ->
                R.string.near_error_signature_verification_failed
            NearRefusal.MALFORMED_SIGNED_TRANSACTION ->
                R.string.near_error_malformed_signed_transaction
            NearRefusal.SWAPKIT_DEPOSIT_ONLY -> R.string.near_error_swapkit_deposit_only
            NearRefusal.SWAPKIT_NOT_NATIVE_NEAR -> R.string.near_error_swapkit_not_native_near
            NearRefusal.SWAPKIT_DEPOSIT_NOT_IMPLICIT ->
                R.string.near_error_swapkit_deposit_not_implicit
            NearRefusal.SWAPKIT_DEPOSIT_RECEIVER_MISMATCH ->
                R.string.near_error_swapkit_deposit_receiver_mismatch
            NearRefusal.SWAPKIT_DEPOSIT_AMOUNT_MISMATCH ->
                R.string.near_error_swapkit_deposit_amount_mismatch
            NearRefusal.SWAPKIT_DEPOSIT_PREBUILT -> R.string.near_error_swapkit_deposit_prebuilt
            NearRefusal.SWAPKIT_DEPOSIT_MEMO -> R.string.near_error_swapkit_deposit_memo
        }
