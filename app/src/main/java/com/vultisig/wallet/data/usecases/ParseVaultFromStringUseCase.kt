@file:OptIn(ExperimentalSerializationApi::class, ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import android.util.Base64
import com.vultisig.wallet.data.mappers.VaultFromOldJsonMapper
import com.vultisig.wallet.data.mappers.utils.MapHexToPlainString
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.OldJsonVault
import com.vultisig.wallet.data.models.OldJsonVaultRoot
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.proto.v1.VaultContainerProto
import com.vultisig.wallet.data.models.proto.v1.VaultProto
import com.vultisig.wallet.data.models.proto.v1.toSigningLibType
import io.ktor.util.decodeBase64Bytes
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.UUID
import javax.inject.Inject

internal interface ParseVaultFromStringUseCase : (String, String?) -> Vault

internal class ParseVaultFromStringUseCaseImpl @Inject constructor(
    private val vaultFromOldJsonMapper: VaultFromOldJsonMapper,
    private val mapHexToPlainString: MapHexToPlainString,
    private val encryption: Encryption,
    private val protoBuf: ProtoBuf,
    private val json: Json,
) : ParseVaultFromStringUseCase {

    override fun invoke(input: String, password: String?): Vault =
        parseProtoBufVault(input, password)
            .getOrElse {
                if (it is java.lang.IllegalStateException && it.message == "Vault is encrypted, but no password provided") {
                    throw it
                }
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

        val possiblyEncryptedVaultBytes =
            containerProto.vault.decodeBase64Bytes().takeIf { it.isNotEmpty() }
                ?: error("Empty vault")


        val vaultBytes = if (containerProto.isEncrypted) {
            if (!password.isNullOrBlank()) {
                encryption.decrypt(possiblyEncryptedVaultBytes, password.toByteArray())
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
            libType = proto.libType.toSigningLibType(),
        )
    }

    private fun parseOldVault(
        input: String,
        password: String?,
    ): Result<Vault> = runCatching {
        val fromJson = try {
            val hexToPlainString = mapHexToPlainString(
                if (!password.isNullOrBlank()) {
                    encryption.decrypt(Base64.decode(input, Base64.DEFAULT), password.toByteArray())
                        ?.decodeToString()
                        ?: error("Failed to decrypt the old vault")
                } else {
                    input
                }
            )
            json.decodeFromString<OldJsonVaultRoot>(hexToPlainString).vault
        } catch (e: Exception) {
            json.decodeFromString<OldJsonVault>(input)
        }

        vaultFromOldJsonMapper(fromJson)
    }

}