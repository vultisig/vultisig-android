package com.vultisig.wallet.data.tss

import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.Vault

class LocalStateAccessor(private val vault: Vault) : tss.LocalStateAccessor {
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

class LocalStateAccessorImpl(
    private val keyshares: MutableList<KeyShare>,
) : tss.LocalStateAccessor {
    override fun getLocalState(pubKey: String): String {
        for (share in keyshares) {
            if (share.pubKey == pubKey) {
                return share.keyShare
            }
        }
        return ""
    }

    override fun saveLocalState(pubKey: String, localState: String) {
        keyshares += KeyShare(pubKey, localState)
    }
}