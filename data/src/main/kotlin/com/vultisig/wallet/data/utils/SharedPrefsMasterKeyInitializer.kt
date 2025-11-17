package com.vultisig.wallet.data.utils

import androidx.security.crypto.MasterKeys.AES256_GCM_SPEC
import androidx.security.crypto.MasterKeys.getOrCreate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SharedPrefsMasterKeyInitializer {
    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            getOrCreate(AES256_GCM_SPEC)
        }
    }
}