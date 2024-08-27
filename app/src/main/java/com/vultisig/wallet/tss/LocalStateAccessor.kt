package com.vultisig.wallet.tss

import com.vultisig.wallet.models.KeyShare
import com.vultisig.wallet.models.Vault

internal class LocalStateAccessor(private val vault: Vault) : tss.LocalStateAccessor {
    override fun getLocalState(pubKey: String): String {
        for (share in vault.keyshares) {
            if (share.pubKey == pubKey) {
                return share.keyShare
            }
        }
        return ""
    }

    override fun saveLocalState(pubKey: String, localState: String) {
        // save the keyshare to vault
        vault.keyshares += KeyShare(pubKey, localState)
    }
}