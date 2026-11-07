package com.vultisig.wallet.data.keygen

import com.silencelaboratories.godilithium.MldsaSecurityLevel

/**
 * The single ML-DSA level used by every keygen and keysign setup and session. A keyshare can only
 * sign at the level it was generated with, and all parties in a ceremony must use the same level.
 */
internal val MLDSA_LEVEL: MldsaSecurityLevel = MldsaSecurityLevel.MlDsa65

/**
 * Whether VultiServer runs ML-DSA at [MLDSA_LEVEL]. While it doesn't, a Fast Vault post-quantum
 * keygen starts a ceremony the server can't join, so its entry points stay closed for Fast Vaults.
 * Flip this once VultiServer moves to the same level.
 */
internal const val VULTISERVER_SUPPORTS_MLDSA_LEVEL = false
