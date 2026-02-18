package com.vultisig.wallet.data.models.proto.v1

import com.vultisig.wallet.data.models.SigningLibType
import vultisig.keygen.v1.LibType
import vultisig.vault.v1.Vault

typealias KeysignMessageProto = vultisig.keysign.v1.KeysignMessage
typealias KeysignPayloadProto = vultisig.keysign.v1.KeysignPayload
typealias CoinProto = vultisig.keysign.v1.Coin
typealias ThorChainSwapPayloadProto = vultisig.keysign.v1.THORChainSwapPayload
typealias SignAminoProto = vultisig.keysign.v1.SignAmino
typealias SignDirectProto = vultisig.keysign.v1.SignDirect
typealias SignSolanaProto = vultisig.keysign.v1.SignSolana
typealias KeygenMessageProto = vultisig.keygen.v1.KeygenMessage
typealias ReshareMessageProto = vultisig.keygen.v1.ReshareMessage
typealias VaultProto = Vault
typealias VaultContainerProto = vultisig.vault.v1.VaultContainer
typealias KeyShareProto = Vault.KeyShare


fun LibType.toSigningLibType() = when (this) {
    LibType.LIB_TYPE_GG20 -> SigningLibType.GG20
    LibType.LIB_TYPE_DKLS -> SigningLibType.DKLS
    LibType.LIB_TYPE_KEYIMPORT -> SigningLibType.KeyImport
}

fun SigningLibType.toProto() = when (this) {
    SigningLibType.GG20 -> LibType.LIB_TYPE_GG20
    SigningLibType.DKLS -> LibType.LIB_TYPE_DKLS
    SigningLibType.KeyImport -> LibType.LIB_TYPE_KEYIMPORT
}