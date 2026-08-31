package com.vultisig.wallet.data.crypto

import com.google.protobuf.CodedInputStream
import java.io.IOException

/**
 * Reads the memo a dApp put in the `signDirect` body it asked us to sign.
 *
 * Those bytes are a Cosmos SDK `TxBody`, whose memo is field 2. Parsing them as a wallet-core
 * `Cosmos.SigningInput` — where memo is field 5 — never matches that tag, so the memo decoded as
 * empty and the dApp's memo was silently replaced by the payload's. Returns null when the body
 * carries no memo or cannot be walked.
 */
internal fun cosmosTxBodyMemo(bodyBytes: ByteArray): String? =
    try {
        readMemoField(CodedInputStream.newInstance(bodyBytes))
    } catch (_: IOException) {
        null
    }

private fun readMemoField(input: CodedInputStream): String? {
    while (true) {
        val tag = input.readTag()
        when {
            tag == 0 -> return null
            tag == TX_BODY_MEMO_TAG ->
                return input.readStringRequireUtf8().takeIf(String::isNotEmpty)
            !input.skipField(tag) -> return null
        }
    }
}

private const val TX_BODY_MEMO_TAG = (2 shl 3) or 2
