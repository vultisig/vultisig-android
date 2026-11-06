package com.vultisig.wallet.ui.models.deposit

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault

/**
 * The vault's address on [chain] for the other half of an LP pair, or `null` when the vault holds
 * no native coin there.
 *
 * Read from the held coin rather than derived: a KeyImport vault without a key for [chain] would
 * otherwise name an address derived from its root key that it can never enable or spend from, and a
 * withdrawal pays that half's share there.
 */
internal fun Vault.pairedLpAddress(chain: Chain): String? =
    coins.firstOrNull { it.chain == chain && it.isNativeToken }?.address?.takeIf { it.isNotBlank() }
