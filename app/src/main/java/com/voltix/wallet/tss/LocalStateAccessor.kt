package com.voltix.wallet.tss

import com.voltix.wallet.models.KeyShare
import com.voltix.wallet.models.Vault

class LocalStateAccessor(private val vault: Vault) : tss.LocalStateAccessor {
    override fun getLocalState(pubKey: String): String {
        for (share in vault.Keyshares) {
            if (share.pubKey == pubKey) {
                return share.keyshare
            }
        }
        return ""
    }

    override fun saveLocalState(pubKey: String, localState: String) {
        // save the keyshare to vault
        vault.Keyshares += KeyShare(pubKey, localState)
    }
}