package com.vultisig.wallet.data.keygen

import com.silencelaboratories.godilithium.MldsaSecurityLevel

/**
 * The single ML-DSA level used by every keygen and keysign setup and session. A keyshare can only
 * sign at the level it was generated with, and all parties in a ceremony must use the same level.
 */
internal val MLDSA_LEVEL: MldsaSecurityLevel = MldsaSecurityLevel.MlDsa65
