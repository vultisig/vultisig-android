package com.voltix.wallet.Tss

import com.voltix.wallet.models.Vault

class LocalStateAccessor(private val vault:Vault) : tss.LocalStateAccessor {
    override fun getLocalState(p0: String?): String {
        return ""
    }

    override fun saveLocalState(p0: String?, p1: String?) {

    }
}