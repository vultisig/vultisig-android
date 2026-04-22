package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.models.signer.JoinKeysignRequestJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault

/**
 * Single source of truth for the FastVault `/sign` request shape. The chain field uses [Chain.raw]
 * (wire string), not [Enum.name] (Kotlin case identifier) — server stores chain pubkeys keyed by
 * `.raw` at import time and matches case-insensitively at sign time.
 */
internal object JoinKeysignRequestBuilder {

    fun build(
        vault: Vault,
        chain: Chain?,
        derivePath: String,
        sessionId: String,
        encryptionKeyHex: String,
        messages: List<String>,
        password: String,
        tssKeysignType: TssKeyType,
    ): JoinKeysignRequestJson =
        JoinKeysignRequestJson(
            publicKeyEcdsa = vault.pubKeyECDSA,
            messages = messages,
            sessionId = sessionId,
            hexEncryptionKey = encryptionKeyHex,
            derivePath = derivePath,
            isEcdsa = tssKeysignType == TssKeyType.ECDSA,
            password = password,
            chain = chain?.raw ?: "",
            mldsa = tssKeysignType == TssKeyType.MLDSA,
        )
}
