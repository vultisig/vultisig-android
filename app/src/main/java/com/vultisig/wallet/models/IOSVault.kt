package com.vultisig.wallet.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class IOSVaultRoot(
    val vault: IOSVault,
    val version: String,
) : Parcelable

@Parcelize
data class IOSVault(
    val coins: List<Coin>?,
    val localPartyID: String,
    val pubKeyECDSA: String,
    val hexChainCode: String,
    val pubKeyEdDSA: String,
    val name: String,
    val signers: List<String>,
    val createdAt: Float,
    val keyshares: List<IOSKeyShare>,
) : Parcelable

@Parcelize
data class IOSKeyShare(
    val pubkey: String,
    val keyshare: String,
): Parcelable