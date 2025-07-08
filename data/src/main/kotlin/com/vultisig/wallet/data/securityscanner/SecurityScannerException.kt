package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.models.Chain

class SecurityScannerException(
    message: String,
    cause: Throwable? = null,
    val chain: Chain? = null,
    val transaction: String? = null
) : Exception(message, cause)