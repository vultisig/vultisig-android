@file:OptIn(ExperimentalSerializationApi::class, ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import android.util.Base64
import com.google.gson.Gson
import com.vultisig.wallet.common.CryptoManager
import com.vultisig.wallet.common.decodeFromHex
import com.vultisig.wallet.data.mappers.VaultIOSToAndroidMapper
import com.vultisig.wallet.data.models.proto.v1.VaultContainerProto
import com.vultisig.wallet.data.models.proto.v1.VaultProto
import com.vultisig.wallet.models.IOSVaultRoot
import com.vultisig.wallet.models.KeyShare
import com.vultisig.wallet.models.Vault
import io.ktor.util.decodeBase64Bytes
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.UUID
import javax.inject.Inject

internal interface ParseVaultFromStringUseCase : (String, String?) -> Vault

internal class ParseVaultFromStringUseCaseImpl @Inject constructor(
    private val protoBuf: ProtoBuf,
    private val vaultIOSToAndroidMapper: VaultIOSToAndroidMapper,
    private val gson: Gson,
    private val cryptoManager: CryptoManager,
) : ParseVaultFromStringUseCase {

    override fun invoke(input: String, password: String?): Vault =
        parseProtoBufVault(input, password)
            .getOrElse {
                parseOldVault(input, password)
                    .getOrThrow()
            }

    private fun parseProtoBufVault(
        input: String,
        password: String?,
    ): Result<Vault> = runCatching {
        val containerProto = protoBuf.decodeFromByteArray<VaultContainerProto>(
            input.decodeBase64Bytes(),
        )

        val possiblyEncryptedVaultBytes = containerProto.vault.decodeBase64Bytes()

        val vaultBytes = if (containerProto.isEncrypted) {
            if (password != null) {
                cryptoManager.decrypt(possiblyEncryptedVaultBytes, password)
                    ?: error("Failed to decrypt the vault")
            } else {
                error("Vault is encrypted, but no password provided")
            }
        } else {
            possiblyEncryptedVaultBytes
        }

        val proto: VaultProto = protoBuf.decodeFromByteArray(vaultBytes)

        Vault(
            id = UUID.randomUUID().toString(),
            name = proto.name,
            pubKeyECDSA = proto.publicKeyEcdsa,
            pubKeyEDDSA = proto.publicKeyEddsa,
            hexChainCode = proto.hexChainCode,
            localPartyID = proto.localPartyId,
            signers = proto.signers,
            resharePrefix = proto.resharePrefix,
            keyshares = proto.keyShares.filterNotNull().map { keyshare ->
                KeyShare(
                    pubKey = keyshare.publicKey,
                    keyshare = keyshare.keyshare
                )
            },
            coins = emptyList(),
        )
    }

    private fun parseOldVault(
        input: String,
        password: String?
    ): Result<Vault> = runCatching {
        val decrypted = if (password != null) {
            cryptoManager.decrypt(Base64.decode(input, Base64.DEFAULT), password)
                ?.decodeToString()
                ?: error("Failed to decrypt the old vault")
        } else {
            input
        }.decodeFromHex()

        val fromJson = gson.fromJson(decrypted, IOSVaultRoot::class.java)
        vaultIOSToAndroidMapper(fromJson)
    }

}