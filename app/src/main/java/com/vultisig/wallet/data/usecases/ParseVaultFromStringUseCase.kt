@file:OptIn(ExperimentalSerializationApi::class, ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import android.util.Base64
import com.google.gson.Gson
import com.vultisig.wallet.data.mappers.VaultFromOldJsonMapper
import com.vultisig.wallet.data.mappers.utils.MapHexToPlainString
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.OldJsonVaultRoot
import com.vultisig.wallet.data.models.proto.v1.VaultContainerProto
import com.vultisig.wallet.data.models.proto.v1.VaultProto
import com.vultisig.wallet.models.Vault
import io.ktor.util.decodeBase64Bytes
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.UUID
import javax.inject.Inject

internal interface ParseVaultFromStringUseCase : (String, String?) -> Vault

internal class ParseVaultFromStringUseCaseImpl @Inject constructor(
    private val vaultFromOldJsonMapper: VaultFromOldJsonMapper,
    private val mapHexToPlainString: MapHexToPlainString,
    private val encryption: Encryption,
    private val protoBuf: ProtoBuf,
    private val gson: Gson,
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
                encryption.decrypt(possiblyEncryptedVaultBytes, password)
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
            keyshares = proto.keyShares.filterNotNull().map { keyShare ->
                KeyShare(
                    pubKey = keyShare.publicKey,
                    keyShare = keyShare.keyshare
                )
            },
            coins = emptyList(),
        )
    }

    private fun parseOldVault(
        input: String,
        password: String?
    ): Result<Vault> = runCatching {
        val decrypted = mapHexToPlainString(
            if (password != null) {
                encryption.decrypt(Base64.decode(input, Base64.DEFAULT), password)
                    ?.decodeToString()
                    ?: error("Failed to decrypt the old vault")
            } else {
                input
            }
        )

        val fromJson = gson.fromJson(decrypted, OldJsonVaultRoot::class.java)
        vaultFromOldJsonMapper(fromJson)
    }

}